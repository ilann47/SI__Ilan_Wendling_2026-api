package com.kaneko.estacionamento.acesso;

import com.kaneko.estacionamento.common.web.TraceIdFilter;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {
        AcessoController.class,
        CheckoutController.class,
        TentativaAcessoController.class
})
public class AcessoApiExceptionHandler {
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleCabecalhoObrigatorio(
            MissingRequestHeaderException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Requisicao invalida",
                "O cabecalho " + ex.getHeaderName() + " e obrigatorio.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleValorInvalido(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Requisicao invalida", ex.getMessage());
    }

    private static ProblemDetail problem(
            HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        if (traceId != null) pd.setProperty("traceId", traceId);
        return pd;
    }
}
