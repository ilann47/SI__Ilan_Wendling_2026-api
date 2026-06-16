package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.financeiro.dto.BaixaRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaReceberRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaReceberResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST dos titulos a receber ({@code /api/contas-receber}). O
 * lancamento e manual; titulos de vendas sao gerados pela confirmacao da nota
 * de saida.
 */
@RestController
@RequestMapping("/api/contas-receber")
public class ContaReceberController {

    private final ContaReceberService service;

    public ContaReceberController(ContaReceberService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ContaReceberResponse> listar(Pageable pageable) {
        return service.listar(pageable).map(ContaReceberResponse::from);
    }

    @GetMapping("/{id}")
    public ContaReceberResponse buscar(@PathVariable Long id) {
        return ContaReceberResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContaReceberResponse criar(@Valid @RequestBody ContaReceberRequest req) {
        return ContaReceberResponse.from(service.criar(req));
    }

    @PostMapping("/{id}/baixa")
    public ContaReceberResponse baixar(@PathVariable Long id, @Valid @RequestBody BaixaRequest req) {
        return ContaReceberResponse.from(service.baixar(id, req));
    }

    @PostMapping("/{id}/cancelamento")
    public ContaReceberResponse cancelar(@PathVariable Long id) {
        return ContaReceberResponse.from(service.cancelar(id));
    }
}
