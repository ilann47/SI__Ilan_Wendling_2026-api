package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patio")
@PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:checkout', authentication)"
        + " or @rbacGuard.hasPermissionInActiveContext('operations:manage', authentication)")
public class PatioCaixaController {
    private final PatioCaixaService service;

    public PatioCaixaController(PatioCaixaService service) {
        this.service = service;
    }

    public record Abertura(@NotNull @PositiveOrZero @Digits(integer = 12, fraction = 6) BigDecimal fundoTroco) {}
    public record Fechamento(@NotNull @PositiveOrZero @Digits(integer = 12, fraction = 6) BigDecimal dinheiroContado) {}
    public record EntradaPlaca(
            @NotBlank(message = "Informe a placa ou o identificador do veículo.")
            @Size(max = PatioPlaca.MAXIMO, message = "Informe até 10 caracteres na placa.") String placa,
            @Positive Long patioId, @Positive Long eventoId, UUID chaveEntrada, PatioCaixaService.Meio meio,
            @Positive @Digits(integer = 8, fraction = 6) BigDecimal valorEsperado,
            @PositiveOrZero @Digits(integer=12,fraction=6) BigDecimal recebido,
            @Valid PatioTomador tomador) {}
    public record Pagamento(@NotNull @Positive Long turnoId, @NotNull UUID chave,
                            @NotNull PatioCaixaService.Meio meio,
                            @PositiveOrZero @Digits(integer=12,fraction=6) BigDecimal recebido,
                            @Valid PatioTomador tomador) {}

    @GetMapping("/caixa")
    public PatioCaixaService.Caixa caixa(@AuthenticationPrincipal UsuarioAutenticado ator) {
        return service.caixa(ator);
    }

    @PostMapping("/turnos")
    @ResponseStatus(HttpStatus.CREATED)
    public PatioCaixaService.Resumo abrir(@AuthenticationPrincipal UsuarioAutenticado ator,
                                        @Valid @RequestBody Abertura req) {
        return service.abrir(ator, req.fundoTroco());
    }

    @PostMapping("/turnos/{id}/fechamento")
    public PatioCaixaService.Resumo fechar(@AuthenticationPrincipal UsuarioAutenticado ator,
                                         @PathVariable long id, @Valid @RequestBody Fechamento req) {
        return service.fechar(ator, id, req.dinheiroContado());
    }

    @PostMapping("/entradas")
    @ResponseStatus(HttpStatus.CREATED)
    public PatioCaixaService.Entrada entrada(@AuthenticationPrincipal UsuarioAutenticado ator,
                                           @Valid @RequestBody EntradaPlaca req) {
        return service.entrada(ator, req.placa(), req.patioId(), req.eventoId(), req.chaveEntrada(),
                req.meio(), req.valorEsperado(), req.recebido(), req.tomador());
    }

    @GetMapping("/movimentacoes/{id}/fiscal-evento")
    public PatioEventoService.Detalhe fiscalEvento(@AuthenticationPrincipal UsuarioAutenticado ator,
                                                   @PathVariable long id) {
        return service.fiscalEvento(ator, id);
    }

    @PostMapping("/movimentacoes/{id}/previa-saida")
    public PatioCaixaService.Previa previa(@AuthenticationPrincipal UsuarioAutenticado ator, @PathVariable long id) {
        return service.previa(ator, id);
    }

    @PostMapping("/movimentacoes/{id}/saida")
    public PatioCaixaService.Comprovante saida(@AuthenticationPrincipal UsuarioAutenticado ator,
                                              @PathVariable long id, @Valid @RequestBody Pagamento req) {
        return service.saida(ator, id, req.turnoId(), req.chave(), req.meio(), req.recebido(), req.tomador());
    }
}
