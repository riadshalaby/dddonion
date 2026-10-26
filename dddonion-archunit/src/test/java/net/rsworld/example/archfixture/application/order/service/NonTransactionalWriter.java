package net.rsworld.example.archfixture.application.order.service;

import java.util.function.Consumer;
import net.rsworld.example.archfixture.domain.customer.repository.FixtureCustomerRepository;
import net.rsworld.example.archfixture.domain.order.repository.FixtureOrderRepository;

/** Fixture: a service without {@code @TransactionalUseCase} that writes through repositories and reads. */
class NonTransactionalWriter {
    private final FixtureCustomerRepository customers;
    private final FixtureOrderRepository orders;

    NonTransactionalWriter(FixtureCustomerRepository customers, FixtureOrderRepository orders) {
        this.customers = customers;
        this.orders = orders;
    }

    /** Violation: direct write call outside a transactional use case. */
    void writesViaCall(Object customer) {
        customers.save(customer);
    }

    /** Violation: write through a method reference. */
    Consumer<Object> writesViaMethodReference() {
        return customers::save;
    }

    /** Violation: non-save write outside a transactional use case. */
    void deletesForeign(Object customer) {
        customers.delete(customer);
    }

    /** Allowed: reads need no transaction. */
    Object readsForeign(String id) {
        return customers.findById(id);
    }

    /** Violation: write to the own aggregate is also a write outside a transaction. */
    void writesOwn(Object order) {
        orders.save(order);
    }
}
