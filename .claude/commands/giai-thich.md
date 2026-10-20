---
description: Giảng lại code bằng tiếng Việt như dạy người chưa biết, kèm ví dụ cụ thể chạy tay được
argument-hint: <tên file, tên hàm, hoặc "phần vừa viết">
---

# Giải thích code bằng tiếng Việt

Đối tượng: **$ARGUMENTS** (không có thì giải thích phần vừa viết trong phiên)

Người đọc phải tự bảo vệ code trước giảng viên. Giảng như dạy người mới học Java; dùng từ tiếng Việt khi có ("giao dịch" thay "transaction" lần đầu nhắc, sau đó dùng cả hai).

## Cấu trúc

### 1. Phần này để làm gì
Một câu theo nghĩa nghiệp vụ.

> Sai: "Phương thức này acquire pessimistic lock rồi update aggregate."
> Đúng: "Khi tài xế bấm Nhận chuyến, hàm này bảo đảm chỉ một tài xế được nhận, kể cả khi nhiều người bấm cùng lúc."

### 2. Đầu vào, đầu ra
Theo nghĩa nghiệp vụ: "nhận mã offer và tài xế đang đăng nhập, trả về chuyến đã ghép".

### 3. Đi qua từng bước với một ví dụ cụ thể
Bám **một tình huống có số và mốc thời gian**: "08:30:00 khách đặt chuyến T1; 08:30:01 offer gửi tài xế A (hết hạn 08:30:16); 08:30:05 A và B cùng bấm…". Chạy tay xem code làm gì ở từng dòng.

Với code đồng thời: **vẽ hai cột Luồng 1 / Luồng 2** theo thời gian, chỉ ra dòng nào luồng 2 phải chờ.

### 4. Vì sao viết thế này
2–3 quyết định: vì sao khóa ở đây, vì sao `BigDecimal`, vì sao outbox thay vì gọi thẳng, vì sao trả 404 thay vì 403.

### 5. Nếu bị chỉ vào dòng này
2–3 dòng dễ bị hỏi nhất kèm câu trả lời mẫu ngắn, tự tin.

## Kết thúc

Hỏi: *"Chỗ nào chưa rõ thì nói, tôi giảng lại kỹ hơn."* Phát hiện đoạn khó giải thích → nói thẳng nên viết lại, kèm đề xuất.
