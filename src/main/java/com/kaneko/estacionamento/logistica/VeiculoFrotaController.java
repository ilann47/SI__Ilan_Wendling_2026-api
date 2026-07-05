package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.logistica.dto.VeiculoFrotaRequest;
import com.kaneko.estacionamento.logistica.dto.VeiculoFrotaResponse;
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
 * Endpoints REST do cadastro de Veiculo de Frota ({@code /api/veiculos-frota}).
 */
@RestController
@RequestMapping("/api/veiculos-frota")
public class VeiculoFrotaController {

    private final VeiculoFrotaService service;

    public VeiculoFrotaController(VeiculoFrotaService service) {
        this.service = service;
    }

    @GetMapping
    public Page<VeiculoFrotaResponse> listar(
            @RequestParam(required = false) String placa,
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(placa, marca, ativo, pageable);
    }

    @GetMapping("/{id}")
    public VeiculoFrotaResponse buscar(@PathVariable Long id) {
        return VeiculoFrotaResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VeiculoFrotaResponse criar(@Valid @RequestBody VeiculoFrotaRequest req) {
        return VeiculoFrotaResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public VeiculoFrotaResponse atualizar(@PathVariable Long id, @Valid @RequestBody VeiculoFrotaRequest req) {
        return VeiculoFrotaResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
