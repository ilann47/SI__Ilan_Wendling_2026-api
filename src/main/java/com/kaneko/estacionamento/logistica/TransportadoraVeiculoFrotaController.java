package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.logistica.dto.TransportadoraVeiculoFrotaRequest;
import com.kaneko.estacionamento.logistica.dto.TransportadoraVeiculoFrotaResponse;
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
 * Endpoints REST do vinculo transportadora-veiculo/frota
 * ({@code /api/transportadora-veiculos}).
 */
@RestController
@RequestMapping("/api/transportadora-veiculos")
public class TransportadoraVeiculoFrotaController {

    private final TransportadoraVeiculoFrotaService service;

    public TransportadoraVeiculoFrotaController(TransportadoraVeiculoFrotaService service) {
        this.service = service;
    }

    @GetMapping
    public Page<TransportadoraVeiculoFrotaResponse> listar(Pageable pageable) {
        return service.listar(pageable).map(TransportadoraVeiculoFrotaResponse::from);
    }

    @GetMapping("/{id}")
    public TransportadoraVeiculoFrotaResponse buscar(@PathVariable Long id) {
        return TransportadoraVeiculoFrotaResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransportadoraVeiculoFrotaResponse criar(@Valid @RequestBody TransportadoraVeiculoFrotaRequest req) {
        return TransportadoraVeiculoFrotaResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public TransportadoraVeiculoFrotaResponse atualizar(@PathVariable Long id,
                                                        @Valid @RequestBody TransportadoraVeiculoFrotaRequest req) {
        return TransportadoraVeiculoFrotaResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
