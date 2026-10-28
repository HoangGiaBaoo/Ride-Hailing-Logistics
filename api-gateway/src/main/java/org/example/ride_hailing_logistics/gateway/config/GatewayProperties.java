package org.example.ride_hailing_logistics.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Cau hinh xac thuc o gateway, lay tu config-server. Viet dang JavaBean (khong phai record) co chu dich:
 * khi /actuator/busrefresh phat EnvironmentChangeEvent, ConfigurationPropertiesRebinder gan lai gia tri moi
 * vao chinh bean nay -> doi danh sach duong mo khong can khoi dong lai gateway. Record bat bien thi khong gan lai duoc.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.gateway")
public class GatewayProperties {

    /** Khoa HS256 dung chung voi identity-service (bien moi truong JWT_SECRET). Doi khoa can khoi dong lai. */
    private String jwtSecret;

    private String jwtIssuer;

    /** Duong /api khong can token (mau AntPath). */
    private List<String> publicPaths = new ArrayList<>();
}
