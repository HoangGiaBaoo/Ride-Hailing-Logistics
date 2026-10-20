# Quy tắc kiểm thử — luôn áp dụng

NFR-MNT-003: **state machine, matching, pricing, payment, wallet phải có unit test tự động.** Phụ lục C liệt kê thêm concurrency test. Không đặt mục tiêu phủ % code — đặt mục tiêu **chứng minh được các bảo đảm cứng**.

## Unit test bắt buộc (JUnit 5 thuần, không Spring, không DB)

| Lớp test | Kiểm |
|---|---|
| `TripStateMachineTest` | Mọi cặp chuyển hợp lệ ở SRS §7.1 đi qua; nhảy cóc (ACCEPTED→IN_TRIP), đi lùi, ra khỏi trạng thái kết thúc bị từ chối; sai actor bị từ chối (IN_TRIP→CANCELLED chỉ hỗ trợ) |
| `FareCalculatorTest` | Số tính tay: mở cửa + km + phút, cước tối thiểu, surge, làm tròn lên 1.000; cùng đầu vào → cùng kết quả (FR-PRI-011) |
| `SurgeCalculatorTest` | Cung 0, cầu 0, trần 2.5, sàn 1.0, làm tròn 0.1, không âm |
| `CancellationPolicyTest` | Từng dòng bảng phí hủy `01-nghiep-vu.md` §5, biên 2 phút và 5 phút |
| `CommissionCalculatorTest` | 20% của 87.000 = 17.400; làm tròn HALF_UP; ròng = tổng − hoa hồng |
| `CandidateRankerTest` | Gần hơn đứng trước; hòa → vị trí mới hơn; loại tài xế đã được mời cho chuyến này (lọc độ mới/accuracy nằm ở `LocationService`) |
| `TelemetryValidatorTest` | lat/lng biên ±90/±180, tương lai > 30 s, cũ > 5 phút, accuracy > 500 m, nhảy > 70 m/s bị đánh dấu |
| `GeoUtilsTest` | Haversine giữa hai điểm tính tay; geohash của tọa độ đã biết |
| `NormalizersTest` | `0901234567`, `+84 901 234 567`, `84901234567` → cùng một dạng; email, biển số, che SĐT |
| `SandboxSignatureTest` | Chữ ký đúng qua, sửa 1 byte thì trượt, timestamp quá hạn thì trượt |

## Integration test (tag `integration`, cần PostgreSQL + Redis local)

| Test | Kiểm |
|---|---|
| `ConcurrentAcceptIT` | UC-06 kịch bản A: 100 luồng cùng chấp nhận một offer → mọi phản hồi cùng một kết quả (FR-MAT-012), DB có đúng 1 offer ACCEPTED, 1 dòng lịch sử ACCEPTED, 1 event `TripAccepted`; trip ACCEPTED, driver BUSY. Tài xế khác chấp nhận offer không phải của mình → 404 |
| `DriverContentionIT` | UC-06 kịch bản B: 50 luồng `reserve` cùng tài xế → đúng 1 thắng; 8 chuyến tranh 1 tài xế → tối đa 1 offer PENDING, tài xế chỉ được gán vào 1 chuyến |
| `IdempotentTripCreationIT` | 10 luồng cùng `Idempotency-Key` → 1 trip (luồng đến lúc đang xử lý nhận `CONFLICT`); khác payload → `IDEMPOTENCY_KEY_REUSED`; quote của người khác → 404 |
| `TripLifecycleIT` | Luồng UC-04 đầy đủ tới ví tài xế; event không xử lý hai lần; khách hủy lúc MATCHING trả tài xế về AVAILABLE |
| `RideHailingLogisticsApplicationTests` | Ứng dụng khởi động, Flyway + `ddl-auto=validate` khớp |

## Cách viết

- **Dữ liệu cứng, tính tay được, ghi phép tính trong comment.** Ví dụ: `// 12.000 + 4,2 km x 5.000 + 14 phut x 500 = 40.000`.
- Test logic thuần không dựng Spring context. Các lớp lõi (`TripStateMachine`, `FareCalculator`, `CancellationPolicy`, `CandidateRanker`, `TelemetryValidator`…) **phải viết thuần** để test được như vậy — tiêm `Clock` khi cần thời gian.
- Không mock thứ chạy thật được. Chỉ mock/giả lập phụ thuộc ngoài (bản đồ, cổng thanh toán).
- Test đồng thời dùng `ExecutorService` + `CountDownLatch` để thả các luồng cùng lúc.

## Lệnh chạy

```
mvnw.cmd test                                   # unit test (bo qua integration)
mvnw.cmd test -Dtest=FareCalculatorTest         # mot lop
mvnw.cmd verify -Pintegration                   # ca integration test (can Postgres + Redis)
```
