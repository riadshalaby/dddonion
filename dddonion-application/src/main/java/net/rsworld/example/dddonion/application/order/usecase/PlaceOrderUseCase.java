package net.rsworld.example.dddonion.application.order.usecase;

import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.architecture.UseCase;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import reactor.core.publisher.Mono;

@UseCase(name = "order.place", kind = UseCase.Kind.COMMAND)
public interface PlaceOrderUseCase {
    Mono<OrderId> handle(PlaceOrderCommand cmd, UseCaseMetadata metadata);
}
