# Danh sách chức năng & tiêu chí hoàn thành

Chia thành 7 đợt, làm theo thứ tự (đợt sau phụ thuộc đợt trước). ★ = bắt buộc nghiệm thu, ○ = cắt được nếu thiếu thời gian.

## Đợt 1 — Nền tảng
| Mã | Việc | Yêu cầu SRS | DoD |
|---|---|---|---|
| CN-01-01 ★ | Khung dự án, cấu hình PostgreSQL/Redis, Flyway | NFR-MNT-006 | `mvnw spring-boot:run` khởi động, Flyway tạo đủ bảng, `ddl-auto=validate` không lỗi |
| CN-01-02 ★ | Cấu trúc phản hồi/lỗi thống nhất, correlation ID | COM-003, COM-004 | Mọi lỗi trả `{code,message,correlationId}`; header `X-Correlation-Id` vào/ra |
| CN-01-03 ★ | Outbox + relay + consumer idempotent + DLQ | FR-EVT-001…008, CON-07 | Event trùng không xử lý hai lần; handler lỗi 8 lần → `FAILED`; admin retry được |
| CN-01-04 ★ | Audit bất biến | FR-ADM-004…006 | UPDATE/DELETE trên `audit_records` bị DB từ chối |
| CN-01-05 ★ | Idempotency-Key | COM-008, BR-015 | Gửi lặp cùng khóa+payload trả cùng kết quả; khác payload → 409 `IDEMPOTENCY_KEY_REUSED` |
| CN-01-06 ★ | Rate limit | NFR-SEC-007 | Vượt ngưỡng → 429 `RATE_LIMIT_EXCEEDED` |

## Đợt 2 — Tài khoản & phân quyền
| Mã | Việc | SRS | DoD |
|---|---|---|---|
| CN-02-01 ★ | Đăng ký khách/tài xế, chuẩn hóa email & SĐT, duy nhất | FR-IAM-001…003, DR-VAL-003 | Email viết hoa/thường coi là một; SĐT `0901…` và `+84901…` coi là một; mật khẩu BCrypt |
| CN-02-02 ★ | Đăng nhập JWT, refresh, logout, khóa 15 phút sau 5 lần sai | FR-IAM-005…007 | Sai mật khẩu và sai tài khoản trả cùng thông báo; refresh token cũ bị thu hồi khi xoay vòng |
| CN-02-03 ★ | Đặt lại mật khẩu | FR-IAM-007 | Token 30 phút, dùng một lần, phản hồi không lộ tài khoản tồn tại |
| CN-02-04 ★ | Xem/sửa hồ sơ (chống mass assignment) | FR-IAM-008, DR-VAL-005 | Gửi thêm `roles` trong body không có tác dụng |
| CN-02-05 ★ | RBAC 6 vai trò, quản trị đổi vai trò/khóa tài khoản có audit | FR-IAM-009…011, FR-ADM-003 | Gọi endpoint sai vai trò → 403 |

## Đợt 3 — Tài xế
| Mã | Việc | SRS | DoD |
|---|---|---|---|
| CN-03-01 ★ | Hồ sơ, phương tiện (biển số chuẩn hóa, duy nhất) | FR-DRV-001, FR-DRV-007 | |
| CN-03-02 ★ | Tải tài liệu, kiểm MIME thật/kích thước/hạn | FR-DRV-002, DR-VAL-004, NFR-SEC-011 | File `.exe` đổi đuôi `.png` bị từ chối; lưu ngoài thư mục static |
| CN-03-03 ★ | Gửi duyệt, duyệt/từ chối/yêu cầu bổ sung, lịch sử quyết định | FR-DRV-003…006, UC-01 | Hồ sơ thiếu tài liệu/hết hạn không duyệt được; quyết định ghi người, lúc, lý do, phiên bản hồ sơ |
| CN-03-04 ★ | Online/Offline, ràng buộc điều kiện | FR-DRV-008…011 | Chưa duyệt bật Online → 422 kèm lý do; đang BUSY không Offline được |
| CN-03-05 ★ | Đình chỉ tài xế (admin) | FR-DRV-003 | Bị đình chỉ → biến khỏi matching ngay |

## Đợt 4 — Vị trí & báo giá
| Mã | Việc | SRS | DoD |
|---|---|---|---|
| CN-04-01 ★ | Nhận telemetry REST + WebSocket, kiểm hợp lệ | FR-LOC-001…006 | Bản tin cũ hơn (sequence/thời điểm) không ghi đè bản mới; driverId lấy từ token |
| CN-04-02 ★ | Redis GEO, truy vấn bán kính, loại vị trí hết hạn | FR-LOC-007…010, CON-04 | Tài xế im lặng > 30 s không xuất hiện trong kết quả |
| CN-04-03 ★ | Bảng giá theo dịch vụ/loại xe/vùng/hiệu lực, chống chồng lấn, audit | FR-PRI-001…004, FR-PRI-012 | Tạo quy tắc chồng thời gian hiệu lực → 409 |
| CN-04-04 ★ | Ước tính tuyến (Haversine, OSRM tùy chọn) | FR-PRI-005, FR-PRI-006 | OSRM chết vẫn báo giá được, `approximate=true` |
| CN-04-05 ★ | Surge cung/cầu theo ô geohash | FR-PRI-007…011 | Cùng đầu vào → cùng hệ số; có trần/sàn |
| CN-04-06 ★ | Tạo quote có hạn, gắn khách | FR-PRI-013…016 | Người khác dùng quote → 404; hết hạn → 422 `QUOTE_EXPIRED` |

## Đợt 5 — Chuyến & matching (lõi)
| Mã | Việc | SRS | DoD |
|---|---|---|---|
| CN-05-01 ★ | Tạo chuyến từ quote, idempotent, một chuyến hoạt động/khách, snapshot | FR-TRIP-001…007 | Retry cùng key → cùng trip; trip chuyển MATCHING |
| CN-05-02 ★ | Matching engine: ứng viên, xếp hạng, mở rộng bán kính | FR-MAT-001…006 | Chọn đúng tài xế gần nhất đủ điều kiện |
| CN-05-03 ★ | Offer có hạn, gửi riêng tài xế qua WS, từ chối/hết hạn → ứng viên kế | FR-MAT-007…009, FR-MAT-013 | Tài xế hết hạn offer trở lại AVAILABLE đúng một lần |
| CN-05-04 ★ | Chấp nhận nguyên tử | FR-MAT-010…014, UC-06 | 100 request đồng thời → đúng 1 thắng; không tài xế nào kẹt OFFERED/BUSY |
| CN-05-05 ★ | NO_DRIVER sau giới hạn | FR-MAT-015 | Khách nhận thông báo realtime |
| CN-05-06 ★ | State machine + lịch sử | FR-TRIP-008…015 | Bước nhảy cóc (ACCEPTED → IN_TRIP) → 409 `INVALID_TRIP_STATE` |
| CN-05-07 ★ | PIN bắt đầu chuyến, OTP giao hàng | FR-TRIP-012, FR-TRIP-013 | Sai mã → 422 |
| CN-05-08 ★ | Hủy chuyến có phí, xem trước phí, hủy ngoại lệ của hỗ trợ | FR-CAN-001…007, UC-08 | Hủy lặp không thu phí hai lần; có audit khi nhân viên hủy |
| CN-05-09 ★ | Tra cứu chuyến cho hỗ trợ (lọc + phân trang) | FR-ADM-001, FR-ADM-002, FR-ADM-007 | |

## Đợt 6 — Realtime
| Mã | Việc | SRS | DoD |
|---|---|---|---|
| CN-06-01 ★ | STOMP, xác thực JWT khi CONNECT, chặn SUBSCRIBE trái phép | COM-005, NFR-SEC-005, NFR-PRV-002 | Khách A subscribe chuyến của khách B → bị từ chối |
| CN-06-02 ★ | Fan-out vị trí tài xế cho khách của chuyến | FR-LOC-014, FR-RT-001…004 | Chỉ khi chuyến ACCEPTED…IN_TRIP |
| CN-06-03 ★ | Envelope có sequence, snapshot khi reconnect | COM-007, FR-RT-005…008, UC-07 | |

## Đợt 7 — Thanh toán & ví
| Mã | Việc | SRS | DoD |
|---|---|---|---|
| CN-07-01 ★ | Chốt cước khi hoàn tất (FareFinalized) | FR-PRI-017, FR-PRI-018, FR-TRIP-014 | Cước = quote snapshot |
| CN-07-02 ★ | Thanh toán CASH/SANDBOX, callback ký HMAC, idempotent, thử lại | FR-PAY-001…008, NFR-SEC-010 | Callback lặp không ghi nhận hai lần; chữ ký sai → 401 |
| CN-07-03 ★ | Ví + ledger + hoa hồng có phiên bản | FR-WAL-001…009 | Ghi ví đúng một lần mỗi trip; số dư = tổng bút toán |
| CN-07-04 ★ | Hoàn tiền, điều chỉnh ví | FR-PAY-009…013 | Hoàn vượt số đã thu → 422 |

## Đợt 8 — Demo & nghiệm thu
| Mã | Việc | SRS | DoD |
|---|---|---|---|
| CN-08-01 ★ | Dữ liệu mẫu (profile dev) | | Có sẵn tài khoản mỗi vai trò + 100 tài xế đã duyệt |
| CN-08-02 ★ | Trang demo khách / tài xế / nhân viên | UI-CUS, UI-DRV, UI-ADM (rút gọn) | Đi hết luồng UC-03, UC-04 trên trình duyệt |
| CN-08-03 ★ | Script mô phỏng 100 tài xế / 3 s | UC-09, NFR-PERF-002 | Chạy 5 phút không lỗi, matching vẫn đúng |
| CN-08-04 ★ | Unit test logic lõi + integration test đồng thời | NFR-MNT-003, UC-06 | Xem `.claude/rules/kiem-thu.md` |
| CN-08-05 ○ | Đo độ trễ realtime p95 | NFR-PERF-001 | |
