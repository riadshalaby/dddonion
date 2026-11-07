package net.rsworld.example.dddonion.domain.customer.repository;

import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import reactor.core.publisher.Mono;

/**
 * Domain-Port: reaktiv und lazy, die Arbeit beginnt erst mit der Subscription. „Nicht gefunden“ wird durch einen leeren
 * {@link Mono} signalisiert.
 */
public interface CustomerRepository {

    Mono<Customer> save(Customer customer);

    Mono<Customer> findById(CustomerId id);
}
