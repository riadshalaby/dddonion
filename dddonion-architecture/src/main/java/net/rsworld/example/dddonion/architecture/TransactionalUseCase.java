package net.rsworld.example.dddonion.architecture;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the service class of a {@link UseCase.Kind#COMMAND} use case that must run in one transaction (Spring-free
 * marker). The bootstrap module wraps each invocation in a transaction and retries it on a concurrent aggregate update.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TransactionalUseCase {}
