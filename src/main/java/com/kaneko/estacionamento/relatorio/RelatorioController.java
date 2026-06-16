package com.kaneko.estacionamento.relatorio;

import com.kaneko.estacionamento.relatorio.dto.ContasAVencerResponse;
import com.kaneko.estacionamento.relatorio.dto.EstoqueMinimoResponse;
import com.kaneko.estacionamento.relatorio.dto.FaturamentoDiaResponse;
import com.kaneko.estacionamento.relatorio.dto.MensalistasAtivosResponse;
import com.kaneko.estacionamento.relatorio.dto.PatioAtualResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
    public PatioAtualResponse patio() {
        return service.patioAtual();
    }

    /**
     * 4.27 — Faturamento de um dia ou periodo. {@code inicio} e obrigatorio;
     * {@code fim} omitido assume o mesmo dia.
     */
    @GetMapping("/faturamento")
    public FaturamentoDiaResponse faturamento(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.faturamento(inicio, fim != null ? fim : inicio);
    }

    /**
     * 4.28 — Contas a vencer no periodo. Sem parametros, assume de hoje aos
     * proximos 30 dias.
     */
    @GetMapping("/contas-a-vencer")
    public ContasAVencerResponse contasAVencer(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        LocalDate de = inicio != null ? inicio : LocalDate.now();
        LocalDate ate = fim != null ? fim : de.plusDays(30);
        return service.contasAVencer(de, ate);
    }

    /** 4.29 — Produtos com estoque no minimo ou abaixo. */
    @GetMapping("/estoque-minimo")
    public EstoqueMinimoResponse estoqueMinimo() {
        return service.estoqueMinimo();
    }

    /** 4.30 — Contratos de mensalista ativos. */
    @GetMapping("/mensalistas-ativos")
    public MensalistasAtivosResponse mensalistasAtivos() {
        return service.mensalistasAtivos();
    }
}
