package com.kaneko.estacionamento.rh;

import com.kaneko.estacionamento.rh.dto.CargoRequest;
import com.kaneko.estacionamento.rh.dto.CargoResponse;
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
 * Endpoints REST do cadastro de Cargo ({@code /api/cargos}).
 */
@RestController
@RequestMapping("/api/cargos")
public class CargoController {

    private final CargoService service;

    public CargoController(CargoService service) {
        this.service = service;
    }

    @GetMapping
    public Page<CargoResponse> listar(Pageable pageable) {
        return service.listar(pageable).map(CargoResponse::from);
    }

    @GetMapping("/{id}")
    public CargoResponse buscar(@PathVariable Long id) {
        return CargoResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CargoResponse criar(@Valid @RequestBody CargoRequest req) {
        return CargoResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public CargoResponse atualizar(@PathVariable Long id, @Valid @RequestBody CargoRequest req) {
        return CargoResponse.from(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
