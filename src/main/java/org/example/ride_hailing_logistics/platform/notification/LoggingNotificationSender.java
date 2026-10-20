package org.example.ride_hailing_logistics.platform.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoggingNotificationSender implements NotificationSender {

    private final Environment environment;

    @Override
    public void send(UUID userId, String channelAddress, String subject, String body) {
        // Noi dung (vd: token dat lai mat khau) la bi mat; chi in ra o profile dev de demo duoc, moi truong khac khong (NFR-SEC-009)
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            log.warn("[DEV ONLY - no real provider] notification to user {}: {} | {}", userId, subject, body);
        } else {
            log.info("Notification queued for user {}: {}", userId, subject);
        }
    }
}
