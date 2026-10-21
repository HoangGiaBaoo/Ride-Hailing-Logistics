package org.example.ride_hailing_logistics.common.web;

import org.slf4j.MDC;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Correlation ID gan theo luong xu ly qua MDC (COM-004), di kem log, loi va event. */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private CorrelationId() {
    }

    public static String current() {
        return MDC.get(MDC_KEY);
    }

    public static String currentOrNew() {
        String id = current();
        return id != null ? id : newId();
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }

    // Chi nhan gia tri tu client neu dung dinh dang, tranh log injection
    public static String sanitize(String incoming) {
        return incoming != null && SAFE.matcher(incoming).matches() ? incoming : newId();
    }

    /** Chay mot tac vu nen (scheduler, relay) voi correlation ID rieng, don dep MDC sau khi xong. */
    public static <T> T with(String id, Supplier<T> work) {
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, id != null ? id : newId());
        try {
            return work.get();
        } finally {
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
