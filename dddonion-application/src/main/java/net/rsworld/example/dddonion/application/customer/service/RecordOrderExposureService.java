package net.rsworld.example.dddonion.application.customer.service;

import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.customer.usecase.RecordOrderExposureUseCase;
import net.rsworld.example.dddonion.architecture.ApplicationComponent;
import net.rsworld.example.dddonion.domain.customer.command.RecordOrderExposureCommand;
import reactor.core.publisher.Mono;

/**
 * Records an order's amount on the customer; concurrent changes are retried by the {@link CustomerUpdater}. The event
 * path has no client to answer a conflict to, so it retries more often than the REST default.
 */
@ApplicationComponent
class RecordOrderExposureService implements RecordOrderExposureUseCase {

    static final int EVENT_MAX_ATTEMPTS = 10;

    private final CustomerUpdater updater;

    RecordOrderExposureService(CustomerUpdater updater) {
        this.updater = updater;
    }

    @Override
    public Mono<Void> handle(RecordOrderExposureCommand cmd, UseCaseMetadata metadata) {
        return updater.update(cmd.customerId(), customer -> customer.recordOrder(cmd.amount()), EVENT_MAX_ATTEMPTS)
                .then();
    }
}
