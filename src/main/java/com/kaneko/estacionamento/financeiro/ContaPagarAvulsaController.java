package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.financeiro.dto.BaixaRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarAvulsaRequest;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarAvulsaResponse;
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
 * Endpoints REST da despesa avulsa a pagar ({@code /api/contas-pagar-avulsas}),
 * incluindo a baixa e o cancelamento.
 */
@RestController
@RequestMapping("/api/contas-pagar-avulsas")
public class ContaPagarAvulsaController {

    private final ContaPagarAvulsaService service;

    public ContaPagarAvulsaController(ContaPagarAvulsaService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ContaPagarAvulsaResponse> listar(Pageable pageable) {
        return service.listar(pageable).map(ContaPagarAvulsaResponse::from);
    }

    @GetMapping("/{id}")
    public ContaPagarAvulsaResponse buscar(@PathVariable Long id) {
        return ContaPagarAvulsaResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContaPagarAvulsaResponse criar(@Valid @RequestBody ContaPagarAvulsaRequest req) {
        return ContaPagarAvulsaResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public ContaPagarAvulsaResponse atualizar(@PathVariable Long id,
                                              @Valid @RequestBody ContaPagarAvulsaRequest req) {
        return ContaPagarAvulsaResponse.from(service.atualizar(id, req));
    }

    @PostMapping("/{id}/baixa")
    public ContaPagarAvulsaResponse baixar(@PathVariable Long id, @Valid @RequestBody BaixaRequest req) {
        return ContaPagarAvulsaResponse.from(service.baixar(id, req));
    }

    @PostMapping("/{id}/cancelamento")
    public ContaPagarAvulsaResponse cancelar(@PathVariable Long id) {
        return ContaPagarAvulsaResponse.from(service.cancelar(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
