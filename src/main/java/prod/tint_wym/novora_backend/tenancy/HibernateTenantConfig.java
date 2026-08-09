package prod.tint_wym.novora_backend.tenancy;

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link TenantIdResolver} into the Hibernate SessionFactory. Without this, the
 * {@code @TenantId} annotation on entity classes is silent — Hibernate has no resolver to ask for
 * the current tenant, so it doesn't add the WHERE clause and doesn't stamp new rows.
 *
 * <p>The bean implements {@link HibernatePropertiesCustomizer}, which Spring Boot calls during
 * EntityManagerFactory setup. This is preferred over setting {@code spring.jpa.properties.*} in
 * {@code application.properties} because we need to inject the live Spring-managed resolver bean
 * (so it can pick up {@link TenantContext}, which is a thread-local that the rest of the app
 * already understands).
 */
@Configuration
public class HibernateTenantConfig implements HibernatePropertiesCustomizer {

    private final TenantIdResolver tenantIdResolver;

    public HibernateTenantConfig(TenantIdResolver tenantIdResolver) {
        this.tenantIdResolver = tenantIdResolver;
    }

    @Override
    public void customize(java.util.Map<String, Object> hibernateProperties) {
        hibernateProperties.put("hibernate.tenant_identifier_resolver", tenantIdResolver);
    }
}
