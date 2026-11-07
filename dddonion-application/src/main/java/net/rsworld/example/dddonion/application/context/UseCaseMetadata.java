package net.rsworld.example.dddonion.application.context;

import java.util.Objects;

/**
 * Wer einen Use Case über welchen Kanal aufgerufen hat, explizit neben Command/Query übergeben. Der eingehende Adapter
 * baut sie; der Name des Use Cases gehört nicht dazu, weil er sich aus dem aufgerufenen Use Case ergibt.
 *
 * @param actorType fachliche Einordnung des Aufrufers
 * @param channel eingehender Kanal, z. B. {@code rest}
 * @param correlationId Id, die alle Logzeilen und Events eines Aufrufs verknüpft
 */
public record UseCaseMetadata(ActorType actorType, String channel, String correlationId) {

    public UseCaseMetadata {
        Objects.requireNonNull(actorType, "actorType must not be null");
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel must not be blank");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
    }
}
