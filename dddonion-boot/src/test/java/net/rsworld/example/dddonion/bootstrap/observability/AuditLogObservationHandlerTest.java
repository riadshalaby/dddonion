package net.rsworld.example.dddonion.bootstrap.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import java.util.stream.Collectors;
import net.rsworld.example.dddonion.application.context.ActorType;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.architecture.UseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AuditLogObservationHandlerTest {

    @UseCase(name = "thing.get", kind = UseCase.Kind.QUERY)
    interface GetThingUseCase {
        Mono<String> find(String id, UseCaseMetadata metadata);
    }

    private final Logger audit = (Logger) LoggerFactory.getLogger(AuditLogObservationHandler.LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        audit.addAppender(appender);
    }

    @AfterEach
    void detach() {
        audit.detachAppender(appender);
    }

    @Test
    @DisplayName("Writes one audit line with structured key-values per use-case execution")
    void writesOneStructuredAuditLine() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new AuditLogObservationHandler());
        GetThingUseCase observed =
                UseCaseObservability.wrap(GetThingUseCase.class, (id, meta) -> Mono.empty(), registry);

        StepVerifier.create(observed.find("1", new UseCaseMetadata(ActorType.EXTERNAL_ERP, "rest", "corr-7")))
                .verifyComplete();

        assertThat(appender.list).hasSize(1);
        Map<String, Object> fields = appender.list.getFirst().getKeyValuePairs().stream()
                .collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
        assertThat(fields)
                .containsEntry("usecase", "thing.get")
                .containsEntry("actorType", "EXTERNAL_ERP")
                .containsEntry("channel", "rest")
                .containsEntry("outcome", "EMPTY")
                .containsEntry("error", "none")
                .containsEntry("correlationId", "corr-7")
                .containsKey("durationMs");
    }
}
