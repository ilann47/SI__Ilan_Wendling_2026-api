package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.financeiro.dto.ContaReceberResponse;
import com.kaneko.estacionamento.patio.dto.MensalistaRequest;
import com.kaneko.estacionamento.patio.dto.MensalistaResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Endpoints REST do contrato de Mensalista ({@code /api/mensalistas}).
 */
@RestController
@RequestMapping("/api/mensalistas")
public class MensalistaController {

    private final MensalistaService service;

    public MensalistaController(MensalistaService service) {
        this.service = service;
    }

    @GetMapping
    public Page<MensalistaResponse> listar(Pageable pageable) {
        return service.listar(pageable).map(MensalistaResponse::from);
    }

    @GetMapping("/{id}")
    public MensalistaResponse buscar(@PathVariable Long id) {
        return MensalistaResponse.from(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MensalistaResponse criar(@Valid @RequestBody MensalistaRequest req) {
        return MensalistaResponse.from(service.criar(req));
    }

    @PutMapping("/{id}")
    public MensalistaResponse atualizar(@PathVariable Long id, @Valid @RequestBody MensalistaRequest req) {
        return MensalistaResponse.from(service.atualizar(id, req));
    }

    /** Gera a mensalidade (conta a receber) do contrato. */
    @PostMapping("/{id}/mensalidade")
    @ResponseStatus(HttpStatus.CREATED)
    public ContaReceberResponse gerarMensalidade(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimento) {
        return ContaReceberResponse.from(service.gerarMensalidade(id, vencimento));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluir(id);
    }
}
