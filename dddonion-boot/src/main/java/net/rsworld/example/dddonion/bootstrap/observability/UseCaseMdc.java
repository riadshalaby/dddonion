package net.rsworld.example.dddonion.bootstrap.observability;

import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;

/**
 * Bridges the use-case keys from the Reactor context into the SLF4J MDC. Together with
 * {@code spring.reactor.context-propagation=auto} every log line inside a use case carries them, regardless of the
 * thread Reactor runs on.
 */
public final class UseCaseMdc {

    private UseCaseMdc() {}

    public static void register(ContextRegistry registry) {
        for (String key : new String[] {
            UseCaseObservability.MDC_USECASE,
            UseCaseObservability.MDC_ACTOR_TYPE,
            UseCaseObservability.MDC_CORRELATION_ID
        }) {
            registry.registerThreadLocalAccessor(
                    key, () -> MDC.get(key), value -> MDC.put(key, value), () -> MDC.remove(key));
        }
    }
}
