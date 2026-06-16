package com.kaneko.estacionamento.pagamento;

import com.kaneko.estacionamento.pagamento.dto.FormaPagamentoRequest;
import com.kaneko.estacionamento.pagamento.dto.FormaPagamentoResponse;
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
 * Endpoints REST do cadastro de Forma de Pagamento ({@code /api/formas-pagamento}).
 */
@RestController
@RequestMapping("/api/formas-pagamento")
public class FormaPagamentoController {

    private final FormaPagamentoService service;

    public FormaPagamentoController(FormaPagamentoService service) {
        this.service = service;
    }

    @GetMapping
    public Page<FormaPagamentoResponse> listar(
            @RequestParam(required = false) String nome,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(nome, ativo, pageable);
    }

    @GetMapping("/{id}")
    public FormaPagamentoResponse buscar(@PathVariable Long id) {
        return FormaPagamentoResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FormaPagamentoResponse criar(@Valid @RequestBody FormaPagamentoRequest req) {
        return FormaPagamentoResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public FormaPagamentoResponse atualizar(@PathVariable Long id, @Valid @RequestBody FormaPagamentoRequest req) {
        return FormaPagamentoResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
