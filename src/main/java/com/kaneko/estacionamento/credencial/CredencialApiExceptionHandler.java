package com.kaneko.estacionamento.credencial;

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
@RestControllerAdvice(assignableTypes = CredencialAcessoController.class)
public class CredencialApiExceptionHandler {
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleCabecalhoObrigatorio(MissingRequestHeaderException ex) {
        if ("If-Match".equalsIgnoreCase(ex.getHeaderName())) {
            return problem(HttpStatus.PRECONDITION_REQUIRED,
                    "Pre-condicao obrigatoria",
                    "O cabecalho If-Match com a versao esperada e obrigatorio.");
        }
        return problem(HttpStatus.BAD_REQUEST, "Requisicao invalida",
                "O cabecalho " + ex.getHeaderName() + " e obrigatorio.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleValorInvalido(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Requisicao invalida", ex.getMessage());
    }

    @ExceptionHandler(VersaoCredencialDivergenteException.class)
    public ProblemDetail handleVersaoDivergente(
            VersaoCredencialDivergenteException ex) {
        return problem(HttpStatus.PRECONDITION_FAILED, "Versao desatualizada",
                "A credencial foi alterada por outra operacao. Consulte-a novamente.");
    }

    @ExceptionHandler({IllegalStateException.class,
            OptimisticLockingFailureException.class})
    public ProblemDetail handleEstadoInvalido(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "Estado invalido", ex.getMessage());
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
