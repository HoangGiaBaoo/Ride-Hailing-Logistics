---
description: Soát code vừa viết theo quy tắc riêng của dự án — tiền BigDecimal, kiểm quyền & sở hữu, ranh giới miền, bí mật, audit, phân trang
argument-hint: [tên file, để trống thì soát các file vừa thay đổi]
---

# Soát code

Phạm vi: **$ARGUMENTS** (để trống thì lấy các file thay đổi qua `git diff`)

Gọi agent **`nguoi-soat-code`** để thực hiện.

## Lưu ý

- Phong cách code là bình thường — Lombok, Stream, record đều được phép
- Không gợi ý thêm tính năng, không trừu tượng hóa "cho sau này" — phạm vi chốt ở `docs/04-chuc-nang.md`
- Có file trong `trip/`, `location/`, `pricing/`, `platform/outbox` → phần đồng thời để `/soat-dispatch` lo

## Sau khi có báo cáo

- **Vi phạm cứng** → sửa ngay
- **Cần sửa nhưng không chặn** → sửa nếu kịp, không thì ghi `docs/TIEN-DO.md` mục nợ kỹ thuật
- Kết luận "chưa đạt" → **đừng commit**
