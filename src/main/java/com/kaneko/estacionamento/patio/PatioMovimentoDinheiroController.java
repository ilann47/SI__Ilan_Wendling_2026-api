package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.rbac.RbacGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patio/turnos")
public class PatioMovimentoDinheiroController {
    private final PatioMovimentoDinheiroService service;
    private final RbacGuard guard;
    PatioMovimentoDinheiroController(PatioMovimentoDinheiroService service,RbacGuard guard){this.service=service;this.guard=guard;}
    public record MovimentoRequest(@NotNull UUID chave,@NotNull PatioMovimentoDinheiroService.Tipo tipo,
        @NotNull @Positive @Digits(integer=12,fraction=6) BigDecimal valor,@NotBlank @Size(min=10,max=300) String motivo){}
    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:manage',authentication)")
    public PatioMovimentoDinheiroService.PaginaTurnos listar(@AuthenticationPrincipal UsuarioAutenticado ator,
        @RequestParam(defaultValue="0") int pagina,@RequestParam(defaultValue="20") int tamanho){
        return service.listar(ator,pagina,tamanho);
    }
    @GetMapping("/{id}/movimentos-dinheiro")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:manage',authentication) or @rbacGuard.hasPermissionInActiveContext('operations:checkout',authentication)")
    public List<PatioMovimentoDinheiroRepository.Movimento> historico(@AuthenticationPrincipal UsuarioAutenticado ator,
        @PathVariable long id,Authentication auth){
        return service.historico(ator,id,guard.hasPermissionInActiveContext("operations:manage",auth));
    }
    @PostMapping("/{id}/movimentos-dinheiro")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:manage',authentication) or @rbacGuard.hasPermissionInActiveContext('operations:checkout',authentication)")
    public PatioCaixaService.Resumo registrar(@AuthenticationPrincipal UsuarioAutenticado ator,@PathVariable long id,
        @Valid @RequestBody MovimentoRequest req){
        return service.registrar(ator,id,req.chave(),req.tipo(),req.valor(),req.motivo());
    }
}
