package net.rsworld.example.dddonion.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.OutputStreamAppender;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import net.rsworld.example.dddonion.domain.order.event.OrderPlaced;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class DomainEventLoggingListenerTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private OutputStreamAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender;

    @AfterEach
    void tearDown() {
        if (appender != null) {
            appender.stop();
            ((Logger) LoggerFactory.getLogger(DomainEventLoggingListener.class)).detachAppender(appender);
        }
    }

    @Test
    @DisplayName("Schreibt beim Empfang eines DomainEvents alle relevanten Felder ins Log")
    void onDomainEvent_logsExpectedFields() {
        // route logger output of the class under test to an in-memory stream
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = (Logger) LoggerFactory.getLogger(DomainEventLoggingListener.class);
        logger.setLevel(Level.INFO);

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(ctx);
        encoder.setPattern("%msg%n");
        encoder.start();

        appender = new OutputStreamAppender<>();
        appender.setContext(ctx);
        appender.setEncoder(encoder);
        appender.setOutputStream(out);
        appender.start();

        logger.addAppender(appender);

        DomainEventLoggingListener listener = new DomainEventLoggingListener();

        var evt = new OrderPlaced("agg-1", 1L, Instant.now(), "john.doe@example.com", new BigDecimal("12.34"));

        listener.onDomainEvent(evt);

        String logged = out.toString();
        assertThat(logged).contains("DomainEvent received");
        assertThat(logged).contains("type=OrderPlaced");
        assertThat(logged).contains("aggregateId=agg-1");
        assertThat(logged).contains("sequence=1");
        assertThat(logged).contains("occurredAt=");
    }
}
