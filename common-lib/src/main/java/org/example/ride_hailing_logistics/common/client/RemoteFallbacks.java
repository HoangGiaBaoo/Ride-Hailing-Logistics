package org.example.ride_hailing_logistics.common.client;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;

/**
 * Dung trong FallbackFactory cua Feign client: loi nghiep vu 4xx cua service dich (vd 404) giu nguyen de
 * tra dung loi cho client; mach mo / het thoi gian / mat ket noi -> 503 DEPENDENCY_UNAVAILABLE co thong bao ro rang.
 */
@Slf4j
public final class RemoteFallbacks {

    private RemoteFallbacks() {
    }

    public static BusinessException translate(String service, Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof BusinessException b && b.getCode().getStatus().is4xxClientError()) {
                return b;
            }
        }
        log.warn("Remote call to {} failed: {}", service, cause == null ? "unknown" : cause.toString());
        return new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                "Hệ thống đang bận, vui lòng thử lại sau ít phút.");
    }
}
