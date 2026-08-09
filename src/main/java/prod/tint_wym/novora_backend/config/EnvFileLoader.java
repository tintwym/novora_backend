package prod.tint_wym.novora_backend.config;

import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads {@code .env} into system properties so Spring can resolve {@code ${DB_URL}} etc.
 * Env vars already set in the process always win. File is optional.
 *
 * <p>Search order (first existing file wins): {@code ENV_FILE}, then {@code ./.env},
 * {@code ./novora_backend/.env}, {@code ./backend/.env}.
 */
public final class EnvFileLoader {

    private EnvFileLoader() {}

    public static void loadOptionalEnvFile() {
        String custom = System.getenv("ENV_FILE");
        Path[] candidates =
                custom != null && !custom.isBlank()
                        ? new Path[] {Path.of(custom)}
                        : new Path[] {
                            Path.of(System.getProperty("user.dir"), ".env"),
                            Path.of(System.getProperty("user.dir"), "novora_backend", ".env"),
                            Path.of(System.getProperty("user.dir"), "backend", ".env")
                        };
        for (Path p : candidates) {
            if (!Files.isRegularFile(p)) {
                continue;
            }
            Path dir = p.getParent() != null ? p.getParent() : Path.of(".");
            String name = p.getFileName().toString();
            Dotenv dotenv =
                    Dotenv.configure()
                            .directory(dir.toString())
                            .filename(name)
                            .ignoreIfMissing()
                            .ignoreIfMalformed()
                            .load();
            dotenv
                    .entries()
                    .forEach(
                            e -> {
                                String key = e.getKey();
                                if (System.getenv(key) != null) {
                                    return;
                                }
                                if (System.getProperty(key) != null) {
                                    return;
                                }
                                System.setProperty(key, e.getValue());
                            });
            break;
        }
    }
}
