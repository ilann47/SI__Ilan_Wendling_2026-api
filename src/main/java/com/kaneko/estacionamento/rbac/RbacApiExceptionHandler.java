package com.kaneko.estacionamento.rbac;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.*;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = RbacController.class)
public class RbacApiExceptionHandler {
    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail missing(MissingRequestHeaderException ex) {
        HttpStatus status = "If-Match".equalsIgnoreCase(ex.getHeaderName())
                ? HttpStatus.PRECONDITION_REQUIRED : HttpStatus.BAD_REQUEST;
        return ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    }
    @ExceptionHandler(VersaoAtribuicaoDivergenteException.class)
    ProblemDetail version(VersaoAtribuicaoDivergenteException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED, ex.getMessage());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail conflict(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }
}
