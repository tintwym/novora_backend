package prod.tint_wym.novora_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

@Entity
@Table(name = "ot_policies")
public class OtPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "weekday_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal weekdayMultiplier = new BigDecimal("1.50");

    @Column(name = "weekend_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal weekendMultiplier = new BigDecimal("2.00");

    @Column(name = "holiday_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal holidayMultiplier = new BigDecimal("3.00");

    @Column(name = "daily_threshold_hours", nullable = false, precision = 4, scale = 2)
    private BigDecimal dailyThresholdHours = new BigDecimal("8.00");

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval = true;

    @Column(nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getWeekdayMultiplier() { return weekdayMultiplier; }
    public void setWeekdayMultiplier(BigDecimal weekdayMultiplier) { this.weekdayMultiplier = weekdayMultiplier; }
    public BigDecimal getWeekendMultiplier() { return weekendMultiplier; }
    public void setWeekendMultiplier(BigDecimal weekendMultiplier) { this.weekendMultiplier = weekendMultiplier; }
    public BigDecimal getHolidayMultiplier() { return holidayMultiplier; }
    public void setHolidayMultiplier(BigDecimal holidayMultiplier) { this.holidayMultiplier = holidayMultiplier; }
    public BigDecimal getDailyThresholdHours() { return dailyThresholdHours; }
    public void setDailyThresholdHours(BigDecimal dailyThresholdHours) { this.dailyThresholdHours = dailyThresholdHours; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
