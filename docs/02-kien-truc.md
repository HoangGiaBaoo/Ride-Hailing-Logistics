# Kiến trúc kỹ thuật

## 1. Công nghệ

| Thành phần | Chọn |
|---|---|
| Ngôn ngữ / framework | Java 21, Spring Boot 4.0.8 + Spring Cloud 2025.1.3 (Web MVC, WebSocket, Data JPA, Data Redis, Security + OAuth2 Resource Server, Validation, Flyway, OpenFeign, Eureka, Config, Gateway) |
| CSDL | PostgreSQL 18 — **mỗi service một database**: `rhl_identity`, `rhl_driver`, `rhl_pricing`, `rhl_trip`, `rhl_payment` (test: thêm hậu tố `_test`) |
| Chỉ mục không gian, bộ đếm, bus event | Redis 7.4 (container `rhl-redis`, cổng 6379): GEO, bộ đếm, Redis Stream `rhl:events`, Pub/Sub realtime |
| Realtime | STOMP over WebSocket ở trip-service, simple broker heartbeat 10 s; gateway chuyển `/ws/**` |
| Tài liệu API | springdoc-openapi ở từng service → `/swagger-ui.html`, `/v3/api-docs` (NFR-MNT-001) |
| Demo | Trang tĩnh trong `api-gateway/src/main/resources/static/` (Leaflet + STOMP.js), `tools/seed-dev.mjs`, `tools/simulate-drivers.mjs` (Node 22) |

Spring Boot 4.1 chưa có Spring Cloud bản phát hành (chỉ milestone) → dùng 4.0.8.

## 2. Module Maven và dịch vụ (CON-01)

```
ride-hailing-logistics (pom cha)
├── common-lib/        jar dùng chung, KHÔNG chạy độc lập
│   ├── common/        ApiResponse, ErrorCode, BusinessException, GlobalExceptionHandler, SecurityConfig,
│   │                  JwtService (+ token SERVICE), RateLimitInterceptor, AppProperties, GeoUtils, Normalizers,
│   │                  client/FeignSupport (gắn token SERVICE, đổi lỗi Feign -> BusinessException),
│   │                  realtime/RealtimeBridge (Redis Pub/Sub -> trip-service)
│   └── platform/      outbox/ (OutboxService, OutboxRelay, EventStreamConsumer, InboxProcessor, EventHandler)
│                      audit/, idempotency/, PlatformAdminController
├── eureka-server      :8761  danh bạ dịch vụ
├── config-server      :8888  cấu hình tập trung (native: configs/*.properties + config/ gitignored)
├── api-gateway        :8080  cửa vào duy nhất, route lb://, phục vụ trang demo tĩnh
├── identity-service   :8081  identity/, notification/                       DB rhl_identity
├── driver-service     :8082  driver/, location/ (Redis GEO)                 DB rhl_driver
├── pricing-service    :8083  pricing/                                       DB rhl_pricing
├── trip-service       :8084  trip/, trip/dispatch/, trip/client/, realtime/ DB rhl_trip
└── payment-service    :8085  payment/, wallet/                              DB rhl_payment
```

Mọi service giữ package gốc `org.example.ride_hailing_logistics`; lớp `*ServiceApplication` nằm ở package gốc.

**Luật ranh giới (CON-02, DR-002):**
- Mỗi service chỉ đọc/ghi **database của mình**. Không service nào kết nối DB của service khác.
- Trong một service có nhiều miền (driver + location, trip + dispatch, payment + wallet): Repository của miền nào chỉ được tiêm vào service **cùng miền**.
- Gọi đồng bộ sang service khác qua **OpenFeign + Eureka** vào `/internal/**` (chỉ vai trò `SERVICE`; gateway không có route cho `/internal`). Gọi bất đồng bộ qua **event** (outbox → Redis Stream).
- Không `@ManyToOne` xuyên miền — chỉ lưu ID (UUID).

### Gọi đồng bộ giữa service

| Bên gọi | Bên nhận | Endpoint `/internal/**` | Dùng cho |
|---|---|---|---|
| trip | pricing | `POST /quotes/{id}/consume`, `/release` | dùng quote một lần theo tripId; bù trừ khi tạo chuyến rollback |
| trip | driver | `GET /drivers/candidates`, `/{id}/snapshot`, `/{id}/location`, `POST /{id}/location` | ứng viên gần, ảnh chụp tài xế khi ghép, vị trí, telemetry qua WebSocket |
| trip | driver | `POST /drivers/{id}/reserve`, `/release-offer`, `/busy`, `/revert-busy`, `/finish-trip` | giữ/thả tài xế (saga, xem dưới) |
| pricing | driver | `GET /drivers/supply` | cung cho surge (lỗi → surge 1.0) |
| driver | identity | `GET /users/{id}`, `POST /users/batch` | tên, SĐT cho hồ sơ/kiểm duyệt |
| driver | trip | `GET /drivers/{id}/engagement` | đối soát giữ tài xế bị treo |

Token: service gọi tự ký JWT vai trò `SERVICE` bằng `JWT_SECRET` chung. Lỗi nghiệp vụ của bên nhận (`ApiResponse` lỗi) → bên gọi dựng lại `BusinessException` cùng mã; lỗi mạng/5xx → 503 `DEPENDENCY_UNAVAILABLE`.

### Giữ tài xế xuyên service

`driver_profiles.availability_ref` ghi offer (khi `OFFERED`) hoặc chuyến (khi `BUSY`) đang giữ tài xế. Mọi lệnh là cập nhật có điều kiện theo cả trạng thái **và** ref → gọi lại sau timeout cho cùng kết quả; lệnh đến trễ của offer cũ không chạm được chuyến mới.

1. trip-service sinh `offerId` trước → `reserve(driver, offerId)` (AVAILABLE→OFFERED). Thắng mới ghi offer; transaction trip rollback → `AfterCommit.onRollback` gọi `release-offer`.
2. Chấp nhận: trong transaction trip (khóa trip + offer) gọi `busy(driver, offerId, tripId)` (OFFERED→BUSY); rollback → `revert-busy` (BUSY→OFFERED cho đúng offer đó).
3. Đóng offer / kết thúc chuyến: gọi `release-offer` / `finish-trip` **sau commit**, best-effort.
4. `DriverHoldReconciler` (driver-service, 30 s) hỏi trip-service tài xế còn gắn với offer/chuyến nào; giữ quá hạn mà không còn gắn → thả về AVAILABLE. Bù cho mọi lệnh thả bị mất.

DB trip-service vẫn có partial unique index chặn một tài xế hai chuyến hoạt động — tầng chặn cuối.

## 3. Dữ liệu (Flyway `db/migration/V1__init.sql` của từng service)

| Service | Bảng nghiệp vụ |
|---|---|
| identity | `users`, `user_roles`, `refresh_tokens`, `password_reset_tokens` |
| driver | `driver_profiles` (+ `availability_ref`), `vehicles`, `driver_documents`, `driver_review_decisions` |
| pricing | `pricing_rules`, `fare_quotes` (+ `used_by_trip`) |
| trip | `trips`, `trip_status_history`, `driver_offers` |
| payment | `payments` (+ `trip_code`), `refunds`, `payment_callback_events`, `wallets`, `wallet_entries`, `commission_rules` |
| mọi service | `outbox_events`, `inbox_events`, `processed_events`, `audit_records`, `idempotency_records` |

Bảo đảm trong DB (không chỉ trong code):
- `trips`: partial unique index `driver_id WHERE status IN (ACCEPTED, PICKING_UP, ARRIVED, IN_TRIP)` và `customer_id WHERE status IN (các trạng thái hoạt động)` → **không thể** double assignment (NFR-REL-003).
- `driver_offers`: unique `trip_id WHERE status='ACCEPTED'`, unique `driver_id WHERE status='PENDING'`.
- `payments`: unique `(trip_id, kind)`. `wallet_entries`: unique `(wallet_id, entry_type, reference_id)`.
- Trigger chặn `UPDATE/DELETE` trên `trip_status_history`, `wallet_entries`, `audit_records` (DR-005, FR-ADM-006).
- Cột tiền `NUMERIC(15,2)` + cột `currency`; thời gian `timestamptz` (UTC).
- `@Version` (optimistic lock) trên `trips`, `driver_profiles`, `payments`, `wallets` (DR-009).

Redis:

| Khóa | Kiểu | Nội dung |
|---|---|---|
| `loc:{driverId}` | hash | lat, lng, accuracy, heading, speed, deviceTs, serverTs, sequence, vehicleType, suspicious |
| `geo:drivers:{vehicleType}` | GEO | tài xế online theo loại xe |
| `geo:lastseen` | zset | driverId → serverTs, để dọn vị trí hết hạn (DR-GEO-005) |
| `surge:demand:{geohash5}:{window}` | string | bộ đếm cầu, TTL 15 phút |
| `trip:active-by-driver` | hash | driverId → tripId, read model cho fan-out vị trí |
| `rt:seq:{topic}` | string | sequence của bản tin realtime |
| `rl:{bucket}:{subject}:{window}` | string | rate limit cửa sổ cố định |

## 4. Event (Outbox → Redis Stream → Inbox) — SRS §5.6

Envelope (COM-009): `eventId, eventType, eventVersion, occurredAt, correlationId, producer, aggregateType, aggregateId, payload`.

| Event | Producer | Consumer | Tác dụng |
|---|---|---|---|
| `UserRegistered` | identity | driver | tạo hồ sơ tài xế nháp cho tài khoản DRIVER |
| `DriverApproved` | driver | payment | mở ví tài xế |
| `DriverAvailabilityChanged` | driver | driver (location) | Offline/đình chỉ → xóa khỏi GEO |
| `TripRequested` | trip | pricing | tăng bộ đếm cầu surge |
| `TripAccepted` | trip | (chưa có consumer) | ghi outbox để mở rộng; realtime đi after-commit |
| `TripStatusChanged` | trip | realtime | **Không ghi outbox**: Spring event after-commit cho độ trễ thấp; mất bản tin thì client lấy snapshot (FR-RT-007) |
| `TripCompleted` | trip | pricing | chốt cước → `FareFinalized` |
| `TripCancelled` | trip | payment | thu phí hủy nếu > 0 |
| `FareFinalized` | pricing | payment, trip | tạo & thu thanh toán chuyến; lưu cước cuối vào chuyến |
| `PaymentSucceeded` | payment | payment (wallet) | ghi thu nhập & hoa hồng |
| `PaymentFailed`, `RefundSucceeded` | payment | (chưa có consumer) | khách nhận `PAYMENT_UPDATED` qua RealtimeBridge |
| `DriverEarningPosted` | payment (wallet) | trip (realtime) | `DRIVER_EARNING_POSTED` tới tài xế |

Đường đi của một event:
1. **Outbox** — service ghi `outbox_events` trong cùng transaction với thay đổi nghiệp vụ (FR-EVT-002).
2. **Relay** (200 ms, tối đa 50, `FOR UPDATE SKIP LOCKED`) — `XADD rhl:events` rồi đánh dấu `PROCESSED`; lỗi → lùi theo cấp số nhân, quá 8 lần → `FAILED`.
3. **Consumer** (100 ms) — mỗi service một consumer group = tên service (tạo ở offset 0 + `MKSTREAM`; group đã có → `BUSYGROUP` là bình thường), lấy lại pending khi khởi động; chỉ giữ event có handler, ghi `inbox_events` (bỏ qua trùng `eventId`) rồi `XACK`.
4. **InboxProcessor** — chạy handler trong transaction riêng, ghi `processed_events(event_id, handler)` → **idempotent** (FR-EVT-004). Lỗi → lùi; quá 8 lần → `FAILED` (DLQ), admin xử lý lại (FR-EVT-008).

Realtime giữa service (không cần bền): `RealtimeBridge` publish Redis Pub/Sub kênh `rt:driver-location`, `rt:user-notification`; trip-service nghe và đẩy STOMP.

## 5. REST API (v1) — xem Swagger để có đủ schema

| Nhóm | Endpoint |
|---|---|
| Auth | `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `/password-reset/request`, `/password-reset/confirm` |
| Tài khoản | `GET/PATCH /api/v1/users/me` |
| Tài xế | `GET /api/v1/drivers/me`, `PUT /drivers/me/profile`, `PUT /drivers/me/vehicle`, `POST /drivers/me/documents` (multipart), `POST /drivers/me/submit`, `GET /drivers/me/availability`, `POST /drivers/me/online`, `/offline`, `POST /drivers/me/location`, `GET /drivers/me/offers/current`, `GET /drivers/me/wallet`, `/wallet/entries` |
| Kiểm duyệt | `GET /api/v1/review/drivers`, `GET /review/drivers/{id}`, `GET /review/documents/{id}/file`, `POST /review/drivers/{id}/decisions` |
| Báo giá | `POST /api/v1/quotes` |
| Chuyến | `POST /api/v1/trips` *(Idempotency-Key)*, `GET /trips/{id}`, `GET /trips/{id}/history`, `GET /trips/mine`, `GET /trips/{id}/cancellation-preview`, `POST /trips/{id}/cancel` *(Idempotency-Key)*, `POST /trips/{id}/pickup-started`, `/arrived`, `/started`, `/completed`, `GET /trips/{id}/payments` |
| Offer | `POST /api/v1/offers/{id}/accept` *(Idempotency-Key)*, `POST /offers/{id}/decline` |
| Thanh toán | `POST /api/v1/payments/{id}/retry` *(Idempotency-Key)*, `POST /api/v1/payments/sandbox/callback` (header `X-Sandbox-Timestamp`, `X-Sandbox-Signature` = HMAC-SHA256(secret, timestamp + "." + body)) |
| Hỗ trợ | `GET /api/v1/support/trips`, `POST /support/trips/{id}/cancel` |
| Tài chính | `GET /api/v1/finance/payments`, `POST /finance/payments/{id}/refunds` *(Idempotency-Key)*, `GET /finance/wallets/{driverId}`, `GET /finance/wallets/{driverId}/entries`, `POST /finance/wallets/{driverId}/adjustments` *(Idempotency-Key)* |
| Quản trị | `GET /api/v1/admin/users`, `GET/POST /api/v1/admin/pricing-rules`, `POST /admin/pricing-rules/{id}/deactivate`, `GET/POST /admin/commission-rules`, `PUT /admin/users/{id}/roles`, `PUT /admin/users/{id}/status`, `POST /admin/drivers/{id}/suspend`, `GET /admin/platform/{service}/audit`, `GET /admin/platform/{service}/events/failed-outgoing`, `/events/failed-incoming`, `POST /admin/platform/{service}/events/{id}/retry` (`{service}` = `identity-service`…`payment-service`) |

Mọi đường dẫn trên đi qua gateway `http://localhost:8080`; gateway chọn service theo tiền tố (`config-server/src/main/resources/configs/api-gateway.properties`).

Phản hồi thành công: `{code:"SUCCESS", message, data, correlationId}`. Danh sách luôn phân trang (`page`, `size` ≤ 100) — NFR-PERF-008.

## 6. WebSocket (STOMP) — COM-005…007

- Kết nối: `ws://localhost:8080/ws` (gateway → trip-service), frame `CONNECT` có header `Authorization: Bearer <access token>`. Token sai/hết hạn → từ chối.
- Heartbeat 10 s/10 s. Client reconnect thì **gọi `GET /api/v1/trips/{id}` lấy snapshot** rồi mới áp dụng bản tin mới (FR-RT-005).

| Hướng | Destination | Ai | Nội dung |
|---|---|---|---|
| Client → server | `/app/driver/location` | Tài xế | telemetry (Phụ lục A.1) |
| Server → client | `/user/queue/offers` | Tài xế được offer | `DRIVER_OFFER_CREATED`, `DRIVER_OFFER_CLOSED` |
| Server → client | `/topic/trips/{tripId}` | Khách & tài xế của chuyến, nhân viên hỗ trợ | `TRIP_STATUS_CHANGED`, `DRIVER_LOCATION_UPDATED` |
| Server → client | `/user/queue/notifications` | Chủ sở hữu | `PAYMENT_UPDATED`, `DRIVER_EARNING_POSTED`, `TELEMETRY_REJECTED` |

Mọi bản tin: `{messageId, type, version, sentAt, sequence, data}`; `sequence` tăng dần theo từng destination (Redis INCR) để client bỏ bản tin cũ/trùng (FR-RT-006). `SUBSCRIBE /topic/trips/{id}` bị chặn nếu người dùng không phải người tham gia chuyến hoặc chuyến đã kết thúc quá 2 phút (FR-RT-008, NFR-PRV-002).

## 7. Cấu hình & bí mật

- Mỗi service chỉ có `src/main/resources/application.properties` gồm tên và `spring.config.import=configserver:...`.
- Cấu hình thật ở `config-server/src/main/resources/configs/`: `application.properties` (chung), `<service>.properties` (cổng, URL DB), `application-dev.properties`, `identity-service-dev.properties` (tạo 4 tài khoản nhân viên), `api-gateway.properties` (route). Mọi bí mật dạng `${ENV_VAR}`.
- `config/application.properties` (thư mục gốc, **đã gitignore**): `DB_PASSWORD`, `JWT_SECRET`, `PAYMENT_SANDBOX_SECRET`. config-server đọc thư mục này và phát cho mọi service. Mẫu ở `config/application.properties.example`.
- Test không cần config-server: `src/test/resources/application.properties` của từng service import thẳng các file cấu hình trên.
- Mỗi service pool tối đa 10 kết nối (5 × 10 = 50 < `max_connections` 100 mặc định của PostgreSQL).

## 8. Chạy

```
mvnw.cmd package -DskipTests
powershell -File tools/start-all.ps1        # eureka -> config -> 5 service -> gateway, log ở logs/
node tools/seed-dev.mjs                     # bảng giá, hoa hồng, 2 khách, 101 tài xế đã duyệt (qua API)
node tools/simulate-drivers.mjs --drivers 100 --interval 3000 --rides
powershell -File tools/start-all.ps1 -Stop
```
