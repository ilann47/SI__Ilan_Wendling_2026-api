package com.kaneko.estacionamento.ordensservico;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OrdemServicoController.class)
public class OrdemServicoApiExceptionHandler {
    @ExceptionHandler(MissingRequestHeaderException.class)
    ProblemDetail header(MissingRequestHeaderException ex) {
        if ("If-Match".equalsIgnoreCase(ex.getHeaderName())) {
            return problema(HttpStatus.PRECONDITION_REQUIRED,
                    "O cabecalho If-Match com a versao esperada e obrigatorio.");
        }
        return problema(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    @ExceptionHandler({VersaoOrdemServicoDivergenteException.class,
            OptimisticLockingFailureException.class})
    ProblemDetail versao(RuntimeException ex) {
        return problema(HttpStatus.PRECONDITION_FAILED,
                "A ordem foi alterada por outra operacao. Consulte-a novamente.");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail valor(IllegalArgumentException ex) {
        return problema(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
    private static ProblemDetail problema(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
