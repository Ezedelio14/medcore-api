package com.medcore.api.messaging;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Wire format for every Kafka message produced by the platform. */
public record EventEnvelope(UUID eventId, String eventType, Instant occurredAt, String correlationId, JsonNode payload) {
}
