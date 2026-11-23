package prod.tint_wym.novora_backend.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RequestSignerTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-chars";
    private static final String PATH = "/api/v1/integrations/hrms/events";
    private static final String BODY = "{\"eventId\":\"1\"}";

    private final Instant now = Instant.parse("2026-10-15T12:00:00Z");
    private final long ts = now.getEpochSecond();

    @Test
    void acceptsAnUntouchedRequest() {
        String sig = RequestSigner.sign(SECRET, ts, "POST", PATH, BODY);
        assertTrue(RequestSigner.verify(SECRET, Long.toString(ts), sig, "POST", PATH, BODY, now));
    }

    @Test
    void rejectsAlteredBodyPathMethodOrSecret() {
        String sig = RequestSigner.sign(SECRET, ts, "POST", PATH, BODY);
        assertFalse(RequestSigner.verify(SECRET, Long.toString(ts), sig, "POST", PATH, "{\"eventId\":\"2\"}", now));
        assertFalse(RequestSigner.verify(SECRET, Long.toString(ts), sig, "POST", "/api/v1/other", BODY, now));
        assertFalse(RequestSigner.verify(SECRET, Long.toString(ts), sig, "GET", PATH, BODY, now));
        assertFalse(RequestSigner.verify(SECRET + "x", Long.toString(ts), sig, "POST", PATH, BODY, now));
    }

    @Test
    void rejectsStaleOrFutureTimestamps() {
        long stale = ts - RequestSigner.MAX_SKEW_SECONDS - 1;
        String staleSig = RequestSigner.sign(SECRET, stale, "POST", PATH, BODY);
        assertFalse(RequestSigner.verify(SECRET, Long.toString(stale), staleSig, "POST", PATH, BODY, now));

        long future = ts + RequestSigner.MAX_SKEW_SECONDS + 1;
        String futureSig = RequestSigner.sign(SECRET, future, "POST", PATH, BODY);
        assertFalse(RequestSigner.verify(SECRET, Long.toString(future), futureSig, "POST", PATH, BODY, now));
    }

    @Test
    void rejectsMissingOrMalformedHeaders() {
        String sig = RequestSigner.sign(SECRET, ts, "POST", PATH, BODY);
        assertFalse(RequestSigner.verify(SECRET, null, sig, "POST", PATH, BODY, now));
        assertFalse(RequestSigner.verify(SECRET, Long.toString(ts), null, "POST", PATH, BODY, now));
        assertFalse(RequestSigner.verify(SECRET, "not-a-number", sig, "POST", PATH, BODY, now));
        assertFalse(RequestSigner.verify("", Long.toString(ts), sig, "POST", PATH, BODY, now));
    }

    /** Known answer shared with the CRM RequestSignerTest, so the two implementations can't drift apart. */
    @Test
    void matchesTheSharedKnownAnswer() {
        String sig = RequestSigner.sign("0123456789abcdef0123456789abcdef", 1_700_000_000L, "post",
                "/api/v1/integrations/hrms/events", "{\"hello\":\"world\"}");
        assertEquals("v1=a1bc6140cafdc910ecf5e40b0efd02b9c2448ed81bf3282116ed29fbd6faa8e9", sig);
    }
}
