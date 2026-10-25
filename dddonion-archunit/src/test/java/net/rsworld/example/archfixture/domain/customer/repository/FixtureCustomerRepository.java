package net.rsworld.example.archfixture.domain.customer.repository;

/** Fixture: stands in for a foreign aggregate's repository port. */
public interface FixtureCustomerRepository {
    void save(Object customer);

    void delete(Object customer);

    Object findById(String id);
}
