package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.adapter;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import java.util.Optional;
import java.util.concurrent.CompletionStage;
import net.rsworld.example.dddonion.domain.common.ConcurrentAggregateUpdateException;
import net.rsworld.example.dddonion.domain.order.model.Order;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import net.rsworld.example.dddonion.domain.order.repository.OrderRepository;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.dto.OrderRow;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.mapper.OrderMapper;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.repo.OrderR2dbcRepository;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Update;
import reactor.core.publisher.Mono;

/**
 * R2DBC adapter for {@link OrderRepository}.
 *
 * <p>The version of an order is also the sequence of its domain events and advances with every transition
 * ({@code place()}, {@code pay()}), possibly several times between load and save. It is therefore stored as is, not
 * managed by Spring Data's {@code @Version}. An update only succeeds if the row still carries
 * {@link Order#loadedVersion()}; otherwise another writer changed the order in the meantime and the save fails with a
 * {@link ConcurrentAggregateUpdateException}. The version is never adopted from the database row.
 */
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderR2dbcRepository repo;
    private final R2dbcEntityTemplate template;

    public OrderRepositoryAdapter(OrderR2dbcRepository repo, R2dbcEntityTemplate template) {
        this.repo = repo;
        this.template = template;
    }

    @Override
    public CompletionStage<Order> save(Order order) {
        OrderRow row = OrderMapper.toRow(order);

        Mono<Order> result = repo.existsById(row.getId())
                .flatMap(exists -> exists ? update(order, row) : template.insert(row))
                .map(OrderMapper::toDomain);

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

    /** {@code UPDATE ... WHERE id = ? AND version = loadedVersion}; no matched row means a concurrent change. */
    private Mono<OrderRow> update(Order order, OrderRow row) {
        return template.update(OrderRow.class)
                .matching(query(where("id").is(row.getId()).and("version").is(order.loadedVersion())))
                .apply(Update.update("customerId", row.getCustomerId())
                        .set("total", row.getTotal())
                        .set("status", row.getStatus())
                        .set("version", row.getVersion()))
                .flatMap(updated -> updated == 0
                        ? Mono.error(new ConcurrentAggregateUpdateException("Order", row.getId()))
                        : Mono.just(row));
    }
}
