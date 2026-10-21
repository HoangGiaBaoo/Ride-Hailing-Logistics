# Quy tắc viết code — luôn áp dụng

Viết code như một lập trình viên Java bình thường: Lombok, Stream API, record, annotation Spring đầy đủ. Không cần viết dài dòng cho dễ đọc.

Những gì dưới đây **không phải quy tắc phong cách** — chúng là đúng/sai của hệ thống đặt xe thời gian thực.

## Năm điều cấm — không thương lượng

| Cấm | Vì sao |
|---|---|
| **`float`/`double` cho tiền** | Tiền là `BigDecimal` + `currency` (FR-PRI-016, FR-WAL-008). `double` chỉ dùng cho tọa độ, khoảng cách (mét), accuracy, tốc độ |
| **Đổi `status` của Trip bằng `setStatus` ngoài `TripStateMachine`/`Trip.transitionTo`** | Mọi chuyển trạng thái phải qua bảng chuyển hợp lệ + ghi `trip_status_history` (FR-TRIP-008…010) |
| **Đọc rồi ghi trạng thái khả dụng tài xế/offer/thanh toán kiểu "if (x == A) set B"** | Race condition → double assignment. Dùng `UPDATE … WHERE status = :expected` (kiểm số dòng = 1), `SELECT … FOR UPDATE`, hoặc `@Version` (CON-06, DR-009) |
| **Tin dữ liệu client tự khai**: driverId trong payload, số tiền thanh toán, giá, trạng thái | Lấy danh tính từ token, tiền từ cước đã chốt (FR-LOC-005, FR-PAY-002, NFR-SEC-012) |
| **Tiêm Repository của miền khác, hay đọc DB của service khác** | Vi phạm ranh giới miền (CON-02). Cùng service: gọi service công khai của miền đó. Khác service: Feign vào `/internal/**` hoặc nghe event |

## Bốn lỗi kinh điển phải tự kiểm

1. **Double assignment** — chấp nhận offer phải kiểm *đồng thời* trip `MATCHING`, offer `PENDING` chưa hết hạn, driver `OFFERED`, trong **một transaction**; DB còn có partial unique index chặn nốt.
2. **Hiệu ứng trùng khi retry** — tạo chuyến, chấp nhận offer, hủy, thanh toán, ghi ví, consumer event: tất cả idempotent (BR-015).
3. **Vị trí cũ đè vị trí mới** — so `sequence`/`deviceTimestamp` trước khi ghi (FR-LOC-006, NFR-REL-002).
4. **Lộ vị trí/PII** — khách chỉ nhận vị trí tài xế khi được ghép; tài xế chỉ thấy SĐT người nhận khi được gán (BR-013).

## Event & transaction

- Thay đổi nghiệp vụ + event tài chính ghi **trong cùng transaction** qua `OutboxService.append(...)` (FR-EVT-002). Không gọi thẳng service miền khác để "báo" việc tài chính.
- Đẩy realtime dùng `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)` — không gửi WebSocket trước khi commit.
- Consumer outbox phải idempotent (dựa `processed_events` hoặc business key). Event đi outbox → Redis Stream `rhl:events` → `inbox_events` của service nhận; handler là bean `EventHandler`.
- Gọi sang service khác (Feign) **không** nằm trong transaction của mình: lệnh có tác dụng phụ phải idempotent theo khóa nghiệp vụ, có bù trừ khi mình rollback (`AfterCommit.onRollback`), và có đối soát cho lệnh bị mất.
- Endpoint `/internal/**` chỉ cho vai trò `SERVICE`, không có route ở gateway; endpoint cho người dùng luôn ở `/api/v1/**`.

## Được dùng thoải mái

- Lombok: `@Getter`, `@Setter`, `@Builder`, `@RequiredArgsConstructor`, `@Slf4j`… (entity dùng `@Getter @Setter`, **không `@Data`**)
- Stream API, `Optional`, `var`, record (DTO là record), switch expression, text block
- Annotation Spring đầy đủ

Lưu ý kỹ thuật: JDK trên máy là 23, `pom.xml` đặt `java.version=21`. Từ JDK 21 javac không tự chạy annotation processor trên classpath → `pom.xml` khai báo `annotationProcessorPaths` cho Lombok — **đừng xóa**.

Spring Boot **4.x**: Jackson 3 (`tools.jackson.databind.*`, annotation vẫn là `com.fasterxml.jackson.annotation.*`), Hibernate 7, Spring Security 7, starter tách nhỏ (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`…).

## Quy ước đặt tên

| Đối tượng | Quy ước | Ví dụ |
|---|---|---|
| Bảng / cột | snake_case, bảng số nhiều | `trips`, `driver_offers`, `pickup_lat` |
| Entity | PascalCase số ít | `Trip`, `DriverOffer` |
| DTO | record, hậu tố `Request`/`Response` | `CreateTripRequest`, `TripResponse` |
| Service | `<Nghiệp vụ>Service` | `DispatchService` |
| Controller | `<Nghiệp vụ>Controller`, REST dưới `/api/v1` | `TripController` |
| URL | kebab-case, danh từ số nhiều | `/api/v1/pricing-rules` |
| Enum | UPPER_SNAKE_CASE | `PICKING_UP`, `PENDING_REVIEW` |
| Event | PascalCase quá khứ | `TripCompleted`, `FareFinalized` |
| Khóa Redis | `miền:mục:{id}` | `loc:{driverId}` |

**Mọi định danh viết tiếng Anh.** **Comment viết tiếng Việt không dấu**, trả lời *tại sao* chứ không kể lại *cái gì*. Thông báo lỗi trả cho người dùng (`message`) là tiếng Việt có dấu, hướng dẫn hành động tiếp theo, không lộ chi tiết nội bộ (NFR-USA-005).

## Kiểu dữ liệu

| Loại | Kiểu Java | Kiểu DB |
|---|---|---|
| ID aggregate | `UUID` | `uuid` |
| Tiền | `BigDecimal` (VND scale 0 khi tính, lưu scale 2) | `numeric(15,2)` + `currency varchar(3)` |
| Tọa độ | `double` | `double precision` (WGS84) |
| Khoảng cách | `double`/`long`, **mét** | |
| Thời điểm | `Instant` (UTC) | `timestamptz` |
| Trạng thái | `enum` + `@Enumerated(EnumType.STRING)` | `varchar` |
| Cấu hình | `@ConfigurationProperties` (`AppProperties`) | — |

## Cách làm việc

1. **Làm từng đợt trong `docs/04-chuc-nang.md`.** Xong một đợt thì dừng, tóm tắt, cập nhật `docs/TIEN-DO.md`.
2. **Không tự thêm chức năng ngoài `docs/04-chuc-nang.md`.** Thấy SRS có mà docs chưa có → hỏi.
3. Nhiều cách → chọn cách đơn giản nhất **vẫn đúng dưới đồng thời**.
4. Không tạo file `.md` mới ngoài `docs/` trừ khi được yêu cầu.
