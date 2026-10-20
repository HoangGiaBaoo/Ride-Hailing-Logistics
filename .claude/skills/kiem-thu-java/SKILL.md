---
name: kiem-thu-java
description: Cách viết test cho dự án đặt xe bằng JUnit 5 — unit test thuần cho state machine, cước, surge, phí hủy, hoa hồng, xếp hạng ứng viên, telemetry; integration test đồng thời (100 luồng chấp nhận một offer) trên PostgreSQL + Redis local; dữ liệu cứng tính tay được. Nạp skill này khi viết test, khi sửa lỗi cần test chặn tái phát, hoặc khi cần trả lời "kiểm thử thế nào".
---

# Kiểm thử

Danh sách test bắt buộc: `.claude/rules/kiem-thu.md`.

## Unit test — dữ liệu cứng, tính tay

```java
class FareCalculatorTest {

    // Bang gia mau: mo cua 12.000, 5.000/km, 500/phut, toi thieu 20.000, phi dat 2.000
    private final PricingRuleSnapshot rule = new PricingRuleSnapshot(
            bd(12_000), bd(5_000), bd(500), bd(20_000), bd(2_000), "rule-1", 1);

    @Test
    void fareFollowsHandCalculation() {
        // 4.200 m, 840 s (14 phut), surge 1.0
        //   12.000 + 4,2 x 5.000 + 14 x 500 = 12.000 + 21.000 + 7.000 = 40.000
        //   max(40.000, 20.000) + 2.000      = 42.000  (da tron 1.000)
        FareBreakdown f = FareCalculator.calculate(rule, 4_200, 840, bd("1.0"));
        assertEquals(0, bd(42_000).compareTo(f.total()));
    }
}
```

So `BigDecimal` bằng `compareTo` hoặc `assertThat(x).isEqualByComparingTo(...)`.

## Thời gian cố định

```java
Clock clock = Clock.fixed(Instant.parse("2026-10-05T08:30:00Z"), ZoneOffset.UTC);
```

## Integration test đồng thời

```java
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentAcceptIT {

    @Test
    void onlyOneOfHundredConcurrentAcceptsWins() throws Exception {
        // chuan bi: 1 trip MATCHING, 1 offer PENDING cho driver D (driver D dang OFFERED)
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(), conflict = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                start.await();                         // tha tat ca cung luc
                try { dispatchService.accept(offerId, driverId); ok.incrementAndGet(); }
                catch (BusinessException e) { conflict.incrementAndGet(); }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, SECONDS);
        // Lap lai request da thang tra cung ket qua (FR-MAT-012) nen co the nhieu "ok" cung mot ket qua;
        // dieu bat bien la: DB chi co 1 offer ACCEPTED, trip co dung 1 driver, driver BUSY.
    }
}
```

Profile `test` trỏ tới database `ride_hailing_test` (Flyway `clean` + `migrate` trước mỗi lớp) và Redis DB số 1.

## Không làm

- Không test getter/setter, không test CRUD đơn giản.
- Không mock `Clock`/`GeoUtils`/`FareCalculator` — chạy thật.
- Không sinh ngẫu nhiên rồi so với chính code.

## Dấu hiệu test nói dối

- Test đồng thời xanh nhưng `threads` thực chất chạy tuần tự (quên `CountDownLatch`) → không chứng minh gì.
- Test idempotency chỉ gửi 1 lần.
- Test vị trí cũ/mới nhưng sequence bằng nhau.
