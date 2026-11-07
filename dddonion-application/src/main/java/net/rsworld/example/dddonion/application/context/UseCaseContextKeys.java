package net.rsworld.example.dddonion.application.context;

/**
 * Schlüssel, unter denen der Kontext eines Use-Case-Aufrufs bereitsteht (Reactor-Context und SLF4J-MDC). Eingehende
 * Adapter, die außerhalb eines Use Cases laufen, z. B. Event-Listener, lesen den Kontext des Aufrufers mit diesen
 * Schlüsseln aus dem MDC.
 */
public final class UseCaseContextKeys {

    public static final String USECASE = "usecase";
    public static final String ACTOR_TYPE = "actorType";
    public static final String CORRELATION_ID = "correlationId";

    private UseCaseContextKeys() {}
}
