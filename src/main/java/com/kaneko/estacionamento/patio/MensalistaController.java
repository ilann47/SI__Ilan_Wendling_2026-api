package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.financeiro.dto.ContaReceberResponse;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.patio.dto.MensalistaRequest;
import com.kaneko.estacionamento.patio.dto.MensalistaResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:read', authentication)")
    public Page<MensalistaResponse> listar(@AuthenticationPrincipal UsuarioAutenticado principal,
            Pageable pageable) {
        return service.listar(org(principal), pageable).map(MensalistaResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:read', authentication)")
    public MensalistaResponse buscar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        return MensalistaResponse.from(service.buscar(org(principal), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:manage', authentication)")
    public MensalistaResponse criar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @Valid @RequestBody MensalistaRequest req) {
        return MensalistaResponse.from(service.criar(org(principal), req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:manage', authentication)")
    public MensalistaResponse atualizar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id, @Valid @RequestBody MensalistaRequest req) {
        return MensalistaResponse.from(service.atualizar(org(principal), id, req));
    }

    /** Gera a mensalidade (conta a receber) do contrato. */
    @PostMapping("/{id}/mensalidade")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:manage', authentication)"
            + " and @rbacGuard.hasPermissionInActiveContext('finance:manage', authentication)")
    public ContaReceberResponse gerarMensalidade(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimento) {
        return ContaReceberResponse.from(service.gerarMensalidade(org(principal), id, vencimento));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:manage', authentication)")
    public void excluir(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id) {
        service.excluir(org(principal), id);
    }

    private static Long org(UsuarioAutenticado principal) {
        return principal == null ? null : principal.organizacaoId();
    }
}
