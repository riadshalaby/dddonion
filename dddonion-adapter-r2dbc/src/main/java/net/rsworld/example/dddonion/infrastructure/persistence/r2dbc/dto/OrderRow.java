package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("orders")
public class OrderRow {

    @Id
    private String id;

    @Column("customer_email")
    private String customerEmail;

    @Column("total")
    private BigDecimal total;

    @Column("status")
    private String status;

    @Version
    private Long version; // Spring Data R2DBC uses this for optimistic locking

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
