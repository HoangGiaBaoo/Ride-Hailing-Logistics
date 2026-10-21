package org.example.ride_hailing_logistics.identity;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.security.JwtService;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.common.util.Normalizers;
import org.example.ride_hailing_logistics.common.util.Secrets;
import org.example.ride_hailing_logistics.identity.IdentityDtos.LoginRequest;
import org.example.ride_hailing_logistics.identity.IdentityDtos.RegisterRequest;
import org.example.ride_hailing_logistics.identity.IdentityDtos.TokenResponse;
import org.example.ride_hailing_logistics.identity.IdentityDtos.UserResponse;
import org.example.ride_hailing_logistics.identity.notification.NotificationSender;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {

    // Thong bao chung cho moi kieu that bai dang nhap: khong lo tai khoan co ton tai / bi khoa (FR-IAM-006)
    private static final String LOGIN_FAILED = "Thông tin đăng nhập không đúng hoặc tài khoản tạm thời không thể đăng nhập.";

    public static final String EVENT_USER_REGISTERED = "UserRegistered";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OutboxService outbox;
    private final NotificationSender notificationSender;
    private final AppProperties.Security props;
    private final TransactionTemplate requiresNew;
    private final Clock clock;
    // Bam san mot mat khau gia de so sanh khi tai khoan khong ton tai: thoi gian phan hoi nhu nhau
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordResetTokenRepository resetTokens, PasswordEncoder passwordEncoder, JwtService jwtService,
                       OutboxService outbox, NotificationSender notificationSender,
                       AppProperties appProperties, PlatformTransactionManager txManager, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.outbox = outbox;
        this.notificationSender = notificationSender;
        this.props = appProperties.security();
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-1");
    }

    @Transactional
    public UserResponse register(RegisterRequest req) {
        String email = Normalizers.email(req.email())
                .orElseThrow(() -> invalid("email", "Email không đúng định dạng."));
        String phone = Normalizers.phone(req.phone())
                .orElseThrow(() -> invalid("phone", "Số điện thoại di động Việt Nam không hợp lệ."));
        if (users.existsByEmail(email) || users.existsByPhone(phone)) {
            // Dang ky la noi duy nhat buoc phai bao trung; tra loi chung chung cho ca email lan SDT
            throw new BusinessException(ErrorCode.CONFLICT, "Email hoặc số điện thoại đã được sử dụng.");
        }
        Role role = req.role() == IdentityDtos.SelfRegistrationRole.DRIVER ? Role.DRIVER : Role.CUSTOMER;
        Instant now = clock.instant();
        User user = users.save(User.register(email, phone, passwordEncoder.encode(req.password()),
                req.fullName().trim(), role, now));
        // driver-service nghe event nay de mo ho so nhap cho tai xe (FR-DRV-001); cung transaction voi tai khoan
        outbox.append("identity", "User", user.getId(), EVENT_USER_REGISTERED, 1,
                new UserRegistered(user.getId(), role, now));
        return UserResponse.from(user);
    }

    public TokenResponse login(LoginRequest req) {
        Instant now = clock.instant();
        Optional<User> found = findByLogin(req.login());
        if (found.isEmpty()) {
            passwordEncoder.matches(req.password(), dummyHash);
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, LOGIN_FAILED);
        }
        User user = found.get();
        if (!user.canLogin(now)) {
            passwordEncoder.matches(req.password(), dummyHash);
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, LOGIN_FAILED);
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            // Transaction rieng: phai luu so lan sai du request nay ket thuc bang loi
            requiresNew.executeWithoutResult(s -> users.findById(user.getId()).ifPresent(u ->
                    u.recordFailedLogin(props.login().maxFailedAttempts(), props.login().lockDuration(), now)));
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, LOGIN_FAILED);
        }
        return requiresNew.execute(s -> {
            User fresh = users.findById(user.getId()).orElseThrow();
            fresh.recordSuccessfulLogin(now);
            return issueTokens(fresh, now);
        });
    }

    /** Xoay vong refresh token; dung lai token da thu hoi = dau hieu bi lo -> thu hoi toan bo phien (FR-IAM-007). */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse refresh(String rawToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHash(Secrets.sha256Hex(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "Phiên đăng nhập đã hết hạn."));
        if (token.getRevokedAt() != null) {
            refreshTokens.revokeAllForUser(token.getUserId(), now);
            log.warn("Revoked refresh token reused for user {}; all sessions revoked", token.getUserId());
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "Phiên đăng nhập đã hết hạn.");
        }
        if (!token.isUsable(now)) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "Phiên đăng nhập đã hết hạn.");
        }
        User user = users.findById(token.getUserId())
                .filter(u -> u.canLogin(now))
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "Phiên đăng nhập đã hết hạn."));
        token.revoke(now);
        return issueTokens(user, now);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(Secrets.sha256Hex(rawToken)).ifPresent(t -> t.revoke(clock.instant()));
    }

    @Transactional
    public void requestPasswordReset(String login) {
        Instant now = clock.instant();
        findByLogin(login).filter(u -> u.getStatus() == UserStatus.ACTIVE).ifPresent(user -> {
            String raw = Secrets.randomToken(32);
            resetTokens.save(PasswordResetToken.issue(user.getId(), Secrets.sha256Hex(raw),
                    now.plus(props.passwordResetTtl()), now));
            notificationSender.send(user.getId(), user.getEmail(), "Đặt lại mật khẩu",
                    "Mã đặt lại mật khẩu (hiệu lực " + props.passwordResetTtl().toMinutes() + " phút): " + raw);
        });
        // Luon tra cung mot phan hoi o controller, du tai khoan co ton tai hay khong
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByTokenHash(Secrets.sha256Hex(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> BusinessException.rule("Mã đặt lại mật khẩu không hợp lệ hoặc đã hết hạn."));
        User user = users.findById(token.getUserId()).orElseThrow(() -> BusinessException.notFound("tài khoản"));
        user.changePassword(passwordEncoder.encode(newPassword), now);
        token.markUsed(now);
        // Doi mat khau thi dang xuat moi thiet bi khac
        refreshTokens.revokeAllForUser(user.getId(), now);
    }

    private TokenResponse issueTokens(User user, Instant now) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(user.getId(), user.getRoles());
        String rawRefresh = Secrets.randomToken(32);
        Instant refreshExpiry = now.plus(props.jwt().refreshTokenTtl());
        refreshTokens.save(RefreshToken.issue(user.getId(), Secrets.sha256Hex(rawRefresh), refreshExpiry, now));
        return new TokenResponse(access.value(), access.expiresAt(), rawRefresh, refreshExpiry, "Bearer",
                UserResponse.from(user));
    }

    private Optional<User> findByLogin(String login) {
        if (login == null) return Optional.empty();
        if (login.contains("@")) {
            return Normalizers.email(login).flatMap(users::findByEmail);
        }
        return Normalizers.phone(login).flatMap(users::findByPhone);
    }

    private static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, Map.of("fields", Map.of(field, message)));
    }

    public record UserRegistered(UUID userId, Role role, Instant registeredAt) {}
}
