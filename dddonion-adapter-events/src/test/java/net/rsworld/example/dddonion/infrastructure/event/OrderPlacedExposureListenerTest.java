package net.rsworld.example.dddonion.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.time.Instant;
import net.rsworld.example.dddonion.application.context.ActorType;
import net.rsworld.example.dddonion.application.context.UseCaseContextKeys;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.application.customer.usecase.RecordOrderExposureUseCase;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.command.RecordOrderExposureCommand;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.order.event.OrderPlaced;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;

class OrderPlacedExposureListenerTest {

    private final RecordOrderExposureUseCase useCase = mock(RecordOrderExposureUseCase.class);
    private final OrderPlacedExposureListener listener = new OrderPlacedExposureListener(useCase);
    private final OrderPlaced event = new OrderPlaced(
            OrderId.newId().value(), 1L, Instant.now(), CustomerId.newId().value(), new BigDecimal("60.00"));
    // captures the MDC at log time; the listener restores the MDC afterwards
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent e) {
            e.prepareForDeferredProcessing();
            super.append(e);
        }
    };
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(OrderPlacedExposureListener.class);
        logger.setLevel(Level.INFO);
        logs.start();
        logger.addAppender(logs);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        MDC.clear();
    }

    @Test
    void buildsCommandAndMetadataFromEventAndMdc() {
        MDC.put(UseCaseContextKeys.ACTOR_TYPE, "BUSINESS_CUSTOMER");
        MDC.put(UseCaseContextKeys.CORRELATION_ID, "c-1");
        when(useCase.handle(any(), any())).thenReturn(Mono.empty());

        listener.onOrderPlaced(event);

        var cmd = ArgumentCaptor.forClass(RecordOrderExposureCommand.class);
        var meta = ArgumentCaptor.forClass(UseCaseMetadata.class);
        verify(useCase).handle(cmd.capture(), meta.capture());
        assertThat(cmd.getValue())
                .isEqualTo(new RecordOrderExposureCommand(
                        new CustomerId(event.customerId()),
                        new OrderId(event.orderId()),
                        new Money(new BigDecimal("60.00"))));
        assertThat(meta.getValue()).isEqualTo(new UseCaseMetadata(ActorType.BUSINESS_CUSTOMER, "event", "c-1"));
    }

    @Test
    void fallsBackToUnknownActorAndGeneratedCorrelation() {
        MDC.put(UseCaseContextKeys.ACTOR_TYPE, "NOPE");
        when(useCase.handle(any(), any())).thenReturn(Mono.empty());

        listener.onOrderPlaced(event);

        var meta = ArgumentCaptor.forClass(UseCaseMetadata.class);
        verify(useCase).handle(any(), meta.capture());
        assertThat(meta.getValue().actorType()).isEqualTo(ActorType.UNKNOWN);
        assertThat(meta.getValue().channel()).isEqualTo("event");
        assertThat(meta.getValue().correlationId()).isNotBlank();
    }

    @Test
    void swallowsAndLogsUseCaseFailure() {
        MDC.put(UseCaseContextKeys.CORRELATION_ID, "c-9");
        when(useCase.handle(any(), any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        assertThatCode(() -> listener.onOrderPlaced(event)).doesNotThrowAnyException();

        assertThat(logs.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getFormattedMessage()).contains(event.orderId()).contains("c-9");
        });
    }

    @Test
    void logsUnderItsOwnUseCaseAndRestoresPublisherMdc() {
        MDC.put(UseCaseContextKeys.USECASE, "order.place");
        when(useCase.handle(any(), any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        listener.onOrderPlaced(event);

        assertThat(logs.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.WARN);
            assertThat(e.getMDCPropertyMap())
                    .containsEntry(UseCaseContextKeys.USECASE, "customer.record-order-exposure");
        });
        assertThat(MDC.get(UseCaseContextKeys.USECASE)).isEqualTo("order.place");
    }

    @Test
    void removesItsUseCaseFromMdcWhenNoneWasSet() {
        when(useCase.handle(any(), any())).thenReturn(Mono.empty());

        listener.onOrderPlaced(event);

        assertThat(MDC.get(UseCaseContextKeys.USECASE)).isNull();
    }
}
