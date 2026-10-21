package org.example.ride_hailing_logistics.driver.client;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.common.security.Role;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Thong tin co ban cua tai khoan, lay tu identity-service (CON-02: khong doc thang bang users). */
@Component
@RequiredArgsConstructor
public class UserDirectory {

    private final IdentityClient client;

    public Optional<UserBasic> find(UUID userId) {
        try {
            return Optional.ofNullable(client.find(userId).data());
        } catch (BusinessException e) {
            if (e.getCode() == ErrorCode.RESOURCE_NOT_FOUND) return Optional.empty();
            throw e;
        }
    }

    public Map<UUID, UserBasic> findAll(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, UserBasic> found = client.findAll(ids).data();
        return found == null ? Map.of() : found;
    }

    public record UserBasic(UUID id, String fullName, String phone, boolean active, Set<Role> roles) {}
}
