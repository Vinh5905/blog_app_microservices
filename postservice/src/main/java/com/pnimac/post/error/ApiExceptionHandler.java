package com.pnimac.post.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> handleStatus(ResponseStatusException exception, ServletWebRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String code = status == HttpStatus.NOT_FOUND ? "RESOURCE_NOT_FOUND" : "NOT_OWNER";
        return ResponseEntity.status(status).body(ProblemSupport.create(status, code, exception.getReason(),
                request.getRequest().getRequestURI()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fieldErrors
                .computeIfAbsent(error.getField(), ignored -> new java.util.ArrayList<>())
                .add(error.getDefaultMessage()));
        String uri = ((ServletWebRequest) request).getRequest().getRequestURI();
        ProblemDetail problem = ProblemSupport.create(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request validation failed", uri);
        problem.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String uri = ((ServletWebRequest) request).getRequest().getRequestURI();
        return ResponseEntity.badRequest().body(ProblemSupport.create(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is malformed", uri));
    }
}
