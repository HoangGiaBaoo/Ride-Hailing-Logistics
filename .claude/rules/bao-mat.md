# Quy tắc bảo mật — luôn áp dụng

Theo SRS mục 6.3 (NFR-SEC) và 6.4 (NFR-PRV). Đây là chỗ dễ bị đánh trượt nghiệm thu nhất.

## Bắt buộc

| Mã | Nội dung |
|---|---|
| FR-IAM-010, NFR-SEC-004 | **Kiểm quyền ở server cho mọi endpoint và mọi bản tin WebSocket**: vai trò (`@PreAuthorize`) **và** quyền sở hữu tài nguyên (trong service). Không có quyền xem → 404, không phải 403 |
| NFR-SEC-002 | Mật khẩu BCrypt. Không bao giờ lưu/log văn bản thuần, kể cả trong `StaffSeeder` và `tools/seed-dev.mjs` |
| NFR-SEC-003 | Bí mật (`JWT_SECRET`, mật khẩu DB, khóa ký sandbox) chỉ đến từ biến môi trường / `config/application.properties` (gitignored). **Không ghi cứng trong `.java` hay `src/main/resources`** |
| FR-IAM-006 | Đăng nhập sai: cùng một thông báo cho sai tài khoản / sai mật khẩu / bị khóa. 5 lần sai → khóa 15 phút |
| NFR-SEC-005 | WebSocket: JWT kiểm ở frame `CONNECT`; `SUBSCRIBE` vào `/topic/trips/{id}` kiểm người tham gia chuyến |
| NFR-SEC-007 | Rate limit cho login, telemetry, quote, tạo chuyến, offer, payment, upload |
| NFR-SEC-009 | Log **không** chứa password, token, refresh token, chữ ký, số giấy tờ, tọa độ chính xác của khách |
| NFR-SEC-010 | Callback thanh toán: kiểm chữ ký HMAC, timestamp ±5 phút, đối chiếu trip/amount/currency, idempotent |
| NFR-SEC-011, DR-VAL-004 | Upload: kiểm magic bytes, ≤ 5 MB, đuôi cho phép; lưu ở `storage/` ngoài classpath, tên file do server sinh (chống path traversal) |
| NFR-SEC-012 | Client không quyết định giá, trạng thái, tài xế được gán, kết quả thanh toán, số dư |
| DR-VAL-005 | DTO đầu vào là record chỉ có trường được phép sửa → chống mass assignment |

## Danh sách kiểm khi soát code

- [ ] Không có bí mật thật nào trong file được commit
- [ ] Mọi truy vấn dùng tham số (`:param`) — không nối chuỗi SQL/JPQL
- [ ] Controller `/internal/**` chỉ nhận token vai trò `SERVICE` (đã khóa trong `SecurityConfig`), không thêm route `/internal` ở gateway
- [ ] Mọi controller dưới `/api/v1/**` có `@PreAuthorize` hoặc được mở có chủ đích trong `SecurityConfig` (chỉ `auth/**`, `payments/sandbox/callback`, tài liệu API, trang demo tĩnh)
- [ ] Service kiểm `customerId`/`driverId` của tài nguyên khớp người gọi (hoặc người gọi là nhân viên có quyền)
- [ ] Phản hồi cho tài xế không chứa SĐT/tên khách khi mới chỉ được offer
- [ ] Phản hồi danh sách luôn phân trang, `size` ≤ 100
- [ ] Hành động nhạy cảm (hủy bởi nhân viên, hoàn tiền, điều chỉnh ví, đổi bảng giá/hoa hồng/vai trò, duyệt hồ sơ, xem tài liệu tài xế) ghi audit

## CSRF

API là stateless, xác thực bằng header `Authorization: Bearer` → CSRF **không áp dụng** và được tắt có chủ đích cho `/api/**` (NFR-SEC-006 "khi áp dụng"). Không dùng cookie phiên cho API. Nếu sau này thêm form login bằng cookie thì phải bật lại CSRF cho phần đó.
