package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.dto.CustomerRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CustomerMapperTest {

    private static final String ID = "3f6a9c1e-4b2d-4f0a-9c7e-8a1b2c3d4e5f";

    @Test
    @DisplayName("Mappt alle Felder hin und zurück")
    void roundTrip_keepsAllFields() {
        Customer customer = new Customer(
                new CustomerId(ID),
                new CustomerEmail("john.doe@example.com"),
                new Money(new BigDecimal("1000.00")),
                new Money(new BigDecimal("42.50")),
                7L);

        CustomerRow row = CustomerMapper.toRow(customer);

        assertEquals(ID, row.getId());
        assertEquals("john.doe@example.com", row.getEmail());
        assertEquals(new BigDecimal("1000.00"), row.getCreditLimit());
        assertEquals(new BigDecimal("42.50"), row.getOpenAmount());
        assertEquals(7L, row.getVersion());

        Customer back = CustomerMapper.toDomain(row);

        assertEquals(ID, back.id().value());
        assertEquals("john.doe@example.com", back.email().value());
        assertEquals(new BigDecimal("1000.00"), back.creditLimit().amount());
        assertEquals(new BigDecimal("42.50"), back.openAmount().amount());
        assertEquals(7L, back.version());
    }

    @Test
    @DisplayName("Setzt beim Mapping auf Domain die Version auf 0, wenn sie in der Row null ist")
    void toDomain_defaults_null_row_version_to_zero() {
        CustomerRow row =
                new CustomerRow(ID, "a@example.com", new BigDecimal("10.00"), new BigDecimal("1.00"), null, null, null);

        assertEquals(0L, CustomerMapper.toDomain(row).version());
    }
}
