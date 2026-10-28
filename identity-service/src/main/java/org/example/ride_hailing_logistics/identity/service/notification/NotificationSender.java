package org.example.ride_hailing_logistics.identity.service.notification;

import java.util.UUID;

/** Gui email/SMS cho nguoi dung. Phien ban nay chua tich hop nha cung cap that (xem docs/00-yeu-cau.md §4). */
public interface NotificationSender {

    void send(UUID userId, String channelAddress, String subject, String body);
}
