package com.medcore.api.integration.sms;

/** Port for outbound SMS (reminders, 2FA codes). */
public interface SmsSender {

    void send(String phoneNumber, String message);
}
