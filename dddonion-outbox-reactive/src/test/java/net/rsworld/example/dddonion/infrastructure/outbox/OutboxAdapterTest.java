package net.rsworld.example.dddonion.infrastructure.outbox;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import net.rsworld.example.dddonion.domain.order.event.OrderPlaced;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class OutboxAdapterTest {

    @Test
    @DisplayName("serializes occurredAt as RFC/ISO-8601 UTC string (with Z), not numeric timestamp")
    void writes_occurredAt_as_rfc_timestamp_string_in_utc() {
        OutboxRepository repo = Mockito.mock(OutboxRepository.class);
        when(repo.save(anyString(), anyLong(), anyString(), any(), anyString())).thenReturn(Mono.empty());

        OutboxAdapter adapter = new OutboxAdapter(repo, new ObjectMapper());
        Instant occurredAt = Instant.parse("2026-01-02T03:04:05.123456Z");
        OrderPlaced event = new OrderPlaced("order-1", 7L, occurredAt, "john.doe@example.com", new BigDecimal("42.50"));

        StepVerifier.create(adapter.save(event)).verifyComplete();

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(repo).save(anyString(), anyLong(), anyString(), any(), payloadCaptor.capture());

        String payload = payloadCaptor.getValue();

        assertTrue(payload.contains("\"occurredAt\":\"" + occurredAt + "\""), payload);
        assertFalse(payload.matches(".*\"occurredAt\"\\s*:\\s*\\d+.*"), payload);
        assertTrue(payload.matches(".*\"occurredAt\"\\s*:\\s*\"[^\"]*Z\".*"), payload);
    }
}
