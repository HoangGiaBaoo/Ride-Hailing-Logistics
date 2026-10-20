---
description: Đối chiếu code với SRS, quy tắc nghiệp vụ và tiêu chí hoàn thành trong docs
argument-hint: [mã chức năng CN-xx-xx hoặc mã SRS FR-xxx; để trống thì soát phần vừa làm]
---

# Soát nghiệp vụ

Phạm vi: **$ARGUMENTS** (để trống thì soát chức năng vừa làm trong phiên này)

Gọi agent **`soat-nghiep-vu`** để thực hiện.

## Lệnh này trả lời

*"Code chạy rồi, nhưng đã đủ so với SRS chưa?"* — chức năng bấm vào chạy ngon nhưng thiếu một bước ngầm mà SRS đã quy định.

## Hay thiếu nhất

| Hay thiếu | Mã |
|---|---|
| Chuyển trạng thái không ghi lịch sử | FR-TRIP-010 |
| Hủy khi đang MATCHING mà không hủy offer, không trả tài xế | FR-CAN-005, FR-DRV-013 |
| Ẩn nút ở trang demo nhưng API vẫn cho gọi | FR-IAM-010 |
| Offer gửi tài xế kèm PII khách | FR-MAT-008, BR-013 |
| Tạo chuyến không đánh dấu quote đã dùng | BR-005 |
| Cước cuối tính lại surge | FR-PRI-018 |
| Thao tác nhân viên không có audit | BR-014 |
| Lỗi tự chế thay vì đúng mã lỗi Phụ lục B | COM-003 |

Mục "chưa đạt" thì sửa ngay; "không tìm thấy code" thì kiểm lại: chưa làm hay đặt chỗ khác?
