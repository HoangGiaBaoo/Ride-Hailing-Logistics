# Nhật ký

Không xóa dòng nào ở đây. Mỗi mục: đã làm gì, vì sao, và số đo thật nếu có.

## 2026-10-17 — Dựng toàn bộ hệ thống từ SRS

**Làm rõ yêu cầu.** Đọc SRS-RHL-002 (IEEE 830), chốt 15 mục TBD thành giá trị cấu hình cụ thể (`docs/00-yeu-cau.md` §3). Chốt với người dùng: modular monolith, PostgreSQL + Redis GEO, REST + WebSocket kèm trang demo.

**Chuyển thể `.claude/`.** Bộ cấu hình gốc thuộc đồ án MPRoute (CVRP). Giữ cấu trúc và cách làm (luật cứng, hook, agent soát, lệnh tiếng Việt), viết lại nội dung cho bài toán dispatch: skill `dispatch-matching` thay `thuat-toan-cvrp`, agent `chuyen-gia-dispatch` thay `chuyen-gia-thuat-toan`, hook bắt tiền `double`, `setStatus(TripStatus…)`, import repository chéo miền, bí mật ghi cứng.

**Hạ tầng.** Redis 7.4 chạy trong container `rhl-redis` (volume `rhl-redis-data`, `--appendonly yes`, restart unless-stopped). PostgreSQL 18 sẵn có; tạo DB `ride_hailing` và `ride_hailing_test`.

### Bốn lỗi chỉ lộ ra khi chạy thật — và cách sửa

1. **`(:param is null or …)` trong JPQL** → PostgreSQL báo `could not determine data type of parameter`. Sửa: tách truy vấn (bảng giá) hoặc dùng Specification (audit, user, tra cứu chuyến).
2. **`@Version long` + UUID tự gán** → Spring Data không nhận ra entity mới, gọi `merge()` thay vì `persist()`, cascade lịch sử trạng thái không chạy → `trip_status_history.trip_id` null. Sửa: `@Version Long`.
3. **`saveAndFlush()` trên entity đang được quản lý** → lại đi `merge()`, làm mất bản ghi lịch sử mới thêm. Sửa: chỉ `flush()`.
4. **Gọi `@Transactional` trong `afterCommit`** → transaction cũ còn gắn thread, REQUIRED "tham gia" vào transaction đã xong → `No active transaction`, thanh toán thẻ kẹt PENDING. Sửa: `REQUIRES_NEW`. Job đối soát đã tự gửi lại khoản bị kẹt sau khi khởi động lại — chứng minh NFR-REL-007 trên dữ liệu thật.

Một lỗi bắt được trước khi chạy: `@Modifying(clearAutomatically = true)` trên cập nhật trạng thái tài xế sẽ detach `Trip`/`DriverOffer` đang sửa trong cùng transaction chấp nhận offer → thay đổi im lặng không được lưu. Bỏ `clearAutomatically`, đọc trạng thái bằng truy vấn scalar.

### Quyết định thiết kế đáng nhớ

- **Ba lớp chống double assignment**: khóa hàng trip (`FOR UPDATE`), cập nhật có điều kiện cho tài xế (`WHERE availability = 'OFFERED'`), partial unique index trong DB. Thứ tự khóa luôn trip → offer → driver ở mọi luồng → không deadlock.
- **Lấy `tripId` bằng truy vấn projection trước, rồi mới khóa**: nạp entity offer trước khi khóa thì Hibernate giữ bản cũ trong persistence context, bỏ qua dữ liệu mới của `SELECT … FOR UPDATE`.
- **Realtime đi Spring event after-commit, tài chính đi outbox**: vị trí cần ≤ 500 ms và mất một bản tin không sao; tiền thì không được mất.
- **Giá trọn gói (upfront)**: cước cuối = quote, đúng surge khách đã xác nhận (FR-PRI-018).

### Số đo (máy dev, localhost)

| Đo | Kết quả |
|---|---|
| Seed 4 nhân viên + 2 khách + 101 tài xế qua đủ luồng duyệt | 23,6 s |
| Mô phỏng 100 tài xế / 3 s, 90 s | 3.490 bản tin, 0 lỗi, p95 REST 30–37 ms |
| Vị trí server → khách qua STOMP (20 mẫu) | p95 ≈ 18 ms, max 24 ms |
| 100 luồng chấp nhận cùng offer | 1 gán, 1 `TripAccepted`, 1 dòng lịch sử ACCEPTED |

## 2026-10-21 — Chuyển sang microservices

**Vì sao.** Người dùng muốn cấu trúc thư mục kiểu microservices (eureka, config-server, gateway, mỗi service một module) như SRS CON-01/CON-02, không phải modular monolith. Không dùng docker-compose: giữ PostgreSQL Windows và container `rhl-redis`.

**Làm gì.** Tách thành 9 module Maven, mỗi service một database `rhl_*`. Code miền giữ nguyên package, chỉ thay chỗ gọi xuyên miền:
- Gọi đồng bộ → OpenFeign vào `/internal/**` với JWT vai trò `SERVICE`; gateway không có route `/internal`.
- Event → outbox → Redis Stream `rhl:events` → inbox → handler idempotent (Redis đã có, không thêm broker).
- Đăng ký tài xế → event `UserRegistered` thay cho gọi thẳng driver; duyệt tài xế → `DriverApproved` mở ví.
- Giữ tài xế không còn chung transaction với chuyến → `availability_ref` + lệnh có điều kiện theo offer/chuyến, bù trừ khi rollback, `DriverHoldReconciler` đối soát.
- Quote dùng một lần theo `used_by_trip` để trip-service gọi lại sau timeout không mất quote.
- `DataSeeder` → `StaffSeeder` (chỉ nhân viên) + `tools/seed-dev.mjs` (mọi thứ còn lại qua API công khai).

**Vấp và sửa.**
- Spring Cloud cho Boot 4.1 mới có milestone → hạ Boot xuống 4.0.8.
- `@FeignClient` mặc định là bean primary → đè bản giả trong test; đặt `primary = false`.
- **Consumer Redis Stream kẹt vĩnh viễn sau khi khởi động lại**: group đã có → `XGROUP CREATE` trả `BUSYGROUP`, nhưng Spring bọc lỗi nên `getMessage()` chỉ là "Error in execution", không thấy chữ `BUSYGROUP` → coi là lỗi thật, lặp mãi. Test không bắt được vì IT luôn `flushDb`. Sửa: xét `getMostSpecificCause()`; thêm `EventStreamRestartIT` (đỏ trước khi sửa, xanh sau). Sau khi sửa, ~400 event tồn đọng được xử lý hết, 6 chuyến nhận thanh toán đúng.
- 5 service × pool 20 = 100 kết nối = `max_connections` mặc định → psql "too many clients"; hạ pool về 10.
- Gateway trả 503 khoảng 30 s đầu (chưa tải danh bạ Eureka) → `start-all.ps1` chờ tới khi route được mọi service.
- `TRUNCATE` dọn dữ liệu trong IT của trip-service đôi khi deadlock với scheduler dispatch → thử lại tối đa 5 lần.
- Script mô phỏng dừng giữa chuyến làm tài xế kẹt BUSY ở lần chạy sau → tài xế lấy chuyến dở qua `/trips/mine` rồi chạy tiếp.

## 2026-10-21 (chiều) — Gộp còn 3 service nghiệp vụ

**Vì sao.** Bản 5 service tách driver và trip ra hai database, nên việc quan trọng nhất của đề tài — gán một tài xế cho một chuyến, nguyên tử — thành giao dịch phân tán: `availability_ref`, lệnh `reserve/busy/revert-busy/release-offer/finish-trip` qua Feign, bù trừ `AfterCommit.onRollback`, `DriverHoldReconciler` 30 s, quote có `used_by_trip` + `release`. Nhiều code, nhiều chỗ kẹt tài xế, khó bảo vệ. Ranh giới service nên theo ranh giới nhất quán chứ không theo từng miền.

**Làm gì.**
- `driver-service` + `pricing-service` + `trip-service` → `ride-service` (:8082, DB `rhl_ride`). Payment chuyển sang :8083.
- Chấp nhận offer, giữ/thả tài xế, dùng quote quay lại **một transaction** như bản đầu (lấy lại code từ commit `47297ae`).
- Bỏ: `availability_ref`, `used_by_trip`, `DriverHoldReconciler`, `HoldState`, mọi `/internal` giữa driver/pricing/trip, `AfterCommit.onRollback`, kênh Redis `rt:driver-location` (vị trí giờ là Spring event trong tiến trình).
- Còn đúng một lời gọi Feign: ride → identity (tên, SĐT, trạng thái tài khoản), chỉ đọc.
- Event giữa các miền trong ride-service vẫn đi outbox → stream → inbox: giữ idempotent, tách lại sau không phải sửa handler.
- Test: bỏ `DriverHoldIT`, `QuoteConsumptionIT`, `InMemoryDriverPool`, `InMemoryPricing` (kiểm cơ chế phân tán không còn tồn tại); IT của ride chạy tài xế/giá/chuyến thật, chỉ giả identity. `TripLifecycleIT` thêm kiểm cước cuối về tới chuyến qua Redis Stream.
- Database dev dựng lại từ đầu (`rhl_driver/pricing/trip` bỏ, `rhl_identity/payment` làm mới), dữ liệu mẫu tạo lại bằng `seed-dev.mjs`.

## 2026-10-25 — Chạy bằng IntelliJ, bổ sung giao diện theo SRS §3.1

**Chạy trong IntelliJ.** Nút Run xám vì chưa có run configuration và IntelliJ còn nhớ module cũ. Thêm `.idea/runConfigurations/` (gitignored): 6 ứng dụng + 2 nhóm `1. Ha tang (eureka + config)` rồi `2. Cac service + gateway` — phải chạy đúng thứ tự vì service `fail-fast` khi chưa có config-server. Reload Maven trong IntelliJ 2025.3 lỗi `No implementation for ArtifactHandlerManager was bound` do wrapper dùng Maven 3.10.0 → hạ `.mvn/wrapper/maven-wrapper.properties` về 3.9.16.

**Giao diện.** Người dùng hỏi phần quản lý hồ sơ/tài khoản ở đâu: API đã có từ đợt 2–3 nhưng trang demo chỉ làm luồng đặt xe, vì đã hiểu "rút gọn" thành "chỉ UC-03/UC-04" và không liệt kê phần cắt. SRS §3.1 ghi PHẢI có các màn đó → bổ sung đủ (bảng ở `TIEN-DO.md`), sửa CN-08-02 trong `04-chuc-nang.md` cho rõ. Bài học: cắt phạm vi phải ghi rõ cắt gì vào `TIEN-DO.md` để người dùng quyết định.

**Vấp.** Form bảng giá ban đầu cho nhập vùng tự do, nhưng server chỉ nhận vùng trong `app.regions` → đổi thành ô chọn. Thiếu loại xe `VAN` trong ô chọn ở trang hồ sơ tài xế và bảng giá. Mọi tổ hợp dịch vụ/xe/vùng đã có quy tắc không hạn → muốn đổi giá phải tắt quy tắc cũ rồi tạo mới; ghi hướng dẫn ngay trên form.

## 2026-10-27 — 6 service theo SRS, Kafka, chia package theo lớp, bám chương trình học

**Yêu cầu.** Người dùng muốn backend "đầy đủ và chặt chẽ nhất", mỗi service có đủ thư mục controller/dto/exception…, dùng Kafka, và bám tài liệu học trên lớp (DTO + MapStruct, GlobalExceptionHandler, Eureka, OpenFeign + Resilience4j, Gateway GlobalFilter JWT, Config Server Git + Bus, Kafka outbox/DLT, Redis cache/PubSub/Redisson, refresh token + blacklist). Chốt: 6 service theo SRS, giữ Maven, config ở repo git riêng, không commit mốc trước khi làm.

**Tách service.**
- `ride-service` tách thành driver (:8082), location (:8083), pricing (:8084), trip (:8085); payment chuyển :8086. Mỗi service một DB.
- Giữ chấp nhận offer nguyên tử: **trạng thái khả dụng tài xế** (`driver_states`) chuyển sang trip-service. driver-service chỉ giữ hồ sơ/kiểm duyệt; Online thì trip hỏi `online-eligibility` qua Feign ngoài transaction.
- Tạo chuyến: `tripId = UUID(customerId + Idempotency-Key)`; tiêu quote qua Feign idempotent theo `tripId`, lỗi thì `release`.
- Dispatch dùng khóa Redisson `dispatch:lock:{tripId}`.

**Kafka thay Redis Stream.**
- Outbox → topic `rhl.{producer}.events` (`acks=all`, producer idempotent, key = aggregateId) → consumer group `rhl.{service}`, ack thủ công sau khi ghi inbox, lỗi đọc → `.DLT`.
- Vị trí đi topic riêng `rhl.location.updates` (không outbox).
- Namespace topic/group (`app.kafka.namespace`) để integration test (`rhltest`) không đọc lẫn event dev trên cùng broker.

**Cấu hình.**
- config-server composite: native `config/` (bí mật, gitignored) + git `config-repo/` (repo riêng, có commit).
- Spring Cloud Bus qua Kafka; `SandboxSettings` `@RefreshScope`.
- Gateway: `AuthenticationFilter` kiểm JWT, blacklist `jti`, `token-version`, xóa `X-User-*` giả mạo.

**Vấp khi chạy thật (test đều xanh nhưng vẫn lỗi):**
- Tạo quy tắc giá 500 "Cannot find cache named 'pricing-rules'": Redisson kéo `cache-api` (JCache) vào classpath, Boot chọn JCache trước Redis. Sửa `spring.cache.type=redis`; thêm test khẳng định CacheManager là Redis ở pricing/location.
- `busrefresh` 500: refresh dựng lại EurekaClient với hostname rỗng → NPE trong eureka-client 2.0.6. Sửa `eureka.client.refresh.enable=false`.
- Bus ack lỗi `Can't convert value of class [B … StringSerializer`: binder của Bus kế thừa serializer chuỗi của `spring.kafka.producer.*`. Sửa: serializer `ByteArray` riêng cho binder.
- Dữ liệu dev cũ: event `UserRegistered` bản cũ không có `fullName`/`phone` → hồ sơ tài xế không tên → chấp nhận offer 404. Sao lưu `rhl_identity`, `rhl_payment` bằng `pg_dump` rồi dựng lại 6 DB dev, seed lại.
- `start-all.ps1` truyền `--spring.profiles.active=native` cho config-server (đè `composite`) → sửa sang `composite` + `CONFIG_GIT_URI`. `seed-dev.mjs`/`simulate-drivers.mjs` đọc mật khẩu mẫu ở đường cũ.

**Số đo.** 90 unit + 21 integration xanh; 47/47 bước kịch bản trang demo; mô phỏng 30 tài xế 2 phút 1.830 bản tin 0 lỗi p95 35–46 ms; chuyến tiền mặt 22.000 → hoa hồng 4.400 đúng một lần.
