package org.example.ride_hailing_logistics.identity;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.api.PageResponse;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.security.Actor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.identity.IdentityDtos.UserResponse;
import org.example.ride_hailing_logistics.platform.audit.AuditService;
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
public class UserService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;
    private final Clock clock;

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return users.findById(userId).map(UserResponse::from).orElseThrow(() -> BusinessException.notFound("tài khoản"));
    }

    /** Chi cho sua ho ten; email/SDT la dinh danh dang nhap, doi phai qua quy trinh xac minh rieng (FR-IAM-008). */
    @Transactional
    public UserResponse updateMe(UUID userId, String fullName) {
        User user = users.findById(userId).orElseThrow(() -> BusinessException.notFound("tài khoản"));
        user.rename(fullName.trim(), clock.instant());
        return UserResponse.from(user);
    }

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
        return PageResponse.of(users.findAll(spec, pageable), UserResponse::from);
    }

    @Transactional
    public UserResponse updateRoles(UUID userId, Set<Role> roles, String reason, Actor actor) {
        if (userId.equals(actor.id()) && !roles.contains(Role.ADMIN)) {
            // Tranh truong hop admin tu tuoc quyen cua chinh minh va khong con ai quan tri
            throw BusinessException.rule("Không thể tự gỡ quyền quản trị của chính mình.");
        }
        User user = users.findById(userId).orElseThrow(() -> BusinessException.notFound("tài khoản"));
        Set<Role> before = Set.copyOf(user.getRoles());
        user.replaceRoles(roles, clock.instant());
        // Doi vai tro co hieu luc ngay o lan cap token tiep theo; thu hoi refresh de buoc dang nhap lai
        refreshTokens.revokeAllForUser(userId, clock.instant());
        audit.record(actor, "USER_ROLES_CHANGED", "User", userId,
                Map.of("before", before, "after", roles, "reason", reason));
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse updateStatus(UUID userId, UserStatus status, String reason, Actor actor) {
        if (userId.equals(actor.id())) {
            throw BusinessException.rule("Không thể tự khóa tài khoản của chính mình.");
        }
        User user = users.findById(userId).orElseThrow(() -> BusinessException.notFound("tài khoản"));
        UserStatus before = user.getStatus();
        Instant now = clock.instant();
        user.changeStatus(status, now);
        if (status == UserStatus.DISABLED) {
            refreshTokens.revokeAllForUser(userId, now);
        }
        audit.record(actor, "USER_STATUS_CHANGED", "User", userId,
                Map.of("before", before, "after", status, "reason", reason));
        return UserResponse.from(user);
    }
}
