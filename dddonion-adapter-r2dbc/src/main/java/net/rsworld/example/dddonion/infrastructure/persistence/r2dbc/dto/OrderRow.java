package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("orders")
public class OrderRow {

    @Id
    private String id;

    @Column("customer_id")
    private String customerId;

    @Column("total")
    private BigDecimal total;

    @Column("status")
    private String status;

    @Column("version")
    private Long version; // domain version (event sequence); optimistic locking is done by OrderRepositoryAdapter

    // managed by the database (DEFAULT / ON UPDATE); never written by the application
    @ReadOnlyProperty
    @Column("created_at")
    private LocalDateTime createdAt;

    @ReadOnlyProperty
    @Column("updated_at")
    private LocalDateTime updatedAt;
}
