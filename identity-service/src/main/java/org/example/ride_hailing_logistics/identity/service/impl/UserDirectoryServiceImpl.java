package org.example.ride_hailing_logistics.identity.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.ride_hailing_logistics.identity.dto.response.UserBasicResponse;
import org.example.ride_hailing_logistics.identity.exception.UserNotFoundException;
import org.example.ride_hailing_logistics.identity.mapper.UserMapper;
import org.example.ride_hailing_logistics.identity.repository.UserRepository;
import org.example.ride_hailing_logistics.identity.service.UserDirectoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDirectoryServiceImpl implements UserDirectoryService {

    private final UserRepository repository;
    private final UserMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public UserBasicResponse find(UUID userId) {
        return repository.findById(userId).map(mapper::toBasic).orElseThrow(UserNotFoundException::new);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserBasicResponse> findAll(Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        return repository.findByIdIn(ids).stream().map(mapper::toBasic)
                .collect(Collectors.toMap(UserBasicResponse::id, Function.identity()));
    }
}
