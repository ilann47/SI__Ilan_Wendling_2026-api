package com.kaneko.estacionamento.geografia;

import com.kaneko.estacionamento.geografia.dto.EstadoRequest;
import com.kaneko.estacionamento.geografia.dto.EstadoResponse;
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
 * Endpoints REST do cadastro de Estado ({@code /api/estados}).
 */
@RestController
@RequestMapping("/api/estados")
public class EstadoController {

    private final EstadoService service;

    public EstadoController(EstadoService service) {
        this.service = service;
    }

    @GetMapping
    public Page<EstadoResponse> listar(
            @RequestParam(required = false) Long paisId,
            @RequestParam(required = false) String uf,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(paisId, uf, ativo, pageable);
    }

    @GetMapping("/{id}")
    public EstadoResponse buscar(@PathVariable Long id) {
        return EstadoResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EstadoResponse criar(@Valid @RequestBody EstadoRequest req) {
        return EstadoResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public EstadoResponse atualizar(@PathVariable Long id, @Valid @RequestBody EstadoRequest req) {
        return EstadoResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
