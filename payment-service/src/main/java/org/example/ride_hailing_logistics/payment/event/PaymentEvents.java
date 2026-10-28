package org.example.ride_hailing_logistics.payment.event;

/** Ten event payment-service phat len topic rhl.payment.events (qua outbox). Them truong = them cuoi (NFR-COMP-002). */
public final class PaymentEvents {

    public static final String PRODUCER = "payment";
    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String REFUND_SUCCEEDED = "RefundSucceeded";
    public static final String DRIVER_EARNING_POSTED = "DriverEarningPosted";

    private PaymentEvents() {
    }
}
