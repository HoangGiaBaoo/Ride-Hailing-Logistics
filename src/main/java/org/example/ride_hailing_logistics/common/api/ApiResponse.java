package org.example.ride_hailing_logistics.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.example.ride_hailing_logistics.common.web.CorrelationId;

import java.util.Map;
// abc
/** Cau truc phan hoi thong nhat (COM-003, Phu luc B). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String code, String message, T data, String correlationId, Map<String, Object> details) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("SUCCESS", "Request completed successfully", data, CorrelationId.current(), null);
    }

    public static ApiResponse<Void> error(String code, String message, Map<String, Object> details) {
        return new ApiResponse<>(code, message, null, CorrelationId.current(), details == null || details.isEmpty() ? null : details);
    }
}
