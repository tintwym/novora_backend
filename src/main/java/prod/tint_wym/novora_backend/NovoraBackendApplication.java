package prod.tint_wym.novora_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import prod.tint_wym.novora_backend.config.EnvFileLoader;

@SpringBootApplication
// Drives TrialExpirySweeper (@Scheduled hourly) so trials flip to READ_ONLY without waiting
// for the next user request to walk through TrialExpiryFilter.
@EnableScheduling
public class NovoraBackendApplication {

    public static void main(String[] args) {
        EnvFileLoader.loadOptionalEnvFile();
        SpringApplication.run(NovoraBackendApplication.class, args);
    }
}
