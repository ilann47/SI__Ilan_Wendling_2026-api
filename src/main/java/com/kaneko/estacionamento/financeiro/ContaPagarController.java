package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.financeiro.dto.BaixaRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarResponse;
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
 * Endpoints REST dos titulos a pagar ({@code /api/contas-pagar}). O lancamento e
 * manual; titulos de compras sao gerados pela confirmacao da nota de entrada.
 */
@RestController
@RequestMapping("/api/contas-pagar")
public class ContaPagarController {

    private final ContaPagarService service;

    public ContaPagarController(ContaPagarService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ContaPagarResponse> listar(Pageable pageable) {
        return service.listar(pageable).map(ContaPagarResponse::from);
    }

    @GetMapping("/{id}")
    public ContaPagarResponse buscar(@PathVariable Long id) {
        return ContaPagarResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContaPagarResponse criar(@Valid @RequestBody ContaPagarRequest req) {
        return ContaPagarResponse.from(service.criar(req));
    }

    @PostMapping("/{id}/baixa")
    public ContaPagarResponse baixar(@PathVariable Long id, @Valid @RequestBody BaixaRequest req) {
        return ContaPagarResponse.from(service.baixar(id, req));
    }

    @PostMapping("/{id}/cancelamento")
    public ContaPagarResponse cancelar(@PathVariable Long id) {
        return ContaPagarResponse.from(service.cancelar(id));
    }
}
