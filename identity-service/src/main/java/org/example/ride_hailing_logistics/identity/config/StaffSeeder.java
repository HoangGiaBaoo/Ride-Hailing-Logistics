package org.example.ride_hailing_logistics.identity.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.identity.entity.User;
import org.example.ride_hailing_logistics.identity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Instant;

/**
 * Tai khoan nhan vien mau (profile dev). Nhan vien khong tu dang ky duoc nen phai tao san;
 * khach, tai xe, bang gia mau do tools/seed-dev.mjs tao qua API cong khai cua gateway.
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class StaffSeeder implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Value("${app.dev.seed-enabled:false}")
    private boolean enabled;

    @Value("${app.dev.seed-password}")
    private String seedPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled || users.count() > 0) {
            return;
        }
        Instant now = clock.instant();
        staff("admin@rhl.dev", "+84911000001", "Quản trị viên", Role.ADMIN, now);
        staff("reviewer@rhl.dev", "+84911000002", "Kiểm duyệt viên", Role.REVIEWER, now);
        staff("support@rhl.dev", "+84911000003", "Nhân viên hỗ trợ", Role.SUPPORT_STAFF, now);
        staff("finance@rhl.dev", "+84911000004", "Nhân viên tài chính", Role.FINANCE_STAFF, now);
        log.info("Dev seed: 4 staff accounts created. Password is app.dev.seed-password.");
    }

    private void staff(String email, String phone, String name, Role role, Instant now) {
        users.save(User.register(email, phone, passwordEncoder.encode(seedPassword), name, role, now));
    }
}
