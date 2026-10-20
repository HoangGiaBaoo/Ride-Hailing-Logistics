# Tiến độ

Cập nhật: 2026-10-17. Chi tiết đã làm gì và vì sao: `docs/NHAT-KY.md`.

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
| 8 Demo & nghiệm thu | 🟡 Gần xong | Seeder, 4 trang demo, script mô phỏng, 90 unit + 10 integration test xanh. **Trang demo chưa được bấm thử trên trình duyệt thật** |

## Đã kiểm chứng thật (không chỉ biên dịch)

- `./mvnw test`: **90/90** unit test xanh.
- `./mvnw verify -Pintegration`: **10/10** xanh trên PostgreSQL + Redis local — gồm 100 luồng cùng chấp nhận một offer (đúng 1 gán, 1 event), 50 luồng giữ cùng tài xế (đúng 1 thắng), 8 chuyến tranh 1 tài xế, 10 lần gửi lặp cùng Idempotency-Key (1 chuyến), consumer chạy lại không ghi ví trùng.
- E2E HTTP (UC-03/UC-04, CASH + SANDBOX_CARD tới ví): đạt. Số dư ví khớp tính tay từng đồng.
- E2E WebSocket: CONNECT không token bị từ chối; khách khác SUBSCRIBE kênh chuyến bị chặn; offer tới đúng tài xế; vị trí tới khách với độ trễ server→client **p95 ≈ 18 ms** (localhost, 20 mẫu).
- Mô phỏng UC-09: 100 tài xế / 3 s trong 90 s — 3.490 bản tin, **0 lỗi**, p95 phản hồi REST 30–37 ms; đồng thời 3 chuyến được ghép và chạy.

## Việc tiếp theo

1. Bấm thử 4 trang demo trên trình duyệt (`/index.html`), sửa lỗi giao diện nếu có.
2. Commit lần đầu (chưa commit gì — đang ở nhánh `master`; đổi tên sang `main` trước khi đẩy remote).
3. Đo NFR-PERF-003 (truy vấn ứng viên ≤ 200 ms) bằng log thời gian trong `DispatchService` khi chạy mô phỏng.

## Nợ kỹ thuật / giới hạn đã biết

| # | Vấn đề | Ảnh hưởng | Hướng xử lý |
|---|---|---|---|
| 1 | Telemetry qua **WebSocket chưa rate limit** (REST đã có) | Một client có thể spam `/app/driver/location` | Đếm theo session trong `StompAuthInterceptor` |
| 2 | Access token còn hiệu lực tối đa 15 phút sau khi khóa tài khoản/đổi vai trò (refresh đã bị thu hồi) | Cửa sổ 15 phút | Danh sách thu hồi `jti` trên Redis nếu cần |
| 3 | Cổng sandbox lưu giao dịch **trong bộ nhớ**: khởi động lại giữa lúc charge và callback → payment PENDING có `providerRef` không xác minh được | Phải xử lý tay | Chấp nhận cho sandbox; cổng thật có API tra cứu |
| 4 | Simple broker STOMP + cache ngữ cảnh telemetry nằm trong tiến trình | Chỉ chạy **một instance** | Chuyển STOMP broker relay (RabbitMQ) khi scale ngang |
| 5 | OSRM chưa chạy thử với máy chủ thật (mặc định `haversine`) | Quãng đường là ước tính, `approximate=true` | Bật `app.map.provider=osrm` khi có OSRM |
| 6 | Phí hủy của chuyến **tiền mặt** chỉ ghi khoản phải thu (PENDING), không tự thu | Theo chốt TBD-10 | Thu ở chuyến kế tiếp — ngoài phạm vi |
| 7 | Quên mật khẩu chỉ "gửi" qua log ở profile dev | Không có email/SMS thật | Cắm `NotificationSender` thật |

## Chạy lại từ đầu (máy local)

```
docker start rhl-redis                                   # Redis
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev    # tự migrate + seed nếu DB trống
node tools/simulate-drivers.mjs --drivers 100 --rides    # tùy chọn
```
Tài khoản mẫu: `customer@rhl.dev`, `driver.demo@rhl.dev`, `support@ / finance@ / reviewer@ / admin@rhl.dev`, `driver001…driver100@rhl.dev`; mật khẩu = `app.dev.seed-password` trong `application-dev.properties`.
