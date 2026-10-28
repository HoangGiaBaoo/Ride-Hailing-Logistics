---
name: chuan-bi-van-dap
description: Chuẩn bị bảo vệ/nghiệm thu đề tài đặt xe & giao hàng thời gian thực trước giảng viên — các câu hỏi chắc chắn bị hỏi (double assignment, idempotency, outbox, Redis GEO, surge, WebSocket bảo mật), cách giải thích bằng tiếng Việt, kịch bản demo, checklist trước buổi bảo vệ. Nạp skill này khi được hỏi về vấn đáp, demo, hoặc giải thích code cho người chưa biết.
---

# Chuẩn bị vấn đáp

Người hỏi sẽ mở code, chỉ vào một dòng và hỏi *"dòng này làm gì, sao viết thế này"*. Mỗi phần viết xong, **luôn giảng lại được bằng tiếng Việt**. Không giảng được → viết lại cho đơn giản.

## Năm câu chắc chắn bị hỏi

### 1. "Làm sao bảo đảm hai tài xế không cùng nhận một chuyến?" (CON-06, UC-06)

> Em có ba lớp. Thứ nhất, lúc chấp nhận em khóa dòng chuyến bằng `SELECT … FOR UPDATE`, nên các request chấp nhận cùng một chuyến phải xếp hàng. Thứ hai, trạng thái tài xế đổi bằng câu `UPDATE … WHERE availability = 'OFFERED'` — nếu số dòng cập nhật khác 1 thì coi như thua. Thứ ba, trong DB có partial unique index: mỗi tài xế chỉ có tối đa một chuyến ở trạng thái đang hoạt động. Em có test 100 luồng bấm cùng lúc, chỉ đúng một thắng.

### 2. "Khách bấm đặt xe hai lần do mạng chậm thì sao?" (FR-TRIP-002, BR-015)

> Request tạo chuyến bắt buộc có header `Idempotency-Key`. Lần đầu em lưu khóa cùng mã băm payload rồi mới xử lý; lần sau cùng khóa cùng payload thì trả lại đúng kết quả cũ, khác payload thì báo `IDEMPOTENCY_KEY_REUSED`. Ngoài ra mỗi quote chỉ dùng một lần và mỗi khách chỉ có một chuyến hoạt động — có index trong DB chặn.

### 3. "Chuyến hoàn tất mà dịch vụ thanh toán đang chết thì mất tiền không?" (FR-EVT-002, FR-EVT-007)

> Không. Lúc chuyển COMPLETED, em ghi event `TripCompleted` vào bảng outbox **trong cùng transaction** với việc đổi trạng thái. Hoặc cả hai cùng lưu, hoặc không cái nào. Một relay đọc outbox và giao cho các handler; handler lỗi thì thử lại với thời gian chờ tăng dần, quá 8 lần thì đánh dấu FAILED để admin xử lý lại. Mỗi handler ghi `processed_events` nên chạy lại cũng không ghi ví hai lần.

### 4. "Tìm tài xế gần bằng cách nào, sao không quét cả bảng?" (CON-04, NFR-PERF-003)

> Vị trí mới nhất nằm trong Redis GEO — bản chất là sorted set với điểm geohash 52 bit, nên `GEOSEARCH` bán kính chạy O(N+log M) chỉ trên các ô lân cận. Em lọc thêm độ mới 30 giây và độ chính xác ≤ 50 m, rồi lọc trạng thái AVAILABLE trong PostgreSQL bằng một câu `IN`. Không có ai thì mở bán kính 2 → 4 → 6 km.

### 5. "Surge tính thế nào, có công bằng với khách không?" (FR-PRI-007…012, FR-PRI-018)

> Chia bản đồ thành ô geohash cỡ ~5 km. Cầu là số chuyến tạo trong ô trong 5 phút gần nhất, cung là số tài xế rảnh có vị trí mới trong ô. Hệ số = 1 + 0,25 × (cầu/cung − 1), kẹp trong [1,0; 2,5]. Khách thấy hệ số trước khi đặt và phải xác nhận; cước cuối dùng đúng hệ số đã xác nhận, không tính lại.

## Câu hỏi tiếp theo hay gặp

| Câu hỏi | Ý chính |
|---|---|
| "Tài xế ở driver-service, chuyến ở trip-service — gán tài xế có nguyên tử không?" | Có. Hồ sơ/kiểm duyệt ở driver-service, nhưng **trạng thái khả dụng** (`driver_states`: AVAILABLE/OFFERED/BUSY) thuộc trip-service. Nên trip MATCHING + offer PENDING + tài xế OFFERED→BUSY vẫn đổi trong **một transaction một DB**. Bấm Online thì trip hỏi driver-service qua Feign (chỉ đọc) xem đã duyệt chưa. Payment chỉ cần nhất quán sau cùng (event `FareFinalized`) |
| "Event đi giữa service bằng gì?" | Outbox trong DB của service phát → relay gửi Kafka (`acks=all`, idempotent producer, key = aggregateId nên cùng chuyến giữ thứ tự) → consumer group mỗi service, ack thủ công **sau khi** ghi inbox → handler idempotent theo `eventId`. Service tắt vẫn không mất event: offset chưa commit thì khởi động lại đọc tiếp; đọc lỗi mãi → topic `.DLT` |
| "Bản tin GPS đến sai thứ tự?" | So `sequence` trong Lua script trên Redis — so và ghi nguyên tử; bản cũ bị bỏ, không làm lùi vị trí |
| "Khách khác có xem được vị trí tài xế của tôi không?" | `SUBSCRIBE /topic/trips/{id}` bị interceptor kiểm: chỉ khách/tài xế của chuyến, chuyến đang hoạt động. Vị trí chỉ phát khi ACCEPTED…IN_TRIP |
| "Mất WebSocket giữa chuyến?" | Trạng thái nằm ở server. Client reconnect → gọi API snapshot → nghe tiếp, bỏ bản tin có sequence cũ |
| "Tiền dùng kiểu gì?" | `BigDecimal` + currency, DB `numeric(15,2)`. `double` không biểu diễn chính xác 0,1 |
| "Sửa sai ví thế nào nếu không được sửa bút toán?" | Ghi bút toán bù `ADJUSTMENT`; trigger DB chặn UPDATE/DELETE trên `wallet_entries` |
| "Tài xế nhận offer rồi tắt app?" | Offer hết hạn 15 s → tài xế về AVAILABLE, chuyển ứng viên kế. Vị trí quá 30 s → bị loại khỏi matching |

## Kịch bản demo (≈ 10 phút)

1. Chạy `tools/simulate-drivers.mjs` 100 tài xế → mở `ops.html` thấy số tài xế online.
2. `customer.html`: chọn điểm đón/đến → báo giá (quãng đường, ETA, surge, hạn).
3. Đặt chuyến → trạng thái "Đang tìm tài xế".
4. `driver.html` (tài khoản `driver.demo`): offer hiện với đồng hồ 15 s → chấp nhận.
5. Khách thấy tài xế, biển số, vị trí di chuyển realtime.
6. Tài xế: Đang đến → Đã đến → nhập PIN → Bắt đầu → Hoàn tất.
7. Khách thấy biên nhận; tài xế thấy ví tăng (thu nhập − hoa hồng).
8. Tắt mạng tab khách → bật lại: trạng thái đúng.
9. Chạy `ConcurrentAcceptIT` trước mặt người chấm.
10. Thử gõ API lấy chuyến của khách khác → 404.

## Checklist trước buổi bảo vệ

- [ ] Docker + `rhl-redis` + `rhl-kafka` chạy, PostgreSQL chạy, `config-repo/` đã commit
- [ ] `mvnw.cmd test` xanh, `mvnw.cmd verify -Pintegration` xanh
- [ ] `tools/start-all.ps1` lên đủ 8 tiến trình, `node tools/seed-dev.mjs` chạy xong, đăng nhập được mọi vai trò
- [ ] Swagger mở được
- [ ] Có tag git của bản chạy được
