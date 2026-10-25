package net.rsworld.example.archfixture.infrastructure.event;

import net.rsworld.example.archfixture.domain.customer.repository.FixtureCustomerRepository;

/** Fixture: violation, an inbound event adapter writing through a repository instead of calling a use case. */
class FixtureRepositoryListener {
    private final FixtureCustomerRepository customers;

    FixtureRepositoryListener(FixtureCustomerRepository customers) {
        this.customers = customers;
    }

    void on(Object customer) {
        customers.save(customer);
    }
}
