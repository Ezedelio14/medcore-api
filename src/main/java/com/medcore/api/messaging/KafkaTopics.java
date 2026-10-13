package com.medcore.api.messaging;

import java.util.List;

public final class KafkaTopics {

    public static final String APPOINTMENT_CREATED = "appointment.created";
    public static final String APPOINTMENT_CONFIRMED = "appointment.confirmed";
    public static final String APPOINTMENT_CANCELLED = "appointment.cancelled";
    public static final String APPOINTMENT_COMPLETED = "appointment.completed";
    public static final String APPOINTMENT_RESCHEDULED = "appointment.rescheduled";
    public static final String APPOINTMENT_REMINDER = "appointment.reminder";
    public static final String PAYMENT_CREATED = "payment.created";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String PAYMENT_REFUNDED = "payment.refunded";
    public static final String PRESCRIPTION_CREATED = "prescription.created";
    public static final String NOTIFICATION_CREATED = "notification.created";

    public static final String DLT_SUFFIX = ".DLT";

    public static final List<String> ALL = List.of(
            APPOINTMENT_CREATED, APPOINTMENT_CONFIRMED, APPOINTMENT_CANCELLED, APPOINTMENT_COMPLETED,
            APPOINTMENT_RESCHEDULED, APPOINTMENT_REMINDER,
            PAYMENT_CREATED, PAYMENT_COMPLETED, PAYMENT_FAILED, PAYMENT_REFUNDED,
            PRESCRIPTION_CREATED, NOTIFICATION_CREATED);

    private KafkaTopics() {
    }
}
