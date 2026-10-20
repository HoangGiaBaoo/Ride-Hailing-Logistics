---
description: Đối chiếu code hiện có với các đợt trong docs/04-chuc-nang.md, báo còn thiếu gì và nên cắt gì nếu chậm
allowed-tools: Read, Grep, Glob, Bash
---

# Kiểm tiến độ

## 1. Quét code

- Migration trong `db/migration/` có đủ bảng ở `docs/02-kien-truc.md` §3?
- Mỗi miền (`identity`, `driver`, `location`, `pricing`, `trip`, `trip/dispatch`, `payment`, `wallet`, `realtime`, `platform`) có entity/service/controller nào?
- Endpoint thực tế so với bảng `docs/02-kien-truc.md` §5
- Event `outbox.append` / `EventHandler` so với §4
- Lớp test so với `.claude/rules/kiem-thu.md`
- Trang demo trong `static/`, script `tools/`

## 2. Đối chiếu `docs/04-chuc-nang.md`

Từng mã CN: xong / dở / chưa làm. Đếm ★ đã xong.

## 3. Đọc `docs/TIEN-DO.md`

## Báo cáo

```
## Tình hình
Đợt 1 Nền tảng:     6/6
Đợt 5 Chuyến:       5/9 (thiếu CN-05-07, CN-05-08 ...)
...
Mục ★ xong: X/Y

## Đang dở
...

## Việc tiếp theo (đúng thứ tự)
1. ...
```

## Nếu chậm

**Nói thẳng.** Cắt theo thứ tự, an toàn nhất trước:

1. Mục ○
2. Trang `ops.html` (giữ API, demo bằng Swagger)
3. OSRM (giữ Haversine)
4. Đặt lại mật khẩu, upload tệp tài liệu (giữ metadata)

**Không bao giờ cắt:** chấp nhận nguyên tử (CN-05-04), state machine (CN-05-06), idempotency + outbox (CN-01-03, CN-01-05), kiểm quyền vị trí (CN-06-01), ví idempotent (CN-07-03).

Cuối cùng đề nghị cập nhật `docs/TIEN-DO.md`.
