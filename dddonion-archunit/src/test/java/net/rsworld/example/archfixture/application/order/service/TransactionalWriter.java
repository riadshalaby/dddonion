package net.rsworld.example.archfixture.application.order.service;

import net.rsworld.example.archfixture.domain.customer.repository.FixtureCustomerRepository;
import net.rsworld.example.dddonion.architecture.TransactionalUseCase;

/** Fixture: allowed, a service annotated with {@code @TransactionalUseCase} may write through repositories. */
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
