package com.kaneko.estacionamento.pagamento;

import com.kaneko.estacionamento.pagamento.dto.CondicaoPagamentoRequest;
import com.kaneko.estacionamento.pagamento.dto.CondicaoPagamentoResponse;
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
 * Endpoints REST da condicao de pagamento ({@code /api/condicoes-pagamento}).
 */
@RestController
@RequestMapping("/api/condicoes-pagamento")
public class CondicaoPagamentoController {

    private final CondicaoPagamentoService service;

    public CondicaoPagamentoController(CondicaoPagamentoService service) {
        this.service = service;
    }

    @GetMapping
    public Page<CondicaoPagamentoResponse> listar(Pageable pageable) {
        return service.listar(pageable).map(CondicaoPagamentoResponse::from);
    }

    @GetMapping("/{id}")
    public CondicaoPagamentoResponse buscar(@PathVariable Long id) {
        return CondicaoPagamentoResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CondicaoPagamentoResponse criar(@Valid @RequestBody CondicaoPagamentoRequest req) {
        return CondicaoPagamentoResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public CondicaoPagamentoResponse atualizar(@PathVariable Long id,
                                               @Valid @RequestBody CondicaoPagamentoRequest req) {
        return CondicaoPagamentoResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
