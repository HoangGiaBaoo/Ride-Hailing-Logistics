package org.example.ride_hailing_logistics.identity.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.security.JwtService;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.common.security.TokenRevocationStore;
import org.example.ride_hailing_logistics.common.tx.AfterCommit;
import org.example.ride_hailing_logistics.common.util.Normalizers;
import org.example.ride_hailing_logistics.common.util.Secrets;
import org.example.ride_hailing_logistics.identity.dto.request.LoginRequest;
import org.example.ride_hailing_logistics.identity.dto.request.RegisterRequest;
import org.example.ride_hailing_logistics.identity.dto.response.TokenResponse;
import org.example.ride_hailing_logistics.identity.dto.response.UserResponse;
import org.example.ride_hailing_logistics.identity.entity.PasswordResetToken;
import org.example.ride_hailing_logistics.identity.entity.RefreshToken;
import org.example.ride_hailing_logistics.identity.entity.User;
import org.example.ride_hailing_logistics.identity.enums.SelfRegistrationRole;
import org.example.ride_hailing_logistics.identity.enums.UserStatus;
import org.example.ride_hailing_logistics.identity.event.IdentityEvents;
import org.example.ride_hailing_logistics.identity.event.UserRegistered;
import org.example.ride_hailing_logistics.identity.exception.InvalidCredentialsException;
import org.example.ride_hailing_logistics.identity.mapper.UserMapper;
import org.example.ride_hailing_logistics.identity.repository.PasswordResetTokenRepository;
import org.example.ride_hailing_logistics.identity.repository.RefreshTokenRepository;
import org.example.ride_hailing_logistics.identity.repository.UserRepository;
import org.example.ride_hailing_logistics.identity.service.AuthService;
import org.example.ride_hailing_logistics.identity.service.notification.NotificationSender;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OutboxService outbox;
    private final NotificationSender notificationSender;
    private final TokenRevocationStore revocations;
    private final UserMapper userMapper;
    private final AppProperties.Security props;
    private final TransactionTemplate requiresNew;
    private final Clock clock;
    // Bam san mot mat khau gia de so sanh khi tai khoan khong ton tai: thoi gian phan hoi nhu nhau
    private final String dummyHash;

    public AuthServiceImpl(UserRepository users, RefreshTokenRepository refreshTokens,
                           PasswordResetTokenRepository resetTokens, PasswordEncoder passwordEncoder, JwtService jwtService,
                           OutboxService outbox, NotificationSender notificationSender, TokenRevocationStore revocations,
                           UserMapper userMapper, AppProperties appProperties, PlatformTransactionManager txManager,
                           Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.outbox = outbox;
        this.notificationSender = notificationSender;
        this.revocations = revocations;
        this.userMapper = userMapper;
        this.props = appProperties.security();
        this.requiresNew = new TransactionTemplate(txManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-1");
    }

    @Override
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
        Role role = req.role() == SelfRegistrationRole.DRIVER ? Role.DRIVER : Role.CUSTOMER;
        Instant now = clock.instant();
        User user = users.save(User.register(email, phone, passwordEncoder.encode(req.password()),
                req.fullName().trim(), role, now));
        // driver-service nghe event nay de mo ho so nhap cho tai xe (FR-DRV-001); cung transaction voi tai khoan
        outbox.append(IdentityEvents.PRODUCER, "User", user.getId(), IdentityEvents.USER_REGISTERED, 1,
                new UserRegistered(user.getId(), role, user.getFullName(), user.getPhone(), now));
        return userMapper.toResponse(user);
    }

    @Override
    public TokenResponse login(LoginRequest req) {
        Instant now = clock.instant();
        Optional<User> found = findByLogin(req.login());
        if (found.isEmpty()) {
            passwordEncoder.matches(req.password(), dummyHash);
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        if (!user.canLogin(now)) {
            passwordEncoder.matches(req.password(), dummyHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            // Transaction rieng: phai luu so lan sai du request nay ket thuc bang loi
            requiresNew.executeWithoutResult(s -> users.findById(user.getId()).ifPresent(u ->
                    u.recordFailedLogin(props.login().maxFailedAttempts(), props.login().lockDuration(), now)));
            throw new InvalidCredentialsException();
        }
        return requiresNew.execute(s -> {
            User fresh = users.findById(user.getId()).orElseThrow();
            fresh.recordSuccessfulLogin(now);
            // Redis co the bi xoa: dang nhap dong bo lai token version tu DB (nguon su that)
            revocations.publishVersion(fresh.getId(), fresh.getTokenVersion());
            return issueTokens(fresh, UUID.randomUUID(), now);
        });
    }

    /**
     * Xoay vong refresh token (FR-IAM-007): moi lan refresh cap cap token moi, token cu bi thu hoi bang UPDATE co dieu kien.
     * Token da thu hoi ma bi dung lai = dau hieu bi lo -> thu hoi moi phien va tang token version (cat ca access token).
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenResponse refresh(String rawToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findByTokenHash(Secrets.sha256Hex(rawToken))
                .orElseThrow(AuthServiceImpl::sessionExpired);
        if (token.getRevokedAt() != null) {
            revokeEverything(token.getUserId(), now);
            log.warn("Revoked refresh token reused for user {}; all sessions revoked", token.getUserId());
            throw sessionExpired();
        }
        if (!token.isUsable(now)) {
            throw sessionExpired();
        }
        User user = users.findById(token.getUserId()).filter(u -> u.canLogin(now))
                .orElseThrow(AuthServiceImpl::sessionExpired);
        UUID replacement = UUID.randomUUID();
        if (refreshTokens.rotate(token.getId(), replacement, now) != 1) {
            // Request song song vua xoay token nay truoc: coi nhu dung lai token cu
            revokeEverything(token.getUserId(), now);
            throw sessionExpired();
        }
        return issueTokens(user, replacement, now);
    }

    /** Dang xuat: thu hoi refresh token va dua access token dang dung vao danh sach den den khi no het han. */
    @Override
    @Transactional
    public void logout(String rawToken, String accessJti, Instant accessExpiresAt) {
        Instant now = clock.instant();
        refreshTokens.findByTokenHash(Secrets.sha256Hex(rawToken)).ifPresent(t -> t.revoke(now));
        if (accessJti != null && accessExpiresAt != null) {
            AfterCommit.run(() -> revocations.revoke(accessJti, Duration.between(now, accessExpiresAt)));
        }
    }

    @Override
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

    @Override
    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken token = resetTokens.findByTokenHash(Secrets.sha256Hex(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> BusinessException.rule("Mã đặt lại mật khẩu không hợp lệ hoặc đã hết hạn."));
        User user = users.findById(token.getUserId()).orElseThrow(() -> BusinessException.notFound("tài khoản"));
        user.changePassword(passwordEncoder.encode(newPassword), now);
        token.markUsed(now);
        // Doi mat khau thi dang xuat moi thiet bi, ke ca access token con han (token version tang)
        refreshTokens.revokeAllForUser(user.getId(), now);
        publishVersionAfterCommit(user);
    }

    private void revokeEverything(UUID userId, Instant now) {
        refreshTokens.revokeAllForUser(userId, now);
        users.findById(userId).ifPresent(u -> {
            u.revokeAllAccessTokens(now);
            publishVersionAfterCommit(u);
        });
    }

    private void publishVersionAfterCommit(User user) {
        UUID id = user.getId();
        long version = user.getTokenVersion();
        AfterCommit.run(() -> revocations.publishVersion(id, version));
    }

    private TokenResponse issueTokens(User user, UUID refreshId, Instant now) {
        JwtService.IssuedToken access = jwtService.issueAccessToken(user.getId(), user.getRoles(), user.getTokenVersion());
        String rawRefresh = Secrets.randomToken(32);
        Instant refreshExpiry = now.plus(props.jwt().refreshTokenTtl());
        refreshTokens.save(RefreshToken.issue(refreshId, user.getId(), Secrets.sha256Hex(rawRefresh), refreshExpiry, now));
        return new TokenResponse(access.value(), access.expiresAt(), rawRefresh, refreshExpiry, "Bearer",
                userMapper.toResponse(user));
    }

    private Optional<User> findByLogin(String login) {
        if (login == null) return Optional.empty();
        if (login.contains("@")) {
            return Normalizers.email(login).flatMap(users::findByEmail);
        }
        return Normalizers.phone(login).flatMap(users::findByPhone);
    }

    private static BusinessException sessionExpired() {
        return new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED,
                "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại.");
    }

    private static BusinessException invalid(String field, String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, Map.of("fields", Map.of(field, message)));
    }
}
