package net.rsworld.example.dddonion.domain.common;

/** Zeigt an, dass ein Aggregate nebenläufig geändert wurde (Optimistic-Locking-Konflikt). */
public class ConcurrentAggregateUpdateException extends RuntimeException {

    public ConcurrentAggregateUpdateException(String aggregateType, String id) {
        super("Concurrent update of " + aggregateType + " " + id);
    }
}
