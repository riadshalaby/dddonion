package net.rsworld.example.archfixture.application.order.service;

import java.util.function.Consumer;
import net.rsworld.example.archfixture.domain.customer.repository.FixtureCustomerRepository;
import net.rsworld.example.archfixture.domain.order.repository.FixtureOrderRepository;

/** Fixture: ein Service ohne {@code @TransactionalUseCase}, der über Repositories schreibt und liest. */
class NonTransactionalWriter {
    private final FixtureCustomerRepository customers;
    private final FixtureOrderRepository orders;

    NonTransactionalWriter(FixtureCustomerRepository customers, FixtureOrderRepository orders) {
        this.customers = customers;
        this.orders = orders;
    }

    /** Verstoß: direkter Schreibaufruf außerhalb eines transaktionalen Use Cases. */
    void writesViaCall(Object customer) {
        customers.save(customer);
    }

    /** Verstoß: Schreiben über eine Methodenreferenz. */
    Consumer<Object> writesViaMethodReference() {
        return customers::save;
    }

    /** Verstoß: Schreiben ohne save außerhalb eines transaktionalen Use Cases. */
    void deletesForeign(Object customer) {
        customers.delete(customer);
    }

    /** Erlaubt: Lesen braucht keine Transaktion. */
    Object readsForeign(String id) {
        return customers.findById(id);
    }

    /** Verstoß: Auch Schreiben auf das eigene Aggregate ist ein Schreiben außerhalb einer Transaktion. */
    void writesOwn(Object order) {
        orders.save(order);
    }
}
