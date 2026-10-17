package net.rsworld.example.dddonion.bootstrap.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * Decorates a use case so that every invocation becomes one {@link Observation} named {@value #OBSERVATION_NAME}
 * (→ timer metric + audit log line) and carries its {@link UseCaseMetadata} into the Reactor context (→ MDC).
 *
 * <p>The use case name is derived from the interface ({@code PlaceOrderUseCase} → {@code place-order}), so it cannot
 * drift from the code. Low-cardinality values become metric tags; the correlation id is high-cardinality and therefore
 * only reaches logs/spans.
 */
public final class UseCaseObservability {

    public static final String OBSERVATION_NAME = "usecase.execution";

    public static final String KEY_USECASE = "usecase";
    public static final String KEY_ACTOR_TYPE = "actor.type";
    public static final String KEY_CHANNEL = "channel";
    public static final String KEY_OUTCOME = "outcome";
    public static final String KEY_CORRELATION_ID = "correlation.id";

    /** Reactor-context / MDC keys, bridged by {@link UseCaseMdc}. */
    public static final String MDC_USECASE = "usecase";

    public static final String MDC_ACTOR_TYPE = "actorType";
    public static final String MDC_CORRELATION_ID = "correlationId";

    /** Result of an invocation; {@code EMPTY} is a business signal, e.g. "order not found". */
    public enum Outcome {
        SUCCESS,
        EMPTY,
        ERROR,
        CANCELLED
    }

    private UseCaseObservability() {}

    /**
     * Wraps {@code target}. Fails fast at wiring time if a use case method returns no {@link Mono} or takes no
     * {@link UseCaseMetadata}, so a new use case cannot silently escape the audit.
     */
    public static <T> T wrap(Class<T> useCase, T target, ObservationRegistry registry) {
        if (!useCase.isInterface()) {
            throw new IllegalArgumentException(useCase.getName() + " must be an interface");
        }
        Arrays.stream(useCase.getMethods())
                .filter(m -> Modifier.isAbstract(m.getModifiers()))
                .forEach(UseCaseObservability::requireObservable);

        String name = useCaseName(useCase);
        InvocationHandler handler = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "Observed[" + name + "] " + target;
                    default -> invoke(target, method, args);
                };
            }
            if (!Modifier.isAbstract(method.getModifiers())) {
                return invoke(target, method, args); // default methods delegate to observed abstract ones
            }
            UseCaseMetadata metadata = metadataOf(args);
            return observe(name, metadata, registry, () -> (Mono<?>) invoke(target, method, args));
        };
        return useCase.cast(Proxy.newProxyInstance(useCase.getClassLoader(), new Class<?>[] {useCase}, handler));
    }

    /** {@code PlaceOrderUseCase} → {@code place-order}. */
    public static String useCaseName(Class<?> useCase) {
        String simple = useCase.getSimpleName().replaceFirst("UseCase$", "");
        return simple.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    private static Mono<?> observe(
            String name, UseCaseMetadata metadata, ObservationRegistry registry, InvocationSupplier invocation) {
        return Mono.deferContextual(reactorContext -> {
                    Observation observation = Observation.createNotStarted(OBSERVATION_NAME, registry)
                            .parentObservation(reactorContext.getOrDefault(ObservationThreadLocalAccessor.KEY, null))
                            .contextualName(name)
                            .lowCardinalityKeyValue(KEY_USECASE, name)
                            .lowCardinalityKeyValue(KEY_ACTOR_TYPE, metadata.actorType().name())
                            .lowCardinalityKeyValue(KEY_CHANNEL, metadata.channel())
                            .highCardinalityKeyValue(KEY_CORRELATION_ID, metadata.correlationId())
                            .start();
                    AtomicBoolean emitted = new AtomicBoolean();
                    return Mono.defer(invocation::get)
                            .doOnNext(value -> emitted.set(true))
                            .doOnError(observation::error)
                            .doFinally(signal -> {
                                observation.lowCardinalityKeyValue(
                                        KEY_OUTCOME, outcome(signal, emitted.get()).name());
                                observation.stop();
                            })
                            .contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
                })
                .contextWrite(ctx -> ctx.put(MDC_USECASE, name)
                        .put(MDC_ACTOR_TYPE, metadata.actorType().name())
                        .put(MDC_CORRELATION_ID, metadata.correlationId()));
    }

    private static Outcome outcome(SignalType signal, boolean emitted) {
        return switch (signal) {
            case ON_ERROR -> Outcome.ERROR;
            case CANCEL -> emitted ? Outcome.SUCCESS : Outcome.CANCELLED; // Mono cancels upstream after onNext
            default -> emitted ? Outcome.SUCCESS : Outcome.EMPTY;
        };
    }

    private static void requireObservable(Method method) {
        if (!Mono.class.equals(method.getReturnType())) {
            throw new IllegalArgumentException("Use case method " + method + " must return Mono");
        }
        if (Arrays.stream(method.getParameterTypes()).noneMatch(UseCaseMetadata.class::equals)) {
            throw new IllegalArgumentException("Use case method " + method + " must take a UseCaseMetadata");
        }
    }

    private static UseCaseMetadata metadataOf(Object[] args) {
        return Arrays.stream(args)
                .filter(UseCaseMetadata.class::isInstance)
                .map(UseCaseMetadata.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("UseCaseMetadata must not be null"));
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @FunctionalInterface
    private interface InvocationSupplier {
        Mono<?> call() throws Throwable;

        default Mono<?> get() {
            try {
                return call();
            } catch (Throwable t) {
                return Mono.error(t);
            }
        }
    }
}
