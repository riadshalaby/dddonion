package net.rsworld.example.dddonion.application.order.usecase;

import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.domain.order.command.PlaceOrderCommand;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import reactor.core.publisher.Mono;

public interface PlaceOrderUseCase {
    Mono<OrderId> handle(PlaceOrderCommand cmd, UseCaseMetadata metadata);
}
