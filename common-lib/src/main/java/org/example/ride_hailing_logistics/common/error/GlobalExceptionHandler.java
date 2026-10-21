package org.example.ride_hailing_logistics.common.error;

import feign.FeignException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.api.ApiResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dich moi loi thanh {code, message, correlationId, details} (COM-003).
 * Thong bao khong lo chi tiet noi bo: ten bang, stack trace (NFR-USA-005, NFR-SEC-009).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return build(ex.getCode(), ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors().forEach(e -> fields.putIfAbsent(e.getObjectName(), e.getDefaultMessage()));
        return build(ErrorCode.VALIDATION_ERROR, "Dữ liệu không hợp lệ, vui lòng kiểm tra các trường được đánh dấu.", Map.of("fields", fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        Map<String, Object> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> fields.putIfAbsent(v.getPropertyPath().toString(), v.getMessage()));
        return build(ErrorCode.VALIDATION_ERROR, "Dữ liệu không hợp lệ.", Map.of("fields", fields));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(Exception ex) {
        return build(ErrorCode.VALIDATION_ERROR, "Yêu cầu sai định dạng hoặc thiếu tham số.", Map.of());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        return build(ErrorCode.VALIDATION_ERROR, "Thiếu header bắt buộc: " + ex.getHeaderName() + ".", Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException ex) {
        return build(ErrorCode.VALIDATION_ERROR, "Tệp vượt quá dung lượng cho phép (5 MB).", Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.ACCESS_DENIED, "Bạn không có quyền thực hiện thao tác này.", Map.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return build(ErrorCode.AUTHENTICATION_REQUIRED, "Vui lòng đăng nhập lại.", Map.of());
    }

    // Vi pham unique/partial index: xung dot dong thoi da bi DB chan (NFR-REL-003). Tra 409, khong 500.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.CONFLICT, "Dữ liệu vừa bị thay đổi bởi thao tác khác, vui lòng thử lại.", Map.of());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(Exception ex) {
        return build(ErrorCode.CONFLICT, "Dữ liệu vừa bị thay đổi bởi thao tác khác, vui lòng thử lại.", Map.of());
    }

    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleRedis(RedisConnectionFailureException ex) {
        log.error("Redis unavailable: {}", ex.getMessage());
        return build(ErrorCode.DEPENDENCY_UNAVAILABLE, "Hệ thống tạm thời gián đoạn, vui lòng thử lại sau ít phút.", Map.of());
    }

    // Service phu thuoc khong tra loi / tra loi ha tang (5xx): khong lo ten service hay URL noi bo ra client
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<Void>> handleRemote(FeignException ex) {
        log.error("Remote service call failed: {}", ex.getMessage());
        return build(ErrorCode.DEPENDENCY_UNAVAILABLE, "Hệ thống tạm thời gián đoạn, vui lòng thử lại sau ít phút.", Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(405).body(ApiResponse.error("METHOD_NOT_ALLOWED", "Phương thức không được hỗ trợ.", null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài nguyên.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(ErrorCode.INTERNAL_ERROR, "Đã có lỗi xảy ra, vui lòng thử lại sau.", Map.of());
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code, String message, Map<String, Object> details) {
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.error(code.name(), message, details));
    }
}
