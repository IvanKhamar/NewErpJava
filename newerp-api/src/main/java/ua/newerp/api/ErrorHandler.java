package ua.newerp.api;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import ua.newerp.application.common.NotFoundException;
import ua.newerp.domain.common.DomainException;

/**
 * Turns exceptions into clear HTTP responses in the ProblemDetails format (RFC 9457).
 * Same mapping as ErrorHandler in the .NET version. The base class already answers Spring's own errors
 * (unknown route, wrong method, bad parameter) with ProblemDetails; anything else stays a 500.
 */
@RestControllerAdvice
class ErrorHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Не знайдено", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail domainRule(DomainException e) {
        return problem(HttpStatus.BAD_REQUEST, "Порушено бізнес-правило", e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail concurrency(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Дані щойно змінив хтось інший, повторіть дію", e.getMessage());
    }

    // The request body could not be read (broken JSON, wrong field type). A client error, not a server one.
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "Некоректний запит",
                "Не вдалося прочитати тіло запиту: перевірте, що це правильний JSON у кодуванні UTF-8."));
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
