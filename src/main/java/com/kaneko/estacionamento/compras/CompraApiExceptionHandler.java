package com.kaneko.estacionamento.compras;

import com.kaneko.estacionamento.common.web.TraceIdFilter;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OrdemCompraController.class)
public class CompraApiExceptionHandler {

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleCabecalho(MissingRequestHeaderException ex) {
        if ("If-Match".equalsIgnoreCase(ex.getHeaderName())) {
            return problem(HttpStatus.PRECONDITION_REQUIRED, "Pre-condicao obrigatoria",
                    "O cabecalho If-Match com a versao esperada e obrigatorio.");
        }
        return problem(HttpStatus.BAD_REQUEST, "Requisicao invalida", ex.getMessage());
    }

    @ExceptionHandler({VersaoOrdemCompraDivergenteException.class,
            OptimisticLockingFailureException.class})
    public ProblemDetail handleVersao(RuntimeException ex) {
        return problem(HttpStatus.PRECONDITION_FAILED, "Versao desatualizada",
                "A ordem foi alterada por outra operacao. Consulte-a novamente.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleValor(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Requisicao invalida", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }
}
