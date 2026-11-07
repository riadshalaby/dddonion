package net.rsworld.example.dddonion.architecture;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Markiert die Service-Klasse eines {@link UseCase.Kind#COMMAND}-Use-Cases, der in einer Transaktion laufen muss
 * (Spring-freier Marker). Das Bootstrap-Modul hüllt jeden Aufruf in eine Transaktion und wiederholt ihn bei einem
 * nebenläufigen Aggregate-Update.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TransactionalUseCase {}
