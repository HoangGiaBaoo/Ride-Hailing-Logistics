# Nghiệp vụ — tra nhanh khi viết code

Nguồn sự thật: SRS mục 4, 7 và `00-yeu-cau.md`. Đây là bản rút gọn để code.

## 1. Vai trò (FR-IAM-009)

| Vai trò | Mã | Được làm |
|---|---|---|
| Khách hàng | `CUSTOMER` | Hồ sơ, báo giá, đặt/hủy chuyến của mình, theo dõi, thanh toán, lịch sử |
| Tài xế | `DRIVER` | Hồ sơ/xe/tài liệu, gửi duyệt, Online/Offline, gửi vị trí, nhận/từ chối offer, chuyển trạng thái chuyến được gán, ví |
| Kiểm duyệt | `REVIEWER` | Xem hồ sơ & tài liệu chờ duyệt, duyệt/từ chối/yêu cầu bổ sung |
| Hỗ trợ | `SUPPORT_STAFF` | Tra cứu chuyến, xem lịch sử, hủy ngoại lệ (bắt buộc lý do, audit) |
| Tài chính | `FINANCE_STAFF` | Xem thanh toán, ví; hoàn tiền; điều chỉnh ví bằng bút toán bù |
| Quản trị | `ADMIN` | Vai trò & trạng thái tài khoản, bảng giá, hoa hồng, đình chỉ tài xế, audit, xử lý lại event lỗi |

**Mọi kiểm tra quyền làm ở server** (FR-IAM-010): `@PreAuthorize` cho quyền chức năng + kiểm **quyền sở hữu** trong service (khách chỉ thấy chuyến của mình, tài xế chỉ thấy chuyến được gán/được offer). Không có quyền xem tài nguyên → trả `404 RESOURCE_NOT_FOUND`, không tiết lộ là nó tồn tại.

## 2. Trạng thái hồ sơ tài xế (FR-DRV-003)

```
DRAFT --submit--> PENDING_REVIEW --approve--> APPROVED --suspend--> SUSPENDED
                       |  \--reject--> REJECTED --sua & submit--> PENDING_REVIEW
                       \--request changes--> DRAFT
```

Online được khi (FR-DRV-008, BR-001): hồ sơ `APPROVED` + tài khoản `ACTIVE` + đủ 3 tài liệu còn hạn + phương tiện `ACTIVE`.

## 3. Trạng thái khả dụng tài xế (FR-DRV-009)

| Từ | Sang | Khi nào | Cách làm |
|---|---|---|---|
| OFFLINE | AVAILABLE | Tài xế bật Online, đủ điều kiện | |
| AVAILABLE | OFFLINE | Tài xế tắt | |
| AVAILABLE | OFFERED | Hệ thống giữ tài xế cho một offer | `UPDATE ... WHERE availability='AVAILABLE'` — **nguyên tử** (FR-DRV-012) |
| OFFERED | AVAILABLE | Offer bị từ chối / hết hạn / chuyến hủy khi đang offer | Điều kiện `WHERE availability='OFFERED'` → đúng một lần (FR-DRV-013) |
| OFFERED | BUSY | Chấp nhận offer thành công | Cùng transaction với trip → ACCEPTED |
| BUSY | AVAILABLE | Chuyến COMPLETED / CANCELLED | |

Tài xế `OFFERED` hoặc `BUSY` **không được** Offline (FR-DRV-011).

## 4. State machine chuyến (SRS §7.1, FR-TRIP-008…014)

| Từ | Sang | Ai | Điều kiện |
|---|---|---|---|
| CREATED | MATCHING | Hệ thống | Ngay sau khi tạo, quote hợp lệ |
| CREATED | CANCELLED | Khách | |
| MATCHING | ACCEPTED | Tài xế | Chấp nhận offer nguyên tử |
| MATCHING | NO_DRIVER | Hệ thống | Hết vòng tìm / hết 60 s |
| MATCHING | CANCELLED | Khách, Hỗ trợ | |
| ACCEPTED | PICKING_UP | Tài xế được gán | |
| ACCEPTED | CANCELLED | Khách, Tài xế, Hỗ trợ | Tính phí theo §5 |
| PICKING_UP | ARRIVED | Tài xế được gán | Cách điểm đón ≤ 300 m nếu có vị trí mới nhất |
| PICKING_UP | CANCELLED | Khách, Tài xế, Hỗ trợ | Tính phí |
| ARRIVED | IN_TRIP | Tài xế được gán | RIDE: nhập đúng **PIN 4 số** của khách (khi `app.trip.require-start-pin=true`) |
| ARRIVED | CANCELLED | Khách, Tài xế (no-show), Hỗ trợ | Tính phí |
| IN_TRIP | COMPLETED | Tài xế được gán | DELIVERY: nhập đúng **OTP giao hàng** |
| IN_TRIP | CANCELLED | **Chỉ Hỗ trợ** | Quy trình ngoại lệ, bắt buộc lý do |
| COMPLETED / CANCELLED / NO_DRIVER | — | | Trạng thái kết thúc |

Mỗi lần chuyển: ghi `trip_status_history` (cũ, mới, actor, vai trò, lý do, tọa độ nếu có, thời điểm) — bảng **chỉ thêm, không sửa/xóa** (DR-005, có trigger chặn).

Trạng thái **hoạt động**: `CREATED, MATCHING, ACCEPTED, PICKING_UP, ARRIVED, IN_TRIP` (dùng cho một chuyến/khách). Ràng buộc tài xế: `ACCEPTED, PICKING_UP, ARRIVED, IN_TRIP`.

## 5. Phí hủy (FR-CAN-003, TBD-07) — quy tắc `cancel-v1`

| Người hủy | Trạng thái | Phí khách trả |
|---|---|---|
| Khách | CREATED, MATCHING | 0 |
| Khách | ACCEPTED, PICKING_UP | 0 nếu trong **2 phút** kể từ lúc được ghép; quá 2 phút: **10.000 VND** |
| Khách | ARRIVED | **20.000 VND** |
| Tài xế | ACCEPTED, PICKING_UP | 0 |
| Tài xế (no-show) | ARRIVED, đã chờ ≥ **5 phút** | Khách trả **20.000 VND**; chờ chưa đủ 5 phút → 0 |
| Hỗ trợ | mọi trạng thái được phép | 0 (hủy ngoại lệ) |

Phí > 0 → event `TripCancelled` mang `feeAmount` → Payment thu phí → ví tài xế nhận như một khoản thu nhập (trừ hoa hồng). Bắt buộc `reasonCode` (FR-CAN-002); `OTHER` thì bắt buộc `note`. Hủy lặp trả cùng kết quả, không thu phí hai lần (FR-CAN-006).

## 6. Thanh toán (FR-PAY-007)

```
PENDING --verify OK--> SUCCEEDED --refund--> PARTIALLY_REFUNDED / REFUNDED
   \--fail--> FAILED --khach thu lai (<=3 lan)--> PENDING
```

- Số tiền **lấy từ cước đã chốt**, không bao giờ lấy từ client (FR-PAY-002).
- Một trip có tối đa một payment mỗi loại (`TRIP_FARE`, `CANCELLATION_FEE`) — ràng buộc unique.
- Tổng hoàn ≤ số đã thu (BR-010). Hoàn tiền **không** trừ ví tài xế (nền tảng chịu) — muốn trừ thì tài chính tạo điều chỉnh riêng.

## 7. Ví & hoa hồng (FR-WAL)

- Ledger `wallet_entries` bất biến; số dư = tổng `amount` có dấu (FR-WAL-002). Idempotent theo `(wallet_id, entry_type, reference_id)`.
- Hoa hồng: bảng `commission_rules` theo loại dịch vụ, có phiên bản & hiệu lực. Mặc định RIDE 20%, DELIVERY 15%.
- Thanh toán `SANDBOX_CARD` thành công: `TRIP_EARNING +tổng` và `COMMISSION −hoa hồng`.
- Thanh toán `CASH`: chỉ `COMMISSION −hoa hồng` (tài xế đã cầm tiền mặt).
- Điều chỉnh: `ADJUSTMENT` ± có lý do, tham chiếu; không được làm số dư âm.
- Hoa hồng = `round(tổng × tỉ lệ, 0, HALF_UP)`; thu nhập ròng = tổng − hoa hồng (BR-012).

## 8. Mã lỗi API (Phụ lục B)

Cấu trúc lỗi luôn là `{code, message, correlationId, details?}`.

| HTTP | code | Khi nào |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Sai dữ liệu đầu vào (kèm `details` theo trường) |
| 401 | `AUTHENTICATION_REQUIRED` | Thiếu/sai token; đăng nhập sai hoặc tài khoản bị khóa tạm (thông báo chung — FR-IAM-006) |
| 403 | `ACCESS_DENIED` | Sai vai trò |
| 404 | `RESOURCE_NOT_FOUND` | Không có hoặc không được phép biết |
| 409 | `INVALID_TRIP_STATE` | Chuyển trạng thái chuyến không hợp lệ |
| 409 | `DRIVER_ALREADY_ASSIGNED` | Tài xế đã được gán / chuyến đã có tài xế khác |
| 409 | `OFFER_EXPIRED` | Offer hết hạn hoặc không còn hiệu lực |
| 409 | `IDEMPOTENCY_KEY_REUSED` | Cùng khóa, payload khác |
| 409 | `CONFLICT` | Xung đột khác (trùng email, quy tắc giá chồng lấn, request đang xử lý…) |
| 422 | `QUOTE_EXPIRED` | Quote hết hạn/đã dùng |
| 422 | `BUSINESS_RULE_VIOLATION` | Vi phạm quy tắc nghiệp vụ |
| 429 | `RATE_LIMIT_EXCEEDED` | Vượt giới hạn tần suất |
| 503 | `DEPENDENCY_UNAVAILABLE` | Redis/bản đồ/thanh toán tạm lỗi |

## 9. Quy tắc nghiệp vụ hay quên

| Mã | Nhớ |
|---|---|
| BR-002/003 | Một tài xế ≤ 1 chuyến hoạt động, một chuyến ≤ 1 tài xế: **kiểm trong code và có partial unique index trong DB** |
| BR-005 | Quote thuộc đúng khách, còn hạn, chưa dùng |
| BR-006 | Surge > 1 → request tạo chuyến phải có `surgeConfirmed=true` |
| BR-007 | Trip lưu snapshot quote (thành phần giá, surge, rule version), điểm đón/đến, người nhận |
| BR-009 | `TripCompleted` phát đúng một lần — chuyển trạng thái có điều kiện |
| BR-013 | Khách chỉ nhận vị trí tài xế khi chuyến ở ACCEPTED…IN_TRIP; tài xế chỉ thấy điểm đón khi được offer, SĐT người nhận khi được gán |
| BR-014 | Audit: hồ sơ tài xế, bảng giá, hoa hồng, hủy bởi nhân viên, hoàn tiền, điều chỉnh ví, đổi vai trò/trạng thái tài khoản |
| BR-015 | `Idempotency-Key` bắt buộc cho: tạo chuyến, chấp nhận offer, hủy chuyến, hoàn tiền, điều chỉnh ví |
