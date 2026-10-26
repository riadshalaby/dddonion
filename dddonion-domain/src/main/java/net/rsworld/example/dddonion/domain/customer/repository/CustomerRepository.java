package net.rsworld.example.dddonion.domain.customer.repository;

import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import reactor.core.publisher.Mono;

/** Domain-Port: reactive and lazy, work starts on subscription. Not-Found is signaled by an empty {@link Mono}. */
public interface CustomerRepository {

    Mono<Customer> save(Customer customer);

    Mono<Customer> findById(CustomerId id);
}
