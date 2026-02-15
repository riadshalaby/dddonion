package net.rsworld.example.dddonion.infrastructure.outbox;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

@DependsOn("liquibase")
@ConditionalOnProperty(prefix = "app.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@Component
public class OutboxPublisher implements ApplicationListener<ApplicationReadyEvent> {

    private final OutboxRepository outbox;
    private final KafkaSender<String, String> sender;
    private final String topic;
    private final int batchSize;
    private Disposable subscription;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public OutboxPublisher(
            OutboxRepository outbox,
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${app.topics.orders:orders.events}") String topic,
            @Value("${app.outbox.batch-size:200}") int batchSize) {
        this.outbox = outbox;
        this.topic = topic;
        this.batchSize = batchSize;

        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 0);
        props.put(ProducerConfig.RETRIES_CONFIG, 10);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        this.sender = KafkaSender.create(SenderOptions.create(props));
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        this.subscription = Flux.interval(Duration.ofSeconds(1))
                .flatMap(tick -> outbox.findBatch(batchSize))
                .concatMap(evt -> sender.send(Flux.just(SenderRecord.create(
                                topic, null, null, evt.getAggregateId(), evt.getPayloadJson(), evt.getId())))
                        .next()
                        .flatMap(res -> outbox.markPublished(evt.getId()).then()))
                .doOnSubscribe(s -> started.set(true))
                .onErrorContinue((ex, obj) -> {
                    /* log */
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    public boolean isStarted() {
        return started.get();
    }

    @PreDestroy
    public void shutdown() {
        started.set(false);
        if (subscription != null) subscription.dispose();
        sender.close();
    }
}
