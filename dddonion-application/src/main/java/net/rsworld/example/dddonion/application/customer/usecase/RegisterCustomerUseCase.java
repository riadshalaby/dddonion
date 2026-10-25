package net.rsworld.example.dddonion.application.customer.usecase;

import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.architecture.UseCase;
import net.rsworld.example.dddonion.domain.customer.command.RegisterCustomerCommand;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import reactor.core.publisher.Mono;

@UseCase(name = "customer.register", kind = UseCase.Kind.COMMAND)
public interface RegisterCustomerUseCase {
    Mono<CustomerId> handle(RegisterCustomerCommand cmd, UseCaseMetadata metadata);
}
