package net.rsworld.example.archfixture.infrastructure.web;

import net.rsworld.example.archfixture.domain.customer.repository.FixtureCustomerRepository;

/** Fixture: Verstoß, ein eingehender Web-Adapter liest ein Repository, statt einen Use Case aufzurufen. */
class FixtureRepositoryController {
    private final FixtureCustomerRepository customers;

    FixtureRepositoryController(FixtureCustomerRepository customers) {
        this.customers = customers;
    }

    Object get(String id) {
        return customers.findById(id);
    }
}
