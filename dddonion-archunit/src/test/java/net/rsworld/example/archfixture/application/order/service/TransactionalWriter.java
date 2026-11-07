package net.rsworld.example.archfixture.application.order.service;

import net.rsworld.example.archfixture.domain.customer.repository.FixtureCustomerRepository;
import net.rsworld.example.dddonion.architecture.TransactionalUseCase;

/** Fixture: erlaubt, ein Service mit {@code @TransactionalUseCase} darf über Repositories schreiben. */
@TransactionalUseCase
class TransactionalWriter {
    private final FixtureCustomerRepository customers;

    TransactionalWriter(FixtureCustomerRepository customers) {
        this.customers = customers;
    }

    void writes(Object customer) {
        customers.save(customer);
        customers.delete(customer);
    }
}
