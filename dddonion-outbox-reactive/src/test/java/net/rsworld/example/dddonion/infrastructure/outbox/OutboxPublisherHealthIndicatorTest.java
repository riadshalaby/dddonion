package net.rsworld.example.dddonion.infrastructure.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.health.contributor.Health;

class OutboxPublisherHealthIndicatorTest {

    @Test
    @DisplayName("Liefert Health UP mit started=true, wenn der Publisher bereits gestartet ist")
    void reports_up_when_publisher_started() {
        OutboxPublisher publisher = Mockito.mock(OutboxPublisher.class);
        when(publisher.isStarted()).thenReturn(true);
        OutboxPublisherHealthIndicator indicator = new OutboxPublisherHealthIndicator(publisher);

        Health health = indicator.health();

        assertEquals("UP", health.getStatus().getCode());
        assertEquals(true, health.getDetails().get("started"));
    }

    @Test
    @DisplayName("Liefert Health OUT_OF_SERVICE mit started=false, wenn Publisher nicht gestartet ist")
    void reports_out_of_service_when_publisher_not_started() {
        OutboxPublisher publisher = Mockito.mock(OutboxPublisher.class);
        when(publisher.isStarted()).thenReturn(false);
        OutboxPublisherHealthIndicator indicator = new OutboxPublisherHealthIndicator(publisher);

        Health health = indicator.health();

        assertEquals("OUT_OF_SERVICE", health.getStatus().getCode());
        assertEquals(false, health.getDetails().get("started"));
    }
}
