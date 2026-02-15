package net.rsworld.example.dddonion.domain.common;

import java.time.Instant;

public interface DomainEvent {
    String aggregateId();

    long sequence();

    Instant occurredAt();

    String type();
}
