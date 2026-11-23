package com.kaneko.estacionamento.auditoria;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
class ContextoAuditoriaProvider {

    ContextoAuditoria atual() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        UsuarioAutenticado principal = authentication != null
                && authentication.getPrincipal() instanceof UsuarioAutenticado p ? p : null;
        HttpServletRequest request = requisicaoAtual();
        String traceId = limitar(MDC.get(TraceIdFilter.MDC_KEY), 100);
        if (traceId == null) traceId = UUID.randomUUID().toString();
        return new ContextoAuditoria(
                principal == null ? TipoAtorAuditoria.SISTEMA : TipoAtorAuditoria.HUMANO,
                principal == null ? null : principal.userId(),
                request == null ? OrigemAuditoria.SISTEMA : OrigemAuditoria.API,
                request == null ? null : limitar(request.getHeader("X-Device-Id"), 120),
                request == null ? null : limitar(request.getRemoteAddr(), 45),
                traceId);
    }

    private static HttpServletRequest requisicaoAtual() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a
                ? a.getRequest() : null;
    }

    private static String limitar(String valor, int limite) {
        if (valor == null || valor.isBlank()) return null;
        String normalizado = valor.trim();
        return normalizado.length() <= limite ? normalizado : normalizado.substring(0, limite);
    }
}
