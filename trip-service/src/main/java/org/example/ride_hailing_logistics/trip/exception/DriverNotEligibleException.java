package org.example.ride_hailing_logistics.trip.exception;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;

import java.util.List;
import java.util.Map;

/** Chua du dieu kien Online: tra kem ly do cu the de app hien (UI-DRV-002). */
public class DriverNotEligibleException extends BusinessException {

    public DriverNotEligibleException(List<String> reasons) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, "Bạn chưa đủ điều kiện hoạt động.", Map.of("reasons", reasons));
    }
}
