package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.repo;

import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.dto.CustomerRow;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface CustomerR2dbcRepository extends ReactiveCrudRepository<CustomerRow, String> {}
