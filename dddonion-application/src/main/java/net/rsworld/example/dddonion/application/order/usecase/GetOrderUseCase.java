package net.rsworld.example.dddonion.application.order.usecase;

import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.architecture.UseCase;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import reactor.core.publisher.Mono;

/** Query-Use-Case: liefert eine Order zur Id. Ein leeres Ergebnis bedeutet „nicht gefunden“. */
@UseCase(name = "order.get", kind = UseCase.Kind.QUERY)
public interface GetOrderUseCase {
    Mono<Order> findById(OrderId id, UseCaseMetadata metadata);
}
