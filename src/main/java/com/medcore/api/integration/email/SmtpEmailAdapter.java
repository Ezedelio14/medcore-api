package com.medcore.api.integration.email;

import com.medcore.api.common.exception.ExternalServiceException;
import com.medcore.api.config.MedcoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Adapter: translates the {@link EmailSender} port to Spring's {@link JavaMailSender} (SMTP). */
@Slf4j
@Component
public class SmtpEmailAdapter implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailAdapter(JavaMailSender mailSender, MedcoreProperties properties) {
        this.mailSender = mailSender;
        this.from = properties.mail().from();
    }

    @Override
    public void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
            log.debug("E-mail '{}' sent", subject);
        } catch (MailException e) {
            throw new ExternalServiceException("SMTP", "could not send e-mail", e);
        }
    }
}
