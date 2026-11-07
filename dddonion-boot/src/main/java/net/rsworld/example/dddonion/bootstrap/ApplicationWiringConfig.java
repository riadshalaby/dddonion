package net.rsworld.example.dddonion.bootstrap;

import net.rsworld.example.dddonion.architecture.ApplicationComponent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/** Registriert die Application Services; das Application-Modul selbst bleibt frei von Spring-Stereotypen. */
@Configuration
@ComponentScan(
        basePackages = "net.rsworld.example.dddonion.application",
        useDefaultFilters = false,
        includeFilters = @Filter(type = FilterType.ANNOTATION, classes = ApplicationComponent.class))
public class ApplicationWiringConfig {}
