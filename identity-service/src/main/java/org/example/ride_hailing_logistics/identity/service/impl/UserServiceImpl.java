package org.example.ride_hailing_logistics.identity.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.common.security.TokenRevocationStore;
import org.example.ride_hailing_logistics.common.tx.AfterCommit;
import org.example.ride_hailing_logistics.identity.dto.response.UserResponse;
import org.example.ride_hailing_logistics.identity.entity.User;
import org.example.ride_hailing_logistics.identity.enums.UserStatus;
import org.example.ride_hailing_logistics.identity.event.IdentityEvents;
import org.example.ride_hailing_logistics.identity.event.UserProfileUpdated;
import org.example.ride_hailing_logistics.identity.event.UserStatusChanged;
import org.example.ride_hailing_logistics.identity.exception.UserNotFoundException;
import org.example.ride_hailing_logistics.identity.mapper.UserMapper;
import org.example.ride_hailing_logistics.identity.repository.RefreshTokenRepository;
import org.example.ride_hailing_logistics.identity.repository.UserRepository;
import org.example.ride_hailing_logistics.identity.service.UserService;
import org.example.ride_hailing_logistics.platform.audit.AuditService;
import org.example.ride_hailing_logistics.platform.outbox.OutboxService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final UserMapper mapper;
    private final AuditService audit;
    private final OutboxService outbox;
    private final TokenRevocationStore revocations;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return mapper.toResponse(load(userId));
    }

    /** Chi cho sua ho ten; email/SDT la dinh danh dang nhap, doi phai qua quy trinh xac minh rieng (FR-IAM-008). */
    @Override
    @Transactional
    public UserResponse updateMe(UUID userId, String fullName) {
        User user = load(userId);
        Instant now = clock.instant();
        user.rename(fullName.trim(), now);
        outbox.append(IdentityEvents.PRODUCER, "User", userId, IdentityEvents.USER_PROFILE_UPDATED, 1,
                new UserProfileUpdated(userId, user.getFullName(), now));
        return mapper.toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(Role role, String query, Pageable pageable) {
        Specification<User> spec = (root, cq, cb) -> cb.conjunction();
        if (role != null) {
            spec = spec.and((r, cq, cb) -> {
                cq.distinct(true);
                return cb.equal(r.join("roles"), role);
            });
        }
        if (query != null && !query.isBlank()) {
            String like = "%" + query.trim().toLowerCase() + "%";
            spec = spec.and((r, cq, cb) -> cb.or(cb.like(cb.lower(r.get("email")), like), cb.like(r.get("phone"), like),
                    cb.like(cb.lower(r.get("fullName")), like)));
        }
        return PageResponse.of(users.findAll(spec, pageable), mapper::toResponse);
    }

    @Override
    @Transactional
    public UserResponse updateRoles(UUID userId, Set<Role> roles, String reason, Actor actor) {
        if (userId.equals(actor.id()) && !roles.contains(Role.ADMIN)) {
            // Tranh truong hop admin tu tuoc quyen cua chinh minh va khong con ai quan tri
            throw BusinessException.rule("Không thể tự gỡ quyền quản trị của chính mình.");
        }
        User user = load(userId);
        Set<Role> before = Set.copyOf(user.getRoles());
        Instant now = clock.instant();
        user.replaceRoles(roles, now);
        // Vai tro nam trong access token: tang token version + thu hoi refresh -> token cu mat hieu luc ngay
        refreshTokens.revokeAllForUser(userId, now);
        publishVersionAfterCommit(user);
        audit.record(actor, "USER_ROLES_CHANGED", "User", userId,
                Map.of("before", before, "after", roles, "reason", reason));
        return mapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateStatus(UUID userId, UserStatus status, String reason, Actor actor) {
        if (userId.equals(actor.id())) {
            throw BusinessException.rule("Không thể tự khóa tài khoản của chính mình.");
        }
        User user = load(userId);
        UserStatus before = user.getStatus();
        Instant now = clock.instant();
        user.changeStatus(status, now);
        if (status == UserStatus.DISABLED) {
            refreshTokens.revokeAllForUser(userId, now);
            publishVersionAfterCommit(user);
        }
        if (before != status) {
            outbox.append(IdentityEvents.PRODUCER, "User", userId, IdentityEvents.USER_STATUS_CHANGED, 1,
                    new UserStatusChanged(userId, status, now));
        }
        audit.record(actor, "USER_STATUS_CHANGED", "User", userId,
                Map.of("before", before, "after", status, "reason", reason));
        return mapper.toResponse(user);
    }

    private void publishVersionAfterCommit(User user) {
        UUID id = user.getId();
        long version = user.getTokenVersion();
        AfterCommit.run(() -> revocations.publishVersion(id, version));
    }

    private User load(UUID userId) {
        return users.findById(userId).orElseThrow(UserNotFoundException::new);
    }
}
