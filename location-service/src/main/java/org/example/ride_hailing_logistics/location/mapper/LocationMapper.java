package org.example.ride_hailing_logistics.location.mapper;

import org.example.ride_hailing_logistics.location.dto.response.LatestLocationResponse;
import org.example.ride_hailing_logistics.location.entity.LatestLocation;
import org.mapstruct.Mapper;

@Mapper
public interface LocationMapper {

    LatestLocationResponse toResponse(LatestLocation location);
}
