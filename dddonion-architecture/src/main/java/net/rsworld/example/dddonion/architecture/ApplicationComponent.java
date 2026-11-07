package net.rsworld.example.dddonion.architecture;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Markiert eine Klasse der Application-Schicht, die das Bootstrap-Modul als Bean registriert (Spring-freier Marker).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApplicationComponent {}
