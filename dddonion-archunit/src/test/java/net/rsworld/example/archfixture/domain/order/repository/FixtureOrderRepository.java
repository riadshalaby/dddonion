package net.rsworld.example.archfixture.domain.order.repository;

/** Fixture: steht für den eigenen Repository-Port des Order-Aggregates. */
public interface FixtureOrderRepository {
    void save(Object order);
}
