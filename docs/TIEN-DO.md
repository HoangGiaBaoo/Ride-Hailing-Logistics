# Tiến độ

Cập nhật: 2026-10-27 — **microservices** 10 module Maven: 6 service nghiệp vụ theo SRS (identity, driver, location, pricing, trip, payment) + eureka, config (git `config-repo/` + Bus), gateway (GlobalFilter JWT), common-lib; event qua **Kafka**; mỗi service chia package controller/dto/entity/repository/service/mapper/exception… — xem `docs/02-kien-truc.md` §2. Chi tiết đã làm gì và vì sao: `docs/NHAT-KY.md`.

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
| 8 Demo & nghiệm thu | 🟡 Gần xong | `tools/start-all.ps1`, `tools/seed-dev.mjs` (qua API), 6 trang demo trên gateway (đủ màn UI-CUS/DRV/ADM — xem dưới), script mô phỏng, 90 unit + 21 integration test xanh, run configuration IntelliJ. **Các trang mới chưa được bấm thử trên trình duyệt thật** (đã kiểm từng lời gọi API mà trang dùng) |

## Đã kiểm chứng thật (không chỉ biên dịch) — bản 6 service + Kafka, 2026-10-27

- `./mvnw test`: **90/90** unit test xanh.
- `./mvnw verify -Pintegration`: **21/21** xanh trên PostgreSQL + Redis + Kafka local (namespace topic `rhltest`) — trip 12: 100 luồng cùng chấp nhận một offer, 50 luồng giữ cùng tài xế, 8 chuyến tranh 1 tài xế, Idempotency-Key (kể cả mất bản ghi idempotency) + quote dùng một lần, vòng đời chuyến, `FareFinalized`/`DriverSuspended` đi qua Kafka thật; payment 3: tiền mặt 87.000 → ví −17.400 đúng một lần, Kafka giao lặp không ghi trùng; 6 test khởi động service (pricing/location kiểm thêm cache là Redis).
- Chạy đủ 9 tiến trình bằng `start-all.ps1`; `seed-dev.mjs` tạo 101 tài xế qua đăng ký → `UserRegistered` (Kafka) → hồ sơ → 3 giấy tờ → duyệt, 0 lỗi. Mọi `outbox_events` PROCESSED, 0 inbox lỗi.
- Kịch bản API của các trang demo qua gateway: **47/47** bước đạt.
- Mô phỏng 30 tài xế / 2 s, 2 phút, có đặt chuyến: **1.830 bản tin, 0 lỗi, p95 35–46 ms**; chuyến tiền mặt 22.000 hoàn tất → payment SUCCEEDED 22.000 → ví hoa hồng −4.400 (20%).
- `POST :8888/actuator/busrefresh` → 204, mọi service tải lại cấu hình qua Bus (Kafka), không lỗi.
- `/internal/**` không có route ở gateway.

## Giao diện demo — đối chiếu SRS §3.1 (2026-10-25)

Trước đó trang demo chỉ phục vụ luồng đặt xe; thiếu các màn SRS ghi PHẢI dù API đã có. Đã bổ sung:

| Mã SRS | Màn | Trang |
|---|---|---|
| UI-CUS-001 | Đăng ký (khách/tài xế), quên mật khẩu (mã ở log identity-service khi chạy dev), hồ sơ của tôi | `index.html`, `account.html` |
| UI-DRV-001, UI-DRV-002 | Khai thông tin + GPLX, phương tiện, tải 3 giấy tờ, xem phần còn thiếu, gửi duyệt, lịch sử quyết định; màn nhận chuyến chỉ chỗ hoàn thiện khi chưa Online được | `driver-profile.html`, `driver.html` |
| UI-ADM-002 | Lọc hồ sơ theo trạng thái, xem chi tiết + tài liệu + lịch sử; quản trị đình chỉ tài xế | `ops.html` › Duyệt tài xế |
| UI-ADM-003 | Tìm chuyến theo mã, khách, tài xế, dịch vụ, trạng thái, khoảng thời gian, phân trang | `ops.html` › Chuyến |
| UI-ADM-004 | Bảng giá (xem, tạo, tắt; vùng chọn từ danh sách; server chặn chồng lấn/hồi tố/xe không hợp dịch vụ) và hoa hồng | `ops.html` › Bảng giá & hoa hồng |
| UI-ADM-005, FR-ADM-003 | Người dùng: tìm, lọc vai trò, đổi vai trò, khóa/mở khóa — mọi thao tác nhạy cảm hỏi xác nhận + lý do, server ghi audit | `ops.html` › Người dùng |

Kiểm chứng: kịch bản gọi đúng các API mà trang dùng, qua gateway, **47/47 bước đạt** — đăng ký tài xế → hồ sơ thiếu bị chặn gửi → khai đủ + 3 giấy tờ (tệp giả PNG bị từ chối) → gửi duyệt → sửa khi chờ duyệt bị chặn → kiểm duyệt viên duyệt → Online được; `PATCH /users/me` kèm `roles` không có tác dụng; khóa tài khoản → không đăng nhập được → mở lại; quản trị không tự khóa mình; hỗ trợ không xem được danh sách người dùng; quy tắc giá chồng lấn → 409, tài chính tạo → 403, tắt + tạo bản mới → báo giá chạy tiếp; tìm chuyến đủ bộ lọc; đình chỉ → không Online được.

## Việc tiếp theo

1. Bấm thử 6 trang demo trên trình duyệt (`http://localhost:8080/index.html`); đo độ trễ vị trí tài xế → khách qua WebSocket (NFR-PERF-001).
2. Commit bản 6 service + Kafka (repo chính chưa commit; `config-repo/` đã có commit riêng).
3. Đo NFR-PERF-003 (truy vấn ứng viên ≤ 200 ms).

## Nợ kỹ thuật / giới hạn đã biết

| # | Vấn đề | Ảnh hưởng | Hướng xử lý |
|---|---|---|---|
| 1 | Telemetry qua **WebSocket chưa rate limit** (REST đã có) | Một client có thể spam `/app/driver/location` | Đếm theo session trong `StompAuthInterceptor` |
| 2 | Thu hồi token (`auth:blacklist`, `auth:token-version`) kiểm ở **gateway**; gọi thẳng cổng service (bỏ qua gateway) thì token đã thu hồi còn dùng được tới hết hạn (15 phút) | Chỉ khi lộ cổng nội bộ | Mạng nội bộ chỉ mở cổng 8080 |
| 3 | Cổng sandbox lưu giao dịch **trong bộ nhớ**: khởi động lại giữa lúc charge và callback → payment PENDING có `providerRef` không xác minh được | Phải xử lý tay | Chấp nhận cho sandbox; cổng thật có API tra cứu |
| 4 | Simple broker STOMP trong tiến trình trip-service (đã fan-out qua Redis Pub/Sub `rt:fanout`) | Chạy nhiều instance được nhưng chưa thử | Thử 2 instance trip-service sau gateway |
| 9 | location-service chết → không tìm được tài xế gần (dispatch thử lại sau 3 s); pricing chết → không tạo được chuyến | CircuitBreaker mở, trả 503 `DEPENDENCY_UNAVAILABLE` nhanh | Chấp nhận — không có dữ liệu thay thế đúng |
| 5 | OSRM chưa chạy thử với máy chủ thật (mặc định `haversine`) | Quãng đường là ước tính, `approximate=true` | Bật `app.map.provider=osrm` khi có OSRM |
| 6 | Phí hủy của chuyến **tiền mặt** chỉ ghi khoản phải thu (PENDING), không tự thu | Theo chốt TBD-10 | Thu ở chuyến kế tiếp — ngoài phạm vi |
| 7 | Quên mật khẩu chỉ "gửi" qua log ở profile dev | Không có email/SMS thật | Cắm `NotificationSender` thật |

## Chạy lại từ đầu (máy local)

```
docker start rhl-redis rhl-kafka                         # Redis + Kafka (start-all cũng tự chạy)
mvnw.cmd package -DskipTests
powershell -File tools/start-all.ps1                     # 9 tiến trình, log ở logs/
node tools/seed-dev.mjs                                  # chạy lại an toàn
node tools/simulate-drivers.mjs --drivers 100 --rides    # tùy chọn
powershell -File tools/start-all.ps1 -Stop
```
Tài khoản mẫu: `customer@rhl.dev`, `customer2@rhl.dev`, `driver.demo@rhl.dev`, `driver001…driver100@rhl.dev` (do `seed-dev.mjs` tạo), `support@ / finance@ / reviewer@ / admin@rhl.dev` (identity-service tự tạo ở profile dev); mật khẩu = `app.dev.seed-password` trong `config-repo/identity-service-dev.properties`.
