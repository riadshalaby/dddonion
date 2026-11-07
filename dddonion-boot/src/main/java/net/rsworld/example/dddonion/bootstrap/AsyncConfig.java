package net.rsworld.example.dddonion.bootstrap;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Executor für alle asynchronen Event-Listener (Monitor und eingehende Event-Adapter). Er liegt in der Composition
 * Root, weil mehrere Module ihn brauchen; fällt eines davon weg, darf das die asynchrone Verarbeitung für die anderen
 * nicht abschalten.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "applicationTaskExecutor")
    public Executor applicationTaskExecutor() {
        // Executor mit einem virtuellen Thread pro Task; der Wrapper übernimmt die ThreadLocals (z. B. MDC) des
        // einreichenden Threads, damit asynchrone Listener im Kontext des Use Cases loggen, der das Event publiziert
        // hat.
        return ContextExecutorService.wrap(
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual().name("vt-events-", 0).factory()),
                ContextSnapshotFactory.builder().build());
    }
}
