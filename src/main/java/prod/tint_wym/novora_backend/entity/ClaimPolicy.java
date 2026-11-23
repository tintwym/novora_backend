package prod.tint_wym.novora_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "claim_policies")
public class ClaimPolicy {

    @Id
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "receipt_required_above", nullable = false, precision = 15, scale = 2)
    private BigDecimal receiptRequiredAbove = new BigDecimal("50.00");

    @Column(name = "max_claim_age_days", nullable = false)
    private int maxClaimAgeDays = 30;

    @Column(name = "block_future_dates", nullable = false)
    private boolean blockFutureDates = true;

    @Column(name = "enforce_receipt", nullable = false)
    private boolean enforceReceipt = true;

    @Column(name = "enforce_claim_age", nullable = false)
    private boolean enforceClaimAge = true;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public BigDecimal getReceiptRequiredAbove() {
        return receiptRequiredAbove;
    }

    public void setReceiptRequiredAbove(BigDecimal receiptRequiredAbove) {
        this.receiptRequiredAbove = receiptRequiredAbove;
    }

    public int getMaxClaimAgeDays() {
        return maxClaimAgeDays;
    }

    public void setMaxClaimAgeDays(int maxClaimAgeDays) {
        this.maxClaimAgeDays = maxClaimAgeDays;
    }

    public boolean isBlockFutureDates() {
        return blockFutureDates;
    }

    public void setBlockFutureDates(boolean blockFutureDates) {
        this.blockFutureDates = blockFutureDates;
    }

    public boolean isEnforceReceipt() {
        return enforceReceipt;
    }

    public void setEnforceReceipt(boolean enforceReceipt) {
        this.enforceReceipt = enforceReceipt;
    }

    public boolean isEnforceClaimAge() {
        return enforceClaimAge;
    }

    public void setEnforceClaimAge(boolean enforceClaimAge) {
        this.enforceClaimAge = enforceClaimAge;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
