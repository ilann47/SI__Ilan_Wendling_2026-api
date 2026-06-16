package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.patio.dto.VeiculoRequest;
import com.kaneko.estacionamento.patio.dto.VeiculoResponse;
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
 * Endpoints REST do cadastro de Veiculo ({@code /api/veiculos}).
 */
@RestController
@RequestMapping("/api/veiculos")
public class VeiculoController {

    private final VeiculoService service;

    public VeiculoController(VeiculoService service) {
        this.service = service;
    }

    @GetMapping
    public Page<VeiculoResponse> listar(
            @RequestParam(required = false) String placa,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Boolean ativo,
            Pageable pageable) {
        return service.listar(placa, clienteId, ativo, pageable);
    }

    @GetMapping("/{id}")
    public VeiculoResponse buscar(@PathVariable Long id) {
        return VeiculoResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VeiculoResponse criar(@Valid @RequestBody VeiculoRequest req) {
        return VeiculoResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public VeiculoResponse atualizar(@PathVariable Long id, @Valid @RequestBody VeiculoRequest req) {
        return VeiculoResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
