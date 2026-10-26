package net.rsworld.example.dddonion.application.customer.service;

import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.customer.usecase.ChangeCreditLimitUseCase;
import net.rsworld.example.dddonion.architecture.ApplicationComponent;
import net.rsworld.example.dddonion.architecture.TransactionalUseCase;
import net.rsworld.example.dddonion.domain.customer.command.ChangeCreditLimitCommand;
import net.rsworld.example.dddonion.domain.customer.model.CustomerNotFoundException;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import reactor.core.publisher.Mono;

/**
 * Changes the credit limit of an existing customer. Retry on a version conflict is part of the pipeline: every attempt
 * subscribes the lazy chain again, so the customer is loaded afresh in a new transaction.
 */
@TransactionalUseCase
@ApplicationComponent
class ChangeCreditLimitService implements ChangeCreditLimitUseCase {

    private final CustomerRepository customers;

    ChangeCreditLimitService(CustomerRepository customers) {
        this.customers = customers;
    }

    @Override
    public Mono<Void> handle(ChangeCreditLimitCommand cmd, UseCaseMetadata metadata) {
        return customers
                .findById(cmd.customerId())
                .switchIfEmpty(Mono.error(() -> new CustomerNotFoundException(cmd.customerId())))
                .doOnNext(customer -> customer.changeCreditLimit(cmd.creditLimit()))
                .flatMap(customers::save)
                .then();
    }
}
