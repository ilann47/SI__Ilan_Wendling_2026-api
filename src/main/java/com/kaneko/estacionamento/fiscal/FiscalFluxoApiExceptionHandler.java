package com.kaneko.estacionamento.fiscal;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.kaneko.estacionamento.fiscal")
public class FiscalFluxoApiExceptionHandler {
    @ExceptionHandler(com.kaneko.estacionamento.fiscal.importacao.VersaoImportacaoException.class)
    ProblemDetail versao(com.kaneko.estacionamento.fiscal.importacao.VersaoImportacaoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PRECONDITION_FAILED, ex.getMessage());
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail valor(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail cabecalho(MissingRequestHeaderException ex) {
        return ProblemDetail.forStatusAndDetail(
                "If-Match".equalsIgnoreCase(ex.getHeaderName())
                        ? HttpStatus.PRECONDITION_REQUIRED : HttpStatus.BAD_REQUEST,
                "Informe o cabeçalho obrigatório " + ex.getHeaderName() + ".");
    }
}
