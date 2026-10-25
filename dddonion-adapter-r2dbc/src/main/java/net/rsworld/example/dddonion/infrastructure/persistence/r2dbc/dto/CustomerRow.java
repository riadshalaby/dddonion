package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("customers")
public class CustomerRow {

    @Id
    private String id;

    @Column("email")
    private String email;

    @Column("credit_limit")
    private BigDecimal creditLimit;

    @Column("open_amount")
    private BigDecimal openAmount;

    @Version
    private Long version; // Spring Data R2DBC uses this for optimistic locking

    @ReadOnlyProperty // managed by DB defaults, never written by Spring Data
    @Column("created_at")
    private LocalDateTime createdAt;

    @ReadOnlyProperty // managed by DB (ON UPDATE CURRENT_TIMESTAMP)
    @Column("updated_at")
    private LocalDateTime updatedAt;
}
