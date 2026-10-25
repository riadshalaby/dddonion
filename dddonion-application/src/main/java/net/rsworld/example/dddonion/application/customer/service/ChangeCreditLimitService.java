package net.rsworld.example.dddonion.application.customer.service;

import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.customer.usecase.ChangeCreditLimitUseCase;
import net.rsworld.example.dddonion.architecture.ApplicationComponent;
import net.rsworld.example.dddonion.domain.customer.command.ChangeCreditLimitCommand;
import reactor.core.publisher.Mono;

@ApplicationComponent
class ChangeCreditLimitService implements ChangeCreditLimitUseCase {

    private final CustomerUpdater updater;

    ChangeCreditLimitService(CustomerUpdater updater) {
        this.updater = updater;
    }

    @Override
    public Mono<Void> handle(ChangeCreditLimitCommand cmd, UseCaseMetadata metadata) {
        return updater.update(cmd.customerId(), customer -> customer.changeCreditLimit(cmd.creditLimit()))
                .then();
    }
}
