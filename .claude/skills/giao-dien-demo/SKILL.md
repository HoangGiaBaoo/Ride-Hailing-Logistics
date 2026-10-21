---
name: giao-dien-demo
description: Quy ước các trang HTML demo tĩnh (khách hàng, tài xế, nhân viên) và script mô phỏng tài xế — HTML/CSS/JS thuần trong api-gateway/src/main/resources/static/, Leaflet + OpenStreetMap, STOMP.js qua WebSocket, gọi REST /api/v1 bằng fetch kèm JWT, hiển thị tiền/khoảng cách/thời gian kiểu Việt Nam. Nạp skill này khi tạo hoặc sửa file trong src/main/resources/static/ hoặc tools/.
---

# Trang demo

Mục đích: **chứng minh luồng nghiệp vụ chạy thật trên trình duyệt** (UC-03, UC-04, UC-07), không phải sản phẩm mobile. Không dành thời gian làm đẹp.

## Cấu trúc

```
api-gateway/src/main/resources/static/   (gateway phuc vu, goi API cung origin :8080)
├── index.html          chon vai tro, dang nhap nhanh tai khoan mau
├── customer.html       bao gia -> dat chuyen -> theo doi tai xe tren ban do -> bien nhan
├── driver.html         Online/Offline, gui GPS (gia lap keo marker), nhan offer co dong ho, chuyen trang thai, vi
├── ops.html            tra cuu chuyen (support), duyet ho so (reviewer), hoan tien (finance)
├── css/app.css
└── js/api.js           fetch + JWT + refresh + Idempotency-Key, ket noi STOMP, dinh dang so
tools/seed-dev.mjs           Node 22: bang gia, hoa hong, khach, 101 tai xe da duyet - qua API gateway
tools/simulate-drivers.mjs   Node 22: N tai xe, moi 3 s gui vi tri, tu dong nhan offer
```

## Thư viện (CDN, ghim phiên bản)

- Leaflet `1.9.4` (unpkg) + tile OpenStreetMap.
- `@stomp/stompjs` `7.0.0` (jsdelivr, bản UMD) — WebSocket gốc, không SockJS.

## Quy tắc

- **Không `innerHTML` với dữ liệu từ server** — dùng `textContent` / tạo phần tử (hook cảnh báo). Tên khách, địa chỉ, mô tả kiện hàng là dữ liệu người dùng nhập.
- Token lưu `sessionStorage` (demo); hết hạn → gọi `/auth/refresh` một lần rồi thử lại.
- Nút tạo chuyến / nhận chuyến / thanh toán: **khóa nút khi đang gửi** (NFR-USA-003) và sinh `Idempotency-Key` một lần cho mỗi thao tác.
- Hủy chuyến có phí: gọi `cancellation-preview`, hiện phí, bắt xác nhận (UI-CUS-009).
- Reconnect STOMP: lấy snapshot `GET /api/v1/trips/{id}` rồi mới nghe tiếp, bỏ bản tin có `sequence` ≤ sequence đã thấy (FR-RT-005/006).
- Hiển thị: tiền `Intl.NumberFormat('vi-VN', {style:'currency', currency:'VND'})`, khoảng cách mét → km 1 chữ số, thời gian theo giờ máy người xem.
- Màu, khoảng cách dùng biến CSS trong `:root` của `app.css`.

## Script mô phỏng (UC-09)

- Đăng nhập các tài khoản `driver001…driver100` do `tools/seed-dev.mjs` tạo, bật Online, mỗi tài xế đi ngẫu nhiên quanh trung tâm TP.HCM, gửi `POST /api/v1/drivers/me/location` mỗi 3 s với `sequence` tăng dần.
- Tùy chọn `--rides`: 2 khách mẫu đặt chuyến liên tục, tài xế tự nhận và chạy hết vòng đời chuyến (tài xế đang dở chuyến từ lần chạy trước thì chạy tiếp).
- In thống kê mỗi 10 s: số bản tin gửi, lỗi, p95 thời gian phản hồi.
