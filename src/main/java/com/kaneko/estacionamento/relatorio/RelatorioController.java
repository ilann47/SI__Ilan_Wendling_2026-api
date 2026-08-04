package com.kaneko.estacionamento.relatorio;

import com.kaneko.estacionamento.relatorio.dto.ContasAVencerResponse;
import com.kaneko.estacionamento.relatorio.dto.EstoqueMinimoResponse;
import com.kaneko.estacionamento.relatorio.dto.FaturamentoDiaResponse;
import com.kaneko.estacionamento.relatorio.dto.MensalistasAtivosResponse;
import com.kaneko.estacionamento.relatorio.dto.PatioAtualResponse;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST dos relatorios gerenciais ({@code /api/relatorios}) — secoes
 * 4.26 a 4.30 dos requisitos funcionais.
 */
@RestController
@RequestMapping("/api/relatorios")
public class RelatorioController {

    private final RelatorioService service;

    public RelatorioController(RelatorioService service) {
        this.service = service;
    }

    /** 4.26 — Veiculos atualmente no patio. */
    @GetMapping("/patio")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:read', authentication)")
    public PatioAtualResponse patio(@AuthenticationPrincipal UsuarioAutenticado principal) {
        return service.patioAtual(org(principal));
    }

    /**
     * 4.27 — Faturamento de um dia ou periodo. {@code inicio} e obrigatorio;
     * {@code fim} omitido assume o mesmo dia.
     */
    @GetMapping("/faturamento")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:read', authentication)"
            + " and @rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)"
            + " and @rbacGuard.hasPermissionInActiveContext('finance:read', authentication)")
    public FaturamentoDiaResponse faturamento(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.faturamento(org(principal), inicio, fim != null ? fim : inicio);
    }

    /**
     * 4.28 — Contas a vencer no periodo. Sem parametros, assume de hoje aos
     * proximos 30 dias.
     */
    @GetMapping("/contas-a-vencer")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('finance:read', authentication)")
    public ContasAVencerResponse contasAVencer(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        LocalDate de = inicio != null ? inicio : LocalDate.now();
        LocalDate ate = fim != null ? fim : de.plusDays(30);
        return service.contasAVencer(org(principal), de, ate);
    }

    /** 4.29 — Produtos com estoque no minimo ou abaixo. */
    @GetMapping("/estoque-minimo")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('stock:read', authentication)")
    public EstoqueMinimoResponse estoqueMinimo(
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        return service.estoqueMinimo(org(principal));
    }

    /** 4.30 — Contratos de mensalista ativos. */
    @GetMapping("/mensalistas-ativos")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:read', authentication)")
    public MensalistasAtivosResponse mensalistasAtivos(
            @AuthenticationPrincipal UsuarioAutenticado principal) {
        return service.mensalistasAtivos(org(principal));
    }

    private static Long org(UsuarioAutenticado principal) {
        return principal == null ? null : principal.organizacaoId();
    }
}
