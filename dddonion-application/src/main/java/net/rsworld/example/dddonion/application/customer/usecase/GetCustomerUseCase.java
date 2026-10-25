package net.rsworld.example.dddonion.application.customer.usecase;

import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.architecture.UseCase;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import reactor.core.publisher.Mono;

/** Query use case: resolves a customer by id. An empty result signals "not found". */
@UseCase(name = "customer.get", kind = UseCase.Kind.QUERY)
public interface GetCustomerUseCase {
    Mono<Customer> findById(CustomerId id, UseCaseMetadata metadata);
}
