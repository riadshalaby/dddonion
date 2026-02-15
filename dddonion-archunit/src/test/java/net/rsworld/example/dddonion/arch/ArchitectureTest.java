package net.rsworld.example.dddonion.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** ArchUnit rules for DDD + Onion + Hex with a relaxed application whitelist. */
@AnalyzeClasses(packages = "net.rsworld.example.dddonion")
public class ArchitectureTest {

    /* --- Domain purity --- */

    /** Domain must not depend on frameworks or other layers. */
    @ArchTest
    static final ArchRule domain_should_not_depend_on_frameworks_or_other_layers = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..application..",
                    "..infrastructure..",
                    "..bootstrap..",
                    "..monitor..",
                    "org.springframework..",
                    "reactor..");

    /** Domain should only depend on JDK and itself. */
    @ArchTest
    static final ArchRule domain_should_only_depend_on_java_and_domain = classes()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("..domain..", "java..", "javax..");

    /* --- Application: relaxed whitelist --- */

    /** Application may depend on Domain (+ reactive APIs) and selected framework-neutral libs. */
    @ArchTest
    static final ArchRule application_whitelist_dependencies = classes()
            .that()
            .resideInAPackage("..application..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                    // own code
                    "..application..",
                    "..domain..",
                    // JDK
                    "java..",
                    "javax..",
                    // reactive
                    "reactor..",
                    "org.reactivestreams..",
                    // logging & annotations
                    "org.slf4j..",
                    "lombok..",
                    // optional: bean validation in app DTOs
                    "jakarta.validation..");

    /* --- Ports are interfaces --- */

    /** Ports must be interfaces (domain repository ports + application event publisher port). */
    @ArchTest
    static final ArchRule ports_are_interfaces = classes()
            .that()
            .resideInAPackage("..application..event..")
            .or()
            .resideInAPackage("..domain..*..repository..")
            .should()
            .beInterfaces();

    /* --- Layered architecture incl. 'monitor' layer --- */

    @ArchTest
    static final ArchRule layered_onion_hex_with_monitor = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("domain")
            .definedBy("..domain..")
            .layer("application")
            .definedBy("..application..")
            .layer("infrastructure")
            .definedBy("..infrastructure..")
            .optionalLayer("monitor")
            .definedBy("..monitor..") // optional: avoid "empty layer" noise
            .optionalLayer("boot")
            .definedBy("..bootstrap..") // optional: avoid "empty layer" noise

            // Outgoing (who MAY access whom)
            .whereLayer("domain")
            .mayNotAccessAnyLayer()
            .whereLayer("application")
            .mayOnlyAccessLayers("domain")
            .whereLayer("infrastructure")
            .mayOnlyAccessLayers("application", "domain")
            .whereLayer("monitor")
            .mayOnlyAccessLayers("domain")
            .whereLayer("boot")
            .mayOnlyAccessLayers("domain", "application", "infrastructure", "monitor", "boot")

            // Incoming (who MAY be accessed by whom) – allow self access
            .whereLayer("application")
            .mayOnlyBeAccessedByLayers("application", "infrastructure", "boot")
            .whereLayer("domain")
            .mayOnlyBeAccessedByLayers("domain", "application", "infrastructure", "monitor", "boot")
            .whereLayer("infrastructure")
            .mayOnlyBeAccessedByLayers("infrastructure", "boot")
            .whereLayer("monitor")
            .mayOnlyBeAccessedByLayers("monitor", "boot");

    /* --- Adapter isolation --- */

    /** Infrastructure adapter slices (web, persistence, ...) must not depend on each other. */
    @ArchTest
    static final ArchRule adapters_should_not_depend_on_each_other = slices().matching(
                    "net.rsworld.example.dddonion.infrastructure.(*)..")
            .should()
            .notDependOnEachOther();

    /** Web controllers must not depend on other infrastructure adapters (only on Application/Domain). */
    @ArchTest
    static final ArchRule controllers_only_call_app_or_domain = noClasses()
            .that()
            .resideInAPackage("..infrastructure.web..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infrastructure.persistence..", "..monitor..");

    /** Only the bootstrap module may declare @SpringBootApplication. */
    @ArchTest
    static final ArchRule only_bootstrap_has_springbootapplication = noClasses()
            .that()
            .resideOutsideOfPackage("..bootstrap..")
            .should()
            .beAnnotatedWith(org.springframework.boot.autoconfigure.SpringBootApplication.class);
}
