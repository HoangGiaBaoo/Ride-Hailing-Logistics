package org.example.ride_hailing_logistics.platform.outbox;

import java.util.Set;

/**
 * Consumer cua outbox. Relay goi handle() trong mot transaction rieng va ghi processed_events
 * cung transaction do, nen moi (event, handler) co hieu ung dung mot lan (FR-EVT-004).
 * Handler van nen idempotent theo business key de an toan khi admin xu ly lai thu cong.
 */
public interface EventHandler {

    /** Ten on dinh, dung lam khoa trong processed_events. Doi ten = event cu bi xu ly lai. */
    String name();

    Set<String> eventTypes();

    void handle(EventEnvelope event);
}
