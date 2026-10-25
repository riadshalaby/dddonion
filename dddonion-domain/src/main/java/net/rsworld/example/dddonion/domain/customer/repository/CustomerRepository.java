package net.rsworld.example.dddonion.domain.customer.repository;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;

/** Domain-Port: framework-free (JDK types). Not-Found is signaled via Optional. */
public interface CustomerRepository {

    CompletionStage<Customer> save(Customer customer);

    CompletionStage<Optional<Customer>> findById(CustomerId id);
}
