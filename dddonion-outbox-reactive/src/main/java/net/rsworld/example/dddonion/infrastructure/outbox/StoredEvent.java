package net.rsworld.example.dddonion.infrastructure.outbox;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("outbox_events")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StoredEvent {
    @Id
    private String id;

    private String aggregateId;
    private long sequence;
    private String type;
    private Instant occurredAt;
    private String payloadJson;
    private boolean published;
}
