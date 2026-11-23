package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.patio.dto.PatioRequest;
import com.kaneko.estacionamento.patio.dto.PatioResponse;
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
 * Endpoints REST do cadastro de Patio ({@code /api/patios}). A listagem ja traz
 * a ocupacao atual (vagas ocupadas) de cada patio.
 */
@RestController
@RequestMapping("/api/patios")
public class PatioController {

    private final PatioService service;

    public PatioController(PatioService service) {
        this.service = service;
    }

    @GetMapping
    public Page<PatioResponse> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(nome, ativo, pageable);
    }

    @GetMapping("/{id}")
    public PatioResponse buscar(@PathVariable Long id) {
        return service.buscarResponse(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatioResponse criar(@Valid @RequestBody PatioRequest req) {
        return service.criar(req);
    }

    @PutMapping("/{id}")
    public PatioResponse atualizar(@PathVariable Long id, @Valid @RequestBody PatioRequest req) {
        return service.atualizar(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
