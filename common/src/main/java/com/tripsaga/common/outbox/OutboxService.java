package com.tripsaga.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
    private final OutboxRepository repo;
    private final ObjectMapper mapper;
    private final ObjectProvider<Tracer> tracer;

    public OutboxService(OutboxRepository repo, ObjectMapper mapper, ObjectProvider<Tracer> tracer) {
        this.repo = repo;
        this.mapper = mapper;
        this.tracer = tracer;
    }

    /** W3C traceparent of the current span, or null when tracing is off. */
    private String currentTraceparent() {
        Tracer t = tracer.getIfAvailable();
        if (t == null || t.currentSpan() == null) return null;
        TraceContext c = t.currentSpan().context();
        if (c.traceId() == null || c.traceId().length() != 32 || c.traceId().matches("0+")) return null;
        return "00-" + c.traceId() + "-" + c.spanId() + "-" + (Boolean.TRUE.equals(c.sampled()) ? "01" : "00");
    }

    /** MANDATORY: fails fast if called outside the business transaction, which would defeat the pattern. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String topic, String key, Object message) {
        try {
            repo.save(new OutboxEvent(topic, key, mapper.writeValueAsString(message),
                    message.getClass().getSimpleName(), currentTraceparent()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize outbox message", e);
        }
    }
}
