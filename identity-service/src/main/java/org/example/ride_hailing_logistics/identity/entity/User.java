package org.example.ride_hailing_logistics.identity.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.example.ride_hailing_logistics.identity.enums.UserStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 30)
    private Set<Role> roles = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Tang khi doi mat khau / vai tro / khoa tai khoan: access token mang so cu bi tu choi ngay (khong doi het han). */
    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @Version
    // Wrapper (khong phai long): version null = entity moi -> Spring Data goi persist() chu khong merge()
    private Long version;

    public static User register(String email, String phone, String passwordHash, String fullName, Role role, Instant now) {
        User u = new User();
        u.id = UUID.randomUUID();
        u.email = email;
        u.phone = phone;
        u.passwordHash = passwordHash;
        u.fullName = fullName;
        u.status = UserStatus.ACTIVE;
        u.roles = new HashSet<>(Set.of(role));
        u.createdAt = now;
        u.updatedAt = now;
        return u;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean canLogin(Instant now) {
        return status == UserStatus.ACTIVE && !isLocked(now);
    }

    /** Dem lan sai; du nguong thi khoa tam va dat lai bo dem (FR-IAM-006). */
    public void recordFailedLogin(int maxAttempts, Duration lockDuration, Instant now) {
        failedLoginAttempts++;
        if (failedLoginAttempts >= maxAttempts) {
            lockedUntil = now.plus(lockDuration);
            failedLoginAttempts = 0;
        }
        updatedAt = now;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginAttempts = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public void changePassword(String newHash, Instant now) {
        passwordHash = newHash;
        failedLoginAttempts = 0;
        lockedUntil = null;
        tokenVersion++;
        updatedAt = now;
    }

    public void revokeAllAccessTokens(Instant now) {
        tokenVersion++;
        updatedAt = now;
    }

    public void rename(String newName, Instant now) {
        fullName = newName;
        updatedAt = now;
    }

    public void replaceRoles(Set<Role> newRoles, Instant now) {
        roles.clear();
        roles.addAll(newRoles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(newRoles));
        tokenVersion++;
        updatedAt = now;
    }

    public void changeStatus(UserStatus newStatus, Instant now) {
        if (newStatus != status && newStatus == UserStatus.DISABLED) {
            tokenVersion++;
        }
        status = newStatus;
        updatedAt = now;
    }
}
