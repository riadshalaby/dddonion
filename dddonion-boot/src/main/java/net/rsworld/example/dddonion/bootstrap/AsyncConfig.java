package net.rsworld.example.dddonion.bootstrap;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Executor for all asynchronous event listeners (monitor and inbound event adapters). It lives in the composition root
 * because several modules rely on it; removing one of them must not switch off async processing for the others.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "applicationTaskExecutor")
    public Executor applicationTaskExecutor() {
        // Virtual-thread per task executor; the wrapper snapshots the submitting thread's ThreadLocals (e.g. MDC)
        // so async listeners log within the context of the use case that published the event.
        return ContextExecutorService.wrap(
                Executors.newThreadPerTaskExecutor(
                        Thread.ofVirtual().name("vt-events-", 0).factory()),
                ContextSnapshotFactory.builder().build());
    }
}
