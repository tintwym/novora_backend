package prod.tint_wym.novora_backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.CrmIntegrationDtos;
import prod.tint_wym.novora_backend.entity.CrmConnection;
import prod.tint_wym.novora_backend.entity.CrmLinkCode;
import prod.tint_wym.novora_backend.entity.Department;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.IntegrationOutboxEvent;
import prod.tint_wym.novora_backend.entity.Organization;
import prod.tint_wym.novora_backend.entity.Position;
import prod.tint_wym.novora_backend.integration.CrmIntegrationSettings;
import prod.tint_wym.novora_backend.repository.CrmConnectionRepository;
import prod.tint_wym.novora_backend.repository.CrmLinkCodeRepository;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.IntegrationOutboxRepository;
import prod.tint_wym.novora_backend.repository.OrganizationRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;
import tools.jackson.databind.json.JsonMapper;

/**
 * Links a workspace to its company in Novora CRM and queues employee changes for delivery.
 *
 * <p>Linking is always started here: an HR admin creates a short-lived one-time code and a CRM
 * admin redeems it. That proves the person linking the CRM controls this workspace, so one
 * customer can never pull another customer's employee list into their CRM.
 */
@Service
public class CrmIntegrationService {

    public static final String EVENT_EMPLOYEE_CHANGED = "employee.changed";

    static final Duration LINK_CODE_TTL = Duration.ofMinutes(15);
    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final String TOMBSTONE_EMAIL_SUFFIX = "@tombstone.local";

    private final SecureRandom random = new SecureRandom();
    private final CrmIntegrationSettings settings;
    private final CrmConnectionRepository connections;
    private final CrmLinkCodeRepository linkCodes;
    private final IntegrationOutboxRepository outbox;
    private final EmployeeRepository employees;
    private final OrganizationRepository organizations;
    private final JsonMapper jsonMapper;

    public CrmIntegrationService(
            CrmIntegrationSettings settings,
            CrmConnectionRepository connections,
            CrmLinkCodeRepository linkCodes,
            IntegrationOutboxRepository outbox,
            EmployeeRepository employees,
            OrganizationRepository organizations,
            JsonMapper jsonMapper) {
        this.settings = settings;
        this.connections = connections;
        this.linkCodes = linkCodes;
        this.outbox = outbox;
        this.employees = employees;
        this.organizations = organizations;
        this.jsonMapper = jsonMapper;
    }

    // ---------------------------------------------------------------- admin (signed-in HR admin)

    @Transactional(readOnly = true)
    public CrmIntegrationDtos.ConnectionStatus status() {
        UUID orgId = requireOrganizationId();
        CrmConnection connection = connections.findByOrganizationId(orgId)
                .filter(CrmConnection::isActive)
                .orElse(null);
        return new CrmIntegrationDtos.ConnectionStatus(
                settings.isReady(),
                connection != null,
                connection != null ? connection.getCrmCompanyId() : null,
                connection != null ? connection.getConnectedAt() : null,
                outbox.countByOrganizationIdAndDeliveredAtIsNull(orgId));
    }

    @Transactional
    public CrmIntegrationDtos.LinkCodeResponse createLinkCode(String callerEmail) {
        requireReady();
        UUID orgId = requireOrganizationId();
        LocalDateTime now = LocalDateTime.now();
        linkCodes.retireOpenCodes(orgId, now);

        String code = generateCode();
        CrmLinkCode row = new CrmLinkCode();
        row.setId(UUID.randomUUID());
        row.setOrganizationId(orgId);
        row.setCodeHash(hashLinkCode(code));
        row.setExpiresAt(now.plus(LINK_CODE_TTL));
        row.setCreatedBy(callerEmail);
        row.setCreatedAt(now);
        linkCodes.save(row);
        return new CrmIntegrationDtos.LinkCodeResponse(code, row.getExpiresAt());
    }

    @Transactional
    public void disconnect() {
        UUID orgId = requireOrganizationId();
        connections.findByOrganizationId(orgId).ifPresent(connection -> {
            LocalDateTime now = LocalDateTime.now();
            connection.setStatus(CrmConnection.STATUS_DISCONNECTED);
            connection.setDisconnectedAt(now);
            connection.setUpdatedAt(now);
            connections.save(connection);
        });
        outbox.deletePending(orgId);
    }

    // ------------------------------------------------------- machine (signed request from Novora CRM)

    @Transactional
    public CrmIntegrationDtos.RedeemResponse redeem(String rawCode, String crmCompanyId) {
        requireReady();
        if (crmCompanyId == null || crmCompanyId.isBlank() || crmCompanyId.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "crmCompanyId is required");
        }
        String normalized = normalizeCode(rawCode);
        LocalDateTime now = LocalDateTime.now();
        CrmLinkCode code = normalized == null ? null : linkCodes.findByCodeHash(hashLinkCode(normalized)).orElse(null);
        if (code == null || code.getUsedAt() != null || code.getExpiresAt().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This code is invalid or has expired. Ask your HR admin for a new one.");
        }
        code.setUsedAt(now);
        linkCodes.save(code);

        Organization org = organizations.findById(code.getOrganizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Workspace no longer exists"));

        CrmConnection connection = connections.findByOrganizationId(org.getId()).orElseGet(() -> {
            CrmConnection fresh = new CrmConnection();
            fresh.setId(UUID.randomUUID());
            fresh.setOrganizationId(org.getId());
            fresh.setCreatedAt(now);
            return fresh;
        });
        connection.setCrmCompanyId(crmCompanyId.trim());
        connection.setStatus(CrmConnection.STATUS_ACTIVE);
        connection.setConnectedAt(now);
        connection.setDisconnectedAt(null);
        connection.setUpdatedAt(now);
        connections.save(connection);

        return new CrmIntegrationDtos.RedeemResponse(org.getId(), org.getName());
    }

    /**
     * Full roster for a reconcile, including terminated employees so the CRM can tell "left the
     * company" apart from "missing from the response". The caller must have set
     * {@link TenantContext} to {@code organizationId} before entering this transaction, because
     * Hibernate fixes the tenant when the session opens.
     */
    @Transactional(readOnly = true)
    public CrmIntegrationDtos.EmployeeListResponse listEmployees(UUID organizationId, String crmCompanyId) {
        requireReady();
        if (!Objects.equals(TenantContext.get(), organizationId)) {
            throw new IllegalStateException("TenantContext must be set to the requested organization");
        }
        requireLinkedTo(organizationId, crmCompanyId);
        List<CrmIntegrationDtos.EmployeeSnapshot> roster = employees.findAll().stream()
                .sorted(Comparator.comparing(Employee::getId))
                .map(e -> toSnapshot(e, organizationId))
                .toList();
        return new CrmIntegrationDtos.EmployeeListResponse(organizationId, LocalDateTime.now(), roster);
    }

    // ------------------------------------------------------------------- outbox (same transaction)

    /**
     * Queues the employee's current state for delivery to the linked CRM. Must be called inside the
     * transaction that changed the employee, after the change has been applied.
     */
    @Transactional
    public void recordEmployeeChange(Employee employee) {
        if (!settings.isReady() || employee == null) {
            return;
        }
        // A just-persisted employee may not have its @TenantId stamped until the insert is flushed.
        UUID orgId = employee.getOrganizationId() != null ? employee.getOrganizationId() : TenantContext.get();
        if (orgId == null) {
            return;
        }
        boolean linked = connections.findByOrganizationId(orgId)
                .map(CrmConnection::isActive)
                .orElse(false);
        if (!linked) {
            return;
        }
        UUID eventId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId);
        payload.put("type", EVENT_EMPLOYEE_CHANGED);
        payload.put("occurredAt", Instant.now().toString());
        payload.put("organizationId", orgId);
        payload.put("employee", toSnapshot(employee, orgId));

        LocalDateTime now = LocalDateTime.now();
        IntegrationOutboxEvent event = new IntegrationOutboxEvent();
        event.setId(eventId);
        event.setOrganizationId(orgId);
        event.setEventType(EVENT_EMPLOYEE_CHANGED);
        event.setPayload(jsonMapper.writeValueAsString(payload));
        event.setAttempts(0);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        outbox.save(event);
    }

    // ------------------------------------------------------------------------------------ helpers

    static CrmIntegrationDtos.EmployeeSnapshot toSnapshot(Employee e, UUID organizationId) {
        Department dept = e.getDepartment();
        Position pos = e.getPosition();
        String email = e.getEmail();
        boolean terminated = "terminated".equalsIgnoreCase(e.getStatus());
        if (terminated || (email != null && email.toLowerCase().endsWith(TOMBSTONE_EMAIL_SUFFIX))) {
            email = null;
        }
        return new CrmIntegrationDtos.EmployeeSnapshot(
                e.getId(),
                organizationId,
                e.getFirstName(),
                e.getLastName(),
                email,
                terminated ? null : e.getEmployeeCode(),
                e.getStatus() == null ? null : e.getStatus().toLowerCase(),
                dept != null ? dept.getId() : null,
                dept != null ? dept.getCode() : null,
                dept != null ? dept.getName() : null,
                pos != null ? pos.getTitle() : null,
                pos != null ? pos.getLevel() : null,
                e.getManager() != null ? e.getManager().getId() : null,
                dept != null && dept.getManager() != null ? dept.getManager().getId() : null,
                e.getHireDate(),
                e.getEndDate(),
                e.getUpdatedAt());
    }

    /** Throws 403 unless the workspace is actively linked to exactly this CRM company. */
    public void requireLinkedTo(UUID organizationId, String crmCompanyId) {
        CrmConnection connection = connections.findByOrganizationId(organizationId)
                .filter(CrmConnection::isActive)
                .orElse(null);
        if (connection == null || crmCompanyId == null || !connection.getCrmCompanyId().equals(crmCompanyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This workspace is not linked to that CRM company");
        }
    }

    private void requireReady() {
        if (!settings.isReady()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The Novora CRM integration is not configured on this server");
        }
    }

    private static UUID requireOrganizationId() {
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        return orgId;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH + 1);
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i == CODE_LENGTH / 2) {
                sb.append('-');
            }
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Uppercases and strips spaces/dashes so "abcd efgh" and "ABCD-EFGH" match; null when the shape is wrong. */
    static String normalizeCode(String raw) {
        if (raw == null) {
            return null;
        }
        String compact = raw.toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (compact.length() != CODE_LENGTH) {
            return null;
        }
        return compact.substring(0, CODE_LENGTH / 2) + "-" + compact.substring(CODE_LENGTH / 2);
    }

    static String hashLinkCode(String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
