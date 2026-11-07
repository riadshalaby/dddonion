package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.adapter;

import net.rsworld.example.dddonion.domain.common.ConcurrentAggregateUpdateException;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.customer.model.EmailAlreadyRegisteredException;
import net.rsworld.example.dddonion.domain.customer.repository.CustomerRepository;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.dto.CustomerRow;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.mapper.CustomerMapper;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.repo.CustomerR2dbcRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;

/**
 * R2DBC-Adapter für {@link CustomerRepository}.
 *
 * <p>Die Version eines Updates stammt immer aus dem Domain-Objekt und wird nie aus der Datenbankzeile übernommen. Sonst
 * wäre das Optimistic Locking ausgehebelt, weil ein veraltetes Aggregate neuere Daten stillschweigend überschreiben
 * würde. Die Customer-Version ist ein reines Concurrency-Token, daher verwaltet sie Spring Datas {@code @Version}. Eine
 * veraltete Version führt zu einer {@link ConcurrentAggregateUpdateException}, ein Duplikat auf dem Unique Key
 * {@code uq_customers_email} zu einer {@link EmailAlreadyRegisteredException}; jedes andere Duplikat wird
 * weitergereicht.
 */
public class CustomerRepositoryAdapter implements CustomerRepository {

    private static final String EMAIL_UNIQUE_KEY = "uq_customers_email";

    private final CustomerR2dbcRepository repo;

    public CustomerRepositoryAdapter(CustomerR2dbcRepository repo) {
        this.repo = repo;
    }

    @Override
    public Mono<Customer> save(Customer customer) {
        // verzögert: Die Row wird pro Subscription neu gebaut, daher verwendet eine erneute Subscription nie eine Row,
        // die ein früherer Versuch verändert hat
        return Mono.defer(() -> {
                    CustomerRow row = CustomerMapper.toRow(customer);
                    return repo.existsById(row.getId()).flatMap(exists -> {
                        if (!exists) {
                            // INSERT-Pfad: Version null -> Spring Data fügt mit der Startversion ein
                            row.setVersion(null);
                        }
                        // UPDATE-Pfad: Die Version bleibt die des Domain-Objekts (UPDATE ... WHERE version = ?)
                        return repo.save(row);
                    });
                })
                .map(CustomerMapper::toDomain)
                .onErrorMap(
                        e -> e instanceof OptimisticLockingFailureException || SnapshotConflicts.isSnapshotConflict(e),
                        e -> new ConcurrentAggregateUpdateException(
                                "Customer", customer.id().value()))
                .onErrorMap(
                        e -> e instanceof DuplicateKeyException && violates(e, EMAIL_UNIQUE_KEY),
                        e -> new EmailAlreadyRegisteredException(customer.email()));
    }

    @Override
    public Mono<Customer> findById(CustomerId id) {
        return repo.findById(id.value()).map(CustomerMapper::toDomain);
    }

    /**
     * Nur der Unique Key auf der E-Mail gilt als E-Mail-Konflikt; andere Duplikate (z. B. Primärschlüssel) werden
     * durchgereicht.
     */
    private static boolean violates(Throwable error, String constraint) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
