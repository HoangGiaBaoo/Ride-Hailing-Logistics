package org.example.ride_hailing_logistics.payment;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NFR-SEC-010: chu ky dung qua; sua 1 byte, sai khoa, hoac timestamp cu deu truot. */
class SandboxSignatureTest {

    private static final String SECRET = "test-secret-not-used-anywhere";
    private static final Instant NOW = Instant.parse("2026-10-05T08:30:00Z");
    private static final Duration TOLERANCE = Duration.ofMinutes(5);
    private static final String BODY = "{\"providerRef\":\"sbx_1\",\"amount\":\"45000\",\"status\":\"SUCCEEDED\"}";

    @Test
    void validSignatureIsAccepted() {
        String ts = String.valueOf(NOW.getEpochSecond());
        assertTrue(SandboxSignature.verify(SECRET, ts, BODY, SandboxSignature.sign(SECRET, ts, BODY), NOW, TOLERANCE));
    }

    @Test
    void tamperedBodyIsRejected() {
        String ts = String.valueOf(NOW.getEpochSecond());
        String sig = SandboxSignature.sign(SECRET, ts, BODY);
        assertFalse(SandboxSignature.verify(SECRET, ts, BODY.replace("45000", "4500"), sig, NOW, TOLERANCE));
    }

    @Test
    void wrongSecretIsRejected() {
        String ts = String.valueOf(NOW.getEpochSecond());
        assertFalse(SandboxSignature.verify(SECRET, ts, BODY, SandboxSignature.sign("other", ts, BODY), NOW, TOLERANCE));
    }

    @Test
    void replayedOldCallbackIsRejected() {
        // Callback hop le nhung ky 6 phut truoc -> ngoai cua so 5 phut
        String oldTs = String.valueOf(NOW.minus(Duration.ofMinutes(6)).getEpochSecond());
        assertFalse(SandboxSignature.verify(SECRET, oldTs, BODY, SandboxSignature.sign(SECRET, oldTs, BODY), NOW, TOLERANCE));
    }

    @Test
    void reusingSignatureWithDifferentTimestampIsRejected() {
        String ts = String.valueOf(NOW.getEpochSecond());
        String sig = SandboxSignature.sign(SECRET, ts, BODY);
        assertFalse(SandboxSignature.verify(SECRET, String.valueOf(NOW.getEpochSecond() + 1), BODY, sig, NOW, TOLERANCE));
    }

    @Test
    void malformedTimestampIsRejected() {
        assertFalse(SandboxSignature.verify(SECRET, "yesterday", BODY, "abc", NOW, TOLERANCE));
    }
}
