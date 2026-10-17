package net.rsworld.example.dddonion.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.rsworld.example.dddonion.application.context.ActorType;
import net.rsworld.example.dddonion.domain.order.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.order.model.Money;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Value-based drift guard between the domain value objects and the OpenAPI contract.
 *
 * <p>Rather than comparing regex <em>strings</em> (brittle and cosmetic), it runs a set of concrete sample values
 * through BOTH sides and asserts each side classifies every value as the hand-labelled expectation:
 *
 * <ul>
 *   <li><b>domain side</b> — the real value-object constructor (accepts, or throws {@link IllegalArgumentException});
 *   <li><b>wire side</b> — the constraints ({@code pattern} / {@code minLength} / {@code maxLength} / {@code minimum})
 *       parsed from {@code openapi/orders.yaml}.
 * </ul>
 *
 * If either side is changed without the other, some sample value is classified differently and this test fails. Adding
 * a new shared value object costs one row in {@link #sharedTypes()}, not one test per property.
 */
class OpenApiDomainDriftTest {

    private static final Map<String, Object> SCHEMAS = loadSchemas();

    @TestFactory
    Stream<DynamicTest> domainAndOpenApiAgreeOnEveryValue() {
        return sharedTypes().stream().flatMap(type -> type.samples().stream()
                .map(sample -> DynamicTest.dynamicTest(type.schemaName() + " :: " + sample.label(), () -> {
                    boolean domainAccepts = domainAccepts(type.factory(), sample.value());
                    boolean wireAccepts = constraintsOf(type.schemaName()).accepts(sample.value());

                    assertThat(domainAccepts)
                            .as(
                                    "domain VO for '%s' must %s value <%s>",
                                    type.schemaName(), verb(sample), sample.value())
                            .isEqualTo(sample.valid());
                    assertThat(wireAccepts)
                            .as(
                                    "OpenAPI schema '%s' must %s value <%s> (drift vs. domain VO)",
                                    type.schemaName(), verb(sample), sample.value())
                            .isEqualTo(sample.valid());
                })));
    }

    @Test
    void actorTypeEnumMatchesApplicationActorTypeWithoutUnknown() {
        List<String> application = Arrays.stream(ActorType.values())
                .filter(t -> t != ActorType.UNKNOWN)
                .map(Enum::name)
                .toList();

        assertThat(((Map<?, ?>) SCHEMAS.get("ActorType")).get("enum"))
                .as("OpenAPI ActorType enum must list every classifiable application ActorType (UNKNOWN is server-side only)")
                .isEqualTo(application);
    }

    private static List<SharedType> sharedTypes() {
        return List.of(
                new SharedType(
                        "OrderId",
                        v -> new OrderId((String) v),
                        List.of(
                                valid(UUID.randomUUID().toString()),
                                valid("3F6A9C1E-4B2D-4F0A-9C7E-8A1B2C3D4E5F"), // upper-case hex
                                invalid(""),
                                invalid("ORD-123"),
                                invalid("3f6a9c1e4b2d4f0a9c7e8a1b2c3d4e5f"), // 32 chars, no dashes
                                invalid("3f6a9c1e-4b2d-4f0a-9c7e-8a1b2c3d4e5"), // 35 chars
                                invalid("z3f6a9c1-4b2d-4f0a-9c7e-8a1b2c3d4e5f"))), // non-hex character
                new SharedType(
                        "CustomerEmail",
                        v -> new CustomerEmail((String) v),
                        List.of(
                                valid("test@example.com"),
                                valid("user+tag@sub.example.co.uk"),
                                invalid(""),
                                invalid("not-an-email"),
                                invalid("a@b"),
                                invalid("user@"),
                                invalid(".user@example.com"))),
                new SharedType(
                        "MonetaryAmount",
                        v -> new Money((BigDecimal) v),
                        List.of(
                                valid(new BigDecimal("0")),
                                valid(new BigDecimal("12.34")),
                                valid(new BigDecimal("999999999.99")),
                                invalid(new BigDecimal("-0.01")),
                                invalid(new BigDecimal("-1")))));
    }

    private static boolean domainAccepts(Consumer<Object> factory, Object value) {
        try {
            factory.accept(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static SchemaConstraints constraintsOf(String schemaName) {
        Object schema = SCHEMAS.get(schemaName);
        if (!(schema instanceof Map<?, ?> map)) {
            throw new IllegalStateException("OpenAPI schema not found: " + schemaName);
        }
        return new SchemaConstraints(map);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadSchemas() {
        try (InputStream in =
                OpenApiDomainDriftTest.class.getClassLoader().getResourceAsStream("openapi/orders.yaml")) {
            if (in == null) {
                throw new IllegalStateException("openapi/orders.yaml not found on the test classpath");
            }
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> components = (Map<String, Object>) root.get("components");
            return (Map<String, Object>) components.get("schemas");
        } catch (Exception e) {
            throw new IllegalStateException("Could not load OpenAPI schemas", e);
        }
    }

    private static String verb(Sample s) {
        return s.valid() ? "accept" : "reject";
    }

    private static Sample valid(Object value) {
        return new Sample(value, true);
    }

    private static Sample invalid(Object value) {
        return new Sample(value, false);
    }

    private record SharedType(String schemaName, Consumer<Object> factory, List<Sample> samples) {}

    private record Sample(Object value, boolean valid) {
        String label() {
            return (valid ? "valid" : "invalid") + " <" + value + ">";
        }
    }

    /** Evaluates a single value against the JSON-Schema constraints declared for a component schema. */
    private static final class SchemaConstraints {
        private final String type;
        private final Integer minLength;
        private final Integer maxLength;
        private final Pattern pattern;
        private final BigDecimal minimum;
        private final BigDecimal maximum;

        SchemaConstraints(Map<?, ?> schema) {
            this.type = (String) schema.get("type");
            this.minLength = asInt(schema.get("minLength"));
            this.maxLength = asInt(schema.get("maxLength"));
            Object p = schema.get("pattern");
            this.pattern = p == null ? null : Pattern.compile((String) p);
            this.minimum = asDecimal(schema.get("minimum"));
            this.maximum = asDecimal(schema.get("maximum"));
        }

        boolean accepts(Object value) {
            if (value == null) {
                return false; // presence is governed by `required`, not by these constraints
            }
            return switch (type) {
                case "string" -> acceptsString(value.toString());
                case "number", "integer" -> acceptsNumber(new BigDecimal(value.toString()));
                default -> throw new IllegalStateException("Unhandled schema type: " + type);
            };
        }

        private boolean acceptsString(String s) {
            if (minLength != null && s.length() < minLength) return false;
            if (maxLength != null && s.length() > maxLength) return false;
            return pattern == null || pattern.matcher(s).matches();
        }

        private boolean acceptsNumber(BigDecimal n) {
            if (minimum != null && n.compareTo(minimum) < 0) return false;
            return maximum == null || n.compareTo(maximum) <= 0;
        }

        private static Integer asInt(Object o) {
            return o == null ? null : ((Number) o).intValue();
        }

        private static BigDecimal asDecimal(Object o) {
            return o == null ? null : new BigDecimal(o.toString());
        }
    }
}
