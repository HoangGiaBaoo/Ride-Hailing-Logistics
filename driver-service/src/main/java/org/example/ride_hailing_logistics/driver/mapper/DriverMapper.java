package org.example.ride_hailing_logistics.driver.mapper;

import org.example.ride_hailing_logistics.driver.dto.response.DecisionResponse;
import org.example.ride_hailing_logistics.driver.dto.response.DocumentResponse;
import org.example.ride_hailing_logistics.driver.dto.response.DriverSnapshotResponse;
import org.example.ride_hailing_logistics.driver.dto.response.DriverSummaryResponse;
import org.example.ride_hailing_logistics.driver.dto.response.VehicleResponse;
import org.example.ride_hailing_logistics.driver.entity.DriverDocument;
import org.example.ride_hailing_logistics.driver.entity.DriverProfile;
import org.example.ride_hailing_logistics.driver.entity.DriverReviewDecision;
import org.example.ride_hailing_logistics.driver.entity.Vehicle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface DriverMapper {

    VehicleResponse toResponse(Vehicle vehicle);

    @Mapping(target = "type", source = "docType")
    @Mapping(target = "maskedNumber", expression = "java(document.maskedNumber())")
    @Mapping(target = "uploadedAt", source = "createdAt")
    DocumentResponse toResponse(DriverDocument document);

    List<DocumentResponse> toDocumentResponses(List<DriverDocument> documents);

    DecisionResponse toResponse(DriverReviewDecision decision);

    List<DecisionResponse> toDecisionResponses(List<DriverReviewDecision> decisions);

    @Mapping(target = "driverId", source = "id")
    DriverSummaryResponse toSummary(DriverProfile profile);

    @Mapping(target = "driverId", source = "profile.id")
    @Mapping(target = "fullName", source = "profile.fullName")
    @Mapping(target = "phone", source = "profile.phone")
    @Mapping(target = "vehicleId", source = "vehicle.id")
    @Mapping(target = "vehicleType", source = "vehicle.vehicleType")
    @Mapping(target = "plateNumber", source = "vehicle.plateNumber")
    @Mapping(target = "vehicleDescription", expression = "java(vehicle.description())")
    DriverSnapshotResponse toSnapshot(DriverProfile profile, Vehicle vehicle);
}
