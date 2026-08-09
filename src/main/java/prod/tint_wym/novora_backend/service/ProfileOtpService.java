package prod.tint_wym.novora_backend.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Short-lived email OTP for employee personal-profile changes.
 * Codes are logged (and optionally returned) until a real mail provider is wired.
 */
@Service
public class ProfileOtpService {

    private static final Logger log = LoggerFactory.getLogger(ProfileOtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final long ttlSeconds;
    private final long resendCooldownSeconds;
    private final boolean exposeCode;
    private final Map<String, OtpEntry> byEmail = new ConcurrentHashMap<>();

    public ProfileOtpService(
            @Value("${app.otp.ttl-seconds:600}") long ttlSeconds,
            @Value("${app.otp.resend-cooldown-seconds:60}") long resendCooldownSeconds,
            @Value("${app.otp.expose-code:false}") boolean exposeCode
    ) {
        this.ttlSeconds = ttlSeconds;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.exposeCode = exposeCode;
    }

    public IssuedOtp issue(String email, String purpose) {
        String normalized = normalize(email);
        Instant now = Instant.now();
        OtpEntry existing = byEmail.get(normalized);
        if (existing != null && existing.issuedAt().plusSeconds(resendCooldownSeconds).isAfter(now)) {
            long wait = resendCooldownSeconds - (now.getEpochSecond() - existing.issuedAt().getEpochSecond());
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Wait " + Math.max(1, wait) + "s before requesting another code");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        OtpEntry entry = new OtpEntry(code, purpose, now, now.plusSeconds(ttlSeconds));
        byEmail.put(normalized, entry);
        if (exposeCode) {
            log.info("Profile OTP issued for {} purpose={} code={} (mail delivery not configured)",
                    normalized, purpose, code);
        } else {
            log.info("Profile OTP issued for {} purpose={} (mail delivery not configured)",
                    normalized, purpose);
        }
        return new IssuedOtp(ttlSeconds, exposeCode ? code : null);
    }

    public void consume(String email, String purpose, String rawCode) {
        String normalized = normalize(email);
        String code = rawCode == null ? "" : rawCode.trim();
        if (!code.matches("\\d{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the 6-digit verification code");
        }
        OtpEntry entry = byEmail.get(normalized);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            byEmail.remove(normalized);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification code expired. Request a new one.");
        }
        if (!entry.purpose().equals(purpose) || !entry.code().equals(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification code");
        }
        byEmail.remove(normalized);
    }

    private static String normalize(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return email.trim().toLowerCase();
    }

    public record IssuedOtp(long expiresInSeconds, String debugCode) {
    }

    private record OtpEntry(String code, String purpose, Instant issuedAt, Instant expiresAt) {
    }
}
