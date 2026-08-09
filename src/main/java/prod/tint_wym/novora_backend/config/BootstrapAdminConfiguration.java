package prod.tint_wym.novora_backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import prod.tint_wym.novora_backend.repository.AppUserRepository;
import prod.tint_wym.novora_backend.service.AdminUserService;

/**
 * Kept out of {@code NovoraBackendApplication} so Spring Boot DevTools restart does not need to
 * resolve {@link AdminUserService} while reflecting on the main class (avoids sporadic
 * {@code ClassNotFoundException} for application classes during restarts).
 */
@Configuration
public class BootstrapAdminConfiguration {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminConfiguration.class);

    @Bean
    ApplicationRunner bootstrapAdminUser(
            AdminUserService adminUserService, AppUserRepository appUserRepository) {
        return (args) -> {
            String email = System.getenv("APP_BOOTSTRAP_ADMIN_EMAIL");
            String password = System.getenv("APP_BOOTSTRAP_ADMIN_PASSWORD");
            if (email != null && !email.isBlank() && password != null && !password.isBlank()) {
                adminUserService.ensureAdminUser(email, password);
                return;
            }
            // No bootstrap credentials supplied. Warn loudly if the database also contains zero
            // SUPER_ADMINs, because there is no other way to provision one (no public sign-up
            // grants admin and no admin endpoint is reachable without an existing admin) and the
            // operator otherwise has no signal that the system is admin-less.
            long superAdmins = appUserRepository.findAll().stream()
                    .filter(u -> "SUPER_ADMIN".equals(u.getRole()) && u.isActive())
                    .count();
            if (superAdmins == 0) {
                log.warn(
                        "No SUPER_ADMIN account exists and APP_BOOTSTRAP_ADMIN_EMAIL / "
                                + "APP_BOOTSTRAP_ADMIN_PASSWORD are not set. The system is admin-less; "
                                + "set both environment variables and restart to provision one.");
            }
        };
    }
}
