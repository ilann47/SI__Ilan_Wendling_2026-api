package com.kaneko.estacionamento.estoque;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.estoque.dto.MovimentoEstoqueResponse;
import com.kaneko.estacionamento.estoque.dto.PosicaoEstoqueResponse;
import com.kaneko.estacionamento.estoque.dto.SaldoEstoqueResponse;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EstoqueController {
    private final EstoqueService service;

    public EstoqueController(EstoqueService service) {
        this.service = service;
    }

    @GetMapping("/stock-balances")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('stock:read', authentication)")
    public Page<SaldoEstoqueResponse> listarSaldos(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) Long produtoId,
            @RequestParam(required = false) Long localEstoqueId, Pageable pageable) {
        return service.listarSaldos(
                principal.organizacaoId(), produtoId, localEstoqueId, pageable);
    }

    @GetMapping("/stock-positions")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('stock:read', authentication)")
    public Page<PosicaoEstoqueResponse> listarPosicoes(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) Long produtoId,
            @RequestParam(required = false) Boolean abaixoMinimo, Pageable pageable) {
        return service.listarPosicoes(
                principal.organizacaoId(), produtoId, abaixoMinimo, pageable);
    }

    @GetMapping("/stock-movements")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('stock:read', authentication)")
    public Page<MovimentoEstoqueResponse> listarMovimentos(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(required = false) Long produtoId,
            @RequestParam(required = false) Long localEstoqueId,
            @RequestParam(required = false) TipoMovimentoEstoque tipo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime de,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime ate,
            Pageable pageable) {
        return service.listarMovimentos(
                principal.organizacaoId(), produtoId, localEstoqueId, tipo, de, ate, pageable);
    }

    @GetMapping("/stock-movements/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('stock:read', authentication)")
    public MovimentoEstoqueResponse buscarMovimento(
            @AuthenticationPrincipal UsuarioAutenticado principal, @PathVariable Long id) {
        return service.buscarMovimento(principal.organizacaoId(), id);
    }

}
