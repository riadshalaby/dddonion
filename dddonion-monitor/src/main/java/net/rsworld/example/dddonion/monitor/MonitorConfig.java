package net.rsworld.example.dddonion.monitor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class MonitorConfig {
    @Bean(name = "applicationTaskExecutor")
    public Executor applicationTaskExecutor() {
        // Virtual-thread per task executor
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("vt-events-", 0).factory());
    }
}
