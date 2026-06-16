package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.conveniencia.dto.ProdutoFornecedorRequest;
import com.kaneko.estacionamento.conveniencia.dto.ProdutoFornecedorResponse;
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
 * Endpoints REST do vinculo produto-fornecedor ({@code /api/produto-fornecedores}).
 */
@RestController
@RequestMapping("/api/produto-fornecedores")
public class ProdutoFornecedorController {

    private final ProdutoFornecedorService service;

    public ProdutoFornecedorController(ProdutoFornecedorService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ProdutoFornecedorResponse> listar(Pageable pageable) {
        return service.listar(pageable).map(ProdutoFornecedorResponse::from);
    }

    @GetMapping("/{id}")
    public ProdutoFornecedorResponse buscar(@PathVariable Long id) {
        return ProdutoFornecedorResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProdutoFornecedorResponse criar(@Valid @RequestBody ProdutoFornecedorRequest req) {
        return ProdutoFornecedorResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public ProdutoFornecedorResponse atualizar(@PathVariable Long id,
                                               @Valid @RequestBody ProdutoFornecedorRequest req) {
        return ProdutoFornecedorResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
