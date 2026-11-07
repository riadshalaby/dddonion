package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.adapter;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

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
 * R2DBC-Adapter für {@link OrderRepository}.
 *
 * <p>Die Version einer Order ist zugleich die Sequenz ihrer Domain Events und steigt mit jedem Übergang
 * ({@code place()}, {@code pay()}), womöglich mehrmals zwischen Laden und Speichern. Sie wird daher unverändert
 * gespeichert und nicht von Spring Datas {@code @Version} verwaltet. Ein Update gelingt nur, wenn die Zeile noch
 * {@link Order#loadedVersion()} trägt; andernfalls hat ein anderer Schreiber die Order inzwischen geändert, und das
 * Speichern scheitert mit einer {@link ConcurrentAggregateUpdateException}. Das deckt auch den Snapshot-Konflikt von
 * MariaDB ab (Fehler 1020, siehe {@link SnapshotConflicts}). Die Version wird nie aus der Datenbankzeile übernommen.
 */
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderR2dbcRepository repo;
    private final R2dbcEntityTemplate template;

    public OrderRepositoryAdapter(OrderR2dbcRepository repo, R2dbcEntityTemplate template) {
        this.repo = repo;
        this.template = template;
    }

    @Override
    public Mono<Order> save(Order order) {
        // verzögert: Die Row wird pro Subscription neu gebaut, daher verwendet eine erneute Subscription nie eine Row,
        // die ein früherer Versuch verändert hat
        return Mono.defer(() -> {
            OrderRow row = OrderMapper.toRow(order);
            return repo.existsById(row.getId())
                    .flatMap(exists -> exists ? update(order, row) : template.insert(row))
                    .map(OrderMapper::toDomain)
                    .onErrorMap(
                            SnapshotConflicts::isSnapshotConflict,
                            e -> new ConcurrentAggregateUpdateException("Order", row.getId()));
        });
    }

    @Override
    public Mono<Order> findById(OrderId id) {
        return repo.findById(id.value()).map(OrderMapper::toDomain);
    }

    /**
     * {@code UPDATE ... WHERE id = ? AND version = loadedVersion}; keine passende Zeile bedeutet eine nebenläufige
     * Änderung.
     */
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
