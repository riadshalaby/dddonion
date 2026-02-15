package net.rsworld.example.dddonion.monitor;

import lombok.extern.slf4j.Slf4j;
import net.rsworld.example.dddonion.domain.common.DomainEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DomainEventLoggingListener {

    @Async("applicationTaskExecutor")
    @EventListener
    public void onDomainEvent(DomainEvent event) {
        try {
            // artificial delay to demonstrate asynchronous execution
            Thread.sleep(2000);
            log.info(
                    "DomainEvent received: type={}, aggregateId={}, sequence={}, occurredAt={}",
                    event.type(),
                    event.aggregateId(),
                    event.sequence(),
                    event.occurredAt());
        } catch (Exception e) {
            log.warn("Failed to log DomainEvent (%s)".formatted(event), e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
