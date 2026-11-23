package com.kaneko.estacionamento.evento;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.evento.dto.AbrirVendasEventoRequest;
import com.kaneko.estacionamento.evento.dto.AtualizarEventoRequest;
import com.kaneko.estacionamento.evento.dto.CriarEventoRequest;
import com.kaneko.estacionamento.evento.dto.EventoResponse;
import com.kaneko.estacionamento.evento.dto.EncerrarOperacaoEventoRequest;
import com.kaneko.estacionamento.evento.dto.PublicarEventoRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/v1/events")
public class EventoController {
    private static final Pattern ETAG_VERSAO = Pattern.compile("^\"(0|[1-9][0-9]*)\"$");
    private final EventoService service;

    public EventoController(EventoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:read', authentication)")
    public Page<EventoResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PageableDefault(size = 50, sort = "inicio", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return service.listar(principal.organizacaoId(), pageable);
    }

    @GetMapping("/service-options")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:create', authentication)")
    @Operation(summary = "Pesquisar tipos de serviço ativos para a criação de evento",
            description = "Opções mínimas (id e nome) dos serviços cadastrados na organização ativa. "
                    + "Não seleciona, cadastra ou altera serviços; exige a permissão de criar eventos.")
    public ResponseEntity<Page<EventoService.OpcaoServico>> opcoesDeServico(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.opcoesDeServico(principal.organizacaoId(), q, PageRequest.of(page, size)));
    }

    @GetMapping("/{eventoId}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:read', authentication)")
    public ResponseEntity<EventoResponse> buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId) {
        EventoResponse response = service.buscar(principal.organizacaoId(), eventoId);
        return ResponseEntity.ok().eTag("\"" + response.version() + "\"").body(response);
    }

    @PatchMapping("/{eventoId}")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:create', authentication)")
    public ResponseEntity<EventoResponse> atualizar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody AtualizarEventoRequest request) {
        EventoResponse response = service.atualizar(
                principal.organizacaoId(), eventoId, extrairVersao(ifMatch), request);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PostMapping
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:create', authentication)")
    @Operation(summary = "Criar evento com o tipo de serviço explicitamente selecionado",
            description = "serviceId é obrigatório e deve identificar um serviço ativo na organização. "
                    + "O vínculo não altera o preço nem as regras de cobrança do evento.")
    public ResponseEntity<EventoResponse> criar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestHeader("Idempotency-Key") String chaveIdempotencia,
            @Valid @RequestBody CriarEventoRequest request) {
        EventoResponse response = service.criar(
                principal.organizacaoId(), principal.userId(), chaveIdempotencia, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PostMapping("/{eventoId}/publication")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:publish', authentication)")
    public ResponseEntity<EventoResponse> publicar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody PublicarEventoRequest request) {
        EventoResponse response = service.publicar(
                principal.organizacaoId(), eventoId, extrairVersao(ifMatch), request);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PostMapping("/{eventoId}/sales-opening")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:publish', authentication)")
    public ResponseEntity<EventoResponse> abrirVendas(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody AbrirVendasEventoRequest request) {
        EventoResponse response = service.abrirVendas(
                principal.organizacaoId(), eventoId, extrairVersao(ifMatch), request);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PostMapping("/{eventoId}/sales-closing")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('events:publish', authentication)")
    public ResponseEntity<EventoResponse> encerrarVendas(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody PublicarEventoRequest request) {
        EventoResponse response = service.encerrarVendas(
                principal.organizacaoId(), eventoId, extrairVersao(ifMatch), request);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PostMapping("/{eventoId}/operation-start")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('access:operate', authentication)")
    public ResponseEntity<EventoResponse> iniciarOperacao(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody PublicarEventoRequest request) {
        EventoResponse response = service.iniciarOperacao(
                principal.organizacaoId(), eventoId, extrairVersao(ifMatch), request);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    @PostMapping("/{eventoId}/operation-closing")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('access:operate', authentication)")
    public ResponseEntity<EventoResponse> encerrarOperacao(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long eventoId,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @Valid @RequestBody EncerrarOperacaoEventoRequest request) {
        EventoResponse response = service.encerrarOperacao(
                principal.organizacaoId(), eventoId, extrairVersao(ifMatch), request);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    private static long extrairVersao(String ifMatch) {
        Matcher matcher = ETAG_VERSAO.matcher(ifMatch);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "If-Match deve conter somente a versao esperada entre aspas.");
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
                    "A versao informada em If-Match e invalida.", ex);
        }
    }
}
