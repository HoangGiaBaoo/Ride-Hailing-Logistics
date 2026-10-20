# Bộ cấu hình Claude Code cho hệ thống đặt xe & giao hàng (SRS-RHL-002)

Chuyển thể từ bộ cấu hình của đồ án MPRoute (CVRP, Thymeleaf, MySQL): **giữ cấu trúc và cách làm việc** (luật cứng, hook bắt lỗi đúng/sai, agent soát chuyên trách, lệnh tiếng Việt, giảng lại code để bảo vệ), **thay toàn bộ nội dung** cho bài toán dispatch thời gian thực trên Spring Boot 4 + PostgreSQL + Redis.

## Có gì

```
.claude/
├── settings.json        Hook + quyen chay lenh
├── rules/               Nap vao moi phien qua CLAUDE.md
│   ├── quy-tac-code.md      5 dieu cam, 4 loi kinh dien (double assignment, trung lap, vi tri cu, lo PII)
│   ├── bao-mat.md           NFR-SEC / NFR-PRV
│   ├── kiem-thu.md          10 unit test + 4 integration test bat buoc
│   └── git.md
├── skills/              Claude tu nap khi gap viec lien quan
│   ├── dispatch-matching/   <- quan trong nhat: matching, chap nhan nguyen tu, GEO, cuoc, surge
│   ├── nghiep-vu-rhl/       quy tac nghiep vu tra nhanh
│   ├── spring-boot-api/     mau entity / Flyway / repository / service / controller / security / Redis / outbox
│   ├── giao-dien-demo/      trang HTML demo + script mo phong tai xe
│   ├── quy-tac-code-java/
│   ├── kiem-thu-java/
│   └── chuan-bi-van-dap/
├── agents/
│   ├── chuyen-gia-dispatch.md   (thay chuyen-gia-thuat-toan)
│   ├── nguoi-soat-code.md
│   ├── soat-nghiep-vu.md
│   ├── nguoi-lap-ke-hoach.md
│   ├── sua-loi-build.md         (loi Boot 4 / Flyway / Postgres / Redis)
│   └── cap-nhat-tai-lieu.md
├── commands/
│   ├── soat-dispatch.md         (thay soat-thuat-toan)
│   ├── giai-thich.md  ke-hoach.md  soat-code.md  soat-nghiep-vu.md
│   ├── kiem-tra.md    tien-do.md   van-dap.md    hoc.md
└── hooks/
    ├── kiem-tra-quy-tac.js
    └── khoi-dong-phien.js
```

## Lệnh gõ nhanh

| Lệnh | Khi nào |
|---|---|
| `/giai-thich <file>` | Được giảng lại code bằng tiếng Việt, có ví dụ thời gian/hai luồng |
| `/ke-hoach <việc>` | Trước một chức năng lớn |
| `/soat-dispatch` | **Sau mỗi lần sửa `trip/`, `location/`, `pricing/`, outbox** |
| `/soat-code` | Trước khi commit |
| `/soat-nghiep-vu` | Kiểm đã đủ so với SRS chưa |
| `/kiem-tra` | Biên dịch + test + quét quy tắc + kiểm hạ tầng |
| `/tien-do` | Xem còn thiếu gì so với `docs/04-chuc-nang.md` |
| `/van-dap` | Luyện trả lời người chấm |
| `/hoc` | Cuối phiên, ghi lại chỗ vừa vấp |

## Hook

| Hook | Khi nào | Làm gì |
|---|---|---|
| `kiem-tra-quy-tac.js` | Sau mỗi lần ghi/sửa file | Bắt: `float`; `double` cho biến tiền; `setStatus(TripStatus.…)` ngoài `Trip.java`; import repository miền khác; `EnumType.ORDINAL`; bí mật ghi cứng; mật khẩu/secret không phải `${ENV}` trong `application*.properties`; `@Data`/`LocalDateTime` trên entity; `@Query` nối chuỗi; `System.out`; `innerHTML` với dữ liệu server. **Không soi phong cách** |
| `khoi-dong-phien.js` | Đầu mỗi phiên | Nhắc bối cảnh, 10 luật vàng, đọc `docs/TIEN-DO.md` |

## Đã bỏ / thay so với bản MPRoute

| Bỏ | Lý do |
|---|---|
| `thuat-toan-cvrp`, `chuyen-gia-thuat-toan`, `soat-thuat-toan` | Không có bài toán CVRP. Thay bằng `dispatch-matching` / `chuyen-gia-dispatch` / `soat-dispatch` — lõi của đề tài này là ghép chuyến đồng thời |
| `giao-dien-thymeleaf` | Sản phẩm là API; thay bằng `giao-dien-demo` (HTML tĩnh + Leaflet + STOMP) |
| `nghiep-vu-mproute` (CBR/BR/MSG) | Thay bằng `nghiep-vu-rhl` theo mã FR/BR/NFR của SRS |
| Kiểm cấu hình MySQL (`useUnicode`, timezone) | Dùng PostgreSQL, thời gian UTC `timestamptz` |
| Kế hoạch 12 tuần, mốc tuần 6–7 | Thay bằng 8 đợt trong `docs/04-chuc-nang.md` |
| Quyền đọc thư mục `DATN_DTNA_ToiUuLogistic` | Thuộc dự án cũ |
