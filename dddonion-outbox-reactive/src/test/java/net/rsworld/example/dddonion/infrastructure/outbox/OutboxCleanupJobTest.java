package net.rsworld.example.dddonion.infrastructure.outbox;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import reactor.core.publisher.Mono;

class OutboxCleanupJobTest {

    @Test
    @DisplayName("Verwendet beim Cleanup einen Cutoff auf Basis der konfigurierten Retention-Days")
    void cleanup_uses_cutoff_based_on_retention_days() {
        OutboxRepository repo = Mockito.mock(OutboxRepository.class);
        when(repo.deletePublishedOlderThan(any())).thenReturn(Mono.just(3L));
        OutboxCleanupJob job = new OutboxCleanupJob(repo, 7);

        job.cleanup();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(repo).deletePublishedOlderThan(cutoffCaptor.capture());

        Instant cutoff = cutoffCaptor.getValue();
        Instant expected = Instant.now().minus(Duration.ofDays(7));
        long driftSeconds = Math.abs(Duration.between(cutoff, expected).toSeconds());
        assertTrue(driftSeconds < 3, "cutoff should be close to now - retentionDays");
    }

    @Test
    @DisplayName("Fängt Repository-Fehler im Cleanup ab und wirft keine Exception nach außen")
    void cleanup_swallows_repository_errors() {
        OutboxRepository repo = Mockito.mock(OutboxRepository.class);
        when(repo.deletePublishedOlderThan(any())).thenReturn(Mono.error(new RuntimeException("db down")));
        OutboxCleanupJob job = new OutboxCleanupJob(repo, 7);

        assertDoesNotThrow(job::cleanup);
        verify(repo).deletePublishedOlderThan(any());
    }
}
