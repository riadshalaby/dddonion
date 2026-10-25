package net.rsworld.example.dddonion.application.customer.usecase;

import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.architecture.UseCase;
import net.rsworld.example.dddonion.domain.customer.command.RecordOrderExposureCommand;
import reactor.core.publisher.Mono;

/** Adds the total of a placed order to the customer's open amount. */
@UseCase(name = "customer.record-order-exposure", kind = UseCase.Kind.COMMAND)
public interface RecordOrderExposureUseCase {
    Mono<Void> handle(RecordOrderExposureCommand cmd, UseCaseMetadata metadata);
}
