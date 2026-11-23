package prod.tint_wym.novora_backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.ClaimDtos;
import prod.tint_wym.novora_backend.entity.ClaimCategoryLimit;
import prod.tint_wym.novora_backend.entity.ClaimPolicy;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.ExpenseClaim;
import prod.tint_wym.novora_backend.entity.Organization;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.ExpenseClaimRepository;
import prod.tint_wym.novora_backend.repository.OrganizationRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@Service
@Transactional(readOnly = true)
public class ClaimService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Singapore");
    private static final int MAX_RECEIPT_CHARS = 8_000_000; // ~6MB base64

    private final ExpenseClaimRepository claimRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final OrganizationRepository organizationRepository;
    private final ClaimPolicyService claimPolicyService;

    public ClaimService(
            ExpenseClaimRepository claimRepository,
            EmployeeRepository employeeRepository,
            NotificationService notificationService,
            OrganizationRepository organizationRepository,
            ClaimPolicyService claimPolicyService) {
        this.claimRepository = claimRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
        this.organizationRepository = organizationRepository;
        this.claimPolicyService = claimPolicyService;
    }

    private UUID requireOrganizationId() {
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        return orgId;
    }

    private Employee employeeForEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.US);
        return employeeRepository.findByAppUser_EmailIgnoreCase(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found for user"));
    }

    private static String name(Employee e) {
        return (e.getFirstName() + " " + e.getLastName()).trim();
    }

    private static Instant toInstant(LocalDateTime t) {
        return t == null ? null : t.atZone(ZONE).toInstant();
    }

    private ClaimDtos.ClaimResponse toResponse(ExpenseClaim c) {
        Employee emp = c.getEmployee();
        String decidedBy = null;
        if (c.getDecidedBy() != null) {
            decidedBy = name(c.getDecidedBy());
        }
        String dept = emp.getDepartment() != null ? emp.getDepartment().getName() : null;
        boolean hasReceipt =
                (c.getReceiptContentBase64() != null && !c.getReceiptContentBase64().isBlank())
                        || (c.getReceiptUrl() != null && !c.getReceiptUrl().isBlank());
        return new ClaimDtos.ClaimResponse(
                c.getId(),
                emp.getId(),
                name(emp),
                dept,
                c.getCategory(),
                c.getClaimDate(),
                c.getAmount(),
                c.getCurrency(),
                c.getVendor(),
                c.getDescription(),
                c.getStatus().toUpperCase(Locale.US),
                c.getDecisionNote(),
                decidedBy,
                toInstant(c.getDecidedAt()),
                toInstant(c.getCreatedAt()),
                hasReceipt,
                c.getReceiptName(),
                c.getReceiptUrl());
    }

    public List<ClaimDtos.ClaimResponse> myClaims(String email) {
        Employee e = employeeForEmail(email);
        return claimRepository.findAllByEmployee_IdOrderByCreatedAtDesc(e.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ClaimDtos.ClaimResponse> listAllClaims() {
        return claimRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ClaimDtos.ClaimResponse> listPendingClaims() {
        return claimRepository.findAllByStatusIgnoreCaseOrderByCreatedAtDesc("pending").stream()
                .map(this::toResponse)
                .toList();
    }

    private void enforcePolicy(UUID orgId, Employee employee, ClaimDtos.CreateClaimRequest request, boolean hasReceipt) {
        ClaimPolicy policy = claimPolicyService.requirePolicy(orgId);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate claimDate = request.claimDate();

        if (policy.isBlockFutureDates() && claimDate.isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Claim date cannot be in the future");
        }
        if (policy.isEnforceClaimAge()) {
            long ageDays = ChronoUnit.DAYS.between(claimDate, today);
            if (ageDays > policy.getMaxClaimAgeDays()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Claim date is older than " + policy.getMaxClaimAgeDays() + " days");
            }
        }

        ClaimCategoryLimit categoryLimit = claimPolicyService.limitsFor(orgId).stream()
                .filter(l -> l.getCategory().equalsIgnoreCase(request.category().trim()))
                .findFirst()
                .orElse(null);

        String receiptMode = categoryLimit != null
                ? categoryLimit.getReceiptRequired().toUpperCase(Locale.US)
                : "THRESHOLD";
        BigDecimal threshold = policy.getReceiptRequiredAbove() == null
                ? new BigDecimal("50.00")
                : policy.getReceiptRequiredAbove();
        boolean receiptRequired = false;
        if (policy.isEnforceReceipt()) {
            if ("ALWAYS".equals(receiptMode)) {
                receiptRequired = true;
            } else if ("THRESHOLD".equals(receiptMode)
                    && request.amount().compareTo(threshold) > 0) {
                receiptRequired = true;
            }
        }
        if (receiptRequired && !hasReceipt) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Receipt attachment is required for this claim");
        }

        if (categoryLimit != null) {
            if (categoryLimit.getDailyLimit() != null) {
                BigDecimal used = java.util.Optional.ofNullable(
                                claimRepository.sumForEmployeeCategoryOnDate(
                                        employee.getId(), request.category().trim(), claimDate))
                        .orElse(BigDecimal.ZERO);
                if (used.add(request.amount()).compareTo(categoryLimit.getDailyLimit()) > 0) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Daily limit exceeded for " + categoryLimit.getCategory()
                                    + " (" + categoryLimit.getDailyLimit() + ")");
                }
            }
            if (categoryLimit.getMonthlyLimit() != null) {
                LocalDate monthStart = claimDate.withDayOfMonth(1);
                LocalDate monthEnd = claimDate.withDayOfMonth(claimDate.lengthOfMonth());
                BigDecimal used = java.util.Optional.ofNullable(
                                claimRepository.sumForEmployeeCategoryInRange(
                                        employee.getId(),
                                        request.category().trim(),
                                        monthStart,
                                        monthEnd))
                        .orElse(BigDecimal.ZERO);
                if (used.add(request.amount()).compareTo(categoryLimit.getMonthlyLimit()) > 0) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Monthly limit exceeded for " + categoryLimit.getCategory()
                                    + " (" + categoryLimit.getMonthlyLimit() + ")");
                }
            }
        }
    }

    @Transactional
    public ClaimDtos.ClaimResponse createMyClaim(String email, ClaimDtos.CreateClaimRequest request) {
        UUID orgId = requireOrganizationId();
        Employee e;
        if (request.employeeId() != null) {
            e = employeeRepository
                    .findByIdAndOrganizationId(request.employeeId(), orgId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claimant employee not found"));
        } else {
            e = employeeForEmail(email);
        }

        String receiptContent =
                request.receiptContentBase64() == null || request.receiptContentBase64().isBlank()
                        ? null
                        : request.receiptContentBase64().trim();
        if (receiptContent != null && receiptContent.length() > MAX_RECEIPT_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Receipt file is too large");
        }
        String receiptUrl =
                request.receiptUrl() == null || request.receiptUrl().isBlank()
                        ? null
                        : request.receiptUrl().trim();
        String receiptName =
                request.receiptName() == null || request.receiptName().isBlank()
                        ? null
                        : request.receiptName().trim();
        boolean hasReceipt = receiptContent != null || receiptUrl != null;

        enforcePolicy(orgId, e, request, hasReceipt);

        LocalDateTime now = LocalDateTime.now();
        ExpenseClaim claim = new ExpenseClaim();
        claim.setOrganizationId(orgId);
        claim.setEmployee(e);
        claim.setCategory(request.category().trim());
        claim.setClaimDate(request.claimDate());
        claim.setAmount(request.amount());
        claim.setCurrency(
                request.currency() == null || request.currency().isBlank()
                        ? organizationRepository.findById(orgId).map(Organization::getCurrency).orElse(Organization.DEFAULT_CURRENCY)
                        : request.currency().trim().toUpperCase(Locale.US));
        claim.setVendor(request.vendor() == null || request.vendor().isBlank() ? null : request.vendor().trim());
        claim.setDescription(
                request.description() == null || request.description().isBlank()
                        ? null
                        : request.description().trim());
        claim.setReceiptName(receiptName);
        claim.setReceiptContentBase64(receiptContent);
        claim.setReceiptUrl(receiptUrl);
        claim.setStatus("pending");
        claim.setCreatedAt(now);
        claim.setUpdatedAt(now);
        ExpenseClaim saved = claimRepository.save(claim);
        if (receiptContent != null && receiptUrl == null) {
            saved.setReceiptUrl("novora://claims/" + saved.getId() + "/receipt");
            saved = claimRepository.save(saved);
        }
        return toResponse(saved);
    }

    @Transactional
    public ClaimDtos.ClaimResponse decideClaim(String adminEmail, UUID claimId, ClaimDtos.DecideClaimRequest request) {
        UUID orgId = requireOrganizationId();
        ExpenseClaim claim = claimRepository
                .findByIdAndOrganizationId(claimId, orgId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Claim not found"));
        if (!"pending".equalsIgnoreCase(claim.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Claim is already decided");
        }
        String decision = request.decision().trim().toUpperCase(Locale.US);
        if (!decision.equals("APPROVE") && !decision.equals("REJECT")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be APPROVE or REJECT");
        }
        Employee admin = employeeForEmail(adminEmail);
        if (admin.getId().equals(claim.getEmployee().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot approve or reject your own claim");
        }
        claim.setStatus(decision.equals("APPROVE") ? "approved" : "rejected");
        claim.setDecisionNote(request.note());
        claim.setDecidedBy(admin);
        claim.setDecidedAt(LocalDateTime.now());
        claim.setUpdatedAt(LocalDateTime.now());
        ExpenseClaim saved = claimRepository.save(claim);
        try {
            if (saved.getEmployee().getAppUser() != null) {
                String decisionLabel = decision.equals("APPROVE") ? "approved" : "rejected";
                notificationService.createNotification(
                        saved.getEmployee().getAppUser().getId(),
                        "Claim " + decisionLabel,
                        "Your expense claim (" + saved.getCategory() + ") was " + decisionLabel + ".",
                        "claim");
            }
        } catch (Exception ignored) {
            // best-effort
        }
        return toResponse(saved);
    }
}
