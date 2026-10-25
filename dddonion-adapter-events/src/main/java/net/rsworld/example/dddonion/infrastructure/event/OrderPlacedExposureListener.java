package net.rsworld.example.dddonion.infrastructure.event;

import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.rsworld.example.dddonion.application.context.ActorType;
import net.rsworld.example.dddonion.application.context.UseCaseContextKeys;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.customer.usecase.RecordOrderExposureUseCase;
import net.rsworld.example.dddonion.architecture.UseCase;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.command.RecordOrderExposureCommand;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.order.event.OrderPlaced;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import org.slf4j.MDC;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Inbound event adapter: turns {@link OrderPlaced} into the {@code customer.record-order-exposure} use case. Actor and
 * correlation id come from the MDC of the publishing call (propagated onto the async thread); the MDC {@code usecase}
 * is switched to this listener's use case while it runs. Failures are logged and never escape, so they cannot affect
 * the publisher.
 */
@Slf4j
@Component
public class OrderPlacedExposureListener {

    static final String CHANNEL = "event";
    static final Duration TIMEOUT = Duration.ofSeconds(30);
    static final String USECASE =
            RecordOrderExposureUseCase.class.getAnnotation(UseCase.class).name();

    private final RecordOrderExposureUseCase useCase;

    public OrderPlacedExposureListener(RecordOrderExposureUseCase useCase) {
        this.useCase = useCase;
    }

    @Async("applicationTaskExecutor")
    @EventListener
    public void onOrderPlaced(OrderPlaced event) {
        String publisherUseCase = MDC.get(UseCaseContextKeys.USECASE);
        MDC.put(UseCaseContextKeys.USECASE, USECASE);
        try {
            recordExposure(event);
        } finally {
            if (publisherUseCase == null) {
                MDC.remove(UseCaseContextKeys.USECASE);
            } else {
                MDC.put(UseCaseContextKeys.USECASE, publisherUseCase);
            }
        }
    }

    private void recordExposure(OrderPlaced event) {
        String correlationId = MDC.get(UseCaseContextKeys.CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        try {
            var metadata = new UseCaseMetadata(actorType(), CHANNEL, correlationId);
            var command = new RecordOrderExposureCommand(
                    new CustomerId(event.customerId()), new OrderId(event.orderId()), new Money(event.total()));
            useCase.handle(command, metadata).block(TIMEOUT);
        } catch (Exception e) {
            log.warn(
                    "Recording order exposure failed: orderId={}, correlationId={}", event.orderId(), correlationId, e);
        }
    }

    private static ActorType actorType() {
        String value = MDC.get(UseCaseContextKeys.ACTOR_TYPE);
        if (value == null) {
            return ActorType.UNKNOWN;
        }
        try {
            return ActorType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return ActorType.UNKNOWN;
        }
    }
}
