package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.conveniencia.dto.ServicoRequest;
import com.kaneko.estacionamento.conveniencia.dto.ServicoResponse;
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
 * Endpoints REST do cadastro de Servico ({@code /api/servicos}).
 */
@RestController
@RequestMapping("/api/servicos")
public class ServicoController {

    private final ServicoService service;

    public ServicoController(ServicoService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ServicoResponse> listar(Pageable pageable) {
        return service.listar(pageable).map(ServicoResponse::from);
    }

    @GetMapping("/{id}")
    public ServicoResponse buscar(@PathVariable Long id) {
        return ServicoResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServicoResponse criar(@Valid @RequestBody ServicoRequest req) {
        return ServicoResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public ServicoResponse atualizar(@PathVariable Long id, @Valid @RequestBody ServicoRequest req) {
        return ServicoResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
