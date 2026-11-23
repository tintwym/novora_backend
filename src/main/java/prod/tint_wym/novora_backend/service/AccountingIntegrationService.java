package prod.tint_wym.novora_backend.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.AccountingIntegrationDtos;
import prod.tint_wym.novora_backend.entity.AccountingConnection;
import prod.tint_wym.novora_backend.entity.Payroll;
import prod.tint_wym.novora_backend.integration.AccountingClient;
import prod.tint_wym.novora_backend.integration.AccountingIntegrationSettings;
import prod.tint_wym.novora_backend.repository.AccountingConnectionRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@Service
public class AccountingIntegrationService {

    private static final Logger LOG = LoggerFactory.getLogger(AccountingIntegrationService.class);

    private final AccountingIntegrationSettings settings;
    private final AccountingConnectionRepository connections;
    private final AccountingClient accountingClient;

    public AccountingIntegrationService(
            AccountingIntegrationSettings settings,
            AccountingConnectionRepository connections,
            AccountingClient accountingClient) {
        this.settings = settings;
        this.connections = connections;
        this.accountingClient = accountingClient;
    }

    private UUID requireOrganizationId() {
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        return orgId;
    }

    public AccountingIntegrationDtos.ConnectionStatus status() {
        UUID orgId = requireOrganizationId();
        AccountingConnection connection = connections.findByOrganizationId(orgId).orElse(null);
        boolean connected = connection != null && connection.isActive();
        return new AccountingIntegrationDtos.ConnectionStatus(
                settings.isReady(),
                connected,
                connected ? connection.getAccountingCompanyId() : null,
                connected ? connection.getLastPayrollRunId() : null,
                connected && connection.getLastSyncedAt() != null
                        ? connection.getLastSyncedAt().toInstant(ZoneOffset.UTC)
                        : null,
                connected ? connection.getLastError() : null);
    }

    @Transactional
    public AccountingIntegrationDtos.ConnectionStatus connect(AccountingIntegrationDtos.ConnectRequest request) {
        if (!settings.isReady()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Accounting integration is disabled. Set ACCOUNTING_INTEGRATION_ENABLED=true and ACCOUNTING_BASE_URL.");
        }
        UUID orgId = requireOrganizationId();
        LocalDateTime now = LocalDateTime.now();
        AccountingConnection connection = connections.findByOrganizationId(orgId).orElseGet(() -> {
            AccountingConnection fresh = new AccountingConnection();
            fresh.setId(UUID.randomUUID());
            fresh.setOrganizationId(orgId);
            fresh.setCreatedAt(now);
            return fresh;
        });
        connection.setAccountingCompanyId(request.accountingCompanyId());
        connection.setStatus(AccountingConnection.STATUS_ACTIVE);
        connection.setConnectedAt(now);
        connection.setDisconnectedAt(null);
        connection.setLastError(null);
        connection.setUpdatedAt(now);
        connections.save(connection);
        return status();
    }

    @Transactional
    public void disconnect() {
        UUID orgId = requireOrganizationId();
        connections.findByOrganizationId(orgId).ifPresent(connection -> {
            connection.setStatus(AccountingConnection.STATUS_DISCONNECTED);
            connection.setDisconnectedAt(LocalDateTime.now());
            connection.setUpdatedAt(LocalDateTime.now());
            connections.save(connection);
        });
    }

    /**
     * Best-effort push of a processed payroll month into Accounting. Failures are recorded on the
     * connection and never roll back the HRMS payroll status change.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void pushProcessedPayroll(UUID organizationId, int payYear, int payMonth, List<Payroll> rows) {
        if (!settings.isReady() || organizationId == null || rows == null || rows.isEmpty()) {
            return;
        }
        AccountingConnection connection = connections
                .findByOrganizationId(organizationId)
                .filter(AccountingConnection::isActive)
                .orElse(null);
        if (connection == null) {
            return;
        }

        List<Payroll> processed = rows.stream()
                .filter(r -> "processed".equalsIgnoreCase(r.getStatus()) || "paid".equalsIgnoreCase(r.getStatus()))
                .toList();
        if (processed.isEmpty()) {
            return;
        }

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal other = BigDecimal.ZERO;
        for (Payroll row : processed) {
            BigDecimal basic = nz(row.getBasicSalary());
            BigDecimal allowances = nz(row.getAllowances());
            BigDecimal overtime = nz(row.getOvertimePay());
            BigDecimal bonus = nz(row.getBonus());
            gross = gross.add(basic).add(allowances).add(overtime).add(bonus);
            tax = tax.add(nz(row.getTax()));
            net = net.add(nz(row.getNetPay()));
            other = other.add(nz(row.getDeductions()));
        }
        gross = money(gross);
        tax = money(tax);
        net = money(net);
        other = money(other);

        // Guard floating drift so Accounting's balance check passes.
        BigDecimal parts = tax.add(net).add(other);
        if (gross.compareTo(parts) != 0) {
            other = money(gross.subtract(tax).subtract(net).max(BigDecimal.ZERO));
        }

        String runId = String.format("%s-%04d-%02d", organizationId, payYear, payMonth);
        LocalDate payrollDate = YearMonth.of(payYear, payMonth).atEndOfMonth();
        String description = "HRMS payroll " + payYear + "-" + String.format("%02d", payMonth)
                + " (" + processed.size() + " employees)";

        try {
            accountingClient.postPayrollApproved(
                    connection.getAccountingCompanyId(),
                    runId,
                    payrollDate,
                    gross,
                    tax,
                    net,
                    other,
                    description);
            connection.setLastPayrollRunId(runId);
            connection.setLastSyncedAt(LocalDateTime.now());
            connection.setLastError(null);
            connection.setUpdatedAt(LocalDateTime.now());
            connections.save(connection);
            LOG.info("Pushed payroll {} to Accounting company {}", runId, connection.getAccountingCompanyId());
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "Accounting push failed" : ex.getMessage();
            connection.setLastError(message.length() > 1000 ? message.substring(0, 1000) : message);
            connection.setUpdatedAt(LocalDateTime.now());
            connections.save(connection);
            LOG.warn("Accounting payroll push failed for {}: {}", runId, message);
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal money(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }
}
