package com.moveinsync.config.server.propagation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class SseSession {
    private static final Logger log = LoggerFactory.getLogger(SseSession.class);

    private final String tenantId;
    private final String serviceId;
    private final SseEmitter emitter;
    private final BlockingQueue<SseEvent> queue = new ArrayBlockingQueue<>(50);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Runnable onSlowConsumerDrop;

    public record SseEvent(String name, String data) {}

    public SseSession(String tenantId, String serviceId, SseEmitter emitter, Runnable onSlowConsumerDrop) {
        this.tenantId = tenantId;
        this.serviceId = serviceId;
        this.emitter = emitter;
        this.onSlowConsumerDrop = onSlowConsumerDrop;
    }

    public String tenantId() {
        return tenantId;
    }

    public String serviceId() {
        return serviceId;
    }

    public SseEmitter emitter() {
        return emitter;
    }

    public boolean isClosed() {
        return closed.get();
    }

    public void send(String eventName, String data) {
        if (closed.get()) return;

        synchronized (this) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                log.debug("Failed to send SSE to session {}: {}", serviceId, e.getMessage());
                close();
            }
        }
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }
}

