package net.rsworld.example.dddonion.arch;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import net.rsworld.example.dddonion.application.event.DomainEventPublisherPort;
import net.rsworld.example.dddonion.architecture.ApplicationComponent;
import net.rsworld.example.dddonion.architecture.TransactionalUseCase;
import net.rsworld.example.dddonion.architecture.UseCase;

/** ArchUnit rules for DDD + Onion + Hex with a relaxed application whitelist. */
@AnalyzeClasses(packages = "net.rsworld.example.dddonion", importOptions = ImportOption.DoNotIncludeTests.class)
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
                    "..application..", "..infrastructure..", "..bootstrap..", "..monitor..", "org.springframework..");

    /** Domain should only depend on JDK, Reactor (reactive port types) and itself. */
    @ArchTest
    static final ArchRule domain_should_only_depend_on_java_and_domain = classes()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("..domain..", "java..", "javax..", "reactor..", "org.reactivestreams..");

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
                    // architecture annotations
                    "net.rsworld.example.dddonion.architecture..",
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

    /** Use case ports are annotated interfaces, so name and kind are explicit. */
    @ArchTest
    static final ArchRule use_case_ports_are_annotated_interfaces = classes()
            .that()
            .resideInAPackage("..application..usecase..")
            .should()
            .beInterfaces()
            .andShould()
            .beAnnotatedWith(UseCase.class);

    /** Services are only reachable through their use case interface, so their classes are not public. */
    @ArchTest
    static final ArchRule application_service_package_is_not_public = classes()
            .that()
            .resideInAPackage("..application..service..")
            .and()
            .areTopLevelClasses()
            .should()
            .notBePublic();

    /** Use case implementations are wired by component scan on {@code @ApplicationComponent}. */
    @ArchTest
    static final ArchRule use_case_implementations_are_application_components = classes()
            .that()
            .resideInAPackage("..application..")
            .and()
            .areNotInterfaces()
            .and(implementAUseCase())
            .should()
            .beAnnotatedWith(ApplicationComponent.class);

    @ArchTest
    static final ArchRule use_cases_do_not_call_use_cases = noClasses()
            .that()
            .resideInAPackage("..application..service..")
            .should()
            .accessTargetWhere(target(owner(resideInAPackage("..application..usecase.."))))
            .because("shared logic belongs in a package-private component, not in another use case");

    private static DescribedPredicate<JavaClass> implementAUseCase() {
        return DescribedPredicate.describe(
                "implement a @UseCase interface",
                javaClass -> javaClass.getAllRawInterfaces().stream().anyMatch(i -> i.isAnnotatedWith(UseCase.class)));
    }

    /* --- Aggregate boundaries --- */

    /** Aggregates reference each other in domain code only through {@code *Id} types. */
    @ArchTest
    static final ArchRule aggregates_reference_each_other_only_by_id = slices().matching("..domain.(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(alwaysTrue(), resideInAPackage("..domain.common.."))
            .ignoreDependency(alwaysTrue(), simpleNameEndingWith("Id"));

    /** Use cases of different aggregates are independent; only context and event ports are shared. */
    @ArchTest
    static final ArchRule aggregate_use_cases_are_independent = slices().matching("..application.(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(alwaysTrue(), resideInAnyPackage("..application.context..", "..application.event.."));

    /**
     * Anything but a read ({@code find*}, {@code exists*}) on a repository port needs the transaction, retry and event
     * pipeline of {@code @TransactionalUseCase}. Method references count as accesses.
     */
    @ArchTest
    static final ArchRule repository_writes_only_in_transactional_use_cases = classes()
            .that()
            .resideInAPackage("..application..")
            .should(writeToRepositoriesOnlyWhenTransactional())
            .because("writes need the transaction, retry and post-commit event handling of @TransactionalUseCase;"
                    + " shared building blocks read and compute, only the service with @TransactionalUseCase saves");

    /**
     * Events are buffered per transaction attempt and published after the commit; outside {@code @TransactionalUseCase}
     * there is no buffer and {@code publish} fails at runtime. Method references count as accesses.
     */
    @ArchTest
    static final ArchRule domain_events_published_only_in_transactional_use_cases = classes()
            .that()
            .resideInAPackage("..application..")
            .should(publishEventsOnlyWhenTransactional())
            .because("events are published after the commit, which only @TransactionalUseCase provides");

    private static ArchCondition<JavaClass> publishEventsOnlyWhenTransactional() {
        return new ArchCondition<>(
                "publish domain events only if annotated with @" + TransactionalUseCase.class.getSimpleName()) {
            @Override
            public void check(JavaClass service, ConditionEvents events) {
                if (isTransactional(service)) {
                    return;
                }
                for (JavaAccess<?> access : service.getAccessesFromSelf()) {
                    if (access instanceof JavaCodeUnitAccess<?>
                            && access.getTargetOwner().isEquivalentTo(DomainEventPublisherPort.class)
                            && access.getTarget().getName().equals("publish")) {
                        events.add(SimpleConditionEvent.violated(access, access.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> writeToRepositoriesOnlyWhenTransactional() {
        return new ArchCondition<>("write to repositories (non find*/exists* methods) only if annotated with @"
                + TransactionalUseCase.class.getSimpleName()) {
            @Override
            public void check(JavaClass service, ConditionEvents events) {
                if (isTransactional(service)) {
                    return;
                }
                // accesses include method calls and method references
                for (JavaAccess<?> access : service.getAccessesFromSelf()) {
                    if (!(access instanceof JavaCodeUnitAccess<?>)) {
                        continue;
                    }
                    String name = access.getTarget().getName();
                    boolean repository =
                            resideInAPackage("..domain..repository..").test(access.getTargetOwner());
                    boolean read = name.startsWith("find") || name.startsWith("exists");
                    if (repository && !read) {
                        events.add(SimpleConditionEvent.violated(access, access.getDescription()));
                    }
                }
            }
        };
    }

    /** Nested and anonymous classes (and their lambdas) belong to the use case that encloses them. */
    private static boolean isTransactional(JavaClass javaClass) {
        JavaClass outer = javaClass;
        while (outer.getEnclosingClass().isPresent()) {
            outer = outer.getEnclosingClass().get();
        }
        return outer.isAnnotatedWith(TransactionalUseCase.class);
    }

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

    /** Inbound adapters (HTTP) reach the domain only through use cases, never through repositories. */
    @ArchTest
    static final ArchRule inbound_adapters_do_not_access_repositories = noClasses()
            .that()
            .resideInAPackage("..infrastructure.web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..domain..repository..")
            .because("inbound adapters go through use cases, which own loading and saving aggregates");

    /** Only the bootstrap module may declare @SpringBootApplication. */
    @ArchTest
    static final ArchRule only_bootstrap_has_springbootapplication = noClasses()
            .that()
            .resideOutsideOfPackage("..bootstrap..")
            .should()
            .beAnnotatedWith(org.springframework.boot.autoconfigure.SpringBootApplication.class);
}
