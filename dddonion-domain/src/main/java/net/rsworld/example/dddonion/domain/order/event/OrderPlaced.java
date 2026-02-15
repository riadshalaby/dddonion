package net.rsworld.example.dddonion.domain.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import net.rsworld.example.dddonion.domain.common.DomainEvent;

public record OrderPlaced(String orderId, long sequence, Instant occurredAt, String customerEmail, BigDecimal total)
        implements DomainEvent {
    @Override
    public String aggregateId() {
        return orderId;
    }

    @Override
    public String type() {
        return "OrderPlaced";
    }
}
