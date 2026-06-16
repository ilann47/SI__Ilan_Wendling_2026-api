package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.fiscal.dto.NotaSaidaRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaSaidaResponse;
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
 * Endpoints REST da nota fiscal de saida ({@code /api/notas-saida}), incluindo a
 * confirmacao (valida e baixa estoque + gera titulos) e o cancelamento.
 */
@RestController
@RequestMapping("/api/notas-saida")
public class NotaSaidaController {

    private final NotaSaidaService service;

    public NotaSaidaController(NotaSaidaService service) {
        this.service = service;
    }

    @GetMapping
    public Page<NotaSaidaResponse> listar(Pageable pageable) {
        return service.listar(pageable).map(NotaSaidaResponse::from);
    }

    @GetMapping("/{id}")
    public NotaSaidaResponse buscar(@PathVariable Long id) {
        return NotaSaidaResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotaSaidaResponse criar(@Valid @RequestBody NotaSaidaRequest req) {
        return NotaSaidaResponse.from(service.criar(req));
    }

    @PostMapping("/{id}/confirmacao")
    public NotaSaidaResponse confirmar(@PathVariable Long id) {
        return NotaSaidaResponse.from(service.confirmar(id));
    }

    @PostMapping("/{id}/cancelamento")
    public NotaSaidaResponse cancelar(@PathVariable Long id) {
        return NotaSaidaResponse.from(service.cancelar(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
