package net.rsworld.example.archfixture.infrastructure.event;

import java.util.function.Consumer;

/** Fixture: allowed, an inbound event adapter that only calls a use case. */
class FixtureUseCaseListener {
    private final Consumer<Object> useCase;

    FixtureUseCaseListener(Consumer<Object> useCase) {
        this.useCase = useCase;
    }

    void on(Object event) {
        useCase.accept(event);
    }
}
