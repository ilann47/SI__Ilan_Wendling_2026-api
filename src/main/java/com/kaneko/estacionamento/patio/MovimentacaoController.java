package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.patio.dto.EntradaRequest;
import com.kaneko.estacionamento.patio.dto.MovimentacaoResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST da operacao de patio ({@code /api/movimentacoes}).
 */
@RestController
@RequestMapping("/api/movimentacoes")
public class MovimentacaoController {

    private final MovimentacaoService service;

    public MovimentacaoController(MovimentacaoService service) {
        this.service = service;
    }

    @GetMapping
    public Page<MovimentacaoResponse> listar(Pageable pageable) {
        return service.listar(pageable).map(MovimentacaoResponse::from);
    }

    /** Pátio atual: veiculos ainda no estacionamento (movimentacoes abertas). */
    @GetMapping("/patio")
    public Page<MovimentacaoResponse> patio(Pageable pageable) {
        return service.listarPatio(pageable).map(MovimentacaoResponse::from);
    }

    @GetMapping("/{id}")
    public MovimentacaoResponse buscar(@PathVariable Long id) {
        return MovimentacaoResponse.from(service.buscar(id));
    }

    @PostMapping("/entrada")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimentacaoResponse entrada(@Valid @RequestBody EntradaRequest req) {
        return MovimentacaoResponse.from(service.registrarEntrada(req));
    }

    @PostMapping("/{id}/saida")
    public MovimentacaoResponse saida(@PathVariable Long id) {
        return MovimentacaoResponse.from(service.registrarSaida(id));
    }

    /** Cancela uma movimentacao ABERTA (entrada por engano) — RF 4.18.3. */
    @PostMapping("/{id}/cancelamento")
    public MovimentacaoResponse cancelar(@PathVariable Long id) {
        return MovimentacaoResponse.from(service.cancelar(id));
    }
}
