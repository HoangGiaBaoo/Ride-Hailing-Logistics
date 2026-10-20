---
description: Chạy biên dịch và test, quét vi phạm quy tắc, kiểm hạ tầng local, báo cáo gọn kèm gợi ý sửa
argument-hint: [nhanh | day-du], mặc định day-du
allowed-tools: Bash, Read, Grep, Glob
---

# Kiểm tra hệ thống

Chế độ: **$ARGUMENTS** (để trống thì chạy đầy đủ)

## 1. Biên dịch
```
./mvnw -q compile
```
Lỗi → dừng, báo cáo, gọi agent `sua-loi-build` nếu không rõ nguyên nhân.

## 2. Unit test (bỏ qua nếu `nhanh`)
```
./mvnw test
```
Báo số test chạy / đỏ. Đỏ → test nào, lý do.

## 3. Test bắt buộc
Đối chiếu danh sách trong `.claude/rules/kiem-thu.md`, liệt kê lớp test còn thiếu.

## 4. Hạ tầng & integration (chỉ `day-du`)
- `docker ps --filter name=rhl-redis` — Redis chạy?
- PostgreSQL service `postgresql-x64-18` chạy?
- Có cả hai → `./mvnw verify -Pintegration`

## 5. Quét vi phạm
Trong `src/main/java`:
- `float` ở bất kỳ đâu; `double` cho tên biến kiểu tiền
- `.setStatus(TripStatus.` ngoài `Trip.java`
- import repository miền khác
- `System.out.print`
- bí mật ghi cứng; `src/main/resources/application.properties` có mật khẩu không phải `${...}`

## 6. Git
`git status --short` — file chưa commit; cảnh báo nếu `config/application.properties` hoặc `storage/` sắp bị commit.

## Báo cáo

```
KET QUA: DAT / CHUA DAT

Bien dich:       OK
Unit test:       42/42 xanh
Test bat buoc:   9/10 (thieu SandboxSignatureTest)
Integration:     4/4 xanh  (hoac: bo qua — Redis khong chay)
Quy tac:         OK
Chua commit:     3 file
```

Mỗi vấn đề 2–3 dòng kèm cách sửa.
