package net.rsworld.example.dddonion.bootstrap.observability;

import io.micrometer.context.ContextRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    static {
        // muss passieren, bevor die erste reaktive Kette den Context erfasst
        UseCaseMdc.register(ContextRegistry.getInstance());
    }

    /** Wird von Boots Auto-Konfiguration der ObservationRegistry neben dem Metrik-Handler aufgenommen. */
    @Bean
    public AuditLogObservationHandler auditLogObservationHandler() {
        return new AuditLogObservationHandler();
    }
}
