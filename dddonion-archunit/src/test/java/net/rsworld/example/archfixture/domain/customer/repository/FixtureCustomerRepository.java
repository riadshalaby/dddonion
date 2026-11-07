package net.rsworld.example.archfixture.domain.customer.repository;

/** Fixture: steht für den Repository-Port eines fremden Aggregates. */
public interface FixtureCustomerRepository {
    void save(Object customer);

    void delete(Object customer);

    Object findById(String id);
}
