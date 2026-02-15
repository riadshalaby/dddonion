package net.rsworld.example.dddonion.infrastructure.outbox;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class OutboxRepository {

    private final DatabaseClient db;

    public OutboxRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Void> save(String aggregateId, long sequence, String type, Instant occurredAt, String payloadJson) {
        String id = UUID.randomUUID().toString();
        // MariaDB R2DBC cannot handle Instant -> convert to UTC LocalDateTime
        LocalDateTime occurredAtUtc = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);

        return db.sql("INSERT INTO outbox_events "
                        + "(id, aggregate_id, sequence, type, occurred_at, payload_json, published) "
                        + "VALUES (:id, :aggregateId, :sequence, :type, :occurredAt, :payloadJson, false)")
                .bind("id", id)
                .bind("aggregateId", aggregateId)
                .bind("sequence", sequence)
                .bind("type", type)
                .bind("occurredAt", occurredAtUtc)
                .bind("payloadJson", payloadJson)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Flux<StoredEvent> findBatch(int limit) {
        return db.sql("SELECT id, aggregate_id, sequence, type, occurred_at, payload_json, published "
                        + "FROM outbox_events WHERE published=false "
                        + "ORDER BY aggregate_id ASC, sequence ASC LIMIT "
                        + limit)
                .map((row, meta) -> {
                    LocalDateTime ldt = row.get("occurred_at", LocalDateTime.class);
                    Instant occurred =
                            (ldt != null ? ldt.atOffset(ZoneOffset.UTC).toInstant() : null);
                    return new StoredEvent(
                            row.get("id", String.class),
                            row.get("aggregate_id", String.class),
                            row.get("sequence", Long.class),
                            row.get("type", String.class),
                            occurred,
                            row.get("payload_json", String.class),
                            Boolean.TRUE.equals(row.get("published", Boolean.class)));
                })
                .all();
    }

    public Mono<Long> markPublished(String id) {
        return db.sql("UPDATE outbox_events SET published=true WHERE id=:id AND published=false")
                .bind("id", id)
                .fetch()
                .rowsUpdated();
    }

    public Mono<Long> deletePublishedOlderThan(Instant cutoff) {
        // also convert cutoff to LocalDateTime(UTC)
        LocalDateTime cutoffUtc = LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC);
        return db.sql("DELETE FROM outbox_events WHERE published=true AND occurred_at < :cutoff")
                .bind("cutoff", cutoffUtc)
                .fetch()
                .rowsUpdated();
    }
}
