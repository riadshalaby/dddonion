package net.rsworld.example.dddonion.infrastructure.outbox;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("outboxPublisherHealth")
public class OutboxPublisherHealthIndicator implements HealthIndicator {

    private final OutboxPublisher publisher;

    public OutboxPublisherHealthIndicator(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public Health health() {
        return publisher.isStarted()
                ? Health.up().withDetail("started", true).build()
                : Health.outOfService().withDetail("started", false).build();
    }
}
