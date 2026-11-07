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
 * Ändert das Kreditlimit eines bestehenden Customers. Der Retry bei einem Versionskonflikt ist Teil der Pipeline: Jeder
 * Versuch abonniert die lazy Kette neu, daher wird der Customer in einer neuen Transaktion frisch geladen.
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
