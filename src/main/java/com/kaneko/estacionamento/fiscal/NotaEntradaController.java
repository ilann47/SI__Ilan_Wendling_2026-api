package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.fiscal.dto.NotaEntradaRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaDetalheResponse;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final NotaEntradaDetalheService detalheService;

    public NotaEntradaController(
            NotaEntradaService service,
            NotaEntradaDetalheService detalheService) {
        this.service = service;
        this.detalheService = detalheService;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    public Page<NotaEntradaResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal, Pageable pageable) {
        return service.listar(org(principal), pageable).map(NotaEntradaResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    public NotaEntradaResponse buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        return NotaEntradaResponse.from(service.buscar(org(principal), id));
    }

    @GetMapping("/{id}/detalhes")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    @Operation(summary = "Consultar detalhes integrados da nota de entrada",
            description = "Retorna documento, compra e recebimento vinculados, "
                    + "titulos a pagar e movimentos fisicos do estoque no tenant ativo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalhes encontrados"),
            @ApiResponse(responseCode = "401", description = "Token ausente ou invalido"),
            @ApiResponse(responseCode = "403", description = "Sem permissao fiscal de leitura"),
            @ApiResponse(responseCode = "404", description = "Nota inexistente ou de outro tenant")
    })
    public NotaEntradaDetalheResponse buscarDetalhes(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        return detalheService.buscar(org(principal), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public NotaEntradaResponse criar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody NotaEntradaRequest req) {
        return NotaEntradaResponse.from(service.criar(org(principal), req));
    }

    @PostMapping("/{id}/confirmacao")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public NotaEntradaResponse confirmar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return NotaEntradaResponse.from(service.confirmar(
                org(principal), principal == null ? null : principal.userId(), id));
    }

    @PostMapping("/{id}/cancelamento")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public NotaEntradaResponse cancelar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return NotaEntradaResponse.from(service.cancelar(org(principal), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public void excluir(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        service.excluir(org(principal), id);
    }

    private static Long org(UsuarioAutenticado principal) {
        return principal == null ? null : principal.organizacaoId();
    }
}
