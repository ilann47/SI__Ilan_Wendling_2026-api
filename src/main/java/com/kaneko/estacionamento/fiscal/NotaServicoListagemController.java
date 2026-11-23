package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class NotaServicoListagemController {
    private final NotaServicoListagemService service;

    NotaServicoListagemController(NotaServicoListagemService service) {
        this.service = service;
    }

    @GetMapping("/api/notas-servico/listagem")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    @Operation(summary = "Listar notas de serviço e rascunhos de estacionamento",
            description = "Somente leitura, na organização ativa. Busca literal; página iniciada em zero, "
                    + "tamanho de 1 a 50. PENDENTE inclui os rascunhos do estacionamento. "
                    + "clienteId restringe às notas internas do cliente. Ordena da criação mais recente à mais antiga. "
                    + "O valor é texto decimal em reais; a identidade da linha é origem + id. "
                    + "Dados fiscais do tomador do estacionamento só estão no detalhe.")
    public ResponseEntity<Page<NotaServicoListagemService.Linha>> listar(
            @AuthenticationPrincipal UsuarioAutenticado ator,
            @RequestParam(defaultValue = "") @Size(max = 100) String busca,
            @RequestParam(required = false) NotaServicoListagemService.Origem origem,
            @RequestParam(required = false) SituacaoNotaServico situacao,
            @RequestParam(required = false) @Positive Long clienteId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        if (ator == null || ator.organizacaoId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Selecione a organização para consultar notas.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listar(ator.organizacaoId(), busca.strip(), origem, situacao, clienteId,
                        PageRequest.of(page, size)));
    }
}
