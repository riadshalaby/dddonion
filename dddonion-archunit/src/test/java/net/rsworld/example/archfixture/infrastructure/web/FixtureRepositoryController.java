package net.rsworld.example.archfixture.infrastructure.web;

import net.rsworld.example.archfixture.domain.customer.repository.FixtureCustomerRepository;

/** Fixture: violation, an inbound web adapter reading a repository instead of calling a use case. */
class FixtureRepositoryController {
    private final FixtureCustomerRepository customers;

    FixtureRepositoryController(FixtureCustomerRepository customers) {
        this.customers = customers;
    }

    Object get(String id) {
        return customers.findById(id);
    }
}
