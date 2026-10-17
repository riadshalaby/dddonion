package net.rsworld.example.dddonion.application.context;

import java.util.Objects;

/**
 * Who invoked a use case and through which channel, passed explicitly alongside the command/query. Built by the inbound
 * adapter; the use case name itself is not part of it because it follows from the invoked use case.
 *
 * @param actorType business classification of the caller
 * @param channel inbound channel, e.g. {@code rest}
 * @param correlationId id correlating all log lines and events of one invocation
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
