package net.rsworld.example.dddonion.application.context;

/**
 * Keys under which the use case invocation context is exposed (Reactor context and SLF4J MDC). Inbound adapters that
 * run outside a use case, e.g. event listeners, read the caller's context from the MDC with these keys.
 */
public final class UseCaseContextKeys {

    public static final String USECASE = "usecase";
    public static final String ACTOR_TYPE = "actorType";
    public static final String CORRELATION_ID = "correlationId";

    private UseCaseContextKeys() {}
}
