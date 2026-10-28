package org.example.ride_hailing_logistics.identity.mapper;

import org.example.ride_hailing_logistics.identity.dto.response.UserBasicResponse;
import org.example.ride_hailing_logistics.identity.dto.response.UserResponse;
import org.example.ride_hailing_logistics.identity.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface UserMapper {

    UserResponse toResponse(User user);

    @Mapping(target = "active", expression = "java(user.getStatus() == org.example.ride_hailing_logistics.identity.enums.UserStatus.ACTIVE)")
    UserBasicResponse toBasic(User user);
}
