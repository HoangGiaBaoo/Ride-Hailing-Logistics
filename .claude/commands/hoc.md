---
description: Rút bài học từ phiên làm việc, ghi lại chỗ đã vấp để lần sau không vấp nữa
---

# Rút bài học từ phiên này

## Đáng ghi

1. **Lỗi mất thời gian** — triệu chứng, nguyên nhân gốc, cách sửa, dấu hiệu nhận biết
2. **Quyết định thiết kế** — chọn A thay B vì sao (chất liệu báo cáo)
3. **Chỗ SRS/TBD chưa rõ mà đã tự quyết** — phải ghi vào `docs/00-yeu-cau.md` để tài liệu và code khớp
4. **Câu hỏi vấn đáp** nảy ra trong lúc làm

## Không đáng ghi

Lỗi gõ nhầm, sự cố một lần, thứ đọc `docs/` là biết.

## Ghi vào đâu

| Loại | File |
|---|---|
| Tiến độ, nợ | `docs/TIEN-DO.md` |
| Đã làm gì, số đo | `docs/NHAT-KY.md` |
| Quyết định thiết kế, TBD | `docs/00-yeu-cau.md` hoặc `docs/02-kien-truc.md` |
| Câu hỏi vấn đáp | `.claude/skills/chuan-bi-van-dap/SKILL.md` |
| Lỗi build hay gặp | `.claude/agents/sua-loi-build.md` bảng "Lỗi hay gặp" |

**Không tạo file `.md` mới.**

## Quy trình

1. Liệt kê 1–3 thứ đáng ghi
2. Mỗi thứ: ghi vào file nào, thêm dòng gì
3. **Hỏi xác nhận trước khi sửa**
4. Sửa xong báo lại

Không có gì đáng ghi → nói "Phiên này không có gì đáng ghi lại".
