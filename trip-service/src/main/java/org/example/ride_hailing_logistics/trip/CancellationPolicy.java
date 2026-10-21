package org.example.ride_hailing_logistics.trip;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Quy tac phi huy "cancel-v1" (FR-CAN-003, TBD-07) — bang o docs/01-nghiep-vu.md §5. Thuan, khong Spring.
 */
public final class CancellationPolicy {

    public static final String VERSION = "cancel-v1";

    private final Duration freeCancelWindow;
    private final Duration noShowWait;
    private final BigDecimal feeAfterAccept;
    private final BigDecimal feeArrived;

    public CancellationPolicy(Duration freeCancelWindow, Duration noShowWait, BigDecimal feeAfterAccept, BigDecimal feeArrived) {
        this.freeCancelWindow = freeCancelWindow;
        this.noShowWait = noShowWait;
        this.feeAfterAccept = feeAfterAccept;
        this.feeArrived = feeArrived;
    }

    /**
     * Phi khach phai tra neu huy luc nay. Khong kiem quyen — viec do cua TripStateMachine.
     * acceptedAt / arrivedAt co the null neu chuyen chua toi buoc do.
     */
    public BigDecimal feeFor(TripActorRole actor, TripStatus status, Instant acceptedAt, Instant arrivedAt, Instant now) {
        if (actor == TripActorRole.SUPPORT_STAFF || actor == TripActorRole.SYSTEM) {
            return BigDecimal.ZERO;
        }
        return switch (status) {
            case CREATED, MATCHING -> BigDecimal.ZERO;
            case ACCEPTED, PICKING_UP -> {
                if (actor == TripActorRole.DRIVER) yield BigDecimal.ZERO;
                boolean withinGrace = acceptedAt != null && Duration.between(acceptedAt, now).compareTo(freeCancelWindow) <= 0;
                yield withinGrace ? BigDecimal.ZERO : feeAfterAccept;
            }
            case ARRIVED -> {
                if (actor == TripActorRole.CUSTOMER) yield feeArrived;
                // Tai xe huy vi khach khong ra (no-show): chi tinh phi khi da cho du lau
                boolean waitedEnough = arrivedAt != null && Duration.between(arrivedAt, now).compareTo(noShowWait) >= 0;
                yield waitedEnough ? feeArrived : BigDecimal.ZERO;
            }
            default -> BigDecimal.ZERO;
        };
    }
}
