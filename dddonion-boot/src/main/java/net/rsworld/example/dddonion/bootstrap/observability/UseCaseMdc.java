package net.rsworld.example.dddonion.bootstrap.observability;

import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;

/**
 * Überträgt die Use-Case-Schlüssel aus dem Reactor-Context in den SLF4J-MDC. Zusammen mit
 * {@code spring.reactor.context-propagation=auto} trägt jede Logzeile innerhalb eines Use Cases sie, egal auf welchem
 * Thread Reactor läuft.
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
