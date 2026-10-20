# Ride Hailing & Logistics — hướng dẫn cho Claude Code

Hệ thống đặt xe và giao hàng theo yêu cầu thời gian thực, theo `SRS_2_Ride_Hailing_Logistics.docx` (SRS-RHL-002).

## Đọc trước khi làm

- `docs/00-yeu-cau.md` — cách hiểu SRS, quyết định kiến trúc, chốt các TBD
- `docs/01-nghiep-vu.md` — vai trò, state machine, phí hủy, thanh toán, ví, mã lỗi
- `docs/02-kien-truc.md` — package, bảng, event, API, WebSocket, cấu hình
- `docs/04-chuc-nang.md` — các đợt chức năng và tiêu chí hoàn thành
- `docs/TIEN-DO.md` — đang ở đâu, việc tiếp theo

## Quy tắc luôn áp dụng

@.claude/rules/quy-tac-code.md
@.claude/rules/bao-mat.md
@.claude/rules/kiem-thu.md
@.claude/rules/git.md

## Chạy

```
# ha tang: PostgreSQL 18 (service Windows) + Redis (docker start rhl-redis)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev     # Git Bash
mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev   # PowerShell
./mvnw test                     # unit test
./mvnw verify -Pintegration     # integration test (can Postgres + Redis)
node tools/simulate-drivers.mjs --drivers 100 --interval 3000
```

Bí mật local đặt ở `config/application.properties` (gitignored, mẫu ở `config/application.properties.example`).
