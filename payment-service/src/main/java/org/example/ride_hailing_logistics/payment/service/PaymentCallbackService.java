package org.example.ride_hailing_logistics.payment.service;

/** Callback cua cong thanh toan sandbox (NFR-SEC-010): kiem chu ky, timestamp, doi chieu, idempotent. */
public interface PaymentCallbackService {

    void handle(String body, String timestamp, String signature);
}
