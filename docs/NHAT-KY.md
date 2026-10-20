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
