package com.medcore.api.payment;

/** Also identifies the payment strategy used to settle the appointment. */
public enum PaymentProvider {
    /** Online card payment through Stripe. */
    STRIPE,
    /** Paid in person at the clinic front desk. */
    CLINIC,
    /** Covered by a health insurance plan. */
    INSURANCE
}
