package com.kaneko.estacionamento.opcoes;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operational-options")
public class OperationalOptionsController {
    private final OperationalOptionsService service;

    public OperationalOptionsController(OperationalOptionsService service) {
        this.service = service;
    }

    @GetMapping("/{kind}")
    @PreAuthorize("@rbacGuard.hasActiveContext(authentication)")
    @Operation(summary = "Pesquisar opções para ações operacionais",
            description = "Projeção paginada de id, rótulo e versão. Só retorna recursos da organização ativa "
                    + "visíveis para a ação, limitados ao próprio operador quando o fluxo original assim exige. "
                    + "Não cadastra nem modifica recursos.")
    public ResponseEntity<Page<Option>> list(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable String kind,
            @RequestParam(defaultValue = "") @Size(max = 180) String q,
            @RequestParam(required = false) @Positive Long id,
            @RequestParam(required = false) @Positive Long eventId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                service.list(principal, kind, q.strip(), id, eventId, PageRequest.of(page, size)));
    }

    public record Option(long id, String label, long version) {}
}
