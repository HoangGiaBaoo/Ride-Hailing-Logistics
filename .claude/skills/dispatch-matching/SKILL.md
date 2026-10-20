---
name: dispatch-matching
description: Kiến thức lõi và mẫu code cho phần ghép chuyến thời gian thực — truy vấn tài xế gần bằng Redis GEO, xếp hạng ứng viên, offer có thời hạn, chấp nhận nguyên tử chống double assignment, mở rộng bán kính, NO_DRIVER, state machine chuyến, surge cung/cầu, tính cước BigDecimal. Nạp skill này BẤT CỨ KHI NÀO đụng tới package trip/, trip/dispatch/, location/, pricing/, hoặc khi viết test đồng thời UC-06.
---

# Dispatch & matching

Đặc tả: SRS §4.3–4.6, `docs/00-yeu-cau.md` (TBD-04, TBD-06, TBD-14), `docs/01-nghiep-vu.md` §3–5.

Đây là phần **lỗi chỉ lộ ra dưới tải/đồng thời**: chạy tay một người bấm thì đúng, 100 người bấm cùng lúc thì sai. Mọi đoạn code ở đây phải tự hỏi "nếu hai luồng chạy dòng này cùng lúc thì sao?".

---

## 1. Luồng matching

```
TripCreated(MATCHING) --after commit--> DispatchService.dispatchNext(tripId)
   1. Trip con MATCHING? qua han tong 60 s? -> NO_DRIVER
   2. Lay ung vien: LocationService.findNearby(pickup, radius vong hien tai, vehicleType)
        -> loc vi tri moi (<= 30 s), accuracy <= 50 m
        -> DriverAvailabilityService.filterAvailable(ids)   (AVAILABLE, APPROVED, xe dung loai)
        -> bo tai xe da tu choi/het han offer cua chuyen nay
   3. CandidateRanker (rank-v1): khoang cach tang dan, hoa -> vi tri moi hon
   4. Voi tung ung vien theo thu tu: reserve(driverId) = UPDATE ... SET OFFERED WHERE AVAILABLE
        -> 0 dong: nguoi khac vua giu, thu ung vien ke
        -> 1 dong: tao DriverOffer PENDING (het han 15 s), gui WS rieng tai xe, DUNG
   5. Khong ai -> tang vong ban kinh (2 -> 4 -> 6 km); het vong -> doi 3 s quet lai, toi 60 s -> NO_DRIVER

DispatchScheduler (1 s/lan): offer PENDING qua han -> EXPIRED, tra tai xe ve AVAILABLE, dispatchNext
```

## 2. Chấp nhận nguyên tử — trái tim của CON-06

```java
@Transactional
public AcceptResult accept(UUID offerId, UUID driverId) {
    // Khoa dong trip truoc: moi request chap nhan cung mot trip xep hang tai day,
    // nen kiem tra ben duoi khong bi luong khac chen ngang.
    DriverOffer offer = offerRepository.findById(offerId)
            .filter(o -> o.getDriverId().equals(driverId))      // offer cua nguoi khac -> 404
            .orElseThrow(NotFoundException::new);
    Trip trip = tripRepository.findByIdForUpdate(offer.getTripId()).orElseThrow();

    // Lap lai request da thang -> tra cung ket qua (FR-MAT-012)
    if (offer.getStatus() == OfferStatus.ACCEPTED) return AcceptResult.of(trip);

    if (offer.getStatus() != OfferStatus.PENDING || offer.isExpired(clock)) throw conflict(OFFER_EXPIRED);
    if (trip.getStatus() != TripStatus.MATCHING) throw conflict(DRIVER_ALREADY_ASSIGNED);

    // Tai xe phai dang OFFERED cho dung offer nay; cap nhat co dieu kien, 0 dong = that bai
    if (!driverAvailability.markBusy(driverId)) throw conflict(DRIVER_ALREADY_ASSIGNED);

    offer.accept(clock.instant());
    trip.assignDriver(driverId, ...);           // transitionTo(ACCEPTED) + ghi lich su
    outbox.append(TripAccepted...);
    // Partial unique index trips(driver_id) WHERE status IN (...) chan not neu code tren co lo
}
```

Ba lớp phòng thủ: **khóa hàng trip** (`@Lock(PESSIMISTIC_WRITE)`), **cập nhật có điều kiện** cho tài xế, **partial unique index** trong DB. Lỗi vi phạm unique (`DataIntegrityViolationException`) phải dịch thành 409, không phải 500.

## 3. Cập nhật có điều kiện

```java
@Modifying
@Query("UPDATE DriverProfile d SET d.availability = :to, d.availabilityChangedAt = :now, d.version = d.version + 1 " +
       "WHERE d.id = :id AND d.availability = :from")
int transition(UUID id, DriverAvailability from, DriverAvailability to, Instant now);
```

Trả `int` → kiểm `== 1`. **Không** `findById` rồi `if (getAvailability() == AVAILABLE) setAvailability(OFFERED)` — hai luồng cùng đọc thấy AVAILABLE.

## 4. Redis GEO

```java
// Ghi: chi khi ban tin moi hon (sequence lon hon) — kiem trong Lua script de nguyen tu
redis.opsForGeo().add("geo:drivers:" + vehicleType, new Point(lng, lat), driverId);   // chu y: (lng, lat)
// Doc
redis.opsForGeo().search(key, GeoReference.fromCoordinate(lng, lat),
        new Distance(radiusKm, Metrics.KILOMETERS),
        RedisGeoCommandArgs.newGeoSearchArgs().includeDistance().sortAscending().limit(50));
```

**Bẫy kinh điển: Redis nhận (longitude, latitude), ngược với thói quen (lat, lng).** Test `GeoUtilsTest` và một test tích hợp phải bắt được nếu đảo.

GEO không có TTL từng phần tử → `geo:lastseen` (zset, score = serverTs) + job dọn mỗi 10 s; matching **vẫn** lọc độ mới từ hash `loc:{id}` (không tin GEO đã sạch).

## 5. Telemetry (FR-LOC-001…006)

`TelemetryValidator` thuần (tiêm `Clock`): kiểm phạm vi, accuracy, timestamp; tính tốc độ ngầm so với vị trí trước → > 70 m/s thì `suspicious=true` (vẫn lưu, không dùng matching). Ghi đè chỉ khi `sequence > sequence cũ` (cùng thiết bị) — so sánh và ghi trong **một Lua script** để không có khoảng hở giữa đọc và ghi.

## 6. Cước & surge (FR-PRI)

```
raw   = base + perKm × km + perMinute × minutes
fare  = max(raw × surge, minimumFare) + bookingFee
total = ceil(fare / 1000) × 1000          // lam tron LEN boi so 1.000 VND
```

- `km = meters / 1000` dùng `BigDecimal.valueOf(meters).divide(1000, 3, HALF_UP)` — không chia `double`.
- `surge = clamp(1 + 0.25 × (demand / max(supply,1) − 1), 1.0, 2.5)`, làm tròn 1 chữ số thập phân `HALF_UP`.
- Quote lưu **mọi thành phần + ruleId + ruleVersion + surgeVersion** để tái lập (FR-PRI-011, FR-PRI-017).

## 7. State machine

Bảng chuyển nằm trong **một chỗ duy nhất** (`TripStateMachine`) dạng `Map<TripStatus, Set<TripStatus>>` + quy tắc actor. `Trip.transitionTo(to, actor, reason, lat, lng, clock)` gọi state machine, đổi trạng thái, thêm `TripStatusHistory`. Mọi chỗ khác **không** gọi `setStatus` (hook bắt).

## 8. Checklist khi soát

- [ ] Có chỗ nào "đọc rồi ghi" trạng thái tài xế / offer / trip mà không khóa hay điều kiện?
- [ ] Tài xế có thể kẹt `OFFERED` khi: offer hết hạn, trip bị hủy lúc đang offer, accept thất bại giữa chừng? (mỗi nhánh phải trả về AVAILABLE đúng một lần)
- [ ] Hủy chuyến lúc đang MATCHING có hủy offer PENDING và trả tài xế không?
- [ ] Lng/lat có bị đảo khi gọi Redis không?
- [ ] Gửi WebSocket có nằm sau commit không?
- [ ] Bán kính, timeout, độ mới có đọc từ `AppProperties` không (NFR-MNT-005)?
