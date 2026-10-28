# Kiến trúc kỹ thuật

## 1. Công nghệ

| Thành phần | Chọn |
|---|---|
| Ngôn ngữ / framework | Java 21, Spring Boot 4.0.8 + Spring Cloud 2025.1.3 (Web MVC, WebSocket, Data JPA, Data Redis, Security + OAuth2 Resource Server, Validation, Flyway, OpenFeign + Resilience4j, Eureka, Config + Bus, Gateway), MapStruct 1.6.3, Redisson 3.52 |
| CSDL | PostgreSQL 18 — **mỗi service một database**: `rhl_identity`, `rhl_driver`, `rhl_location`, `rhl_pricing`, `rhl_trip`, `rhl_payment` (test: thêm hậu tố `_test`) |
| Message broker | **Apache Kafka 3.9** (container `rhl-kafka`, KRaft, cổng 9092): event nghiệp vụ, luồng vị trí, Spring Cloud Bus |
| Redis | Redis 7.4 (container `rhl-redis`, cổng 6379): GEO, cache (`@Cacheable` + TTL), bộ đếm, rate limit, khóa phân tán Redisson, Pub/Sub realtime, thu hồi token |
| Realtime | STOMP over WebSocket ở trip-service, simple broker heartbeat 10 s; gateway chuyển `/ws/**` |
| Tài liệu API | springdoc-openapi ở từng service → `/swagger-ui.html`, `/v3/api-docs` (NFR-MNT-001) |
| Demo | Trang tĩnh trong `api-gateway/src/main/resources/static/` (Leaflet + STOMP.js), `tools/seed-dev.mjs`, `tools/simulate-drivers.mjs` (Node 22) |

Spring Boot 4.1 chưa có Spring Cloud bản phát hành (chỉ milestone) → dùng 4.0.8.

## 2. Module Maven và dịch vụ (CON-01)

```
ride-hailing-logistics (pom cha)
├── common-lib/        jar dùng chung, KHÔNG chạy độc lập
│   ├── common/        api/ApiResponse, error/ (ErrorCode, BusinessException, GlobalExceptionHandler),
│   │                  security/ (SecurityConfig, JwtService + token SERVICE), config/CacheConfig, RateLimitInterceptor,
│   │                  AppProperties, util/ (GeoUtils, Normalizers), client/FeignSupport, realtime/RealtimeBridge
│   └── platform/      outbox/ (OutboxService, OutboxRelay, InboxProcessor, EventHandler), kafka/ (KafkaConfig,
│                      KafkaTopics, KafkaEventConsumer), audit/, idempotency/, PlatformAdminController
├── eureka-server      :8761  danh bạ dịch vụ
├── config-server      :8888  cấu hình tập trung: git (config-repo/) + native (config/ gitignored), Bus qua Kafka
├── api-gateway        :8080  cửa vào duy nhất: GlobalFilter JWT, route lb://, trang demo tĩnh
├── identity-service   :8081  tài khoản, đăng nhập, refresh token, RBAC          DB rhl_identity
├── driver-service     :8082  hồ sơ, xe, giấy tờ, kiểm duyệt                    DB rhl_driver
├── location-service   :8083  telemetry, Redis GEO, tìm tài xế gần              DB rhl_location
├── pricing-service    :8084  bảng giá, báo giá, surge, chốt cước               DB rhl_pricing
├── trip-service       :8085  chuyến, dispatch/offer, khả dụng tài xế, STOMP    DB rhl_trip
└── payment-service    :8086  thanh toán, hoàn tiền, ví, hoa hồng               DB rhl_payment
```

### Cấu trúc package trong mỗi service

`org.example.ride_hailing_logistics.<service>.`:

| Package | Nội dung |
|---|---|
| `controller` | REST controller, chỉ nhận/trả DTO |
| `dto/request`, `dto/response` | record đầu vào (chỉ trường được phép sửa — DR-VAL-005) / đầu ra |
| `entity`, `enums`, `repository` | JPA entity (`@Getter @Setter`, không `@Data`), enum trạng thái, Spring Data repository |
| `service` + `service/impl` | interface nghiệp vụ + cài đặt; `@Transactional` đặt ở impl |
| `domain` | logic thuần không Spring (state machine, cước, surge, phí hủy, xếp hạng, telemetry, chữ ký) — unit test không cần context |
| `mapper` | MapStruct (`unmappedTargetPolicy = ERROR`: thêm trường mà quên map thì không biên dịch được) |
| `exception` | exception riêng của miền, kế thừa `BusinessException` → `GlobalExceptionHandler` trả `ApiResponse` lỗi |
| `client` + `client/dto` | Feign client + `FallbackFactory` |
| `event`, `event/handler`, `event/listener` | tên/payload event, handler đọc từ inbox, listener after-commit |
| `config`, `scheduler`, `websocket`, `gateway` | cấu hình bean, job định kỳ, STOMP, cổng thanh toán sandbox |

Lớp `*ServiceApplication` nằm ở package gốc `org.example.ride_hailing_logistics`.

### Vì sao khả dụng tài xế nằm ở trip-service

Ranh giới service theo SRS là 6 miền, nhưng **gán tài xế cho chuyến phải nguyên tử** (CON-06, UC-06): trip `MATCHING`, offer `PENDING`, tài xế `OFFERED → BUSY` đổi trong **một transaction trên một database**. Vì vậy trạng thái khả dụng (`driver_states`: `OFFLINE/AVAILABLE/OFFERED/BUSY`, cờ đình chỉ, ảnh chụp tên/xe) do trip-service sở hữu; driver-service chỉ giữ hồ sơ và kiểm duyệt. Bấm Online: trip hỏi driver-service `online-eligibility` qua Feign (ngoài transaction), rồi mới đổi trạng thái có điều kiện trong transaction của mình. Mỗi lần đổi phát `DriverAvailabilityChanged` qua outbox để location-service dọn GEO.

Các việc còn lại giữa service đều chỉ cần **nhất quán sau cùng** (chuyến → cước → thanh toán → ví), đi event.

**Luật ranh giới (CON-02, DR-002):**
- Mỗi service chỉ đọc/ghi **database của mình**.
- Gọi đồng bộ qua **OpenFeign + Eureka** vào `/internal/**` (chỉ vai trò `SERVICE`; gateway không có route cho `/internal`), **chỉ để đọc hoặc thao tác có bù trừ**. Gọi bất đồng bộ qua **event** (outbox → Kafka → inbox).
- Không `@ManyToOne` xuyên miền — chỉ lưu ID (UUID).

### Gọi đồng bộ giữa service

| Bên gọi | Bên nhận | Endpoint `/internal/**` | Dùng cho |
|---|---|---|---|
| trip | driver | `GET /drivers/{id}/online-eligibility` | đủ điều kiện Online + ảnh chụp tên/SĐT/xe |
| trip | pricing | `POST /quotes/{id}/consume`, `/release` | tiêu báo giá khi tạo chuyến (idempotent theo `tripId`), trả lại nếu tạo chuyến thất bại |
| trip | location | `GET /locations/nearby`, `GET /locations/drivers/{id}`, `POST /locations/drivers/{id}` | tìm tài xế gần, vị trí server để kiểm "đã đến", telemetry qua STOMP |
| pricing | location | `GET /locations/supply` | cung tài xế cho surge |

- Token: service gọi tự ký JWT vai trò `SERVICE` bằng `JWT_SECRET` chung.
- `ErrorDecoder`: lỗi nghiệp vụ của bên nhận (`ApiResponse` lỗi) → dựng lại `BusinessException` cùng mã; 5xx/mạng → `RetryableException`.
- **Retry chỉ cho GET** (`GetOnlyRetryer`: 100 ms → 1 s, 3 lần) — POST không an toàn để gửi lại mù.
- **CircuitBreaker Resilience4j** (cửa sổ 20 lần, ≥ 10 lần, lỗi ≥ 50 % → mở 10 s; time limiter 6 s); **bỏ qua lỗi 4xx** vì đó là câu trả lời đúng của bên nhận. `FallbackFactory` đổi lỗi còn lại thành 503 `DEPENDENCY_UNAVAILABLE`.
- `@FeignClient(primary = false)`: test thay bằng bản giả `@Primary` mà không đụng nhau.

### Giữ tài xế và chấp nhận offer (trong trip-service)

1. Dispatch: khóa Redisson `dispatch:lock:{tripId}` (`tryLock(0, 30 s)`) để một chuyến chỉ có một vòng dispatch chạy; lập kế hoạch dưới `SELECT … FOR UPDATE` trip, gọi location ngoài transaction, rồi khóa lại để `reserve` = `UPDATE driver_states SET availability='OFFERED' WHERE driver_id=? AND availability='AVAILABLE'`, đúng 1 dòng mới ghi offer.
2. Chấp nhận: khóa trip (`MATCHING`) + offer (`PENDING`, chưa hết hạn), `OFFERED → BUSY` có điều kiện, gán tài xế, ghi lịch sử + outbox — **một transaction**.
3. Đóng offer / kết thúc chuyến trả tài xế về `AVAILABLE` (hoặc `OFFLINE` nếu bị đình chỉ trong lúc đó) trong cùng transaction.
4. Partial unique index trên `trips` và `driver_offers` là tầng chặn cuối.

## 3. Dữ liệu (Flyway `db/migration/V1__init.sql` của từng service)

| Service | Bảng nghiệp vụ |
|---|---|
| identity | `users`, `user_roles`, `refresh_tokens`, `password_reset_tokens` |
| driver | `driver_profiles`, `vehicles`, `driver_documents`, `driver_review_decisions` |
| location | (chỉ bảng nền tảng; vị trí ở Redis) |
| pricing | `pricing_rules`, `fare_quotes` |
| trip | `trips`, `trip_status_history`, `driver_offers`, `driver_states` |
| payment | `payments`, `refunds`, `payment_callback_events`, `wallets`, `wallet_entries`, `commission_rules` |
| mọi service | `outbox_events`, `inbox_events`, `processed_events`, `audit_records`, `idempotency_records` |

Bảo đảm trong DB (không chỉ trong code):
- `trips`: partial unique index `driver_id WHERE status IN (ACCEPTED, PICKING_UP, ARRIVED, IN_TRIP)` và `customer_id WHERE status IN (các trạng thái hoạt động)` → **không thể** double assignment (NFR-REL-003).
- `driver_offers`: unique `trip_id WHERE status='ACCEPTED'`, unique `driver_id WHERE status='PENDING'`.
- `payments`: unique `(trip_id, kind)`. `wallet_entries`: unique `(wallet_id, entry_type, reference_id)`.
- Trigger chặn `UPDATE/DELETE` trên `trip_status_history`, `wallet_entries`, `audit_records` (DR-005, FR-ADM-006).
- Cột tiền `NUMERIC(15,2)` + `currency`; thời gian `timestamptz` (UTC).
- `@Version` (optimistic lock) trên `trips`, `driver_states`, `driver_profiles`, `payments`, `wallets` (DR-009).

Redis:

| Khóa | Kiểu | Nội dung |
|---|---|---|
| `loc:{driverId}` | hash | lat, lng, accuracy, heading, speed, deviceTs, serverTs, sequence, vehicleType, suspicious |
| `geo:drivers:{vehicleType}` | GEO | tài xế online theo loại xe |
| `geo:lastseen` | zset | driverId → serverTs, để dọn vị trí hết hạn (DR-GEO-005) |
| `surge:demand:{geohash5}:{window}` | string | bộ đếm cầu, TTL 15 phút |
| `trip:active-by-driver` | hash | driverId → tripId, read model cho fan-out vị trí |
| `dispatch:lock:{tripId}` | Redisson lock | một vòng dispatch / chuyến |
| `rhl:cache:pricing-rules::…`, `rhl:cache:driver-context::…` | string (JSON) | `@Cacheable`, TTL 10 phút / 30 s, evict khi đổi |
| `auth:blacklist:{jti}` | string | access token đã logout, TTL = thời gian sống còn lại |
| `auth:token-version:{userId}` | string | tăng khi khóa tài khoản/đổi vai trò/đổi mật khẩu → mọi token cũ mất hiệu lực |
| `rt:seq:{topic}` | string | sequence của bản tin realtime |
| `rl:{bucket}:{subject}:{window}` | string | rate limit cửa sổ cố định |

Kênh Pub/Sub: `rt:fanout` (bản tin STOMP giữa các instance trip-service), `rt:user-notification` (payment → trip → `/user/queue/notifications`).

`spring.cache.type=redis` bắt buộc: Redisson kéo theo JCache, thiếu dòng này Boot chọn JCache và không thấy cache nào.

## 4. Event (Outbox → Kafka → Inbox) — SRS §5.6

Envelope (COM-009): `eventId, eventType, eventVersion, occurredAt, correlationId, producer, aggregateType, aggregateId, payload`.

| Event | Producer | Consumer | Tác dụng |
|---|---|---|---|
| `UserRegistered` | identity | driver | tạo hồ sơ tài xế nháp cho tài khoản DRIVER |
| `UserProfileUpdated`, `UserStatusChanged` | identity | driver, trip | đồng bộ tên; tài khoản bị khóa → không Online được |
| `DriverApproved` | driver | trip, payment | cho phép Online; mở ví tài xế |
| `DriverSuspended` | driver | trip | đang rảnh → OFFLINE ngay; đang chạy → OFFLINE khi xong (FR-DRV-014) |
| `DriverAvailabilityChanged` | trip | location | Offline/đình chỉ → xóa khỏi GEO, evict cache ngữ cảnh |
| `TripRequested` | trip | pricing | tăng bộ đếm cầu surge |
| `TripAccepted` | trip | (chưa có consumer) | mở rộng; realtime đi after-commit |
| `TripCompleted` | trip | pricing | chốt cước → `FareFinalized` |
| `TripCancelled` | trip | payment | thu phí hủy nếu > 0 |
| `FareFinalized` | pricing | payment, trip | tạo & thu thanh toán; lưu cước cuối vào chuyến |
| `PaymentSucceeded` | payment | payment (wallet) | ghi thu nhập & hoa hồng |
| `PaymentFailed`, `RefundSucceeded` | payment | (chưa có consumer) | khách nhận `PAYMENT_UPDATED` qua Pub/Sub |
| `DriverEarningPosted` | payment | trip | `DRIVER_EARNING_POSTED` tới tài xế |

Topic (namespace `app.kafka.namespace`, mặc định `rhl`; integration test dùng `rhltest` để không lẫn dữ liệu dev):

| Topic | Key | Nội dung |
|---|---|---|
| `rhl.{producer}.events` (`rhl.identity.events`, `rhl.trip.events`…) | `aggregateId` → cùng aggregate cùng partition, giữ thứ tự | envelope JSON, header `eventType`, `eventId`, `correlationId` |
| `rhl.{producer}.events.DLT` | như trên | bản tin đọc lỗi sau khi thử lại |
| `rhl.location.updates` | `driverId` | vị trí đã nhận (không qua outbox: mất một điểm thì điểm sau thay thế) → trip đẩy STOMP |
| `springCloudBus` | — | Spring Cloud Bus (refresh cấu hình) |

Mỗi topic 3 partition. Consumer group = `rhl.{spring.application.name}` → mỗi service nhận đủ mọi event, các instance cùng service chia partition.

Đường đi của một event:
1. **Outbox** — service ghi `outbox_events` trong cùng transaction với thay đổi nghiệp vụ (FR-EVT-002).
2. **Relay** (200 ms, tối đa 50, `FOR UPDATE SKIP LOCKED` + lease) — gửi Kafka (`acks=all`, `enable.idempotence=true`) và **chờ broker xác nhận** rồi mới đánh dấu `PROCESSED`; lỗi → lùi theo cấp số nhân, quá 8 lần → `FAILED`.
3. **Consumer** (`KafkaEventConsumer`, ack thủ công `MANUAL_IMMEDIATE`) — chỉ giữ event có handler, ghi `inbox_events` (bỏ qua trùng `eventId`) rồi mới `acknowledge()`. Lỗi đọc → thử lại có lùi, hết lượt → `.DLT`; lỗi mất kết nối DB thì thử lại mãi (không đẩy event đúng sang DLT chỉ vì DB chết).
4. **InboxProcessor** — chạy handler trong transaction riêng, ghi `processed_events(event_id, handler)` → **idempotent** (FR-EVT-004); Kafka giao lại (at-least-once) cũng không ghi trùng. Lỗi → lùi; quá 8 lần → `FAILED`, admin xử lý lại (FR-EVT-008).

`TripStatusChanged` **không ghi outbox**: Spring event after-commit cho độ trễ thấp; mất bản tin thì client lấy snapshot (FR-RT-007).

## 5. REST API (v1) — xem Swagger để có đủ schema

| Nhóm | Endpoint | Service |
|---|---|---|
| Auth | `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `/password-reset/request`, `/password-reset/confirm` | identity |
| Tài khoản | `GET/PATCH /api/v1/users/me` | identity |
| Hồ sơ tài xế | `GET /api/v1/drivers/me`, `PUT /drivers/me/profile`, `PUT /drivers/me/vehicle`, `POST /drivers/me/documents` (multipart), `POST /drivers/me/submit` | driver |
| Khả dụng & offer | `GET /api/v1/drivers/me/availability`, `POST /drivers/me/online`, `/offline`, `GET /drivers/me/offers/current`, `POST /offers/{id}/accept` *(Idempotency-Key)*, `POST /offers/{id}/decline` | trip |
| Vị trí | `POST /api/v1/drivers/me/location` | location |
| Kiểm duyệt | `GET /api/v1/review/drivers`, `GET /review/drivers/{id}`, `GET /review/documents/{id}/file`, `POST /review/drivers/{id}/decisions`, `POST /admin/drivers/{id}/suspend` | driver |
| Báo giá | `POST /api/v1/quotes`, `GET/POST /admin/pricing-rules`, `POST /admin/pricing-rules/{id}/deactivate` | pricing |
| Chuyến | `POST /api/v1/trips` *(Idempotency-Key)*, `GET /trips/{id}`, `GET /trips/{id}/history`, `GET /trips/mine`, `GET /trips/{id}/cancellation-preview`, `POST /trips/{id}/cancel` *(Idempotency-Key)*, `POST /trips/{id}/pickup-started`, `/arrived`, `/started`, `/completed`; `GET /support/trips`, `POST /support/trips/{id}/cancel` | trip |
| Thanh toán & ví | `GET /api/v1/trips/{id}/payments`, `POST /payments/{id}/retry` *(Idempotency-Key)*, `POST /payments/sandbox/callback` (header `X-Sandbox-Timestamp`, `X-Sandbox-Signature` = HMAC-SHA256(secret, timestamp + "." + body)), `GET /drivers/me/wallet`, `/wallet/entries`, `GET /finance/payments`, `POST /finance/payments/{id}/refunds` *(Idempotency-Key)*, `GET /finance/wallets/{driverId}`, `/entries`, `POST /finance/wallets/{driverId}/adjustments` *(Idempotency-Key)*, `GET/POST /admin/commission-rules` | payment |
| Quản trị | `GET /api/v1/admin/users`, `PUT /admin/users/{id}/roles`, `PUT /admin/users/{id}/status` | identity |
| Nền tảng | `GET /api/v1/admin/platform/{service}/audit`, `/events/failed-outgoing`, `/events/failed-incoming`, `POST /admin/platform/{service}/events/{id}/retry` | mỗi service |

### Gateway

- Route trong `config-repo/api-gateway.properties`. Các đường `/api/v1/drivers/me/*` thuộc 4 service khác nhau → route cụ thể (order 0: location, trip, payment) đứng trước route theo nhóm (order 10).
- `CorrelationIdFilter` (đầu tiên): gắn/chuyển tiếp `X-Correlation-Id`.
- `AuthenticationFilter` (GlobalFilter): **xóa mọi header `X-User-*` client tự gửi**, cho qua đường công khai (`app.gateway.public-paths`), còn lại kiểm JWT (HS256, hạn, issuer), từ chối token vai trò `SERVICE`, kiểm `auth:blacklist:{jti}` và `auth:token-version:{sub}` trên Redis, rồi gắn `X-User-Id`, `X-User-Roles`. Lỗi → 401 dạng `ApiResponse`.
- Service phía sau **vẫn tự kiểm JWT** (defense in depth, NFR-SEC-004) — header `X-User-*` chỉ dùng cho log/truy vết.

Phản hồi thành công: `{code:"SUCCESS", message, data, correlationId}`. Danh sách luôn phân trang (`page`, `size` ≤ 100) — NFR-PERF-008.

## 6. WebSocket (STOMP) — COM-005…007

- Kết nối: `ws://localhost:8080/ws` (gateway → trip-service), frame `CONNECT` có header `Authorization: Bearer <access token>`. Token sai/hết hạn → từ chối.
- Heartbeat 10 s/10 s. Client reconnect thì **gọi `GET /api/v1/trips/{id}` lấy snapshot** rồi mới áp dụng bản tin mới (FR-RT-005).

| Hướng | Destination | Ai | Nội dung |
|---|---|---|---|
| Client → server | `/app/driver/location` | Tài xế | telemetry (Phụ lục A.1) → trip chuyển sang location qua Feign |
| Server → client | `/user/queue/offers` | Tài xế được offer | `DRIVER_OFFER_CREATED`, `DRIVER_OFFER_CLOSED` |
| Server → client | `/topic/trips/{tripId}` | Khách & tài xế của chuyến, nhân viên hỗ trợ | `TRIP_STATUS_CHANGED`, `DRIVER_LOCATION_UPDATED` |
| Server → client | `/user/queue/notifications` | Chủ sở hữu | `PAYMENT_UPDATED`, `DRIVER_EARNING_POSTED`, `TELEMETRY_REJECTED` |

Mọi bản tin: `{messageId, type, version, sentAt, sequence, data}`; `sequence` tăng dần theo từng destination (Redis INCR) để client bỏ bản tin cũ/trùng (FR-RT-006). `SUBSCRIBE /topic/trips/{id}` bị chặn nếu người dùng không phải người tham gia chuyến hoặc chuyến đã kết thúc quá 2 phút (FR-RT-008, NFR-PRV-002). Nhiều instance trip-service: bản tin đi Redis Pub/Sub `rt:fanout` để instance đang giữ kết nối của người nhận gửi đi.

## 7. Cấu hình & bí mật

- Mỗi service chỉ có `src/main/resources/application.properties` gồm tên và `spring.config.import=configserver:...`.
- config-server dùng backend **composite**:
  1. **native** `config/application.properties` (thư mục gốc, **gitignored**): `DB_PASSWORD`, `JWT_SECRET`, `PAYMENT_SANDBOX_SECRET`. Mẫu ở `config/application.properties.example`.
  2. **git** `config-repo/` — **repo git riêng** (bị `.gitignore` của repo chính), nhánh `main`: `application.properties` (chung: DB, Redis, Kafka, Feign, outbox…), `<service>.properties` (cổng, URL DB, pool), `application-dev.properties`, `identity-service-dev.properties` (4 tài khoản nhân viên + mật khẩu mẫu), `api-gateway.properties` (route). Mọi bí mật dạng `${ENV_VAR}`. `start-all.ps1` truyền `CONFIG_GIT_URI=file:///…/config-repo`; có thể trỏ sang repo GitHub.
- **Đổi cấu hình khi đang chạy**: sửa file trong `config-repo/` → `git commit` → `POST http://localhost:8888/actuator/busrefresh` → Bus (Kafka) báo mọi service tải lại; bean `@RefreshScope` (vd `SandboxSettings` của payment, `payment.sandbox.force-failure`) nhận giá trị mới không cần khởi động lại. Config-server chỉ đọc nội dung **đã commit**.
- Test không cần config-server: `src/test/resources/application.properties` import thẳng `config/` và `config-repo/`; `application-test.properties` đổi DB sang `_test`, Redis DB 1, namespace Kafka `rhltest`, tắt Bus và Eureka.
- Pool kết nối: trip 20, các service khác 5 (= 45 < `max_connections` 100 của PostgreSQL).

## 8. Chạy

```
docker start rhl-redis rhl-kafka            # start-all.ps1 cũng tự chạy lệnh này
mvnw.cmd package -DskipTests
powershell -File tools/start-all.ps1        # eureka -> config -> 6 service -> gateway, log ở logs/
node tools/seed-dev.mjs                     # bảng giá, hoa hồng, 2 khách, 101 tài xế đã duyệt (qua API)
node tools/simulate-drivers.mjs --drivers 100 --interval 3000 --rides
powershell -File tools/start-all.ps1 -Stop
```

Lần đầu: tạo container Kafka `docker run -d --name rhl-kafka -p 9092:9092 apache/kafka:3.9.1`, tạo 12 database (`rhl_<service>` và `rhl_<service>_test`).
