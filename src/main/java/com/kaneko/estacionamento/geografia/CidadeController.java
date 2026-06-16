package com.kaneko.estacionamento.geografia;

import com.kaneko.estacionamento.geografia.dto.CidadeRequest;
import com.kaneko.estacionamento.geografia.dto.CidadeResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST do cadastro de Cidade ({@code /api/cidades}).
 */
@RestController
@RequestMapping("/api/cidades")
public class CidadeController {

    private final CidadeService service;

    public CidadeController(CidadeService service) {
        this.service = service;
    }

    @GetMapping
    public Page<CidadeResponse> listar(Pageable pageable) {
        return service.listar(pageable).map(CidadeResponse::from);
    }

    @GetMapping("/{id}")
    public CidadeResponse buscar(@PathVariable Long id) {
        return CidadeResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CidadeResponse criar(@Valid @RequestBody CidadeRequest req) {
        return CidadeResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public CidadeResponse atualizar(@PathVariable Long id, @Valid @RequestBody CidadeRequest req) {
        return CidadeResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
