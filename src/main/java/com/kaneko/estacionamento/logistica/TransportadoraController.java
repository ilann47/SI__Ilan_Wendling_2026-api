package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.logistica.dto.TransportadoraRequest;
import com.kaneko.estacionamento.logistica.dto.TransportadoraResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST do cadastro de Transportadora ({@code /api/transportadoras}).
 */
@RestController
@RequestMapping("/api/transportadoras")
public class TransportadoraController {

    private final TransportadoraService service;

    public TransportadoraController(TransportadoraService service) {
        this.service = service;
    }

    @GetMapping
    public Page<TransportadoraResponse> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) String documento,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(nome, documento, ativo, pageable);
    }

    @GetMapping("/{id}")
    public TransportadoraResponse buscar(@PathVariable Long id) {
        return TransportadoraResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransportadoraResponse criar(@Valid @RequestBody TransportadoraRequest req) {
        return TransportadoraResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public TransportadoraResponse atualizar(@PathVariable Long id, @Valid @RequestBody TransportadoraRequest req) {
        return TransportadoraResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
