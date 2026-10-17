package net.rsworld.example.dddonion.bootstrap.observability;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.observation.tck.TestObservationRegistry;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import net.rsworld.example.dddonion.application.context.ActorType;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class UseCaseObservabilityTest {

    private static final UseCaseMetadata META =
            new UseCaseMetadata(ActorType.BUSINESS_CUSTOMER, "rest", "corr-42");

    /** Sample use case shaped like the real ones. */
    interface PlaceThingUseCase {
        Mono<String> handle(String cmd, UseCaseMetadata metadata);
    }

    interface NoMetadataUseCase {
        Mono<String> handle(String cmd);
    }

    @BeforeAll
    static void enableContextPropagation() {
        UseCaseMdc.register(ContextRegistry.getInstance());
        Hooks.enableAutomaticContextPropagation(); // what spring.reactor.context-propagation=auto does
    }

    @AfterAll
    static void disableContextPropagation() {
        Hooks.disableAutomaticContextPropagation();
    }

    @Test
    @DisplayName("Derives the use case name from the interface")
    void derivesName() {
        assertThat(UseCaseObservability.useCaseName(PlaceThingUseCase.class)).isEqualTo("place-thing");
    }

    @Test
    @DisplayName("Records one observation with use case, actor type, channel, outcome and correlation id")
    void recordsObservationOnSuccess() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        PlaceThingUseCase observed =
                UseCaseObservability.wrap(PlaceThingUseCase.class, (cmd, meta) -> Mono.just("ok"), registry);

        StepVerifier.create(observed.handle("x", META)).expectNext("ok").verifyComplete();

        assertThat(registry)
                .hasSingleObservationThat()
                .hasNameEqualTo(UseCaseObservability.OBSERVATION_NAME)
                .hasBeenStopped()
                .hasLowCardinalityKeyValue("usecase", "place-thing")
                .hasLowCardinalityKeyValue("actor.type", "BUSINESS_CUSTOMER")
                .hasLowCardinalityKeyValue("channel", "rest")
                .hasLowCardinalityKeyValue("outcome", "SUCCESS")
                .hasHighCardinalityKeyValue("correlation.id", "corr-42")
                .doesNotHaveError();
    }

    @Test
    @DisplayName("Marks empty results and errors with their own outcome")
    void recordsEmptyAndErrorOutcomes() {
        RuntimeException boom = new RuntimeException("boom");
        TestObservationRegistry emptyRegistry = TestObservationRegistry.create();
        TestObservationRegistry failingRegistry = TestObservationRegistry.create();
        TestObservationRegistry throwingRegistry = TestObservationRegistry.create();
        PlaceThingUseCase empty =
                UseCaseObservability.wrap(PlaceThingUseCase.class, (cmd, meta) -> Mono.empty(), emptyRegistry);
        PlaceThingUseCase failing =
                UseCaseObservability.wrap(PlaceThingUseCase.class, (cmd, meta) -> Mono.error(boom), failingRegistry);
        PlaceThingUseCase throwing = UseCaseObservability.wrap(
                PlaceThingUseCase.class,
                (cmd, meta) -> {
                    throw boom; // eager failure before a Mono exists
                },
                throwingRegistry);

        StepVerifier.create(empty.handle("x", META)).verifyComplete();
        StepVerifier.create(failing.handle("x", META)).verifyErrorMatches(e -> e == boom);
        StepVerifier.create(throwing.handle("x", META)).verifyErrorMatches(e -> e == boom);

        assertThat(emptyRegistry).hasSingleObservationThat().hasLowCardinalityKeyValue("outcome", "EMPTY");
        for (TestObservationRegistry registry : new TestObservationRegistry[] {failingRegistry, throwingRegistry}) {
            assertThat(registry)
                    .hasSingleObservationThat()
                    .hasBeenStopped()
                    .hasLowCardinalityKeyValue("outcome", "ERROR")
                    .hasError(boom);
        }
    }

    @Test
    @DisplayName("Rejects use cases without UseCaseMetadata at wiring time")
    void rejectsUseCaseWithoutMetadata() {
        assertThatThrownBy(() -> UseCaseObservability.wrap(
                        NoMetadataUseCase.class, cmd -> Mono.just(cmd), TestObservationRegistry.create()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UseCaseMetadata");
    }

    @Test
    @DisplayName("Use case context is in the MDC inside the use case, across threads and async executors")
    void propagatesContextToMdc() {
        AtomicReference<Map<String, String>> onReactorThread = new AtomicReference<>();
        AtomicReference<Map<String, String>> onAsyncExecutor = new AtomicReference<>();
        ExecutorService async = ContextExecutorService.wrap(
                Executors.newVirtualThreadPerTaskExecutor(),
                ContextSnapshotFactory.builder().build());

        PlaceThingUseCase target = (cmd, meta) -> Mono.just(cmd)
                .publishOn(Schedulers.parallel())
                .doOnNext(c -> onReactorThread.set(MDC.getCopyOfContextMap()))
                // like PlaceOrderService -> ApplicationEventPublisher -> @Async listener
                .flatMap(c -> Mono.fromRunnable(() -> CompletableFuture.runAsync(
                                        () -> onAsyncExecutor.set(MDC.getCopyOfContextMap()), async)
                                .join())
                        .thenReturn(c));
        PlaceThingUseCase observed =
                UseCaseObservability.wrap(PlaceThingUseCase.class, target, TestObservationRegistry.create());

        StepVerifier.create(observed.handle("x", META)).expectNext("x").verifyComplete();
        async.shutdown();

        Map<String, String> expected =
                Map.of("usecase", "place-thing", "actorType", "BUSINESS_CUSTOMER", "correlationId", "corr-42");
        assertThat(onReactorThread.get()).containsAllEntriesOf(expected);
        assertThat(onAsyncExecutor.get()).containsAllEntriesOf(expected);
        assertThat(MDC.get("usecase")).as("no leak into the calling thread").isNull();
    }
}
