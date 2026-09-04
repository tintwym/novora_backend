package prod.tint_wym.novora_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tenant root. Every signup creates one of these; legacy data was retro-fitted into the implicit
 * "Novora Internal" org by {@code ReferenceDataSeeder} so every other entity always has a non-null
 * {@code organization_id} at runtime.
 *
 * <p>{@code Organization} itself is <strong>not</strong> tenant-scoped via {@code @TenantId} —
 * looking up the caller's own org during request setup must always succeed regardless of which
 * tenant the request is running as. Tenant scoping for every other top-level entity is done with
 * Hibernate's native multi-tenancy via {@code @TenantId} on a UUID {@code organizationId} field.
 */
@Entity
@Table(name = "organizations")
public class Organization {

    public enum Plan {
        TRIAL,
        PAID,
        ENTERPRISE,
        EXPIRED
    }

    public enum Status {
        ACTIVE,
        READ_ONLY,
        SUSPENDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "registration_no", length = 80)
    private String registrationNo;

    @Column(name = "address_line1", columnDefinition = "text")
    private String addressLine1;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String country;

    @Column(length = 40)
    private String phone;

    @Column(length = 255)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.TRIAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "trial_started_at")
    private LocalDateTime trialStartedAt;

    @Column(name = "trial_expires_at")
    private LocalDateTime trialExpiresAt;

    @Column(name = "paid_until")
    private LocalDateTime paidUntil;

    /** Reserved for Phase 2 (Stripe). Null today. */
    @Column(name = "seats_purchased")
    private Integer seatsPurchased;

    /** Reserved for Phase 2 (Stripe). Null today. */
    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return status == Status.READ_ONLY || plan == Plan.EXPIRED;
    }

    public boolean isInternal() {
        return "novora-internal".equals(slug);
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }
    public String getRegistrationNo() { return registrationNo; }
    public void setRegistrationNo(String registrationNo) { this.registrationNo = registrationNo; }
    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }
    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getTrialStartedAt() { return trialStartedAt; }
    public void setTrialStartedAt(LocalDateTime trialStartedAt) { this.trialStartedAt = trialStartedAt; }
    public LocalDateTime getTrialExpiresAt() { return trialExpiresAt; }
    public void setTrialExpiresAt(LocalDateTime trialExpiresAt) { this.trialExpiresAt = trialExpiresAt; }
    public LocalDateTime getPaidUntil() { return paidUntil; }
    public void setPaidUntil(LocalDateTime paidUntil) { this.paidUntil = paidUntil; }
    public Integer getSeatsPurchased() { return seatsPurchased; }
    public void setSeatsPurchased(Integer seatsPurchased) { this.seatsPurchased = seatsPurchased; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public void setStripeCustomerId(String stripeCustomerId) { this.stripeCustomerId = stripeCustomerId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
