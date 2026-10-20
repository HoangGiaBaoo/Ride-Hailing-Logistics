package org.example.ride_hailing_logistics.common.security;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Danh tinh nguoi goi luon lay tu token da xac thuc, khong bao gio tu payload (FR-LOC-005, NFR-SEC-012).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        return idOf(authentication());
    }

    public static Set<Role> roles() {
        return rolesOf(authentication());
    }

    public static Actor actor() {
        Authentication auth = authentication();
        return new Actor(idOf(auth), rolesOf(auth));
    }

    public static UUID idOf(Authentication auth) {
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "Phiên đăng nhập không hợp lệ.");
        }
    }

    public static Set<Role> rolesOf(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a != null && a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .flatMap(name -> {
                    try {
                        return java.util.stream.Stream.of(Role.valueOf(name));
                    } catch (IllegalArgumentException e) {
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Authentication authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED, "Vui lòng đăng nhập.");
        }
        return auth;
    }
}
