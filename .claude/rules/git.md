# Quy tắc Git — luôn áp dụng

## Nguyên tắc

1. **Commit sau mỗi thay đổi hoàn chỉnh, chạy được.** Không dồn nhiều đợt vào một commit.
2. **Không commit khi chưa biên dịch được.** Chạy `mvnw.cmd compile`; đụng logic lõi (`trip`, `pricing`, `payment`, `wallet`, `location`) thì chạy `mvnw.cmd test`.
3. Claude **chỉ commit khi được yêu cầu**.
4. Không commit: `target/`, `.idea/`, `config/application.properties` (bí mật local), `storage/` (tài liệu tài xế), file `.docx` nháp nếu không được yêu cầu.

## Định dạng — Conventional Commits

```
<type>(<scope>): <subject> (<ma yeu cau>)
```

Subject tiếng Anh, thể mệnh lệnh, chữ thường sau dấu hai chấm, không chấm cuối, ≤ 72 ký tự. Mã yêu cầu SRS hoặc mã chức năng ở cuối để `git log --oneline` truy vết được.

### Type

`feat` · `fix` · `refactor` · `test` · `docs` · `perf` · `build` · `chore`

### Scope

Trùng tên package miền: `identity` · `driver` · `location` · `pricing` · `trip` · `dispatch` · `payment` · `wallet` · `realtime` · `platform` · `common` · `demo` · `db`

### Ví dụ

```
feat(dispatch): accept offer atomically with trip and driver checks (FR-MAT-010)
feat(location): reject out-of-order telemetry by sequence (FR-LOC-006)
fix(wallet): make commission posting idempotent per trip (FR-WAL-005)
test(dispatch): add 100-thread concurrent accept test (UC-06)
build(db): add partial unique index on active trips per driver (NFR-REL-003)
```

### Thân commit

Chỉ viết khi lý do không nhìn ra từ diff — trả lời "vì sao", tiếng Anh.

## Nhánh

Nhánh chính `main` (máy đang ở `master` — đổi tên trước khi đẩy lên remote). Làm thẳng trên nhánh chính là đủ; tạo nhánh khi thử hướng có thể bỏ.
