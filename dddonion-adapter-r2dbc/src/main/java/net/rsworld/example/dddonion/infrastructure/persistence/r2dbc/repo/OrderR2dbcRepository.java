package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.repo;

import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.dto.OrderRow;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface OrderR2dbcRepository extends ReactiveCrudRepository<OrderRow, String> {}
