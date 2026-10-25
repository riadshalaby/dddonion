package net.rsworld.example.archfixture.domain.order.repository;

/** Fixture: stands in for the order aggregate's own repository port. */
public interface FixtureOrderRepository {
    void save(Object order);
}
