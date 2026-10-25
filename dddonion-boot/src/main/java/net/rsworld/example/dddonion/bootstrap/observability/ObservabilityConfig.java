package net.rsworld.example.dddonion.bootstrap.observability;

import io.micrometer.context.ContextRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    static {
        // must happen before the first reactive chain captures context
        UseCaseMdc.register(ContextRegistry.getInstance());
    }

    /** Picked up by Boot's ObservationRegistry auto-configuration alongside the metrics handler. */
    @Bean
    public AuditLogObservationHandler auditLogObservationHandler() {
        return new AuditLogObservationHandler();
    }

    /** Static, so this post-processor is created without initializing the configuration class early. */
    @Bean
    public static UseCaseObservabilityPostProcessor useCaseObservabilityPostProcessor(
            ObjectProvider<ObservationRegistry> registry) {
        return new UseCaseObservabilityPostProcessor(registry);
    }
}
