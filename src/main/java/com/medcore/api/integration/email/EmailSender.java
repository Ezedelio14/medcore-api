package com.medcore.api.integration.email;

/** Port for outbound e-mail. Business code depends on this, never on a concrete provider. */
public interface EmailSender {

    void send(String to, String subject, String body);
}
