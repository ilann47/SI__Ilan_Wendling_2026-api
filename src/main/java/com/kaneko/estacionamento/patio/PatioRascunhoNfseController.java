package com.kaneko.estacionamento.patio;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.common.web.TraceIdFilter;
import com.kaneko.estacionamento.fiscal.NotaEntradaHttpVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/patio/rascunhos-nfse")
@PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
public class PatioRascunhoNfseController {
    public record Identificacao(@JsonProperty(required = true) @Valid PatioTomador tomador) {}

    private final PatioRascunhoNfseService service;

    public PatioRascunhoNfseController(PatioRascunhoNfseService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Page<PatioRascunhoNfseService.Rascunho>> listar(
            @AuthenticationPrincipal UsuarioAutenticado ator,
            @RequestParam(defaultValue = "") @Size(max = 100) String busca,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.listar(ator.organizacaoId(), busca, PageRequest.of(page, size)));
    }

    @GetMapping("/{movimentacaoId}")
    public ResponseEntity<PatioRascunhoNfseService.Rascunho> buscar(@AuthenticationPrincipal UsuarioAutenticado ator,
            @PathVariable @Positive long movimentacaoId) {
        return resposta(service.buscar(ator.organizacaoId(), movimentacaoId));
    }

    @PutMapping("/{movimentacaoId}/tomador")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication) and "
            + "@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public ResponseEntity<PatioRascunhoNfseService.Rascunho> identificar(@AuthenticationPrincipal UsuarioAutenticado ator,
            @PathVariable @Positive long movimentacaoId, @RequestHeader("If-Match") String versao,
            @Valid @RequestBody Identificacao entrada) {
        return resposta(service.identificar(ator.organizacaoId(), movimentacaoId, NotaEntradaHttpVersion.extrair(versao),
                entrada.tomador()));
    }

    private static ResponseEntity<PatioRascunhoNfseService.Rascunho> resposta(PatioRascunhoNfseService.Rascunho rascunho) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag("\"" + rascunho.version() + "\"").body(rascunho);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail precondicao(ResponseStatusException erro) {
        return problema(erro.getStatusCode(), erro.getReason());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail parametroInvalido(IllegalArgumentException erro) {
        return problema(HttpStatus.BAD_REQUEST, erro.getMessage());
    }

    private static ProblemDetail problema(HttpStatusCode status, String mensagem) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, mensagem);
        String trace = MDC.get(TraceIdFilter.MDC_KEY);
        if (trace != null) problema.setProperty("traceId", trace);
        return problema;
    }
}
