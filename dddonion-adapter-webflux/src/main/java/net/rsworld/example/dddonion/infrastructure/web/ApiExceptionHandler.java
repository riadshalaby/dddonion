package net.rsworld.example.dddonion.infrastructure.web;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain/application validation failures into RFC 7807 {@code application/problem+json} responses, as
 * declared in the OpenAPI contract.
 *
 * <p>Bean-validation failures on the request DTO (e.g. blank e-mail, negative total) are already turned into {@code 400
 * problem+json} by Spring when {@code spring.webflux.problemdetails.enabled=true}. This handler covers the stricter
 * validation performed inside the domain {@code PlaceOrderCommand}, which surfaces as an
 * {@link IllegalArgumentException} on the reactive chain.
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
}
