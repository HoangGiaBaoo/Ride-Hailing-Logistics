---
name: quy-tac-code-java
description: Phong cách viết code Java cho dự án đặt xe — Lombok và Stream API bình thường, record cho DTO, định danh tiếng Anh, comment tiếng Việt không dấu, BigDecimal cho tiền, Instant cho thời gian, logic lõi viết thuần để test không cần Spring. Nạp skill này khi viết hoặc sửa bất kỳ file .java nào.
---

# Phong cách code Java

Luật cứng nằm ở `.claude/rules/quy-tac-code.md`. Skill này là phần "viết thế nào cho gọn và test được".

## Tách lõi thuần khỏi Spring

Các quyết định nghiệp vụ quan trọng là **lớp Java thuần**, không tiêm repository, nhận `Clock` và tham số qua constructor:

| Lớp thuần | Gói | Dùng bởi |
|---|---|---|
| `TripStateMachine` | `trip` | `Trip.transitionTo` |
| `CancellationPolicy` | `trip` | `TripService.cancel`, `previewCancellation` |
| `CandidateRanker` | `trip.dispatch` | `DispatchService` |
| `FareCalculator`, `SurgeCalculator` | `pricing` | `QuoteService`, `SurgeService` |
| `TelemetryValidator` | `location` | `LocationService` |
| `CommissionCalculator` | `wallet` | `WalletService` |
| `GeoUtils`, `PhoneNormalizer` | `common` | khắp nơi |

Nhờ vậy `rules/kiem-thu.md` test được bằng JUnit thuần, chạy trong mili giây.

## DTO là record

```java
public record CreateTripRequest(
        @NotNull UUID quoteId,
        @NotNull PaymentMethod paymentMethod,
        boolean surgeConfirmed,
        @Valid RecipientRequest recipient,
        @Size(max = 500) String packageDescription,
        @PositiveOrZero @Max(500) Double packageWeightKg) {}
```

Record chỉ chứa trường **được phép** gửi lên → chống mass assignment tự nhiên (DR-VAL-005). Response cũng là record, có `static from(Entity)`.

## Entity có method nghiệp vụ

```java
public void transitionTo(TripStatus to, Actor actor, String reason, Double lat, Double lng, Instant now) {
    TripStateMachine.assertAllowed(this.status, to, actor);   // nem BusinessException INVALID_TRIP_STATE
    history.add(TripStatusHistory.of(this, this.status, to, actor, reason, lat, lng, now));
    this.status = to;
    this.updatedAt = now;
}
```

Setter `PROTECTED` hoặc không có; service gọi method nghiệp vụ.

## Thời gian

Tiêm `java.time.Clock` (bean UTC). **Không** `Instant.now()` rải rác trong logic — test không cố định được thời gian.

## Tiền

```java
BigDecimal km = BigDecimal.valueOf(distanceMeters).divide(THOUSAND, 3, RoundingMode.HALF_UP);
BigDecimal total = fare.divide(STEP, 0, RoundingMode.CEILING).multiply(STEP);   // lam tron len 1.000
```

So sánh tiền bằng `compareTo`, không `equals` (`10.00` ≠ `10.0` theo `equals`).

## Enum có `displayName`

```java
@Getter
@RequiredArgsConstructor
public enum TripStatus {
    CREATED("Đã tạo"), MATCHING("Đang tìm tài xế"), ...;
    private final String displayName;
}
```

## Log

`@Slf4j`, log ID chứ không log PII: `log.info("Trip {} accepted by driver {}", tripId, driverId)`. Không log tọa độ khách, SĐT, token.
