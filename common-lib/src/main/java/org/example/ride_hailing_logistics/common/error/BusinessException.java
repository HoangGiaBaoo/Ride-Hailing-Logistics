package org.example.ride_hailing_logistics.common.error;

import lombok.Getter;

import java.util.Map;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public BusinessException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public BusinessException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public static BusinessException notFound(String what) {
        // Khong phan biet "khong ton tai" va "khong co quyen xem" de khong lo tai nguyen (FR-IAM-010)
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy " + what + ".");
    }

    public static BusinessException rule(String message) {
        return new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }

    public static BusinessException conflict(ErrorCode code, String message) {
        return new BusinessException(code, message);
    }
}
