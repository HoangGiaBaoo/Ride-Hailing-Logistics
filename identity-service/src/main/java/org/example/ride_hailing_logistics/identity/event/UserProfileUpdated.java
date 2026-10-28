package org.example.ride_hailing_logistics.identity.event;

import java.time.Instant;
import java.util.UUID;

/** Ho ten doi: trip-service cap nhat ban chup tai xe; ben nhan bo qua ban tin cu hon (so updatedAt). */
public record UserProfileUpdated(UUID userId, String fullName, Instant updatedAt) {}
