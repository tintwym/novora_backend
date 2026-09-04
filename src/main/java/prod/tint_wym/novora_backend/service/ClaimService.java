package prod.tint_wym.novora_backend.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.ClaimDtos;
import prod.tint_wym.novora_backend.entity.Employee;
import prod.tint_wym.novora_backend.entity.ExpenseClaim;
import prod.tint_wym.novora_backend.repository.EmployeeRepository;
import prod.tint_wym.novora_backend.repository.ExpenseClaimRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@Service
@Transactional(readOnly = true)
public class ClaimService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final ExpenseClaimRepository claimRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;

    public ClaimService(
            ExpenseClaimRepository claimRepository,
            EmployeeRepository employeeRepository,
            NotificationService notificationService) {
        this.claimRepository = claimRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
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
                toInstant(c.getCreatedAt()));
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

    @Transactional
    public ClaimDtos.ClaimResponse createMyClaim(String email, ClaimDtos.CreateClaimRequest request) {
        Employee e = employeeForEmail(email);
        LocalDateTime now = LocalDateTime.now();
        ExpenseClaim claim = new ExpenseClaim();
        claim.setOrganizationId(requireOrganizationId());
        claim.setEmployee(e);
        claim.setCategory(request.category().trim());
        claim.setClaimDate(request.claimDate());
        claim.setAmount(request.amount());
        claim.setCurrency(
                request.currency() == null || request.currency().isBlank()
                        ? "SGD"
                        : request.currency().trim().toUpperCase(Locale.US));
        claim.setVendor(request.vendor() == null || request.vendor().isBlank() ? null : request.vendor().trim());
        claim.setDescription(
                request.description() == null || request.description().isBlank()
                        ? null
                        : request.description().trim());
        claim.setStatus("pending");
        claim.setCreatedAt(now);
        claim.setUpdatedAt(now);
        return toResponse(claimRepository.save(claim));
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
