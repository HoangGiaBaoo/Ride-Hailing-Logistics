package org.example.ride_hailing_logistics.payment.dto.request;


public record SandboxCallbackRequest(String eventId, String providerRef, String paymentReference, String amount,
                              String currency, String status) {
    public boolean isComplete() {
        return eventId != null && providerRef != null && paymentReference != null && amount != null
                && currency != null && status != null;
    }
}
