# Tiến độ

Cập nhật: 2026-10-21 — đã chuyển sang **microservices** (9 module Maven, xem `docs/02-kien-truc.md` §2). Chi tiết đã làm gì và vì sao: `docs/NHAT-KY.md`.

## Trạng thái các đợt (`docs/04-chuc-nang.md`)

| Đợt | Trạng thái | Ghi chú |
|---|---|---|
| 1 Nền tảng | ✅ Xong | Outbox + relay + DLQ, audit bất biến (trigger), Idempotency-Key, rate limit Redis, correlation ID, lỗi thống nhất |
| 2 Tài khoản & phân quyền | ✅ Xong | JWT 15 phút + refresh xoay vòng (phát hiện tái sử dụng), khóa 15 phút sau 5 lần sai, đặt lại mật khẩu, RBAC 6 vai trò, audit đổi vai trò/khóa tài khoản |
| 3 Tài xế | ✅ Xong | Hồ sơ, xe, upload kiểm magic bytes, gửi duyệt, duyệt/từ chối/yêu cầu bổ sung có lịch sử, Online/Offline, đình chỉ |
| 4 Vị trí & báo giá | ✅ Xong | Telemetry REST + STOMP, Lua chống ghi đè vị trí cũ, Redis GEO, bảng giá có phiên bản + chống chồng lấn, surge geohash, quote có hạn dùng một lần |
| 5 Chuyến & matching | ✅ Xong | State machine, offer tuần tự 15 s, mở rộng bán kính 2→4→6 km, NO_DRIVER 60 s, chấp nhận nguyên tử, PIN/OTP, hủy có phí, tra cứu hỗ trợ |
| 6 Realtime | ✅ Xong | STOMP `/ws`, JWT ở CONNECT, chặn SUBSCRIBE trái phép, envelope + sequence, fan-out vị trí |
| 7 Thanh toán & ví | ✅ Xong | FareFinalized, CASH/SANDBOX_CARD, callback HMAC + chống replay + đối chiếu, thử lại, đối soát định kỳ, ledger + hoa hồng có phiên bản, hoàn tiền, điều chỉnh ví |
| 8 Demo & nghiệm thu | 🟡 Gần xong | `tools/start-all.ps1`, `tools/seed-dev.mjs` (qua API), 4 trang demo trên gateway, script mô phỏng, 90 unit + 21 integration test xanh. **Trang demo chưa được bấm thử trên trình duyệt thật** |

## Đã kiểm chứng thật (không chỉ biên dịch) — bản microservices, 2026-10-21

- `./mvnw test`: **90/90** unit test xanh.
- `./mvnw verify -Pintegration`: **21/21** xanh trên PostgreSQL + Redis local — trip: 100 luồng cùng chấp nhận một offer, 8 chuyến tranh 1 tài xế, Idempotency-Key, vòng đời chuyến; driver: 50 luồng giữ cùng tài xế, lệnh lặp/trễ/bù trừ; pricing: quote dùng một lần qua API nội bộ; payment: tiền mặt 87.000 → ví −17.400 đúng một lần qua Redis Stream thật, consumer khởi động lại khi group đã có; 5 test khởi động service.
- Chạy đủ 8 tiến trình, `seed-dev.mjs` tạo 101 tài xế qua đủ luồng đăng ký → hồ sơ → 3 giấy tờ → duyệt trong **13 s**.
- Mô phỏng 100 tài xế / 3 s qua gateway, 7 phút: **14.450 bản tin, 0 lỗi, p95 REST 24–43 ms**; 12 chuyến hoàn tất qua trip → pricing → payment → ví. Hoa hồng 20% × 368.000 = **73.600** khớp từng đồng; chuyến thẻ ghi thu nhập 205.000, chuyến tiền mặt chỉ ghi nợ hoa hồng. Lag Redis Stream của cả 4 consumer group = 0.
- `/internal/**` gọi từ ngoài qua gateway → 404.
- E2E WebSocket (STOMP qua gateway) **chưa đo lại** sau khi tách service; số đo cũ (p95 ≈ 18 ms) là của bản monolith.

## Việc tiếp theo

1. Bấm thử 4 trang demo trên trình duyệt (`http://localhost:8080/index.html`), kiểm WebSocket qua gateway.
2. Commit lần đầu (chưa commit bản microservices).
3. Đo NFR-PERF-003 (truy vấn ứng viên ≤ 200 ms) — giờ đi qua Feign trip → driver, cần đo lại.

## Nợ kỹ thuật / giới hạn đã biết

| # | Vấn đề | Ảnh hưởng | Hướng xử lý |
|---|---|---|---|
| 1 | Telemetry qua **WebSocket chưa rate limit** (REST đã có) | Một client có thể spam `/app/driver/location` | Đếm theo session trong `StompAuthInterceptor` |
| 2 | Access token còn hiệu lực tối đa 15 phút sau khi khóa tài khoản/đổi vai trò (refresh đã bị thu hồi) | Cửa sổ 15 phút | Danh sách thu hồi `jti` trên Redis nếu cần |
| 3 | Cổng sandbox lưu giao dịch **trong bộ nhớ**: khởi động lại giữa lúc charge và callback → payment PENDING có `providerRef` không xác minh được | Phải xử lý tay | Chấp nhận cho sandbox; cổng thật có API tra cứu |
| 4 | Simple broker STOMP + cache ngữ cảnh telemetry nằm trong tiến trình trip-service; tên consumer Redis Stream = tên-cổng | trip-service chỉ chạy **một instance**; mỗi service một instance mỗi cổng | Chuyển STOMP broker relay (RabbitMQ) khi scale ngang |
| 8 | Thả tài xế (`release-offer`, `finish-trip`) gọi sau commit, best-effort | Mất lệnh → tài xế kẹt OFFERED/BUSY tới lần đối soát kế tiếp (≤ 30 s + hạn offer) | `DriverHoldReconciler`; chấp nhận được cho demo |
| 9 | Một service chết thì chức năng phụ thuộc trả 503 `DEPENDENCY_UNAVAILABLE` (ví dụ driver-service chết → không ghép được chuyến) | Không có circuit breaker | Thêm Resilience4j nếu cần |
| 5 | OSRM chưa chạy thử với máy chủ thật (mặc định `haversine`) | Quãng đường là ước tính, `approximate=true` | Bật `app.map.provider=osrm` khi có OSRM |
| 6 | Phí hủy của chuyến **tiền mặt** chỉ ghi khoản phải thu (PENDING), không tự thu | Theo chốt TBD-10 | Thu ở chuyến kế tiếp — ngoài phạm vi |
| 7 | Quên mật khẩu chỉ "gửi" qua log ở profile dev | Không có email/SMS thật | Cắm `NotificationSender` thật |

## Chạy lại từ đầu (máy local)

```
docker start rhl-redis                                   # Redis
mvnw.cmd package -DskipTests
powershell -File tools/start-all.ps1                     # 8 tiến trình, log ở logs/
node tools/seed-dev.mjs                                  # chạy lại an toàn
node tools/simulate-drivers.mjs --drivers 100 --rides    # tùy chọn
powershell -File tools/start-all.ps1 -Stop
```
Tài khoản mẫu: `customer@rhl.dev`, `customer2@rhl.dev`, `driver.demo@rhl.dev`, `driver001…driver100@rhl.dev` (do `seed-dev.mjs` tạo), `support@ / finance@ / reviewer@ / admin@rhl.dev` (identity-service tự tạo ở profile dev); mật khẩu = `app.dev.seed-password` trong `config-server/src/main/resources/configs/identity-service-dev.properties`.
