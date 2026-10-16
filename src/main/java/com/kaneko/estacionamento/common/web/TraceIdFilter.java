package com.kaneko.estacionamento.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Atribui um identificador de correlacao (trace id) a cada requisicao: reaproveita
 * o {@code X-Trace-Id} recebido ou gera um novo, publica no MDC (para aparecer em
 * todos os logs da requisicao) e devolve no cabecalho da resposta. Roda antes de
 * tudo ({@link Ordered#HIGHEST_PRECEDENCE}) para cobrir inclusive respostas de erro.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";
    private static final Pattern TRACE_ID_VALIDO =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(HEADER);
        if (traceId == null || !TRACE_ID_VALIDO.matcher(traceId).matches()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
