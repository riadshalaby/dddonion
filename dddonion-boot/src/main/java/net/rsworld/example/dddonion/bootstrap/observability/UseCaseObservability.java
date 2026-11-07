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
 * Dekoriert einen Use Case so, dass jeder Aufruf genau eine {@link Observation} namens {@value #OBSERVATION_NAME} wird
 * (→ Timer-Metrik + Audit-Logzeile) und seine {@link UseCaseMetadata} in den Reactor-Context trägt (→ MDC).
 *
 * <p>Name und Art stammen aus der {@link UseCase}-Annotation am Interface ({@code order.place}, {@code command}), damit
 * sie beim Umbenennen des Interfaces stabil bleiben. Werte mit geringer Kardinalität werden zu Metrik-Tags; die
 * Correlation-ID hat hohe Kardinalität und landet daher nur in Logs/Spans.
 */
public final class UseCaseObservability {

    public static final String OBSERVATION_NAME = "usecase.execution";

    public static final String KEY_USECASE = "usecase";
    public static final String KEY_KIND = "usecase.kind";
    public static final String KEY_ACTOR_TYPE = "actor.type";
    public static final String KEY_CHANNEL = "channel";
    public static final String KEY_OUTCOME = "outcome";
    public static final String KEY_CORRELATION_ID = "correlation.id";

    /** Schlüssel für Reactor-Context / MDC, übertragen von {@link UseCaseMdc}. */
    public static final String MDC_USECASE = UseCaseContextKeys.USECASE;

    public static final String MDC_ACTOR_TYPE = UseCaseContextKeys.ACTOR_TYPE;
    public static final String MDC_CORRELATION_ID = UseCaseContextKeys.CORRELATION_ID;

    /**
     * Ergebnis eines Aufrufs; {@code EMPTY} ist ein fachliches Signal bei einem wertliefernden Use Case, z. B. „Order
     * nicht gefunden“. Ein {@code Mono<Void>}-Use-Case emittiert nie, daher ist sein normaler Abschluss
     * {@code SUCCESS}.
     */
    public enum Outcome {
        SUCCESS,
        EMPTY,
        ERROR,
        CANCELLED
    }

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+$");

    /** Marker, den jeder hier erzeugte Proxy implementiert, damit Wiring-Code doppeltes Wrapping vermeiden kann. */
    public interface Observed {}

    private UseCaseObservability() {}

    public static boolean isObserved(Object bean) {
        return bean instanceof Observed;
    }

    /**
     * Hüllt {@code target} ein. Scheitert schon beim Wiring, wenn eine Use-Case-Methode (abstrakt oder default) kein
     * {@link Mono} liefert oder kein {@link UseCaseMetadata} nimmt oder wenn das Target eine Default-Methode
     * überschreibt; so kann ein neuer Use Case nicht unbemerkt dem Audit entgehen.
     */
    public static <T> T wrap(Class<T> useCase, T target, ObservationRegistry registry) {
        return wrap(useCase, target, registry, UnaryOperator.identity());
    }

    /**
     * Wie {@link #wrap(Class, Object, ObservationRegistry)}, aber {@code inner} hüllt jeden (erneuten) Aufruf des
     * Targets innerhalb der Observation ein, z. B. mit Transaktion und Retry. Die Observation umfasst den Aufruf
     * trotzdem nur einmal.
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
                // Default-Methoden laufen auf dem Proxy, daher werden die abstrakten Methoden, die sie aufrufen,
                // (einmal) beobachtet
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
            case CANCEL -> emitted ? Outcome.SUCCESS : Outcome.CANCELLED; // Mono cancelt den Upstream nach onNext
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

    /** Der Proxy führt Default-Methoden selbst aus, daher würde ein Override im Target stillschweigend umgangen. */
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
            // Target-Klasse nicht public oder Methode nicht sichtbar: Es gibt nichts, was sie über den Proxy
            // überschreiben könnte
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
