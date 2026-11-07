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
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.domain.order.model.OrderId;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * Wertbasierter Drift-Schutz zwischen den Value Objects der Domain und dem OpenAPI-Vertrag.
 *
 * <p>Statt Regex-<em>Strings</em> zu vergleichen (fragil und kosmetisch), schickt er eine Reihe konkreter Beispielwerte
 * durch BEIDE Seiten und prüft, dass jede Seite jeden Wert so einstuft wie die von Hand gesetzte Erwartung:
 *
 * <ul>
 *   <li><b>Domain-Seite</b> — der echte Konstruktor des Value Objects (akzeptiert oder wirft
 *       {@link IllegalArgumentException});
 *   <li><b>Übertragungsseite</b> — die Constraints ({@code pattern} / {@code minLength} / {@code maxLength} /
 *       {@code minimum}), gelesen aus {@code openapi/api.yaml}.
 * </ul>
 *
 * Wird eine Seite ohne die andere geändert, stuft der Test einen Beispielwert unterschiedlich ein und schlägt fehl. Ein
 * neues gemeinsames Value Object kostet eine Zeile in {@link #sharedTypes()}, nicht einen Test pro Eigenschaft.
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
                .as(
                        "OpenAPI ActorType enum must list every classifiable application ActorType (UNKNOWN is server-side only)")
                .isEqualTo(application);
    }

    private static List<SharedType> sharedTypes() {
        return List.of(
                new SharedType(
                        "OrderId",
                        v -> new OrderId((String) v),
                        List.of(
                                valid(UUID.randomUUID().toString()),
                                valid("3F6A9C1E-4B2D-4F0A-9C7E-8A1B2C3D4E5F"), // Hex in Großbuchstaben
                                invalid(""),
                                invalid("ORD-123"),
                                invalid("3f6a9c1e4b2d4f0a9c7e8a1b2c3d4e5f"), // 32 Zeichen, ohne Bindestriche
                                invalid("3f6a9c1e-4b2d-4f0a-9c7e-8a1b2c3d4e5"), // 35 Zeichen
                                invalid("z3f6a9c1-4b2d-4f0a-9c7e-8a1b2c3d4e5f"))), // kein Hex-Zeichen
                new SharedType(
                        "CustomerId",
                        v -> new CustomerId((String) v),
                        List.of(
                                valid(UUID.randomUUID().toString()),
                                valid("3F6A9C1E-4B2D-4F0A-9C7E-8A1B2C3D4E5F"), // Hex in Großbuchstaben
                                invalid(""),
                                invalid("CUS-123"),
                                invalid("3f6a9c1e4b2d4f0a9c7e8a1b2c3d4e5f"), // 32 Zeichen, ohne Bindestriche
                                invalid("3f6a9c1e-4b2d-4f0a-9c7e-8a1b2c3d4e5"), // 35 Zeichen
                                invalid("z3f6a9c1-4b2d-4f0a-9c7e-8a1b2c3d4e5f"))), // kein Hex-Zeichen
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
        try (InputStream in = OpenApiDomainDriftTest.class.getClassLoader().getResourceAsStream("openapi/api.yaml")) {
            if (in == null) {
                throw new IllegalStateException("openapi/api.yaml not found on the test classpath");
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

    /** Prüft einen einzelnen Wert gegen die JSON-Schema-Constraints, die für ein Component-Schema deklariert sind. */
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
                return false; // das Vorhandensein regelt `required`, nicht diese Constraints
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
