package net.rsworld.example.dddonion.infrastructure.outbox;

import net.rsworld.example.dddonion.application.outbox.OutboxPort;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;

@Component
public class OutboxAdapter implements OutboxPort {

    private final OutboxRepository repo;
    private final ObjectMapper mapper;

    public OutboxAdapter(OutboxRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper.rebuild()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    @Override
    public Mono<Void> save(DomainEvent e) {
        try {
            String json = mapper.writeValueAsString(e);
            return repo.save(e.aggregateId(), e.sequence(), e.type(), e.occurredAt(), json);
        } catch (Exception ex) {
            return Mono.error(ex);
        }
    }
}
