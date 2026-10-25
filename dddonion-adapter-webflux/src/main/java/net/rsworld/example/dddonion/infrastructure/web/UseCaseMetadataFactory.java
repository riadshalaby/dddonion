package net.rsworld.example.dddonion.infrastructure.web;

import java.util.UUID;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.infrastructure.web.dto.ActorType;

/** Builds the {@link UseCaseMetadata} for REST calls from the contract's header values. */
final class UseCaseMetadataFactory {

    static final String CHANNEL = "rest";
    static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private UseCaseMetadataFactory() {}

    /** Wire values are validated by the contract; a missing actor type is recorded as UNKNOWN, not rejected. */
    static UseCaseMetadata fromHeaders(ActorType wireActor, String correlationId) {
        var classified = wireActor == null
                ? net.rsworld.example.dddonion.application.context.ActorType.UNKNOWN
                : net.rsworld.example.dddonion.application.context.ActorType.valueOf(wireActor.name());
        var correlation = correlationId == null ? UUID.randomUUID().toString() : correlationId;
        return new UseCaseMetadata(classified, CHANNEL, correlation);
    }
}
