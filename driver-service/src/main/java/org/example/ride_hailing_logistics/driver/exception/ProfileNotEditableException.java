package org.example.ride_hailing_logistics.driver.exception;

import org.example.ride_hailing_logistics.common.error.BusinessException;
import org.example.ride_hailing_logistics.common.error.ErrorCode;
import org.example.ride_hailing_logistics.driver.enums.ReviewStatus;

public class ProfileNotEditableException extends BusinessException {

    public ProfileNotEditableException(ReviewStatus status) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, "Hồ sơ đang ở trạng thái " + status
                + ", chỉ sửa được khi ở trạng thái nháp hoặc bị từ chối.");
    }
}
