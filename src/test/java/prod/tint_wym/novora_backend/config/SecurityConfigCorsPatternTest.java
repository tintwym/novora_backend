package prod.tint_wym.novora_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link SecurityConfig#isSafeOriginPattern(String)} accepts legitimate origins and
 * rejects the substring-injection patterns that bypassed the previous naive check.
 */
class SecurityConfigCorsPatternTest {

    @Test
    void acceptsExactProductionOrigin() {
        assertThat(SecurityConfig.isSafeOriginPattern("https://novora-hrms.vercel.app")).isTrue();
    }

    @Test
    void acceptsExactOriginWithPort() {
        assertThat(SecurityConfig.isSafeOriginPattern("https://app.example.com:8443")).isTrue();
    }

    @Test
    void acceptsLoopbackPortWildcards() {
        assertThat(SecurityConfig.isSafeOriginPattern("http://localhost:*")).isTrue();
        assertThat(SecurityConfig.isSafeOriginPattern("https://localhost:*")).isTrue();
        assertThat(SecurityConfig.isSafeOriginPattern("http://127.0.0.1:*")).isTrue();
        assertThat(SecurityConfig.isSafeOriginPattern("https://127.0.0.1:*")).isTrue();
        assertThat(SecurityConfig.isSafeOriginPattern("http://[::1]:*")).isTrue();
        assertThat(SecurityConfig.isSafeOriginPattern("https://[::1]:*")).isTrue();
    }

    @Test
    void rejectsNonLoopbackWildcards() {
        // The originally documented attack — fully qualified wildcard on a public registrable domain.
        assertThat(SecurityConfig.isSafeOriginPattern("https://*.vercel.app")).isFalse();
        assertThat(SecurityConfig.isSafeOriginPattern("https://*.example.com")).isFalse();
    }

    @Test
    void rejectsSubstringTricksThatNaiveCheckMissed() {
        // These all contain the literal token "localhost" / "127.0.0.1" / "::1" somewhere in the
        // string and would pass a substring-based check, but resolve to attacker-controlled domains.
        assertThat(SecurityConfig.isSafeOriginPattern("https://*.localhost.evil.com")).isFalse();
        assertThat(SecurityConfig.isSafeOriginPattern("https://*-127.0.0.1.attacker.com")).isFalse();
        assertThat(SecurityConfig.isSafeOriginPattern("*://127.0.0.1.attacker.com")).isFalse();
        assertThat(SecurityConfig.isSafeOriginPattern("https://attacker.com/?token=localhost")).isFalse();
        assertThat(SecurityConfig.isSafeOriginPattern("https://attacker.com#localhost")).isFalse();
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertThat(SecurityConfig.isSafeOriginPattern("ftp://files.example.com")).isFalse();
        assertThat(SecurityConfig.isSafeOriginPattern("javascript://localhost:*")).isFalse();
        assertThat(SecurityConfig.isSafeOriginPattern("data:text/plain,foo")).isFalse();
    }

    @Test
    void rejectsBlankAndMalformed() {
        assertThat(SecurityConfig.isSafeOriginPattern(null)).isFalse();
        assertThat(SecurityConfig.isSafeOriginPattern("")).isFalse();
        assertThat(SecurityConfig.isSafeOriginPattern("  ")).isFalse();
        assertThat(SecurityConfig.isSafeOriginPattern("not-a-url")).isFalse();
    }

    @Test
    void rejectsExactOriginsWithExtraPathOrQuery() {
        assertThat(SecurityConfig.isSafeOriginPattern("https://app.example.com/")).isFalse();
        assertThat(SecurityConfig.isSafeOriginPattern("https://app.example.com/path")).isFalse();
        assertThat(SecurityConfig.isSafeOriginPattern("https://app.example.com?q=1")).isFalse();
    }
}
