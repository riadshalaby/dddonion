package net.rsworld.example.dddonion.infrastructure.outbox;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class OutboxCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);

    private final OutboxRepository repo;
    private final long retentionDays;

    public OutboxCleanupJob(
            OutboxRepository repo, @Value("${app.outbox.cleanup.retention-days:7}") long retentionDays) {
        this.repo = repo;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${app.outbox.cleanup.cron:0 0 3 * * *}")
    public void cleanup() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        repo.deletePublishedOlderThan(cutoff)
                .doOnNext(rows -> log.info("Outbox cleanup deleted {} rows older than {}", rows, cutoff))
                .doOnError(err -> log.error("Outbox cleanup failed", err))
                .onErrorResume(err -> Mono.empty())
                .subscribe();
    }
}
