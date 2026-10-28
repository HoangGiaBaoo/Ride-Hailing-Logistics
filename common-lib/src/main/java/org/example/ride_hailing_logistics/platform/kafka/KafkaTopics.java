package org.example.ride_hailing_logistics.platform.kafka;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ten topic. Moi service phat event nghiep vu len topic rieng "{ns}.{producer}.events"; khoa ban tin = aggregateId
 * nen moi event cua cung mot chuyen/tai xe/vi roi vao cung partition va giu dung thu tu.
 *
 * Namespace (app.kafka.namespace, mac dinh "rhl") tach moi truong dung chung mot broker: integration test chay voi
 * "rhltest" nen khong doc event cua lan chay dev, va dev khong nhan event gia cua test.
 * Hang so *_EXPR la placeholder cho annotation @KafkaListener; code thuong goi phuong thuc cua bean.
 */
@Component
public class KafkaTopics {

    public static final List<String> PRODUCERS = List.of("identity", "driver", "location", "pricing", "trip", "payment");

    private static final String NAMESPACE_EXPR = "${app.kafka.namespace:rhl}";

    /** Khop day du ten topic event cua namespace (khong khop topic .DLT). */
    public static final String EVENT_TOPIC_PATTERN_EXPR = NAMESPACE_EXPR + "\\.[a-z]+\\.events";

    /** Vi tri tai xe tan suat cao: khong qua outbox, mat mot ban tin thi ban sau thay the (FR-LOC-006). */
    public static final String LOCATION_UPDATES_EXPR = NAMESPACE_EXPR + ".location.updates";

    /** Consumer group cung theo namespace: dev va test cung ten service khong tranh partition cua nhau. */
    public static final String GROUP_EXPR = NAMESPACE_EXPR + ".${spring.application.name}";

    public static final String DLT_SUFFIX = ".DLT";

    public static final String HEADER_EVENT_TYPE = "eventType";
    public static final String HEADER_EVENT_ID = "eventId";
    public static final String HEADER_CORRELATION_ID = "correlationId";

    private final String namespace;

    public KafkaTopics(@Value("${app.kafka.namespace:rhl}") String namespace) {
        this.namespace = namespace;
    }

    public String events(String producer) {
        return namespace + "." + producer + ".events";
    }

    public String locationUpdates() {
        return namespace + ".location.updates";
    }
}
