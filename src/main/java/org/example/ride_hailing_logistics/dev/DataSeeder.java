package org.example.ride_hailing_logistics.dev;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.domain.ServiceType;
import org.example.ride_hailing_logistics.common.domain.VehicleType;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.driver.DriverDtos.UpdateDriverProfileRequest;
import org.example.ride_hailing_logistics.driver.DriverDtos.VehicleRequest;
import org.example.ride_hailing_logistics.driver.DriverEnums.DocumentType;
import org.example.ride_hailing_logistics.driver.DriverEnums.ReviewDecisionType;
import org.example.ride_hailing_logistics.driver.DriverProfileService;
import org.example.ride_hailing_logistics.driver.DriverReviewService;
import org.example.ride_hailing_logistics.identity.AuthService;
import org.example.ride_hailing_logistics.identity.IdentityDtos.RegisterRequest;
import org.example.ride_hailing_logistics.identity.IdentityDtos.SelfRegistrationRole;
import org.example.ride_hailing_logistics.identity.User;
import org.example.ride_hailing_logistics.identity.UserRepository;
import org.example.ride_hailing_logistics.pricing.PricingRuleService;
import org.example.ride_hailing_logistics.pricing.QuoteDtos.PricingRuleRequest;
import org.example.ride_hailing_logistics.wallet.WalletService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Du lieu mau cho demo (CN-08-01), chi chay voi profile dev va khi DB chua co tai khoan nao.
 * Di qua dung service nghiep vu (dang ky -> khai ho so -> tai tai lieu -> gui duyet -> duyet),
 * nen cung la mot lan chay thu luong UC-01 tu dau den cuoi.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
class DataSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final DriverProfileService driverProfiles;
    private final DriverReviewService driverReview;
    private final PricingRuleService pricingRules;
    private final WalletService walletService;
    private final Clock clock;

    @Value("${app.dev.seed-enabled:false}")
    private boolean enabled;

    @Value("${app.dev.seed-password}")
    private String seedPassword;

    @Value("${app.dev.seed-driver-count:100}")
    private int driverCount;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || users.count() > 0) {
            return;
        }
        long started = System.currentTimeMillis();
        Instant now = clock.instant();
        User admin = staff("admin@rhl.dev", "0911000001", "Quản trị viên", Role.ADMIN, now);
        User reviewer = staff("reviewer@rhl.dev", "0911000002", "Kiểm duyệt viên", Role.REVIEWER, now);
        staff("support@rhl.dev", "0911000003", "Nhân viên hỗ trợ", Role.SUPPORT_STAFF, now);
        staff("finance@rhl.dev", "0911000004", "Nhân viên tài chính", Role.FINANCE_STAFF, now);
        Actor adminActor = new Actor(admin.getId(), Set.of(Role.ADMIN));
        Actor reviewerActor = new Actor(reviewer.getId(), Set.of(Role.REVIEWER));

        seedPricing(adminActor, now);
        walletService.createCommissionRule(ServiceType.RIDE, new BigDecimal("0.2000"), now, adminActor);
        walletService.createCommissionRule(ServiceType.DELIVERY, new BigDecimal("0.1500"), now, adminActor);

        register("customer@rhl.dev", "0922000001", "Khách Hàng Demo", SelfRegistrationRole.CUSTOMER);
        register("customer2@rhl.dev", "0922000002", "Khách Hàng Hai", SelfRegistrationRole.CUSTOMER);

        seedDriver("driver.demo@rhl.dev", "0933000000", "Tài Xế Demo", VehicleType.MOTORBIKE, "59X100000", reviewerActor, 0);
        for (int i = 1; i <= driverCount; i++) {
            String n = String.format("%03d", i);
            VehicleType type = i <= 60 ? VehicleType.MOTORBIKE : i <= 85 ? VehicleType.CAR_4 : i <= 95 ? VehicleType.CAR_7 : VehicleType.VAN;
            String plate = (type == VehicleType.MOTORBIKE ? "59X" : "51F") + String.format("%05d", i);
            seedDriver("driver" + n + "@rhl.dev", "093" + String.format("%07d", i), "Tài Xế " + n, type, plate, reviewerActor, i);
        }
        log.info("Dev seed done in {} ms: 4 staff, 2 customers, {} drivers. Demo password is app.dev.seed-password.",
                System.currentTimeMillis() - started, driverCount + 1);
    }

    private User staff(String email, String phone, String name, Role role, Instant now) {
        String normalizedPhone = "+84" + phone.substring(1);
        return users.save(User.register(email, normalizedPhone, passwordEncoder.encode(seedPassword), name, role, now));
    }

    private UUID register(String email, String phone, String name, SelfRegistrationRole role) {
        return authService.register(new RegisterRequest(email, phone, seedPassword, name, role)).id();
    }

    private void seedDriver(String email, String phone, String name, VehicleType type, String plate, Actor reviewer, int i) {
        UUID id = register(email, phone, name, SelfRegistrationRole.DRIVER);
        LocalDate expiry = LocalDate.now(clock).plusYears(3);
        driverProfiles.updateProfile(id, new UpdateDriverProfileRequest("79" + String.format("%010d", i), type == VehicleType.MOTORBIKE ? "A1" : "B2",
                expiry, "0790" + String.format("%08d", i), "Quận " + (1 + i % 12) + ", TP.HCM"));
        driverProfiles.upsertVehicle(id, new VehicleRequest(type, plate, type == VehicleType.MOTORBIKE ? "Honda" : "Toyota",
                type == VehicleType.MOTORBIKE ? "Wave" : type == VehicleType.VAN ? "Hiace" : "Vios", "Trắng", 2022));
        for (DocumentType doc : DocumentType.values()) {
            driverProfiles.uploadDocument(id, doc, doc.name().substring(0, 3) + String.format("%06d", i), expiry,
                    new SeedFile(doc.name().toLowerCase() + ".png"));
        }
        driverProfiles.submit(id);
        driverReview.decide(id, ReviewDecisionType.APPROVE, "Dữ liệu mẫu", reviewer);
    }

    private void seedPricing(Actor admin, Instant now) {
        rule(admin, now, ServiceType.RIDE, VehicleType.MOTORBIKE, 10_000, 4_500, 300, 15_000, 2_000);
        rule(admin, now, ServiceType.RIDE, VehicleType.CAR_4, 25_000, 10_000, 500, 35_000, 3_000);
        rule(admin, now, ServiceType.RIDE, VehicleType.CAR_7, 30_000, 12_500, 600, 45_000, 3_000);
        rule(admin, now, ServiceType.DELIVERY, VehicleType.MOTORBIKE, 12_000, 5_000, 300, 18_000, 2_000);
        rule(admin, now, ServiceType.DELIVERY, VehicleType.VAN, 60_000, 15_000, 800, 90_000, 5_000);
    }

    private void rule(Actor admin, Instant now, ServiceType service, VehicleType vehicle, long base, long perKm,
                      long perMinute, long minimum, long bookingFee) {
        pricingRules.create(new PricingRuleRequest(service, vehicle, "HCM", BigDecimal.valueOf(base), BigDecimal.valueOf(perKm),
                BigDecimal.valueOf(perMinute), BigDecimal.valueOf(minimum), BigDecimal.valueOf(bookingFee), now, null,
                "Bảng giá mẫu"), admin);
    }
}
