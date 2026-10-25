package net.rsworld.example.dddonion.arch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.Test;

/** Negative tests: prove the aggregate boundary rules detect violations in fixtures outside the main import. */
class AggregateWriteRuleTest {

    private static final JavaClasses FIXTURES =
            new ClassFileImporter().importPackages("net.rsworld.example.archfixture");

    private static String violations(ArchRule rule) {
        EvaluationResult result = rule.evaluate(FIXTURES);
        assertTrue(result.hasViolation());
        return String.join("\n", result.getFailureReport().getDetails());
    }

    @Test
    void detectsWritesToForeignAggregateRepository() {
        String details = violations(ArchitectureTest.aggregates_write_only_their_own_repository);

        assertTrue(details.contains("writesViaCall"), details);
        assertTrue(details.contains("writesViaMethodReference"), details);
        assertTrue(details.contains("deletesForeign"), details);
        assertTrue(details.contains("FixtureCustomerRepository.save"), details);
        assertTrue(details.contains("FixtureCustomerRepository.delete"), details);
        assertFalse(details.contains("readsForeign"), details);
        assertFalse(details.contains("writesOwn"), details);
    }

    @Test
    void detectsAggregateReferenceWithoutId() {
        String details = violations(ArchitectureTest.aggregates_reference_each_other_only_by_id);

        assertTrue(details.contains("FixtureOrderHoldingAggregate"), details);
        assertFalse(details.contains("FixtureOrderHoldingId"), details);
    }

    @Test
    void detectsInboundAdaptersAccessingRepositories() {
        String details = violations(ArchitectureTest.inbound_adapters_do_not_access_repositories);

        assertTrue(details.contains("FixtureRepositoryController"), details);
        assertTrue(details.contains("FixtureRepositoryListener"), details);
        assertFalse(details.contains("FixtureUseCaseListener"), details);
    }
}
