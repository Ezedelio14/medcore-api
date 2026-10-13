package com.medcore.api.messaging.consumer;

import com.medcore.api.messaging.EventEnvelope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class EventEnvelopeReader {

    private final JsonMapper jsonMapper;

    public EventEnvelope read(String message) {
        return jsonMapper.readValue(message, EventEnvelope.class);
    }

    public <T> T payload(EventEnvelope envelope, Class<T> type) {
        return jsonMapper.treeToValue(envelope.payload(), type);
    }
}
