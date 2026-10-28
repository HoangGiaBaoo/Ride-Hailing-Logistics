package org.example.ride_hailing_logistics.identity.service;

import org.example.ride_hailing_logistics.identity.dto.response.UserBasicResponse;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** API cong khai cua mien identity cho service khac (CON-02): chi thong tin co ban. */
public interface UserDirectoryService {

    UserBasicResponse find(UUID userId);

    Map<UUID, UserBasicResponse> findAll(Collection<UUID> ids);
}
