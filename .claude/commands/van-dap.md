---
description: Luyện trả lời câu hỏi của giảng viên/hội đồng về code vừa viết — đóng vai người chấm hỏi khó
argument-hint: [chủ đề, ví dụ "double assignment" hoặc "outbox"; để trống thì hỏi về phần vừa làm]
---

# Luyện vấn đáp

Chủ đề: **$ARGUMENTS** (để trống thì hỏi về phần code vừa viết)

Nạp skill `chuan-bi-van-dap`.

## Cách chạy

Đóng vai **người chấm**: đọc code thật, chỉ vào dòng cụ thể và hỏi. **Từng câu một**, chờ trả lời rồi mới hỏi tiếp.

### Vòng 1 — cơ bản (2–3 câu)
"Hàm này làm gì?", "Biến `availability` chứa gì?"

### Vòng 2 — vì sao (2–3 câu)
"Sao khóa trip mà không khóa driver?", "Sao dùng `CEILING` mà không `HALF_UP`?"

### Vòng 3 — bẻ (2–3 câu)
- "Hai request chấp nhận đến cách nhau 1 ms — đi qua từng dòng cho tôi xem"
- "Redis chết giữa chuyến thì sao?"
- "Relay outbox chạy trên hai instance thì event có bị xử lý hai lần?"
- "Tôi sửa `sequence` thành số nhỏ hơn thì vị trí có lùi không?"
- "Chứng minh khách B không xem được chuyến của khách A"

## Sau mỗi câu

Chấm **Đạt / Lấn cấn / Chưa được**, nói thẳng; Lấn cấn → viết lại câu trả lời gọn; Chưa được → giảng lại rồi hỏi câu tương tự.

## Cuối buổi

```
Tra loi tot:     ...
Can hoc lai:     ... (kem cho doc trong docs/)
Cau tra loi mau: ...
```

Đoạn code không giải thích nổi → nói thẳng nên viết lại cho đơn giản.
