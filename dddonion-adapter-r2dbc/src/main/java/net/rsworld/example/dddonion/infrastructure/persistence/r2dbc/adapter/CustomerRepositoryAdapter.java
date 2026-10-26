package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.adapter;

import io.r2dbc.spi.R2dbcException;
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
 * R2DBC adapter for {@link CustomerRepository}.
 *
 * <p>The version of an update always comes from the domain object and is never adopted from the database row. Otherwise
 * the optimistic lock would be defeated, because a stale aggregate would silently overwrite newer data. The customer
 * version is a pure concurrency token, so Spring Data's {@code @Version} manages it. A stale version results in a
 * {@link ConcurrentAggregateUpdateException}; a duplicate on the unique key {@code uq_customers_email} in an
 * {@link EmailAlreadyRegisteredException}; any other duplicate key is passed on.
 */
public class CustomerRepositoryAdapter implements CustomerRepository {

    private static final String EMAIL_UNIQUE_KEY = "uq_customers_email";
    private static final int SNAPSHOT_CONFLICT_ERROR_CODE = 1020; // MariaDB ER_CHECKREAD

    private final CustomerR2dbcRepository repo;

    public CustomerRepositoryAdapter(CustomerR2dbcRepository repo) {
        this.repo = repo;
    }

    @Override
    public Mono<Customer> save(Customer customer) {
        // deferred: the row is built per subscription, so resubscribing never reuses a row mutated by an earlier
        // attempt
        return Mono.defer(() -> {
                    CustomerRow row = CustomerMapper.toRow(customer);
                    return repo.existsById(row.getId()).flatMap(exists -> {
                        if (!exists) {
                            // INSERT path: version null -> Spring Data inserts with the initial version
                            row.setVersion(null);
                        }
                        // UPDATE path: version stays the one of the domain object (UPDATE ... WHERE version = ?)
                        return repo.save(row);
                    });
                })
                .map(CustomerMapper::toDomain)
                .onErrorMap(
                        e -> e instanceof OptimisticLockingFailureException || isSnapshotConflict(e),
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
     * Inside a transaction MariaDB (snapshot isolation) rejects an UPDATE on a row that another transaction changed
     * since this one read it with error 1020 ("Record has changed since last read") instead of matching zero rows. That
     * is the same optimistic-lock conflict.
     */
    private static boolean isSnapshotConflict(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof R2dbcException r2dbc && r2dbc.getErrorCode() == SNAPSHOT_CONFLICT_ERROR_CODE) {
                return true;
            }
        }
        return false;
    }

    /** Only the e-mail unique key counts as an e-mail conflict; other duplicates (e.g. primary key) pass through. */
    private static boolean violates(Throwable error, String constraint) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains(constraint)) {
                return true;
            }
        }
        return false;
    }
}
