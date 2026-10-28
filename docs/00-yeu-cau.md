# Làm rõ yêu cầu — SRS-RHL-002

Nguồn: `SRS_2_Ride_Hailing_Logistics.docx` (IEEE 830, v1.0). File này ghi lại **cách hiểu và các quyết định** để biến SRS thành hệ thống chạy được. Mã yêu cầu giữ nguyên như SRS (FR-xxx, NFR-xxx, BR-xxx) để truy vết.

## 1. Sản phẩm là gì

Backend cho nền tảng **đặt xe chở khách (RIDE)** và **giao hàng theo yêu cầu (DELIVERY)**, thời gian thực:

1. Khách lấy **báo giá** (quãng đường, ETA, cước, hệ số surge, hạn hiệu lực).
2. Khách tạo **chuyến** từ báo giá → hệ thống **tìm tài xế gần nhất** qua chỉ mục không gian, gửi **offer có thời hạn**.
3. Một tài xế chấp nhận — **nguyên tử**, không bao giờ hai tài xế thắng một chuyến hay một tài xế nhận hai chuyến.
4. Tài xế cập nhật trạng thái theo **state machine**, gửi GPS mỗi 3–5 giây; khách theo dõi qua **WebSocket**.
5. Hoàn tất → chốt cước → **thanh toán** → ghi **ví tài xế** (ledger bất biến, hoa hồng có phiên bản). Tất cả **idempotent**.

## 2. Quyết định kiến trúc (đã chốt với người dùng)

| Vấn đề | SRS nói | Quyết định | Lý do |
|---|---|---|---|
| Phân rã dịch vụ (CON-01, CON-02) | Microservices, mỗi dịch vụ sở hữu dữ liệu | **Microservices** (đổi từ modular monolith theo yêu cầu người dùng, 2026-10-21): 6 service nghiệp vụ theo SRS (identity, driver, location, pricing, trip, payment — đổi từ 3 service ngày 2026-10-27 theo yêu cầu người dùng), mỗi service một database; Eureka, Config Server, API Gateway; gọi đồng bộ bằng OpenFeign vào `/internal/**`, bất đồng bộ bằng event | Đúng CON-01/CON-02 của SRS. Gán tài xế cho chuyến phải nằm trong một transaction (CON-06) nên **trạng thái khả dụng tài xế** (`driver_states`) thuộc trip-service; driver-service chỉ giữ hồ sơ/kiểm duyệt (`02-kien-truc.md` §2) |
| Event broker (CON-07, FR-EVT) | Message broker | **Transactional Outbox** ở từng service → relay gửi **Kafka** (`acks=all`, producer idempotent, key = aggregateId) topic `rhl.{producer}.events` → consumer group mỗi service, ack thủ công sau khi ghi **inbox**, lỗi đọc → `.DLT`, handler idempotent theo `eventId` (`processed_events`), quá số lần thử → `FAILED` (DLQ) | Ghi nguyên tử nghiệp vụ + event (FR-EVT-002). Đổi từ Redis Stream sang Kafka ngày 2026-10-27 theo chương trình học (partition theo key, consumer group, DLT, Spring Cloud Bus) |
| CSDL | Quan hệ | **PostgreSQL 18**, migration bằng **Flyway** (NFR-MNT-006) | Có sẵn trên máy |
| Chỉ mục không gian (CON-04) | Redis GEO/PostGIS/H3 | **Redis GEO** (`GEOADD`/`GEOSEARCH`) cho vị trí mới nhất; Redis còn dùng cho bộ đếm surge, rate limit, sequence realtime | Đúng gợi ý SRS, truy vấn nhanh |
| Realtime (CON-03) | WebSocket/WSS | **STOMP over WebSocket** tại `/ws`, xác thực JWT ở frame CONNECT | Có sẵn trong Spring, hỗ trợ heartbeat, user destination |
| Giao diện | Mobile app + web quản trị | **REST JSON + WebSocket API** là sản phẩm chính; kèm **trang HTML demo** (Leaflet) và **script mô phỏng 100 tài xế** | Mobile app ngoài khả năng thời gian; API là thứ được nghiệm thu |
| Xác thực (FR-IAM-005) | Token có thời hạn | **JWT HS256** access token 15 phút + **refresh token** lưu dạng băm SHA-256, thu hồi được | Stateless, dùng chung cho REST và WebSocket |

## 3. Chốt các mục TBD (mục 10 của SRS)

Giá trị nào cũng **đọc từ cấu hình** (`app.*` trong `application.properties` hoặc bảng quy tắc), không ghi cứng trong logic (NFR-MNT-005).

| Mã | Chốt |
|---|---|
| TBD-01 Phạm vi | Làm **cả RIDE và DELIVERY** |
| TBD-02 Bản đồ | Interface `RouteEstimator`. Mặc định **ước tính Haversine × hệ số đường 1.3, tốc độ TB 25 km/h** (fallback được phê duyệt theo FR-PRI-006). Bản OSRM bật bằng `app.map.provider=osrm`; lỗi/timeout thì rơi về Haversine và đánh dấu `approximate=true` |
| TBD-03 Công thức giá | `tạm tính = giá mở cửa + giá/km × km + giá/phút × phút`; `cước = max(tạm tính × surge, cước tối thiểu) + phí đặt chuyến`; **làm tròn lên bội số 1.000 VND**. `BigDecimal`, không float (FR-PRI-016) |
| TBD-04 Surge | Vùng = **ô geohash độ chính xác 5** (~4,9 km × 4,9 km); cửa sổ **5 phút**. Cầu = số chuyến tạo trong ô/cửa sổ; cung = số tài xế AVAILABLE có vị trí còn mới trong ô. `surge = clamp(1 + 0.25 × (cầu/max(cung,1) − 1), 1.0, 2.5)`, làm tròn 0.1. Quy tắc có phiên bản `surge-v1` |
| TBD-05 Quote | Hiệu lực **5 phút**. **Giá trọn gói (upfront)**: cước cuối = tổng đã báo giá, surge giữ nguyên hệ số khách đã xác nhận (FR-PRI-018). Mỗi quote dùng được **một lần** |
| TBD-06 Matching | Bán kính theo vòng **2 → 4 → 6 km**; offer **tuần tự từng tài xế**, hết hạn sau **15 giây**; tổng thời gian tìm tối đa **60 giây** → `NO_DRIVER`. Xếp hạng `rank-v1`: khoảng cách tăng dần, hòa thì vị trí mới hơn trước |
| TBD-07 Hủy/no-show | Xem bảng phí hủy ở `01-nghiep-vu.md` §5 |
| TBD-08 Giao hàng | Xe máy ≤ 30 kg, van ≤ 500 kg. Bằng chứng giao: **mã OTP 6 số** hiển thị cho người gửi để chuyển người nhận; tài xế nhập khi hoàn tất |
| TBD-09 Thanh toán | `CASH` và `SANDBOX_CARD` (nhà cung cấp giả lập, callback ký HMAC-SHA256, chống replay theo timestamp). Không pre-authorization. Thất bại → khách thử lại tối đa 3 lần |
| TBD-10 Ví | Ghi có khi thanh toán **thành công**. Chuyến tiền mặt: tài xế đã cầm tiền nên ví chỉ ghi **nợ hoa hồng** → số dư được phép âm **riêng cho loại bút toán này** (chính sách rõ ràng theo FR-WAL-009). Rút tiền: ngoài phạm vi |
| TBD-11 KYC | Bắt buộc 3 tài liệu: `DRIVER_LICENSE`, `NATIONAL_ID`, `VEHICLE_REGISTRATION`, còn hạn; tệp JPEG/PNG/PDF ≤ 5 MB, kiểm MIME thật bằng magic bytes |
| TBD-12 Riêng tư | Không lưu lịch sử telemetry dài hạn (chỉ vị trí mới nhất trong Redis); khách mất quyền nhận vị trí ngay khi chuyến kết thúc, kênh trạng thái còn ân hạn 2 phút |
| TBD-13 Mobile | Không áp dụng (không làm app) |
| TBD-14 Ngưỡng vị trí | Độ mới 30 giây; accuracy ≤ 50 m mới dùng cho matching, > 500 m từ chối; nhảy vị trí > 70 m/s đánh dấu nghi vấn; timestamp thiết bị ở tương lai > 30 s hoặc cũ > 5 phút bị từ chối |
| TBD-15 Tải | Mục tiêu nghiệm thu: 100 tài xế / 3 giây (≈ 33 bản tin/giây) |

## 4. Giả định bổ sung (không có trong SRS)

- Một vùng phục vụ mặc định `HCM` (khung tọa độ TP.HCM); quote ngoài vùng bị từ chối `BUSINESS_RULE_VIOLATION`.
- `driverId` = `userId` của tài khoản tài xế (một tài khoản một hồ sơ tài xế, một phương tiện).
- Khách chỉ có **tối đa một chuyến đang hoạt động** (FR-TRIP-007).
- Thời gian lưu UTC (`timestamptz`, `Instant`), client tự đổi múi giờ (DR-007).
- Thông báo email/SMS (quên mật khẩu) đi qua interface `NotificationSender`; bản dev chỉ ghi log — **không có nhà cung cấp thật**.
- Xác minh email/SĐT (FR-IAM-004): chính sách **tắt** ở v1 (SRS ghi "nếu chính sách yêu cầu").
- Realtime (vị trí, trạng thái) đẩy ngay sau commit bằng Spring event để đạt ≤ 500 ms; các event **tài chính** (TripCompleted → FareFinalized → Payment → Wallet) bắt buộc đi qua Outbox.

## 5. Ngoài phạm vi phiên bản này

Toàn bộ mục 2.7 của SRS, cộng thêm: ứng dụng mobile thật; TLS (do reverse proxy đảm nhận khi triển khai — NFR-SEC-001 ghi nhận nhưng không cấu hình trong app); quét lỗ hổng phụ thuộc (NFR-SEC-013, làm bằng công cụ ngoài); gRPC.
