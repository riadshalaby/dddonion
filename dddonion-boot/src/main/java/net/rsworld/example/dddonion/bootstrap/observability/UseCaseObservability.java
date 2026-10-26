package net.rsworld.example.dddonion.bootstrap.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import net.rsworld.example.dddonion.application.context.UseCaseContextKeys;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.architecture.UseCase;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * Decorates a use case so that every invocation becomes one {@link Observation} named {@value #OBSERVATION_NAME} (→
 * timer metric + audit log line) and carries its {@link UseCaseMetadata} into the Reactor context (→ MDC).
 *
 * <p>Name and kind come from the {@link UseCase} annotation on the interface ({@code order.place}, {@code command}), so
 * they stay stable when the interface is renamed. Low-cardinality values become metric tags; the correlation id is
 * high-cardinality and therefore only reaches logs/spans.
 */
public final class UseCaseObservability {

    public static final String OBSERVATION_NAME = "usecase.execution";

    public static final String KEY_USECASE = "usecase";
    public static final String KEY_KIND = "usecase.kind";
    public static final String KEY_ACTOR_TYPE = "actor.type";
    public static final String KEY_CHANNEL = "channel";
    public static final String KEY_OUTCOME = "outcome";
    public static final String KEY_CORRELATION_ID = "correlation.id";

    /** Reactor-context / MDC keys, bridged by {@link UseCaseMdc}. */
    public static final String MDC_USECASE = UseCaseContextKeys.USECASE;

    public static final String MDC_ACTOR_TYPE = UseCaseContextKeys.ACTOR_TYPE;
    public static final String MDC_CORRELATION_ID = UseCaseContextKeys.CORRELATION_ID;

    /**
     * Result of an invocation; {@code EMPTY} is a business signal for a value-returning use case, e.g. "order not
     * found". A {@code Mono<Void>} use case never emits, so its normal completion is {@code SUCCESS}.
     */
    public enum Outcome {
        SUCCESS,
        EMPTY,
        ERROR,
        CANCELLED
    }

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+$");

    /** Marker implemented by every proxy created here, so wiring code can avoid double-wrapping. */
    public interface Observed {}

    private UseCaseObservability() {}

    public static boolean isObserved(Object bean) {
        return bean instanceof Observed;
    }

    /**
     * Wraps {@code target}. Fails fast at wiring time if any use case method (abstract or default) returns no
     * {@link Mono} or takes no {@link UseCaseMetadata}, or if the target overrides a default method, so a new use case
     * cannot silently escape the audit.
     */
    public static <T> T wrap(Class<T> useCase, T target, ObservationRegistry registry) {
        return wrap(useCase, target, registry, UnaryOperator.identity());
    }

    /**
     * As {@link #wrap(Class, Object, ObservationRegistry)}, but {@code inner} wraps each (re-)invocation of the target
     * inside the observation, e.g. with a transaction and retry. The observation still covers the call once.
     */
    public static <T> T wrap(Class<T> useCase, T target, ObservationRegistry registry, UnaryOperator<Mono<?>> inner) {
        if (!useCase.isInterface()) {
            throw new IllegalArgumentException(useCase.getName() + " must be an interface");
        }
        UseCase annotation = useCase.getAnnotation(UseCase.class);
        if (annotation == null) {
            throw new IllegalArgumentException(useCase.getName() + " must be annotated with @UseCase");
        }
        String name = annotation.name();
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid @UseCase name '" + name + "' on " + useCase.getName()
                    + ", expected lowercase dot-separated segments like <aggregate>.<verb> (pattern "
                    + NAME_PATTERN.pattern() + ")");
        }
        String kind = annotation.kind().name().toLowerCase(Locale.ROOT);
        Arrays.stream(useCase.getMethods())
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .forEach(m -> {
                    requireObservable(m);
                    if (!Modifier.isAbstract(m.getModifiers())) {
                        requireNotOverridden(useCase, target, m);
                    }
                });

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
                // default methods run on the proxy, so the abstract methods they call are observed (once)
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            UseCaseMetadata metadata = metadataOf(args);
            boolean voidResult = isVoidMono(method);
            return observe(
                    name, kind, metadata, registry, voidResult, inner, () -> (Mono<?>) invoke(target, method, args));
        };
        return useCase.cast(
                Proxy.newProxyInstance(useCase.getClassLoader(), new Class<?>[] {useCase, Observed.class}, handler));
    }

    private static Mono<?> observe(
            String name,
            String kind,
            UseCaseMetadata metadata,
            ObservationRegistry registry,
            boolean voidResult,
            UnaryOperator<Mono<?>> inner,
            InvocationSupplier invocation) {
        return Mono.deferContextual(reactorContext -> {
                    Observation observation = Observation.createNotStarted(OBSERVATION_NAME, registry)
                            .parentObservation(reactorContext.getOrDefault(ObservationThreadLocalAccessor.KEY, null))
                            .contextualName(name)
                            .lowCardinalityKeyValue(KEY_USECASE, name)
                            .lowCardinalityKeyValue(KEY_KIND, kind)
                            .lowCardinalityKeyValue(
                                    KEY_ACTOR_TYPE, metadata.actorType().name())
                            .lowCardinalityKeyValue(KEY_CHANNEL, metadata.channel())
                            .highCardinalityKeyValue(KEY_CORRELATION_ID, metadata.correlationId())
                            .start();
                    AtomicBoolean emitted = new AtomicBoolean();
                    return inner.apply(Mono.defer(invocation::get))
                            .doOnNext(value -> emitted.set(true))
                            .doOnError(observation::error)
                            .doFinally(signal -> {
                                observation.lowCardinalityKeyValue(
                                        KEY_OUTCOME,
                                        outcome(signal, emitted.get(), voidResult)
                                                .name());
                                observation.stop();
                            })
                            .contextWrite(ctx -> ctx.put(ObservationThreadLocalAccessor.KEY, observation));
                })
                .contextWrite(ctx -> ctx.put(MDC_USECASE, name)
                        .put(MDC_ACTOR_TYPE, metadata.actorType().name())
                        .put(MDC_CORRELATION_ID, metadata.correlationId()));
    }

    private static boolean isVoidMono(Method method) {
        return method.getGenericReturnType() instanceof ParameterizedType type
                && type.getActualTypeArguments().length == 1
                && Void.class.equals(type.getActualTypeArguments()[0]);
    }

    private static Outcome outcome(SignalType signal, boolean emitted, boolean voidResult) {
        return switch (signal) {
            case ON_ERROR -> Outcome.ERROR;
            case CANCEL -> emitted ? Outcome.SUCCESS : Outcome.CANCELLED; // Mono cancels upstream after onNext
            default -> emitted || voidResult ? Outcome.SUCCESS : Outcome.EMPTY;
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

    /** The proxy runs default methods itself, so an override in the target would be silently bypassed. */
    private static void requireNotOverridden(Class<?> useCase, Object target, Method defaultMethod) {
        try {
            Method implementation =
                    target.getClass().getMethod(defaultMethod.getName(), defaultMethod.getParameterTypes());
            if (implementation.getDeclaringClass() != defaultMethod.getDeclaringClass()) {
                throw new IllegalArgumentException(target.getClass().getName() + " overrides default method "
                        + defaultMethod.getName() + " of use case " + useCase.getName()
                        + ", which the use case proxy bypasses; make it abstract");
            }
        } catch (NoSuchMethodException e) {
            // target class not public or method not visible: nothing it could override through the proxy
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
