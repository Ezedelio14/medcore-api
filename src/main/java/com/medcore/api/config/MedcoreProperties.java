package com.medcore.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Typed binding of the {@code medcore.*} configuration tree. */
@ConfigurationProperties(prefix = "medcore")
public record MedcoreProperties(
        Security security,
        RateLimit rateLimit,
        Stripe stripe,
        Storage storage,
        Mail mail,
        String frontendUrl,
        Appointments appointments,
        Kafka kafka) {

    public record Security(Jwt jwt, Duration passwordResetTtl, Duration emailVerificationTtl, Duration twoFactorCodeTtl) {
    }

    public record Jwt(String secret, String issuer, Duration accessTokenTtl, Duration refreshTokenTtl) {
    }

    public record RateLimit(boolean enabled, int requestsPerMinute, int authRequestsPerMinute) {
    }

    public record Stripe(String apiKey, String webhookSecret, String defaultCurrency) {
    }

    public record Storage(String localRoot) {
    }

    public record Mail(String from) {
    }

    public record Appointments(Duration reminderLeadTime, String reminderCron, Duration cancellationMinNotice) {
    }

    public record Kafka(int retryAttempts, Duration retryBackoff) {
    }
}
