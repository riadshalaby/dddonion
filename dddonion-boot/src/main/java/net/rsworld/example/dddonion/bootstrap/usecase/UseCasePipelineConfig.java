package net.rsworld.example.dddonion.bootstrap.usecase;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
public class UseCasePipelineConfig {

    /**
     * Statisch, damit dieser Post-Processor erzeugt wird, ohne die Konfigurationsklasse vorzeitig zu initialisieren.
     */
    @Bean
    public static UseCasePipelinePostProcessor useCasePipelinePostProcessor(
            ObjectProvider<ObservationRegistry> registry,
            ObjectProvider<TransactionalOperator> tx,
            ObjectProvider<ApplicationEventPublisher> events) {
        return new UseCasePipelinePostProcessor(registry, tx, events);
    }
}
