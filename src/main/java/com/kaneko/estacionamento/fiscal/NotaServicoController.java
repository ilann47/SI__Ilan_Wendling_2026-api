package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.fiscal.dto.NotaServicoRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaServicoResponse;
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
 * Endpoints REST da nota de servico ({@code /api/notas-servico}), incluindo a
 * emissao e o cancelamento.
 */
@RestController
@RequestMapping("/api/notas-servico")
public class NotaServicoController {

    private final NotaServicoService service;

    public NotaServicoController(NotaServicoService service) {
        this.service = service;
    }

    @GetMapping
    public Page<NotaServicoResponse> listar(Pageable pageable) {
        return service.listar(pageable).map(NotaServicoResponse::from);
    }

    @GetMapping("/{id}")
    public NotaServicoResponse buscar(@PathVariable Long id) {
        return NotaServicoResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotaServicoResponse criar(@Valid @RequestBody NotaServicoRequest req) {
        return NotaServicoResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public NotaServicoResponse atualizar(@PathVariable Long id, @Valid @RequestBody NotaServicoRequest req) {
        return NotaServicoResponse.from(service.atualizar(id, req));
    }

    @PostMapping("/{id}/emissao")
    public NotaServicoResponse emitir(@PathVariable Long id) {
        return NotaServicoResponse.from(service.emitir(id));
    }

    @PostMapping("/{id}/cancelamento")
    public NotaServicoResponse cancelar(@PathVariable Long id) {
        return NotaServicoResponse.from(service.cancelar(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
