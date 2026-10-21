# Ride Hailing & Logistics — hướng dẫn cho Claude Code

Hệ thống đặt xe và giao hàng theo yêu cầu thời gian thực, theo `SRS_2_Ride_Hailing_Logistics.docx` (SRS-RHL-002).

Microservices Maven đa module: `common-lib`, `eureka-server`, `config-server`, `api-gateway`, `identity-service`, `driver-service`, `pricing-service`, `trip-service`, `payment-service` — sơ đồ ở `docs/02-kien-truc.md` §2.

## Đọc trước khi làm

- `docs/00-yeu-cau.md` — cách hiểu SRS, quyết định kiến trúc, chốt các TBD
- `docs/01-nghiep-vu.md` — vai trò, state machine, phí hủy, thanh toán, ví, mã lỗi
- `docs/02-kien-truc.md` — module/service, gọi nội bộ, saga giữ tài xế, bảng, event, API, WebSocket, cấu hình
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
mvnw.cmd package -DskipTests
powershell -File tools/start-all.ps1          # eureka -> config -> 5 service -> gateway :8080, log o logs/
node tools/seed-dev.mjs                       # du lieu mau qua API gateway (chay lai an toan)
node tools/simulate-drivers.mjs --drivers 100 --interval 3000 --rides
powershell -File tools/start-all.ps1 -Stop
./mvnw test                     # unit test
./mvnw verify -Pintegration     # integration test (can Postgres + Redis), chi chay test tag integration
```

Bí mật local đặt ở `config/application.properties` (gitignored, mẫu ở `config/application.properties.example`).
