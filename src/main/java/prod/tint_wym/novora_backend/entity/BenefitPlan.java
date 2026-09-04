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
@Table(name = "benefit_plans")
public class BenefitPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 80)
    private String category;

    @Column(length = 160)
    private String provider;

    @Column(name = "coverage_summary", columnDefinition = "text")
    private String coverageSummary;

    @Column(name = "employee_cost", precision = 15, scale = 2)
    private BigDecimal employeeCost;

    @Column(name = "employer_cost", precision = 15, scale = 2)
    private BigDecimal employerCost;

    @Column(nullable = false, length = 40)
    private String status = "active";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getCoverageSummary() {
        return coverageSummary;
    }

    public void setCoverageSummary(String coverageSummary) {
        this.coverageSummary = coverageSummary;
    }

    public BigDecimal getEmployeeCost() {
        return employeeCost;
    }

    public void setEmployeeCost(BigDecimal employeeCost) {
        this.employeeCost = employeeCost;
    }

    public BigDecimal getEmployerCost() {
        return employerCost;
    }

    public void setEmployerCost(BigDecimal employerCost) {
        this.employerCost = employerCost;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
