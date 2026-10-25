package net.rsworld.example.dddonion.domain.common;

/** Signals that an aggregate was modified concurrently (optimistic-locking conflict). */
public class ConcurrentAggregateUpdateException extends RuntimeException {

    public ConcurrentAggregateUpdateException(String aggregateType, String id) {
        super("Concurrent update of " + aggregateType + " " + id);
    }
}
