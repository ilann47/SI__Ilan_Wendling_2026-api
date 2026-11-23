package com.kaneko.estacionamento.fiscal;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes={NotaEntradaController.class, NotaSaidaController.class,
        NotaServicoController.class, ReversaoNotaController.class,
        com.kaneko.estacionamento.fiscal.importacao.ImportacaoNotaController.class})
public class NotaEntradaApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail versao(ResponseStatusException ex) {
        return problema(ex.getStatusCode(), ex.getReason());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail valor(IllegalArgumentException ex) {
        return problema(org.springframework.http.HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private static ProblemDetail problema(org.springframework.http.HttpStatusCode status, String detalhe) {
        var resposta = ProblemDetail.forStatusAndDetail(status, detalhe);
        String trace = org.slf4j.MDC.get(com.kaneko.estacionamento.common.web.TraceIdFilter.MDC_KEY);
        if (trace != null) resposta.setProperty("traceId", trace);
        return resposta;
    }
}
