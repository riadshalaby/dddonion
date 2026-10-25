package net.rsworld.example.dddonion.bootstrap;

import net.rsworld.example.dddonion.architecture.ApplicationComponent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/** Registers the application services; the application module itself stays free of Spring stereotypes. */
@Configuration
@ComponentScan(
        basePackages = "net.rsworld.example.dddonion.application",
        useDefaultFilters = false,
        includeFilters = @Filter(type = FilterType.ANNOTATION, classes = ApplicationComponent.class))
public class ApplicationWiringConfig {}
