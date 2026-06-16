package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.conveniencia.dto.UnidadeMedidaRequest;
import com.kaneko.estacionamento.conveniencia.dto.UnidadeMedidaResponse;
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
 * Endpoints REST do cadastro de UnidadeMedida ({@code /api/unidades-medida}).
 */
@RestController
@RequestMapping("/api/unidades-medida")
public class UnidadeMedidaController {

    private final UnidadeMedidaService service;

    public UnidadeMedidaController(UnidadeMedidaService service) {
        this.service = service;
    }

    @GetMapping
    public Page<UnidadeMedidaResponse> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(nome, ativo, pageable);
    }

    @GetMapping("/{id}")
    public UnidadeMedidaResponse buscar(@PathVariable Long id) {
        return UnidadeMedidaResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnidadeMedidaResponse criar(@Valid @RequestBody UnidadeMedidaRequest req) {
        return UnidadeMedidaResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public UnidadeMedidaResponse atualizar(@PathVariable Long id, @Valid @RequestBody UnidadeMedidaRequest req) {
        return UnidadeMedidaResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
