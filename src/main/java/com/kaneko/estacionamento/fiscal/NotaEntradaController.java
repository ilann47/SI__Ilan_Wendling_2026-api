package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.fiscal.dto.NotaEntradaRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST da nota fiscal de entrada ({@code /api/notas-entrada}),
 * incluindo a confirmacao (efetiva estoque + gera titulos) e o cancelamento.
 */
@RestController
@RequestMapping("/api/notas-entrada")
public class NotaEntradaController {

    private final NotaEntradaService service;

    public NotaEntradaController(NotaEntradaService service) {
        this.service = service;
    }

    @GetMapping
    public Page<NotaEntradaResponse> listar(Pageable pageable) {
        return service.listar(pageable).map(NotaEntradaResponse::from);
    }

    @GetMapping("/{id}")
    public NotaEntradaResponse buscar(@PathVariable Long id) {
        return NotaEntradaResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotaEntradaResponse criar(@Valid @RequestBody NotaEntradaRequest req) {
        return NotaEntradaResponse.from(service.criar(req));
    }

    @PostMapping("/{id}/confirmacao")
    public NotaEntradaResponse confirmar(@PathVariable Long id) {
        return NotaEntradaResponse.from(service.confirmar(id));
    }

    @PostMapping("/{id}/cancelamento")
    public NotaEntradaResponse cancelar(@PathVariable Long id) {
        return NotaEntradaResponse.from(service.cancelar(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
