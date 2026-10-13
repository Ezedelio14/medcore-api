package com.medcore.api.integration.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Development adapter that only logs messages. Replace with a provider adapter (Twilio, AWS SNS, Vonage...)
 * implementing {@link SmsSender}; no business code needs to change.
 */
@Slf4j
@Component
public class LoggingSmsAdapter implements SmsSender {

    @Override
    public void send(String phoneNumber, String message) {
        String masked = phoneNumber == null || phoneNumber.length() < 4
                ? "****" : "****" + phoneNumber.substring(phoneNumber.length() - 4);
        log.info("[SMS -> {}] {}", masked, message);
    }
}
