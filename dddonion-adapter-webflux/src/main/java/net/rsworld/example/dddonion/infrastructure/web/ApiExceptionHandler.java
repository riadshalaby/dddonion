package net.rsworld.example.dddonion.infrastructure.web;

import jakarta.validation.ConstraintViolationException;
import net.rsworld.example.dddonion.application.order.UnknownCustomerException;
import net.rsworld.example.dddonion.domain.common.ConcurrentAggregateUpdateException;
import net.rsworld.example.dddonion.domain.customer.model.CreditLimitExceededException;
import net.rsworld.example.dddonion.domain.customer.model.CustomerNotFoundException;
import net.rsworld.example.dddonion.domain.customer.model.EmailAlreadyRegisteredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain/application failures into RFC 7807 {@code application/problem+json} responses, as declared in the
 * OpenAPI contract.
 *
 * <p>Mappings:
 *
 * <ul>
 *   <li>{@code 400}: {@link IllegalArgumentException} (validation inside domain commands and value objects) and
 *       {@link ConstraintViolationException} (non-body parameters such as {@code X-Correlation-Id}). Bean-validation
 *       failures on request DTOs are turned into {@code 400} by Spring itself
 *       ({@code spring.webflux.problemdetails.enabled=true}).
 *   <li>{@code 404}: {@link OrderNotFoundException}, {@link CustomerNotFoundException}.
 *   <li>{@code 409}: {@link EmailAlreadyRegisteredException} (duplicate e-mail) and
 *       {@link ConcurrentAggregateUpdateException} (optimistic-locking retries exhausted; the client may retry).
 *   <li>{@code 422}: {@link UnknownCustomerException} (order for an unknown customer) and
 *       {@link CreditLimitExceededException}.
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        return problem;
    }

    /**
     * Constraint violations on non-body parameters (e.g. the {@code X-Correlation-Id} pattern) are raised by the
     * {@code @Validated} method-validation proxy and would otherwise surface as 500.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        return problem;
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleNotFound(OrderNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ProblemDetail handleCustomerNotFound(CustomerNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }

    @ExceptionHandler({UnknownCustomerException.class, CreditLimitExceededException.class})
    public ProblemDetail handleUnprocessable(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Unprocessable Entity");
        return problem;
    }

    /** Optimistic-locking retries were exhausted; the client may simply retry. */
    @ExceptionHandler(ConcurrentAggregateUpdateException.class)
    public ProblemDetail handleConcurrentUpdate(ConcurrentAggregateUpdateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }
}
