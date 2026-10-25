package net.rsworld.example.dddonion.architecture;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks an application-layer use case port and describes it for wiring, metrics and audit. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface UseCase {

    /**
     * Stable use case name; convention {@code <aggregate>.<verb>}, e.g. {@code order.place}. Further dot-separated
     * lowercase segments and hyphens are allowed (pattern {@code ^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+$}), e.g.
     * {@code customer.change-credit-limit}.
     *
     * <p>The name is part of the metric and audit contract (label {@code usecase}). It must not change when the
     * interface is renamed.
     */
    String name();

    /**
     * Command or query semantics, not the trigger. An event-triggered use case that changes state is a
     * {@link Kind#COMMAND}; the trigger shows in the {@code channel} of the metadata.
     */
    Kind kind();

    enum Kind {
        COMMAND,
        QUERY
    }
}
