package org.example.ride_hailing_logistics.identity;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.common.security.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** API cong khai cua mien identity cho mien khac (CON-02): chi tra thong tin co ban, khong lo password hash. */
@Service
@RequiredArgsConstructor
public class UserDirectory {

    private final UserRepository repository;

    @Transactional(readOnly = true)
    public Optional<UserBasic> find(UUID userId) {
        return repository.findById(userId).map(UserBasic::from);
    }

    @Transactional(readOnly = true)
    public Map<UUID, UserBasic> findAll(Collection<UUID> ids) {
        return repository.findByIdIn(ids).stream().map(UserBasic::from)
                .collect(Collectors.toMap(UserBasic::id, Function.identity()));
    }

    public record UserBasic(UUID id, String fullName, String phone, boolean active, Set<Role> roles) {
        static UserBasic from(User u) {
            return new UserBasic(u.getId(), u.getFullName(), u.getPhone(), u.getStatus() == UserStatus.ACTIVE,
                    Set.copyOf(u.getRoles()));
        }
    }
}
