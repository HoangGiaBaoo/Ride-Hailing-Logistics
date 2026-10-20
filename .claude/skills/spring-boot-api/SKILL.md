---
name: spring-boot-api
description: Mẫu code Spring Boot 4 cho dự án đặt xe — entity JPA trên PostgreSQL, migration Flyway, repository có cập nhật điều kiện và khóa, service theo miền, REST controller dưới /api/v1 với ApiResponse/BusinessException, Spring Security JWT resource server, Redis (GEO, Lua, counter), Outbox, Idempotency-Key, STOMP WebSocket. Nạp skill này khi tạo entity, viết migration, repository, service, controller, cấu hình bảo mật, hoặc khi ứng dụng khởi động lỗi.
---

# Mẫu code Spring Boot cho RHL

Package gốc: `org.example.ride_hailing_logistics`. Kiến trúc & bảng: `docs/02-kien-truc.md`.

## Cấu hình

- `src/main/resources/application.properties` — mọi bí mật dạng `${ENV}` **không giá trị mặc định** (hook chặn).
- `config/application.properties` ở gốc dự án (gitignored) — Spring Boot tự nạp khi chạy từ thư mục gốc. Chứa `DB_PASSWORD`, `JWT_SECRET`… cho máy local.
- `spring.jpa.hibernate.ddl-auto=validate` — **schema chỉ do Flyway sinh**. Thêm cột = thêm file `V<n>__mo_ta.sql`, không sửa file migration đã chạy.
- `spring.jpa.open-in-view=false`.

## Migration Flyway

`src/main/resources/db/migration/V1__init.sql`… Quy ước:
- `id uuid primary key`, tiền `numeric(15,2) not null` + `currency varchar(3) not null default 'VND'`, thời gian `timestamptz`.
- Enum lưu `varchar(30)` + `check (status in (...))`.
- Bảo đảm nghiệp vụ bằng **partial unique index** khi có thể.
- Bảng bất biến: trigger `prevent_mutation()` chặn UPDATE/DELETE.

## Entity

```java
@Entity
@Table(name = "trips")
@Getter
@Setter(AccessLevel.PROTECTED)          // khong cho ben ngoai set tuy y, di qua method nghiep vu
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Trip {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TripStatus status;

    @Column(name = "quoted_total", nullable = false, precision = 15, scale = 2)
    private BigDecimal quotedTotal;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;
}
```

- `@Getter @Setter`, không `@Data`. ID sinh trong code (`UUID.randomUUID()`) trong factory method.
- Không `@ManyToOne` xuyên miền — lưu `UUID customerId`.
- Quan hệ trong cùng miền: `@OneToMany(mappedBy=…, cascade=ALL)` cho lịch sử trạng thái là được.

## Repository

```java
public interface TripRepository extends JpaRepository<Trip, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> findByIdForUpdate(@Param("id") UUID id);
}
```

Cập nhật có điều kiện: `@Modifying(clearAutomatically = true) @Query("update … where … and status = :expected")` trả `int`.

## Service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class TripService {
    private final TripRepository tripRepository;      // cung mien
    private final QuoteService quoteService;          // mien pricing qua service cong khai
    private final OutboxService outbox;
    private final Clock clock;

    @Transactional
    public TripResponse create(UUID customerId, CreateTripRequest req) { ... }
}
```

- `@Transactional` ở service. Đọc thuần: `@Transactional(readOnly = true)`.
- Lỗi nghiệp vụ: `throw new BusinessException(ErrorCode.INVALID_TRIP_STATE, "…")`.
- Không có quyền sở hữu → `BusinessException(RESOURCE_NOT_FOUND)`.

## Controller

```java
@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {
    private final TripService tripService;
    private final IdempotencyService idempotency;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<TripResponse> create(@RequestHeader("Idempotency-Key") String key,
                                            @Valid @RequestBody CreateTripRequest req) {
        UUID me = CurrentUser.id();
        return ApiResponse.ok(idempotency.execute(me, "trip.create", key, req, TripResponse.class,
                () -> tripService.create(me, req)));
    }
}
```

Controller chỉ: lấy danh tính (`CurrentUser`), validate (`@Valid`), gọi service, bọc `ApiResponse.ok(...)`.

## Security (resource server JWT)

- `SecurityConfig`: stateless, CSRF tắt cho `/api/**` (bearer token), `permitAll` cho `/api/v1/auth/**`, `/api/v1/payments/sandbox/callback`, `/ws/**`, `/v3/api-docs/**`, `/swagger-ui/**`, trang tĩnh.
- `JwtService` dùng `NimbusJwtEncoder` + `ImmutableSecret` (HS256), claim `roles`. `JwtAuthenticationConverter` thêm tiền tố `ROLE_`.
- `@EnableMethodSecurity` để `@PreAuthorize` chạy.
- Entry point 401 / access denied 403 trả JSON `{code, message, correlationId}`.

## Redis

- `StringRedisTemplate` cho mọi thứ (khóa/giá trị là chuỗi, dễ xem bằng `redis-cli`).
- Thao tác cần nguyên tử (so sequence rồi ghi) → `DefaultRedisScript` Lua.
- Redis lỗi → `DEPENDENCY_UNAVAILABLE 503`, không 500.

## Outbox

```java
outbox.append("Trip", trip.getId(), "TripCompleted", 1, payloadRecord);   // cung transaction
```

Handler: `implements EventHandler` với `eventType()` và `handle(OutboxEvent)`; relay tự đảm bảo idempotent theo `(eventId, handlerName)`.

## Lỗi khởi động hay gặp (Boot 4)

| Triệu chứng | Nguyên nhân |
|---|---|
| `Schema-validation: missing column` | Entity có trường mà migration chưa tạo, hoặc tên cột lệch (`@Column(name=…)`) |
| `Schema-validation: wrong column type … found [numeric], expecting [float(53)]` | Trường `Double` ánh xạ vào cột `numeric` — đổi cột thành `double precision` |
| `ClassNotFoundException: com.fasterxml.jackson.databind.ObjectMapper` | Boot 4 dùng Jackson 3: `tools.jackson.databind.ObjectMapper` |
| Flyway không chạy | Boot 4 cần `spring-boot-starter-flyway` (không chỉ `flyway-core`) + `flyway-database-postgresql` |
| `cannot find symbol: getXxx()` | Lombok không chạy — kiểm `annotationProcessorPaths` |
