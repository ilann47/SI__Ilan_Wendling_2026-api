package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.patio.dto.ColaboradorRequest;
import com.kaneko.estacionamento.patio.dto.ColaboradorResponse;
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
 * Endpoints REST da equipe de colaboradores ({@code /api/colaboradores}).
 * Filtra por patio via {@code ?patioId=}. Um novo colaborador nasce CONVIDADO.
 */
@RestController
@RequestMapping("/api/colaboradores")
public class ColaboradorController {

    private final ColaboradorService service;

    public ColaboradorController(ColaboradorService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ColaboradorResponse> listar(
            @RequestParam(required = false) Long patioId,
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(patioId, nome, ativo, pageable);
    }

    @GetMapping("/{id}")
    public ColaboradorResponse buscar(@PathVariable Long id) {
        return ColaboradorResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ColaboradorResponse criar(@Valid @RequestBody ColaboradorRequest req) {
        return ColaboradorResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public ColaboradorResponse atualizar(@PathVariable Long id, @Valid @RequestBody ColaboradorRequest req) {
        return ColaboradorResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
