package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.adapter;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.dto.OrderRow;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.mapper.OrderMapper;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.repo.OrderR2dbcRepository;
import reactor.core.publisher.Mono;

public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderR2dbcRepository repo;

    public OrderRepositoryAdapter(OrderR2dbcRepository repo) {
        this.repo = repo;
    }

    @Override
    public CompletionStage<Order> save(Order order) {
        OrderRow row = OrderMapper.toRow(order);

        // Check existence by ID; insert path => force version to null
        Mono<Order> result = repo.findById(row.getId())
                .flatMap(existing -> {
                    // UPDATE path: adopt version from DB (avoids OptimisticLocking on first save)
                    row.setVersion(existing.getVersion());
                    return repo.save(row).map(OrderMapper::toDomain);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // INSERT path: Version null -> Spring Data does INSERT + initial version (DEFAULT 0)
                    row.setVersion(null);
                    return repo.save(row).map(OrderMapper::toDomain);
                }));

        return result.toFuture();
    }

    @Override
    public CompletionStage<Optional<Order>> findById(OrderId id) {
        return repo.findById(id.value())
                .map(OrderMapper::toDomain)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .toFuture();
    }
}
