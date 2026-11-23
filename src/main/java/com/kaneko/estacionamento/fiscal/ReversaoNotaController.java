package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaSaidaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication) "
        + "and @rbacGuard.hasPermissionInActiveContext('stock:manage', authentication) "
        + "and @rbacGuard.hasPermissionInActiveContext('finance:manage', authentication)")
public class ReversaoNotaController {
    private final ReversaoNotaService service;

    public ReversaoNotaController(ReversaoNotaService service) {
        this.service = service;
    }

    @PostMapping("/api/notas-entrada/{id}/reversao")
    @Operation(summary = "Reverter efeitos internos de uma entrada confirmada",
            description = "Exige tenant ativo e versão revisada. Compensa o estoque, restaura custo médio, "
                    + "cancela títulos sem baixa e preserva os lançamentos originais. Não transmite ao fisco.")
    @ApiResponse(responseCode = "409", description = "Nota não reversível, título baixado ou estoque divergente")
    @ApiResponse(responseCode = "412", description = "Versão da nota mudou")
    @ApiResponse(responseCode = "428", description = "If-Match ausente")
    public ResponseEntity<NotaEntradaResponse> entrada(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody MotivoReversao request) {
        var nota = service.entrada(org(principal), ator(principal), id,
                NotaEntradaHttpVersion.extrair(ifMatch), request.motivo());
        return ResponseEntity.ok().eTag("\"" + nota.version() + "\"").body(nota);
    }

    @PostMapping("/api/notas-saida/{id}/reversao")
    @Operation(summary = "Reverter efeitos internos de uma saída avulsa confirmada",
            description = "Preserva a nota de saída e o histórico financeiro. "
                    + "Para saída avulsa, compensa estoque e cancela títulos sem baixa. Não transmite ao fisco.")
    @ApiResponse(responseCode = "409", description = "Nota não reversível, título baixado ou estoque divergente")
    @ApiResponse(responseCode = "412", description = "Versão da nota mudou")
    @ApiResponse(responseCode = "428", description = "If-Match ausente")
    public ResponseEntity<NotaSaidaResponse> saida(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody MotivoReversao request) {
        var nota = service.saida(org(principal), ator(principal), id,
                NotaEntradaHttpVersion.extrair(ifMatch), request.motivo());
        return ResponseEntity.ok().eTag("\"" + nota.version() + "\"").body(nota);
    }

    private static Long org(UsuarioAutenticado principal) {
        return principal == null ? null : principal.organizacaoId();
    }

    private static Long ator(UsuarioAutenticado principal) {
        return principal == null ? null : principal.userId();
    }

    public record MotivoReversao(@NotBlank @Size(max = 500) String motivo) {}
}
