package prod.tint_wym.novora_backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import prod.tint_wym.novora_backend.dto.ClaimPolicyDtos;
import prod.tint_wym.novora_backend.entity.ClaimCategoryLimit;
import prod.tint_wym.novora_backend.entity.ClaimPolicy;
import prod.tint_wym.novora_backend.repository.ClaimCategoryLimitRepository;
import prod.tint_wym.novora_backend.repository.ClaimPolicyRepository;
import prod.tint_wym.novora_backend.tenancy.TenantContext;

@Service
@Transactional(readOnly = true)
public class ClaimPolicyService {

    private static final List<DefaultLimit> DEFAULT_LIMITS = List.of(
            new DefaultLimit("Meal allowance", new BigDecimal("30.00"), null, "THRESHOLD"),
            new DefaultLimit("Transport", null, new BigDecimal("200.00"), "THRESHOLD"),
            new DefaultLimit("Medical", null, new BigDecimal("500.00"), "ALWAYS"),
            new DefaultLimit("Travel", null, new BigDecimal("2000.00"), "ALWAYS"),
            new DefaultLimit("Office supplies", null, new BigDecimal("150.00"), "THRESHOLD"));

    private final ClaimPolicyRepository policyRepository;
    private final ClaimCategoryLimitRepository limitRepository;

    public ClaimPolicyService(
            ClaimPolicyRepository policyRepository, ClaimCategoryLimitRepository limitRepository) {
        this.policyRepository = policyRepository;
        this.limitRepository = limitRepository;
    }

    private UUID requireOrganizationId() {
        UUID orgId = TenantContext.get();
        if (orgId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No organization context");
        }
        return orgId;
    }

    public ClaimPolicy requirePolicy(UUID orgId) {
        return policyRepository.findById(orgId).orElseGet(() -> defaultPolicy(orgId));
    }

    private ClaimPolicy defaultPolicy(UUID orgId) {
        ClaimPolicy p = new ClaimPolicy();
        p.setOrganizationId(orgId);
        p.setReceiptRequiredAbove(new BigDecimal("50.00"));
        p.setMaxClaimAgeDays(30);
        p.setBlockFutureDates(true);
        p.setEnforceReceipt(true);
        p.setEnforceClaimAge(true);
        p.setUpdatedAt(LocalDateTime.now());
        return p;
    }

    public List<ClaimCategoryLimit> limitsFor(UUID orgId) {
        List<ClaimCategoryLimit> existing = limitRepository.findAllByOrganizationIdOrderByCategoryAsc(orgId);
        if (!existing.isEmpty()) {
            return existing;
        }
        List<ClaimCategoryLimit> defaults = new ArrayList<>();
        for (DefaultLimit d : DEFAULT_LIMITS) {
            ClaimCategoryLimit row = new ClaimCategoryLimit();
            row.setOrganizationId(orgId);
            row.setCategory(d.category());
            row.setDailyLimit(d.daily());
            row.setMonthlyLimit(d.monthly());
            row.setReceiptRequired(d.receiptRequired());
            row.setCreatedAt(LocalDateTime.now());
            defaults.add(row);
        }
        return defaults;
    }

    public ClaimPolicyDtos.ClaimPolicyResponse getPolicy() {
        UUID orgId = requireOrganizationId();
        ClaimPolicy policy = requirePolicy(orgId);
        List<ClaimPolicyDtos.CategoryLimitResponse> limits = limitsFor(orgId).stream()
                .map(l -> new ClaimPolicyDtos.CategoryLimitResponse(
                        l.getId(),
                        l.getCategory(),
                        l.getDailyLimit(),
                        l.getMonthlyLimit(),
                        l.getReceiptRequired()))
                .toList();
        return new ClaimPolicyDtos.ClaimPolicyResponse(
                policy.getReceiptRequiredAbove(),
                policy.getMaxClaimAgeDays(),
                policy.isBlockFutureDates(),
                policy.isEnforceReceipt(),
                policy.isEnforceClaimAge(),
                limits);
    }

    @Transactional
    public ClaimPolicyDtos.ClaimPolicyResponse updatePolicy(ClaimPolicyDtos.UpdateClaimPolicyRequest request) {
        UUID orgId = requireOrganizationId();
        ClaimPolicy policy = policyRepository.findById(orgId).orElseGet(() -> {
            ClaimPolicy created = defaultPolicy(orgId);
            created.setOrganizationId(orgId);
            return created;
        });
        policy.setReceiptRequiredAbove(request.receiptRequiredAbove());
        policy.setMaxClaimAgeDays(request.maxClaimAgeDays());
        policy.setBlockFutureDates(Boolean.TRUE.equals(request.blockFutureDates()));
        policy.setEnforceReceipt(Boolean.TRUE.equals(request.enforceReceipt()));
        policy.setEnforceClaimAge(Boolean.TRUE.equals(request.enforceClaimAge()));
        policy.setUpdatedAt(LocalDateTime.now());
        policyRepository.save(policy);

        List<ClaimCategoryLimit> existing = limitRepository.findAllByOrganizationIdOrderByCategoryAsc(orgId);
        limitRepository.deleteAll(existing);

        if (request.categoryLimits() != null) {
            for (ClaimPolicyDtos.CategoryLimitRequest item : request.categoryLimits()) {
                String receipt = item.receiptRequired().trim().toUpperCase(Locale.US);
                if (!receipt.equals("ALWAYS") && !receipt.equals("THRESHOLD") && !receipt.equals("NEVER")) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "receiptRequired must be ALWAYS, THRESHOLD, or NEVER");
                }
                ClaimCategoryLimit row = new ClaimCategoryLimit();
                row.setOrganizationId(orgId);
                row.setCategory(item.category().trim());
                row.setDailyLimit(item.dailyLimit());
                row.setMonthlyLimit(item.monthlyLimit());
                row.setReceiptRequired(receipt);
                row.setCreatedAt(LocalDateTime.now());
                limitRepository.save(row);
            }
        }
        return getPolicy();
    }

    private record DefaultLimit(
            String category, BigDecimal daily, BigDecimal monthly, String receiptRequired) {
    }
}
