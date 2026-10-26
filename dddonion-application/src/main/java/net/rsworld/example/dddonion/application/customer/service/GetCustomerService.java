package net.rsworld.example.dddonion.application.customer.service;

import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.customer.usecase.GetCustomerUseCase;
import net.rsworld.example.dddonion.architecture.ApplicationComponent;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import reactor.core.publisher.Mono;

@ApplicationComponent
class GetCustomerService implements GetCustomerUseCase {

    private final CustomerRepository customers;

    GetCustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    public Mono<Customer> findById(CustomerId id, UseCaseMetadata metadata) {
        return customers.findById(id);
    }
}
