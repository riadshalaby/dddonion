package net.rsworld.example.dddonion.application.customer.usecase;

import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.architecture.UseCase;
import net.rsworld.example.dddonion.domain.customer.command.ChangeCreditLimitCommand;
import reactor.core.publisher.Mono;

/** Scheitert bei einem unbekannten Customer mit {@code CustomerNotFoundException}. */
@UseCase(name = "customer.change-credit-limit", kind = UseCase.Kind.COMMAND)
public interface ChangeCreditLimitUseCase {
    Mono<Void> handle(ChangeCreditLimitCommand cmd, UseCaseMetadata metadata);
}
