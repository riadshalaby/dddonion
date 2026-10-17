package net.rsworld.example.dddonion.bootstrap.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes exactly one line per use-case execution to the {@value #LOGGER_NAME} logger. It is fed by the same observation
 * as the timer metric, so audit logs and metrics cannot disagree. Values are attached as SLF4J key-value pairs, which
 * structured (JSON) logging emits as separate fields for Loki/Grafana.
 */
public class AuditLogObservationHandler implements ObservationHandler<Observation.Context> {

    public static final String LOGGER_NAME = "audit";

    private static final Logger AUDIT = LoggerFactory.getLogger(LOGGER_NAME);

    @Override
    public boolean supportsContext(Observation.Context context) {
        return UseCaseObservability.OBSERVATION_NAME.equals(context.getName());
    }

    @Override
    public void onStart(Observation.Context context) {
        context.put(StartNanos.class, new StartNanos(System.nanoTime()));
    }

    @Override
    public void onStop(Observation.Context context) {
        StartNanos start = context.get(StartNanos.class);
        long durationMs = start == null ? -1 : (System.nanoTime() - start.value()) / 1_000_000;
        String usecase = value(context, UseCaseObservability.KEY_USECASE);
        String actorType = value(context, UseCaseObservability.KEY_ACTOR_TYPE);
        String channel = value(context, UseCaseObservability.KEY_CHANNEL);
        String outcome = value(context, UseCaseObservability.KEY_OUTCOME);
        String correlationId = value(context, UseCaseObservability.KEY_CORRELATION_ID);
        String error = context.getError() == null
                ? "none"
                : context.getError().getClass().getSimpleName();

        AUDIT.atInfo()
                .setMessage("usecase={} actorType={} channel={} outcome={} error={} durationMs={} correlationId={}")
                .addArgument(usecase)
                .addArgument(actorType)
                .addArgument(channel)
                .addArgument(outcome)
                .addArgument(error)
                .addArgument(durationMs)
                .addArgument(correlationId)
                .addKeyValue("usecase", usecase)
                .addKeyValue("actorType", actorType)
                .addKeyValue("channel", channel)
                .addKeyValue("outcome", outcome)
                .addKeyValue("error", error)
                .addKeyValue("durationMs", durationMs)
                .addKeyValue("correlationId", correlationId)
                .log();
    }

    private static String value(Observation.Context context, String key) {
        KeyValue kv = context.getLowCardinalityKeyValue(key);
        if (kv == null) {
            kv = context.getHighCardinalityKeyValue(key);
        }
        return kv == null ? "n/a" : kv.getValue();
    }

    private record StartNanos(long value) {}
}
