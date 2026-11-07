package net.rsworld.example.dddonion.infrastructure.web;

import java.util.UUID;
import net.rsworld.example.dddonion.application.context.UseCaseMetadata;
import net.rsworld.example.dddonion.infrastructure.web.dto.ActorType;

/** Baut für REST-Aufrufe die {@link UseCaseMetadata} aus den Header-Werten des Vertrags. */
final class UseCaseMetadataFactory {

    static final String CHANNEL = "rest";
    static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private UseCaseMetadataFactory() {}

    /**
     * Die übertragenen Werte validiert der Vertrag; ein fehlender Actor-Typ wird als UNKNOWN erfasst, nicht abgelehnt.
     */
    static UseCaseMetadata fromHeaders(ActorType wireActor, String correlationId) {
        var classified = wireActor == null
                ? net.rsworld.example.dddonion.application.context.ActorType.UNKNOWN
                : net.rsworld.example.dddonion.application.context.ActorType.valueOf(wireActor.name());
        var correlation = correlationId == null ? UUID.randomUUID().toString() : correlationId;
        return new UseCaseMetadata(classified, CHANNEL, correlation);
    }
}
