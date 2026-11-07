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
 * Übersetzt Fehler aus Domain und Application in RFC-7807-Antworten {@code application/problem+json}, wie im
 * OpenAPI-Vertrag deklariert.
 *
 * <p>Zuordnungen:
 *
 * <ul>
 *   <li>{@code 400}: {@link IllegalArgumentException} (Validierung in Domain-Commands und Value Objects) und
 *       {@link ConstraintViolationException} (Parameter außerhalb des Bodys wie {@code X-Correlation-Id}).
 *       Bean-Validation-Fehler auf Request-DTOs macht Spring selbst zu {@code 400}
 *       ({@code spring.webflux.problemdetails.enabled=true}).
 *   <li>{@code 404}: {@link OrderNotFoundException}, {@link CustomerNotFoundException}.
 *   <li>{@code 409}: {@link EmailAlreadyRegisteredException} (doppelte E-Mail) und
 *       {@link ConcurrentAggregateUpdateException} (Optimistic-Locking-Retries erschöpft; der Client darf es erneut
 *       versuchen).
 *   <li>{@code 422}: {@link UnknownCustomerException} (Order für einen unbekannten Customer) und
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
     * Constraint-Verletzungen auf Parametern außerhalb des Bodys (z. B. das Pattern von {@code X-Correlation-Id}) wirft
     * der Method-Validation-Proxy von {@code @Validated}; ohne diesen Handler kämen sie als 500 an.
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

    /** Die Optimistic-Locking-Retries sind erschöpft; der Client kann es einfach erneut versuchen. */
    @ExceptionHandler(ConcurrentAggregateUpdateException.class)
    public ProblemDetail handleConcurrentUpdate(ConcurrentAggregateUpdateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }
}
