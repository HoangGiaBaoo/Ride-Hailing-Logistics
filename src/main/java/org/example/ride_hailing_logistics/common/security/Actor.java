package org.example.ride_hailing_logistics.common.security;

import java.util.Set;
import java.util.UUID;

/** Ai dang thuc hien thao tac — dung de kiem quyen so huu va ghi lich su/audit. */
public record Actor(UUID id, Set<Role> roles) {

    public static final Actor SYSTEM = new Actor(null, Set.of());

    public boolean has(Role role) {
        return roles.contains(role);
    }

    public boolean isSystem() {
        return id == null;
    }

    public boolean isStaff() {
        return has(Role.SUPPORT_STAFF) || has(Role.ADMIN) || has(Role.FINANCE_STAFF) || has(Role.REVIEWER);
    }

    public String rolesAsString() {
        return roles.stream().map(Enum::name).sorted().reduce((a, b) -> a + "," + b).orElse("SYSTEM");
    }
}
