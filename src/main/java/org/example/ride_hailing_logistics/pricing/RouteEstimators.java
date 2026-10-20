package org.example.ride_hailing_logistics.pricing;

import lombok.extern.slf4j.Slf4j;
import org.example.ride_hailing_logistics.common.config.AppProperties;
import org.example.ride_hailing_logistics.common.util.GeoUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.Locale;

@Configuration
public class RouteEstimators {

    @Bean
    public RouteEstimator routeEstimator(AppProperties props) {
        AppProperties.MapProvider map = props.map();
        RouteEstimator fallback = new HaversineRouteEstimator(map.roadFactor(), map.averageSpeedKmh());
        if ("osrm".equalsIgnoreCase(map.provider())) {
            return new OsrmRouteEstimator(map, fallback);
        }
        return fallback;
    }

    /**
     * Fallback duoc phe duyet (TBD-02): duong chim bay x he so duong 1.3, toc do trung binh 25 km/h.
     * Luon tra approximate = true de bao cao/hien thi biet day khong phai tuyen that.
     */
    static final class HaversineRouteEstimator implements RouteEstimator {
        private final double roadFactor;
        private final double speedMps;

        HaversineRouteEstimator(double roadFactor, double averageSpeedKmh) {
            this.roadFactor = roadFactor;
            this.speedMps = averageSpeedKmh * 1000 / 3600;
        }

        @Override
        public RouteEstimate estimate(double fromLat, double fromLng, double toLat, double toLng) {
            long meters = Math.round(GeoUtils.haversineMeters(fromLat, fromLng, toLat, toLng) * roadFactor);
            return new RouteEstimate(meters, Math.round(meters / speedMps), true);
        }
    }

    /** OSRM co timeout; loi hoac du lieu sai schema -> roi ve Haversine, khong lam hong bao gia (NFR-REL-005, DR-VAL-006). */
    @Slf4j
    static final class OsrmRouteEstimator implements RouteEstimator {
        private final RestClient client;
        private final RouteEstimator fallback;

        OsrmRouteEstimator(AppProperties.MapProvider map, RouteEstimator fallback) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(map.timeout());
            factory.setReadTimeout(map.timeout());
            this.client = RestClient.builder().baseUrl(map.osrmUrl()).requestFactory(factory).build();
            this.fallback = fallback;
        }

        @Override
        public RouteEstimate estimate(double fromLat, double fromLng, double toLat, double toLng) {
            try {
                String coords = String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", fromLng, fromLat, toLng, toLat);
                JsonNode body = client.get().uri("/route/v1/driving/{coords}?overview=false", coords)
                        .retrieve().body(JsonNode.class);
                JsonNode route = body == null ? null : body.path("routes").path(0);
                if (route == null || !route.path("distance").isNumber() || !route.path("duration").isNumber()) {
                    throw new IllegalStateException("Unexpected OSRM response");
                }
                long distance = Math.round(route.path("distance").asDouble());
                long duration = Math.round(route.path("duration").asDouble());
                if (distance < 0 || duration < 0) {
                    throw new IllegalStateException("Negative OSRM values");
                }
                return new RouteEstimate(distance, duration, false);
            } catch (RuntimeException e) {
                log.warn("OSRM unavailable, using haversine fallback: {}", e.getMessage());
                return fallback.estimate(fromLat, fromLng, toLat, toLng);
            }
        }
    }
}
