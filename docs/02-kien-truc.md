# Kiến trúc kỹ thuật

## 1. Công nghệ

| Thành phần | Chọn |
|---|---|
| Ngôn ngữ / framework | Java 21, Spring Boot 4.1 (Web MVC, WebSocket, Data JPA, Data Redis, Security + OAuth2 Resource Server, Validation, Flyway) |
| CSDL | PostgreSQL 18 — database `ride_hailing` (test: `ride_hailing_test`) |
| Chỉ mục không gian, bộ đếm | Redis 7.4 (container `rhl-redis`, cổng 6379) |
| Realtime | STOMP over WebSocket, simple broker có heartbeat 10 s |
| Tài liệu API | springdoc-openapi → `/swagger-ui.html`, `/v3/api-docs` (NFR-MNT-001) |
| Demo | Trang tĩnh trong `src/main/resources/static/` (Leaflet + STOMP.js), script `tools/simulate-drivers.mjs` (Node 22) |

## 2. Package — mỗi miền một package (CON-01)

```
org.example.ride_hailing_logistics
├── common/        ApiResponse, ErrorCode, BusinessException, GlobalExceptionHandler,
│                  CorrelationIdFilter, RateLimitFilter, SecurityConfig, JwtService,
│                  CurrentUser, AppProperties, GeoUtils (haversine, geohash)
├── platform/      outbox/ (OutboxEvent, OutboxRelay, EventHandler, ProcessedEvent)
│                  audit/ (AuditRecord, AuditService)
│                  idempotency/ (IdempotencyRecord, IdempotencyService)
├── identity/      User, Role, RefreshToken, PasswordResetToken, AuthService, UserAdminService
├── driver/        DriverProfile, Vehicle, DriverDocument, DriverReviewDecision,
│                  DriverService, DriverReviewService, DriverAvailabilityService
├── location/      TelemetryValidator, LocationService, DriverLocationIndex (Redis GEO)
├── pricing/       PricingRule, FareQuote, FareCalculator, SurgeService, RouteEstimator, QuoteService
├── trip/          Trip, TripStatusHistory, TripStateMachine, TripService, CancellationPolicy
│   └── dispatch/  DriverOffer, MatchingEngine, DispatchService, DispatchScheduler
├── payment/       Payment, Refund, PaymentService, SandboxPaymentGateway, PaymentCallbackService
├── wallet/        Wallet, WalletEntry, CommissionRule, WalletService
├── realtime/      WebSocketConfig, StompAuthInterceptor, RealtimePublisher, RealtimeEnvelope
└── dev/           DataSeeder (profile dev)
```

**Luật ranh giới (CON-02, DR-002):**
- Repository của miền nào chỉ được tiêm vào service **cùng miền**.
- Miền khác gọi qua **service công khai** của miền đó (ví dụ `trip` gọi `DriverAvailabilityService.reserve(...)`, `LocationService.findCandidates(...)`, `QuoteService.consumeQuote(...)`), hoặc nghe **domain event**.
- Không `@ManyToOne` xuyên miền — chỉ lưu ID (UUID).

## 3. Dữ liệu (Flyway `db/migration`)

| Miền | Bảng |
|---|---|
| identity | `users`, `user_roles`, `refresh_tokens`, `password_reset_tokens` |
| driver | `driver_profiles`, `vehicles`, `driver_documents`, `driver_review_decisions` |
| pricing | `pricing_rules`, `fare_quotes` |
| trip | `trips`, `trip_status_history`, `driver_offers` |
| payment | `payments`, `refunds` |
| wallet | `wallets`, `wallet_entries`, `commission_rules` |
| platform | `outbox_events`, `processed_events`, `audit_records`, `idempotency_records` |

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

## 4. Event (Outbox) — SRS §5.6

Envelope (COM-009): `eventId, eventType, eventVersion, occurredAt, correlationId, producer, aggregateType, aggregateId, payload`.

| Event | Producer | Consumer | Tác dụng |
|---|---|---|---|
| `TripRequested` | trip | pricing | tăng bộ đếm cầu surge |
| `TripAccepted` | trip | (chưa có consumer) | ghi outbox để mở rộng; realtime đi after-commit |
| `TripStatusChanged` | trip | realtime | **Không ghi outbox**: đi Spring event after-commit cho độ trễ thấp; mất bản tin thì client lấy snapshot (FR-RT-007) |
| `TripCompleted` | trip | pricing | chốt cước → `FareFinalized` |
| `TripCancelled` | trip | payment | thu phí hủy nếu > 0 |
| `FareFinalized` | pricing | payment | tạo & thu thanh toán chuyến |
| `PaymentSucceeded` | payment | wallet | ghi thu nhập & hoa hồng |
| `PaymentFailed` | payment | (chưa có consumer) | khách nhận qua `PAYMENT_UPDATED` after-commit |
| `RefundSucceeded` | payment | (chưa có consumer) | hoàn tiền không trừ ví tài xế (01-nghiep-vu §6) |
| `DriverEarningPosted` | wallet | realtime | `DRIVER_EARNING_POSTED` tới tài xế |
| `DriverAvailabilityChanged` | driver | location | Offline/đình chỉ → xóa khỏi GEO |

Relay: mỗi 300 ms lấy tối đa 50 event `NEW` (`FOR UPDATE SKIP LOCKED`), gọi từng handler; handler chạy trong transaction riêng, ghi `processed_events(event_id, handler)` → **idempotent** (FR-EVT-004). Lỗi → `attempts++`, `next_attempt_at` lùi theo cấp số nhân; quá 8 lần → `FAILED` (DLQ), admin xem và xử lý lại (FR-EVT-008).

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
| Quản trị | `GET /api/v1/admin/users`, `GET/POST /api/v1/admin/pricing-rules`, `POST /admin/pricing-rules/{id}/deactivate`, `GET/POST /admin/commission-rules`, `PUT /admin/users/{id}/roles`, `PUT /admin/users/{id}/status`, `POST /admin/drivers/{id}/suspend`, `GET /admin/audit`, `GET /admin/outbox/failed`, `POST /admin/outbox/{id}/retry` |

Phản hồi thành công: `{code:"SUCCESS", message, data, correlationId}`. Danh sách luôn phân trang (`page`, `size` ≤ 100) — NFR-PERF-008.

## 6. WebSocket (STOMP) — COM-005…007

- Kết nối: `ws://host/ws`, frame `CONNECT` có header `Authorization: Bearer <access token>`. Token sai/hết hạn → từ chối.
- Heartbeat 10 s/10 s. Client reconnect thì **gọi `GET /api/v1/trips/{id}` lấy snapshot** rồi mới áp dụng bản tin mới (FR-RT-005).

| Hướng | Destination | Ai | Nội dung |
|---|---|---|---|
| Client → server | `/app/driver/location` | Tài xế | telemetry (Phụ lục A.1) |
| Server → client | `/user/queue/offers` | Tài xế được offer | `DRIVER_OFFER_CREATED`, `DRIVER_OFFER_CLOSED` |
| Server → client | `/topic/trips/{tripId}` | Khách & tài xế của chuyến, nhân viên hỗ trợ | `TRIP_STATUS_CHANGED`, `DRIVER_LOCATION_UPDATED` |
| Server → client | `/user/queue/notifications` | Chủ sở hữu | `PAYMENT_UPDATED`, `DRIVER_EARNING_POSTED`, `TELEMETRY_REJECTED` |

Mọi bản tin: `{messageId, type, version, sentAt, sequence, data}`; `sequence` tăng dần theo từng destination (Redis INCR) để client bỏ bản tin cũ/trùng (FR-RT-006). `SUBSCRIBE /topic/trips/{id}` bị chặn nếu người dùng không phải người tham gia chuyến hoặc chuyến đã kết thúc quá 2 phút (FR-RT-008, NFR-PRV-002).

## 7. Cấu hình & bí mật

- `src/main/resources/application.properties`: cấu hình chung, mọi bí mật dạng `${ENV_VAR}`.
- `config/application.properties` (thư mục gốc dự án, **đã gitignore**): mật khẩu DB, `JWT_SECRET`, `PAYMENT_SANDBOX_SECRET` cho máy local. Mẫu ở `config/application.properties.example`.
- Profile `dev` bật `DataSeeder` (tài khoản mẫu, 100 tài xế mô phỏng, bảng giá).
