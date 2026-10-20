---
name: nghiep-vu-rhl
description: Quy tắc nghiệp vụ của hệ thống đặt xe & giao hàng (SRS-RHL-002) — sáu vai trò RBAC, trạng thái hồ sơ và khả dụng tài xế, state machine chuyến, phí hủy, thanh toán, ví và hoa hồng, mã lỗi API, các quy tắc BR-001…015. Nạp skill này khi làm bất kỳ chức năng nghiệp vụ nào, khi chuyển trạng thái, khi trả lỗi API, hoặc khi đối chiếu code với SRS.
---

# Nghiệp vụ — tra nhanh

Bản đầy đủ: `docs/01-nghiep-vu.md`. Lý do các quyết định: `docs/00-yeu-cau.md`. SRS gốc: `SRS_2_Ride_Hailing_Logistics.docx`.

## Những thứ hay sai nhất

| Quy tắc | Hay sai thế nào |
|---|---|
| BR-002/003 Một tài xế ≤ 1 chuyến, một chuyến ≤ 1 tài xế | Kiểm bằng `if` rồi ghi → hai luồng cùng qua. Phải nguyên tử (skill `dispatch-matching`) |
| FR-DRV-013 Trả tài xế về AVAILABLE **đúng một lần** | Quên nhánh: offer hết hạn, khách hủy lúc đang offer, accept lỗi giữa chừng |
| FR-DRV-011 Không Offline khi OFFERED/BUSY | |
| BR-005 Quote thuộc đúng khách, còn hạn, dùng một lần | Quên đánh dấu `USED` → một quote tạo nhiều chuyến |
| BR-006 Surge > 1 phải xác nhận | Request tạo chuyến thiếu `surgeConfirmed=true` → 422 |
| FR-PRI-018 Cước cuối không đổi surge | Tính lại cước lúc hoàn tất với surge hiện tại — **sai**. Dùng snapshot |
| FR-TRIP-010 Lịch sử trạng thái | Chuyển trạng thái mà không thêm `TripStatusHistory` |
| FR-TRIP-012/013 PIN / OTP | So sánh chuỗi bằng `equals` thường → dùng `MessageDigest.isEqual` (chống timing) |
| FR-CAN-006 Hủy lặp | Hủy lần 2 thu phí lần 2 |
| FR-PAY-002 Số tiền | Lấy `amount` từ request |
| FR-WAL-005 Ghi ví idempotent | Consumer chạy lại → ghi có hai lần. Unique `(wallet_id, entry_type, reference_id)` |
| BR-010 Hoàn ≤ đã thu | Quên cộng dồn các lần hoàn trước |
| BR-013 Riêng tư | Offer gửi tài xế kèm tên/SĐT khách; trả SĐT người nhận cho tài xế chưa được gán |
| FR-IAM-006 | Thông báo "Email không tồn tại" — lộ tài khoản |

## Vai trò → quyền (tóm tắt)

`CUSTOMER` đặt/hủy/theo dõi chuyến của mình · `DRIVER` hồ sơ, Online, telemetry, offer, chuyến được gán, ví · `REVIEWER` duyệt hồ sơ · `SUPPORT_STAFF` tra cứu, hủy ngoại lệ · `FINANCE_STAFF` thanh toán, hoàn, điều chỉnh ví · `ADMIN` vai trò, bảng giá, hoa hồng, đình chỉ, audit, outbox.

## Chuyến — chuyển hợp lệ

```
CREATED -> MATCHING -> ACCEPTED -> PICKING_UP -> ARRIVED -> IN_TRIP -> COMPLETED
   \           |  \         \            \           \          \
 CANCELLED  NO_DRIVER  CANCELLED   CANCELLED   CANCELLED  CANCELLED  CANCELLED(chi SUPPORT)
```

## Mã lỗi

`VALIDATION_ERROR 400` · `AUTHENTICATION_REQUIRED 401` · `ACCESS_DENIED 403` · `RESOURCE_NOT_FOUND 404` · `INVALID_TRIP_STATE 409` · `DRIVER_ALREADY_ASSIGNED 409` · `OFFER_EXPIRED 409` · `IDEMPOTENCY_KEY_REUSED 409` · `CONFLICT 409` · `QUOTE_EXPIRED 422` · `BUSINESS_RULE_VIOLATION 422` · `RATE_LIMIT_EXCEEDED 429` · `DEPENDENCY_UNAVAILABLE 503`.

Ném `BusinessException(ErrorCode.X, "thông báo tiếng Việt")` — `GlobalExceptionHandler` lo phần còn lại. **Không** tự dựng `ResponseEntity` lỗi trong controller.

## Event tài chính (phải đi Outbox)

`TripCompleted → FareFinalized → (Payment) PaymentSucceeded → (Wallet) DriverEarningPosted`; `TripCancelled(fee > 0) → Payment CANCELLATION_FEE`.
