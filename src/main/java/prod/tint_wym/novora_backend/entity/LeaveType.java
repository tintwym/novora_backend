package prod.tint_wym.novora_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

@Entity
@Table(
        name = "leave_types",
        uniqueConstraints = {
            @UniqueConstraint(name = "uq_leave_types_org_code", columnNames = {"organization_id", "code"}),
            @UniqueConstraint(name = "uq_leave_types_org_name", columnNames = {"organization_id", "name"})
        })
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "organization_id", nullable = true)
    private UUID organizationId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 30)
    private String code;

    /**
     * Legacy Neon / V9 column; kept in sync with {@link #name} when unset so inserts satisfy NOT NULL.
     */
    @Column(name = "label", nullable = false, length = 128)
    private String label;

    @Column(name = "display_color", length = 16)
    private String displayColor;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "company_pool_days", nullable = false)
    private int companyPoolDays;

    @Column(name = "days_allowed", nullable = false)
    private int daysAllowed;

    @Column(name = "is_paid")
    private boolean paid = true;

    @Column(name = "carry_forward")
    private boolean carryForward;

    @Column(name = "max_carry_days")
    private int maxCarryDays;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    @PreUpdate
    private void syncLabelFromName() {
        if ((label == null || label.isBlank()) && name != null && !name.isBlank()) {
            label = name;
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDisplayColor() {
        return displayColor;
    }

    public void setDisplayColor(String displayColor) {
        this.displayColor = displayColor;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public int getCompanyPoolDays() {
        return companyPoolDays;
    }

    public void setCompanyPoolDays(int companyPoolDays) {
        this.companyPoolDays = companyPoolDays;
    }

    public int getDaysAllowed() {
        return daysAllowed;
    }

    public void setDaysAllowed(int daysAllowed) {
        this.daysAllowed = daysAllowed;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public boolean isCarryForward() {
        return carryForward;
    }

    public void setCarryForward(boolean carryForward) {
        this.carryForward = carryForward;
    }

    public int getMaxCarryDays() {
        return maxCarryDays;
    }

    public void setMaxCarryDays(int maxCarryDays) {
        this.maxCarryDays = maxCarryDays;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
