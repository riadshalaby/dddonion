package net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.mapper;

import net.rsworld.example.dddonion.domain.common.Money;
import net.rsworld.example.dddonion.domain.customer.model.Customer;
import net.rsworld.example.dddonion.domain.customer.model.CustomerEmail;
import net.rsworld.example.dddonion.domain.customer.model.CustomerId;
import net.rsworld.example.dddonion.infrastructure.persistence.r2dbc.dto.CustomerRow;

public final class CustomerMapper {

    private CustomerMapper() {}

    public static CustomerRow toRow(Customer c) {
        CustomerRow r = new CustomerRow();
        r.setId(c.id().value());
        r.setEmail(c.email().value());
        r.setCreditLimit(c.creditLimit().amount());
        r.setOpenAmount(c.openAmount().amount());
        r.setVersion(c.version()); // the adapter sets it to null for an INSERT
        return r;
    }

    public static Customer toDomain(CustomerRow r) {
        long version = r.getVersion() == null ? 0L : r.getVersion();
        return new Customer(
                new CustomerId(r.getId()),
                new CustomerEmail(r.getEmail()),
                new Money(r.getCreditLimit()),
                new Money(r.getOpenAmount()),
                version);
    }
}
