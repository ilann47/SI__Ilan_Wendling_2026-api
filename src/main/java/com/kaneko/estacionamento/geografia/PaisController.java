package com.kaneko.estacionamento.geografia;

import com.kaneko.estacionamento.geografia.dto.PaisRequest;
import com.kaneko.estacionamento.geografia.dto.PaisResponse;
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
 * Endpoints REST do cadastro de Pais ({@code /api/paises}).
 */
@RestController
@RequestMapping("/api/paises")
public class PaisController {

    private final PaisService service;

    public PaisController(PaisService service) {
        this.service = service;
    }

    @GetMapping
    public Page<PaisResponse> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(nome, ativo, pageable);
    }

    @GetMapping("/{id}")
    public PaisResponse buscar(@PathVariable Long id) {
        return PaisResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaisResponse criar(@Valid @RequestBody PaisRequest req) {
        return PaisResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public PaisResponse atualizar(@PathVariable Long id, @Valid @RequestBody PaisRequest req) {
        return PaisResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
