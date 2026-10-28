package org.example.ride_hailing_logistics.trip.domain;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.trip.enums.TripActorRole;
import org.example.ride_hailing_logistics.trip.enums.TripStatus;
import static org.example.ride_hailing_logistics.trip.enums.TripActorRole.CUSTOMER;
import static org.example.ride_hailing_logistics.trip.enums.TripActorRole.DRIVER;
import static org.example.ride_hailing_logistics.trip.enums.TripActorRole.SUPPORT_STAFF;
import static org.example.ride_hailing_logistics.trip.enums.TripActorRole.SYSTEM;
import static org.example.ride_hailing_logistics.trip.enums.TripStatus.ACCEPTED;
import static org.example.ride_hailing_logistics.trip.enums.TripStatus.ARRIVED;
import static org.example.ride_hailing_logistics.trip.enums.TripStatus.CANCELLED;
import static org.example.ride_hailing_logistics.trip.enums.TripStatus.COMPLETED;
import static org.example.ride_hailing_logistics.trip.enums.TripStatus.CREATED;
import static org.example.ride_hailing_logistics.trip.enums.TripStatus.IN_TRIP;
import static org.example.ride_hailing_logistics.trip.enums.TripStatus.MATCHING;
import static org.example.ride_hailing_logistics.trip.enums.TripStatus.NO_DRIVER;
import static org.example.ride_hailing_logistics.trip.enums.TripStatus.PICKING_UP;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ma tran chuyen trang thai — ban sao 1:1 cua SRS §7.1 kem actor duoc phep (FR-TRIP-008, FR-TRIP-009).
 * Day la CHO DUY NHAT dinh nghia chuyen hop le; Trip.transitionTo luon goi vao day.
 */
public final class TripStateMachine {

    private static final Map<TripStatus, Map<TripStatus, Set<TripActorRole>>> RULES = new EnumMap<>(TripStatus.class);

    static {
        allow(CREATED, MATCHING, SYSTEM);
        allow(CREATED, CANCELLED, CUSTOMER, SUPPORT_STAFF);
        allow(MATCHING, ACCEPTED, DRIVER);
        allow(MATCHING, NO_DRIVER, SYSTEM);
        allow(MATCHING, CANCELLED, CUSTOMER, SUPPORT_STAFF);
        allow(ACCEPTED, PICKING_UP, DRIVER);
        allow(ACCEPTED, CANCELLED, CUSTOMER, DRIVER, SUPPORT_STAFF);
        allow(PICKING_UP, ARRIVED, DRIVER);
        allow(PICKING_UP, CANCELLED, CUSTOMER, DRIVER, SUPPORT_STAFF);
        allow(ARRIVED, IN_TRIP, DRIVER);
        allow(ARRIVED, CANCELLED, CUSTOMER, DRIVER, SUPPORT_STAFF);
        allow(IN_TRIP, COMPLETED, DRIVER);
        // Huy khi dang van chuyen chi qua quy trinh ngoai le cua nhan vien ho tro
        allow(IN_TRIP, CANCELLED, SUPPORT_STAFF);
        // COMPLETED, CANCELLED, NO_DRIVER: trang thai ket thuc, khong co chuyen ra
    }

    private TripStateMachine() {
    }

    private static void allow(TripStatus from, TripStatus to, TripActorRole... actors) {
        RULES.computeIfAbsent(from, k -> new EnumMap<>(TripStatus.class))
                .put(to, actors.length == 0 ? EnumSet.noneOf(TripActorRole.class) : EnumSet.of(actors[0], actors));
    }

    public static boolean isAllowed(TripStatus from, TripStatus to, TripActorRole actor) {
        Map<TripStatus, Set<TripActorRole>> targets = RULES.get(from);
        return targets != null && targets.containsKey(to) && actor != null && targets.get(to).contains(actor);
    }

    public static boolean isTransitionDefined(TripStatus from, TripStatus to) {
        Map<TripStatus, Set<TripActorRole>> targets = RULES.get(from);
        return targets != null && targets.containsKey(to);
    }

    public static void assertAllowed(TripStatus from, TripStatus to, TripActorRole actor) {
        if (!isTransitionDefined(from, to)) {
            throw new BusinessException(ErrorCode.INVALID_TRIP_STATE,
                    "Không thể chuyển chuyến từ \"" + from.getDisplayName() + "\" sang \"" + to.getDisplayName() + "\".");
        }
        if (!isAllowed(from, to, actor)) {
            throw new BusinessException(ErrorCode.INVALID_TRIP_STATE,
                    "Bạn không được thực hiện bước \"" + to.getDisplayName() + "\" ở trạng thái hiện tại.");
        }
    }
}
