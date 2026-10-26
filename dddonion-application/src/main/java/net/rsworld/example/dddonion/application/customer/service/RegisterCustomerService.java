package net.rsworld.example.dddonion.application.customer.service;

import lombok.extern.slf4j.Slf4j;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.customer.usecase.RegisterCustomerUseCase;
import net.rsworld.example.dddonion.architecture.ApplicationComponent;
import net.rsworld.example.dddonion.domain.customer.command.RegisterCustomerCommand;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import reactor.core.publisher.Mono;

/** Registers a customer. A duplicate e-mail surfaces as {@code EmailAlreadyRegisteredException} from the port. */
@Slf4j
@ApplicationComponent
class RegisterCustomerService implements RegisterCustomerUseCase {

    private final CustomerRepository customers;

    RegisterCustomerService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    public Mono<CustomerId> handle(RegisterCustomerCommand cmd, UseCaseMetadata metadata) {
        var customer = new Customer(cmd.email(), cmd.creditLimit());
        return customers
                .save(customer)
                .map(Customer::id)
                // log only the id: the e-mail is PII
                .doOnNext(id -> log.info("Customer {} registered", id.value()));
    }
}
