package org.example.ride_hailing_logistics.payment.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * Cong tac demo cua cong thanh toan gia lap, doi duoc khi dang chay:
 * sua app.payment.sandbox.force-failure trong config-repo -> commit -> POST /actuator/busrefresh o config-server.
 * @RefreshScope: bean bi huy va tao lai voi gia tri moi o lan goi ke tiep (AppProperties la record bat bien nen khong dung o day).
 */
@Getter
@RefreshScope
@Component
public class SandboxSettings {

    /** true: moi giao dich sandbox deu that bai, de demo luong thu lai (FR-PAY-008). */
    @Value("${app.payment.sandbox.force-failure:false}")
    private boolean forceFailure;
}
