---
description: Soát phần lõi thời gian thực — chấp nhận nguyên tử, tài xế kẹt trạng thái, idempotency, state machine, Redis GEO, cước & surge
argument-hint: [tên file hoặc package, để trống thì soát trip/, location/, pricing/, platform/outbox]
---

# Soát phần lõi dispatch

Phạm vi: **$ARGUMENTS** (để trống thì soát `trip/`, `trip/dispatch/`, `location/`, `pricing/`, `platform/outbox/` và test liên quan)

Gọi agent **`chuyen-gia-dispatch`** để thực hiện.

## Vì sao lệnh này quan trọng nhất

SRS §8.10: *"Không có double assignment, mất trạng thái chuyến, thu tiền trùng hoặc ghi ví trùng"* là điều kiện phát hành. Ba lỗi dưới đây **chạy tay một người không bao giờ thấy**, chỉ lộ ra dưới đồng thời:

1. **Đọc rồi ghi** trạng thái tài xế/offer → hai chuyến cùng giữ một tài xế
2. **Một nhánh quên trả tài xế** về AVAILABLE → tài xế "biến mất" khỏi hệ thống
3. **Consumer chạy lại** ghi ví lần hai

## Sau khi có báo cáo

- Lỗi mức 1 → **sửa ngay** trước khi làm việc khác, viết test chặn tái phát
- Sạch → `mvnw.cmd test`; có DB + Redis thì `mvnw.cmd verify -Pintegration`
