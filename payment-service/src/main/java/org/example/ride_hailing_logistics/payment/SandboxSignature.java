package org.example.ride_hailing_logistics.payment;

import org.example.ride_hailing_logistics.common.util.Secrets;

import java.time.Duration;
import java.time.Instant;

/**
 * Chu ky callback (NFR-SEC-010): HMAC-SHA256(secret, timestamp + "." + body).
 * Ky ca timestamp de ke xau khong lay mot callback cu hop le roi gui lai sau nay (replay).
 */
public final class SandboxSignature {

    private SandboxSignature() {
    }

    public static String sign(String secret, String timestamp, String body) {
        return Secrets.hmacSha256Hex(secret, timestamp + "." + body);
    }

    public static boolean verify(String secret, String timestamp, String body, String signature, Instant now,
                                 Duration tolerance) {
        if (timestamp == null || body == null || signature == null) return false;
        long seconds;
        try {
            seconds = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        Instant sentAt = Instant.ofEpochSecond(seconds);
        if (sentAt.isBefore(now.minus(tolerance)) || sentAt.isAfter(now.plus(tolerance))) {
            return false;
        }
        return Secrets.constantTimeEquals(sign(secret, timestamp, body), signature);
    }
}
