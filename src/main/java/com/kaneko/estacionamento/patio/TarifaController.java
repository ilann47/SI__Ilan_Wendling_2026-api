package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.patio.dto.TarifaRequest;
import com.kaneko.estacionamento.patio.dto.TarifaResponse;
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
 * Endpoints REST do cadastro de Tarifa ({@code /api/tarifas}).
 */
@RestController
@RequestMapping("/api/tarifas")
public class TarifaController {

    private final TarifaService service;

    public TarifaController(TarifaService service) {
        this.service = service;
    }

    @GetMapping
    public Page<TarifaResponse> listar(
            @RequestParam(required = false) String descricao,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(descricao, ativo, pageable);
    }

    @GetMapping("/{id}")
    public TarifaResponse buscar(@PathVariable Long id) {
        return TarifaResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TarifaResponse criar(@Valid @RequestBody TarifaRequest req) {
        return TarifaResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public TarifaResponse atualizar(@PathVariable Long id, @Valid @RequestBody TarifaRequest req) {
        return TarifaResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
