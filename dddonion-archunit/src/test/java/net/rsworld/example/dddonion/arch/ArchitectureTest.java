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

/** ArchUnit-Regeln für DDD + Onion + Hex mit gelockerter Whitelist für die Application. */
@AnalyzeClasses(packages = "net.rsworld.example.dddonion", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {

    /* --- Reinheit der Domain --- */

    /** Die Domain darf weder von Frameworks noch von anderen Schichten abhängen. */
    @ArchTest
    static final ArchRule domain_should_not_depend_on_frameworks_or_other_layers = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..application..", "..infrastructure..", "..bootstrap..", "..monitor..", "org.springframework..");

    /** Die Domain darf nur von JDK, Reactor (reaktive Port-Typen) und sich selbst abhängen. */
    @ArchTest
    static final ArchRule domain_should_only_depend_on_java_and_domain = classes()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("..domain..", "java..", "javax..", "reactor..", "org.reactivestreams..");

    /* --- Application: gelockerte Whitelist --- */

    /**
     * Die Application darf von der Domain (+ reaktiven APIs) und ausgewählten frameworkneutralen Bibliotheken abhängen.
     */
    @ArchTest
    static final ArchRule application_whitelist_dependencies = classes()
            .that()
            .resideInAPackage("..application..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage(
                    // eigener Code
                    "..application..",
                    "..domain..",
                    // Architektur-Annotationen
                    "net.rsworld.example.dddonion.architecture..",
                    // JDK
                    "java..",
                    "javax..",
                    // reaktiv
                    "reactor..",
                    "org.reactivestreams..",
                    // Logging & Annotationen
                    "org.slf4j..",
                    "lombok..",
                    // optional: Bean Validation in App-DTOs
                    "jakarta.validation..");

    /* --- Ports sind Interfaces --- */

    /** Ports müssen Interfaces sein (Repository-Ports der Domain + Event-Publisher-Port der Application). */
    @ArchTest
    static final ArchRule ports_are_interfaces = classes()
            .that()
            .resideInAPackage("..application..event..")
            .or()
            .resideInAPackage("..domain..*..repository..")
            .should()
            .beInterfaces();

    /** Use-Case-Ports sind annotierte Interfaces, damit Name und Art explizit sind. */
    @ArchTest
    static final ArchRule use_case_ports_are_annotated_interfaces = classes()
            .that()
            .resideInAPackage("..application..usecase..")
            .should()
            .beInterfaces()
            .andShould()
            .beAnnotatedWith(UseCase.class);

    /** Services sind nur über ihr Use-Case-Interface erreichbar, daher sind ihre Klassen nicht public. */
    @ArchTest
    static final ArchRule application_service_package_is_not_public = classes()
            .that()
            .resideInAPackage("..application..service..")
            .and()
            .areTopLevelClasses()
            .should()
            .notBePublic();

    /** Use-Case-Implementierungen werden per Component-Scan auf {@code @ApplicationComponent} verdrahtet. */
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

    /* --- Aggregate-Grenzen --- */

    /** Aggregates referenzieren einander im Domain-Code nur über {@code *Id}-Typen. */
    @ArchTest
    static final ArchRule aggregates_reference_each_other_only_by_id = slices().matching("..domain.(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(alwaysTrue(), resideInAPackage("..domain.common.."))
            .ignoreDependency(alwaysTrue(), simpleNameEndingWith("Id"));

    /** Use Cases verschiedener Aggregates sind unabhängig; geteilt werden nur Context- und Event-Ports. */
    @ArchTest
    static final ArchRule aggregate_use_cases_are_independent = slices().matching("..application.(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(alwaysTrue(), resideInAnyPackage("..application.context..", "..application.event.."));

    /**
     * Jeder Zugriff auf einen Repository-Port außer einem Lesen ({@code find*}, {@code exists*}) braucht die
     * Transaktions-, Retry- und Event-Pipeline von {@code @TransactionalUseCase}. Methodenreferenzen zählen als
     * Zugriffe.
     */
    @ArchTest
    static final ArchRule repository_writes_only_in_transactional_use_cases = classes()
            .that()
            .resideInAPackage("..application..")
            .should(writeToRepositoriesOnlyWhenTransactional())
            .because("writes need the transaction, retry and post-commit event handling of @TransactionalUseCase;"
                    + " shared building blocks read and compute, only the service with @TransactionalUseCase saves");

    /**
     * Events werden pro Transaktionsversuch gepuffert und nach dem Commit publiziert; außerhalb von
     * {@code @TransactionalUseCase} gibt es keinen Puffer, und {@code publish} scheitert zur Laufzeit.
     * Methodenreferenzen zählen als Zugriffe.
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
                // Zugriffe umfassen Methodenaufrufe und Methodenreferenzen
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

    /** Verschachtelte und anonyme Klassen (samt ihren Lambdas) gehören zu dem Use Case, der sie umschließt. */
    private static boolean isTransactional(JavaClass javaClass) {
        JavaClass outer = javaClass;
        while (outer.getEnclosingClass().isPresent()) {
            outer = outer.getEnclosingClass().get();
        }
        return outer.isAnnotatedWith(TransactionalUseCase.class);
    }

    /* --- Schichtenarchitektur inkl. 'monitor'-Schicht --- */

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
            .definedBy("..monitor..") // optional: vermeidet Meldungen zu leeren Schichten
            .optionalLayer("boot")
            .definedBy("..bootstrap..") // optional: vermeidet Meldungen zu leeren Schichten

            // Ausgehend (wer auf wen zugreifen DARF)
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

            // Eingehend (wer von wem angesprochen werden DARF) – Zugriff auf sich selbst erlaubt
            .whereLayer("application")
            .mayOnlyBeAccessedByLayers("application", "infrastructure", "boot")
            .whereLayer("domain")
            .mayOnlyBeAccessedByLayers("domain", "application", "infrastructure", "monitor", "boot")
            .whereLayer("infrastructure")
            .mayOnlyBeAccessedByLayers("infrastructure", "boot")
            .whereLayer("monitor")
            .mayOnlyBeAccessedByLayers("monitor", "boot");

    /* --- Adapter-Isolation --- */

    /** Slices der Infrastruktur-Adapter (web, persistence, ...) dürfen nicht voneinander abhängen. */
    @ArchTest
    static final ArchRule adapters_should_not_depend_on_each_other = slices().matching(
                    "net.rsworld.example.dddonion.infrastructure.(*)..")
            .should()
            .notDependOnEachOther();

    /** Web-Controller dürfen nicht von anderen Infrastruktur-Adaptern abhängen (nur von Application/Domain). */
    @ArchTest
    static final ArchRule controllers_only_call_app_or_domain = noClasses()
            .that()
            .resideInAPackage("..infrastructure.web..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infrastructure.persistence..", "..monitor..");

    /** Eingehende Adapter (HTTP) erreichen die Domain nur über Use Cases, nie über Repositories. */
    @ArchTest
    static final ArchRule inbound_adapters_do_not_access_repositories = noClasses()
            .that()
            .resideInAPackage("..infrastructure.web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..domain..repository..")
            .because("inbound adapters go through use cases, which own loading and saving aggregates");

    /** Nur das Bootstrap-Modul darf @SpringBootApplication deklarieren. */
    @ArchTest
    static final ArchRule only_bootstrap_has_springbootapplication = noClasses()
            .that()
            .resideOutsideOfPackage("..bootstrap..")
            .should()
            .beAnnotatedWith(org.springframework.boot.autoconfigure.SpringBootApplication.class);
}
