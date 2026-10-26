package net.rsworld.example.dddonion.bootstrap.observability;

import static io.micrometer.observation.tck.TestObservationRegistryAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ContextSnapshotFactory;
import io.micrometer.observation.tck.TestObservationRegistry;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import net.rsworld.example.dddonion.application.context.ActorType;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.architecture.UseCase;
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

    private static final UseCaseMetadata META = new UseCaseMetadata(ActorType.BUSINESS_CUSTOMER, "rest", "corr-42");

    /** Sample use case shaped like the real ones. */
    @UseCase(name = "thing.place", kind = UseCase.Kind.COMMAND)
    interface PlaceThingUseCase {
        Mono<String> handle(String cmd, UseCaseMetadata metadata);
    }

    @UseCase(name = "thing.nometa", kind = UseCase.Kind.QUERY)
    interface NoMetadataUseCase {
        Mono<String> handle(String cmd);
    }

    @UseCase(name = "thing.remove", kind = UseCase.Kind.COMMAND)
    interface RemoveThingUseCase {
        Mono<Void> handle(String cmd, UseCaseMetadata metadata);
    }

    interface Unannotated {
        Mono<String> handle(String cmd, UseCaseMetadata metadata);
    }

    @UseCase(name = "PlaceThing", kind = UseCase.Kind.COMMAND)
    interface BadNameUseCase {
        Mono<String> handle(String cmd, UseCaseMetadata metadata);
    }

    @UseCase(name = "customer.change-credit-limit", kind = UseCase.Kind.COMMAND)
    interface HyphenatedUseCase {
        Mono<String> handle(String cmd, UseCaseMetadata metadata);
    }

    @UseCase(name = "customer.address.change", kind = UseCase.Kind.COMMAND)
    interface MultiSegmentUseCase {
        Mono<String> handle(String cmd, UseCaseMetadata metadata);
    }

    @UseCase(name = "order..place", kind = UseCase.Kind.COMMAND)
    interface EmptySegmentUseCase {
        Mono<String> handle(String cmd, UseCaseMetadata metadata);
    }

    @UseCase(name = "order", kind = UseCase.Kind.COMMAND)
    interface NoDotUseCase {
        Mono<String> handle(String cmd, UseCaseMetadata metadata);
    }

    @UseCase(name = "thing.default", kind = UseCase.Kind.COMMAND)
    interface DefaultMethodUseCase {
        Mono<String> handle(String cmd, UseCaseMetadata metadata);

        default Mono<String> handleDefault(UseCaseMetadata metadata) {
            return handle("x", metadata);
        }
    }

    @UseCase(name = "thing.bad-default", kind = UseCase.Kind.COMMAND)
    interface BadDefaultMethodUseCase {
        Mono<String> handle(String cmd, UseCaseMetadata metadata);

        default String describe() {
            return "x";
        }
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
    @DisplayName("Takes name and kind from the @UseCase annotation")
    void usesNameAndKindFromAnnotation() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        PlaceThingUseCase observed =
                UseCaseObservability.wrap(PlaceThingUseCase.class, (cmd, meta) -> Mono.just("ok"), registry);

        StepVerifier.create(observed.handle("x", META)).expectNext("ok").verifyComplete();

        assertThat(registry)
                .hasSingleObservationThat()
                .hasLowCardinalityKeyValue("usecase", "thing.place")
                .hasLowCardinalityKeyValue("usecase.kind", "command");
    }

    @Test
    @DisplayName("Rejects interfaces without @UseCase")
    void rejectsInterfaceWithoutUseCaseAnnotation() {
        assertThatThrownBy(() -> UseCaseObservability.wrap(
                        Unannotated.class, (cmd, meta) -> Mono.just(cmd), TestObservationRegistry.create()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@UseCase");
    }

    @Test
    @DisplayName("Rejects names that do not match <aggregate>.<verb>")
    void rejectsInvalidName() {
        assertThatThrownBy(() -> UseCaseObservability.wrap(
                        BadNameUseCase.class, (cmd, meta) -> Mono.just(cmd), TestObservationRegistry.create()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PlaceThing");
    }

    @Test
    @DisplayName("Accepts hyphenated and multi-segment names")
    void acceptsHyphenatedAndMultiSegmentNames() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        HyphenatedUseCase hyphenated =
                UseCaseObservability.wrap(HyphenatedUseCase.class, (cmd, meta) -> Mono.just(cmd), registry);
        MultiSegmentUseCase multi =
                UseCaseObservability.wrap(MultiSegmentUseCase.class, (cmd, meta) -> Mono.just(cmd), registry);

        StepVerifier.create(hyphenated.handle("x", META)).expectNext("x").verifyComplete();
        StepVerifier.create(multi.handle("x", META)).expectNext("x").verifyComplete();

        assertThat(registry)
                .hasObservationWithNameEqualTo(UseCaseObservability.OBSERVATION_NAME)
                .that()
                .hasLowCardinalityKeyValue("usecase", "customer.change-credit-limit");
    }

    @Test
    @DisplayName("Rejects empty segments and names without a dot")
    void rejectsEmptySegmentAndNoDot() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        assertThatThrownBy(() ->
                        UseCaseObservability.wrap(EmptySegmentUseCase.class, (cmd, meta) -> Mono.just(cmd), registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("order..place");
        assertThatThrownBy(() -> UseCaseObservability.wrap(NoDotUseCase.class, (cmd, meta) -> Mono.just(cmd), registry))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'order'");
    }

    @Test
    @DisplayName("A default method delegating to an abstract one is observed exactly once")
    void defaultMethodIsObservedExactlyOnce() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        DefaultMethodUseCase observed =
                UseCaseObservability.wrap(DefaultMethodUseCase.class, (cmd, meta) -> Mono.just("ok"), registry);

        StepVerifier.create(observed.handleDefault(META)).expectNext("ok").verifyComplete();

        assertThat(registry).hasNumberOfObservationsWithNameEqualTo(UseCaseObservability.OBSERVATION_NAME, 1);
    }

    @Test
    @DisplayName("Rejects a target that overrides a default method, which the proxy would bypass")
    void rejectsTargetOverridingDefaultMethod() {
        DefaultMethodUseCase overriding = new DefaultMethodUseCase() {
            @Override
            public Mono<String> handle(String cmd, UseCaseMetadata metadata) {
                return Mono.just(cmd);
            }

            @Override
            public Mono<String> handleDefault(UseCaseMetadata metadata) {
                return Mono.just("unobserved");
            }
        };

        assertThatThrownBy(() -> UseCaseObservability.wrap(
                        DefaultMethodUseCase.class, overriding, TestObservationRegistry.create()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DefaultMethodUseCase")
                .hasMessageContaining("handleDefault")
                .hasMessageContaining("make it abstract");
    }

    @Test
    @DisplayName("Rejects default methods without Mono or UseCaseMetadata")
    void rejectsDefaultMethodWithoutMonoOrMetadata() {
        assertThatThrownBy(() -> UseCaseObservability.wrap(
                        BadDefaultMethodUseCase.class, (cmd, meta) -> Mono.just(cmd), TestObservationRegistry.create()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("describe");
    }

    @Test
    @DisplayName("Marks the proxy as observed")
    void marksProxyAsObserved() {
        PlaceThingUseCase impl = (cmd, meta) -> Mono.just("ok");
        assertThat(UseCaseObservability.isObserved(
                        UseCaseObservability.wrap(PlaceThingUseCase.class, impl, TestObservationRegistry.create())))
                .isTrue();
        assertThat(UseCaseObservability.isObserved(impl)).isFalse();
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
                .hasLowCardinalityKeyValue("usecase", "thing.place")
                .hasLowCardinalityKeyValue("actor.type", "BUSINESS_CUSTOMER")
                .hasLowCardinalityKeyValue("channel", "rest")
                .hasLowCardinalityKeyValue("outcome", "SUCCESS")
                .hasHighCardinalityKeyValue("correlation.id", "corr-42")
                .doesNotHaveError();
    }

    @Test
    @DisplayName("Counts the empty completion of a Mono<Void> use case as SUCCESS")
    void voidCommandCompletingEmptyIsSuccess() {
        TestObservationRegistry registry = TestObservationRegistry.create();
        RemoveThingUseCase useCase =
                UseCaseObservability.wrap(RemoveThingUseCase.class, (cmd, meta) -> Mono.empty(), registry);

        StepVerifier.create(useCase.handle("x", META)).verifyComplete();

        assertThat(registry).hasSingleObservationThat().hasLowCardinalityKeyValue("outcome", "SUCCESS");
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
                .flatMap(c -> Mono.fromCallable(() -> async.submit(() -> onAsyncExecutor.set(MDC.getCopyOfContextMap()))
                                .get())
                        .thenReturn(c));
        PlaceThingUseCase observed =
                UseCaseObservability.wrap(PlaceThingUseCase.class, target, TestObservationRegistry.create());

        StepVerifier.create(observed.handle("x", META)).expectNext("x").verifyComplete();
        async.shutdown();

        Map<String, String> expected =
                Map.of("usecase", "thing.place", "actorType", "BUSINESS_CUSTOMER", "correlationId", "corr-42");
        assertThat(onReactorThread.get()).containsAllEntriesOf(expected);
        assertThat(onAsyncExecutor.get()).containsAllEntriesOf(expected);
        assertThat(MDC.get("usecase")).as("no leak into the calling thread").isNull();
    }
}
