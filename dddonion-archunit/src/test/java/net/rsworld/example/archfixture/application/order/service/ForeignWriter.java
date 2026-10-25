package net.rsworld.example.archfixture.application.order.service;

import java.util.function.Consumer;
import net.rsworld.example.archfixture.domain.customer.repository.FixtureCustomerRepository;
import net.rsworld.example.archfixture.domain.order.repository.FixtureOrderRepository;

/** Fixture: an order service with violating and allowed accesses to repositories. */
class ForeignWriter {
    private final FixtureCustomerRepository customers;
    private final FixtureOrderRepository orders;

    ForeignWriter(FixtureCustomerRepository customers, FixtureOrderRepository orders) {
        this.customers = customers;
        this.orders = orders;
    }

    /** Violation: direct write call on a foreign aggregate. */
    void writesViaCall(Object customer) {
        customers.save(customer);
    }

    /** Violation: write through a method reference. */
    Consumer<Object> writesViaMethodReference() {
        return customers::save;
    }

    /** Violation: non-save write on a foreign aggregate. */
    void deletesForeign(Object customer) {
        customers.delete(customer);
    }

    /** Allowed: foreign read. */
    Object readsForeign(String id) {
        return customers.findById(id);
    }

    /** Allowed: write to the own aggregate. */
    void writesOwn(Object order) {
        orders.save(order);
    }
}
