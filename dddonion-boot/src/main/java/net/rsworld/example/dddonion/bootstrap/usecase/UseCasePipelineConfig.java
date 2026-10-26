package net.rsworld.example.dddonion.bootstrap.usecase;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.reactive.TransactionalOperator;

@Configuration
public class UseCasePipelineConfig {

    /** Static, so this post-processor is created without initializing the configuration class early. */
    @Bean
    public static UseCasePipelinePostProcessor useCasePipelinePostProcessor(
            ObjectProvider<ObservationRegistry> registry, ObjectProvider<TransactionalOperator> tx) {
        return new UseCasePipelinePostProcessor(registry, tx);
    }
}
