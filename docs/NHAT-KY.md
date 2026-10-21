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

