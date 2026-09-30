package com.kaneko.estacionamento.fiscal.importacao;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.rbac.RbacGuard;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notas-entrada/importacoes")
public class ImportacaoNotaController {
    private final ImportacaoNotaService service;
    private final RbacGuard guard;
    public ImportacaoNotaController(ImportacaoNotaService service,RbacGuard guard) {
        this.service=service; this.guard=guard;
    }
    @GetMapping
    @Operation(summary="Lista importações sem carregar XML original; paginação limitada a 50 registros")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    public Page<ResumoImportacaoResponse> listar(@AuthenticationPrincipal UsuarioAutenticado principal,Pageable pageable) {
        return service.listar(principal.organizacaoId(),PageRequest.of(pageable.getPageNumber(),
                Math.min(pageable.getPageSize(),50),Sort.by(Sort.Direction.DESC,"createdAt","id")));
    }
    @GetMapping("/{id}")
    @Operation(summary="Consulta XML e conciliação persistida, sem representar autorização fiscal externa")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:read', authentication)")
    public ResponseEntity<ImportacaoNotaResponse> buscar(@AuthenticationPrincipal UsuarioAutenticado principal,@PathVariable Long id) {
        return resposta(service.buscar(principal.organizacaoId(),id));
    }
    @PostMapping(consumes={"application/xml","text/xml","application/octet-stream"})
    @Operation(summary="Importa uma NF-e 4.00 de até 2 MB, sem movimentar estoque ou financeiro",
            description="Corpo binário XML de NF-e normal de saída do fornecedor. Chave fiscal única por organização; repetição do mesmo arquivo retorna a importação existente. Não verifica assinatura nem consulta SEFAZ.",
            requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(required=true,
                    content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/xml",
                            schema=@io.swagger.v3.oas.annotations.media.Schema(type="string",format="binary"))))
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public ResponseEntity<ImportacaoNotaResponse> importar(@AuthenticationPrincipal UsuarioAutenticado principal,
            HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong()>NotaEntradaXmlParser.LIMITE_BYTES)
            throw new IllegalArgumentException("O XML deve ter até 2 MB.");
        byte[] xml=request.getInputStream().readNBytes(NotaEntradaXmlParser.LIMITE_BYTES+1);
        var response=service.importar(principal.organizacaoId(),principal.userId(),xml);
        return ResponseEntity.created(URI.create("/api/notas-entrada/importacoes/"+response.id()))
                .eTag(etag(response.version())).body(response);
    }
    @PutMapping("/{id}/conciliacao")
    @Operation(summary="Concilia fornecedor, produtos, unidades e pedido/recebimento opcional sem lançamentos")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public ResponseEntity<ImportacaoNotaResponse> conciliar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id,@RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ConciliarImportacaoRequest request,Authentication authentication) {
        if ((request.ordemCompraId()!=null || request.recebimentoCompraId()!=null)
                && !guard.hasPermissionInActiveContext("purchases:read",authentication))
            throw new AuthorizationDeniedException("Sem permissão para consultar o pedido de compra.");
        return resposta(service.conciliar(principal.organizacaoId(),id,versao(ifMatch),request));
    }
    @PostMapping("/{id}/conferencia")
    @Operation(summary="Registra contagem física; divergência mantém importação sem confirmação")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public ResponseEntity<ImportacaoNotaResponse> conferir(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id,@RequestHeader("If-Match") String ifMatch,@Valid @RequestBody ConferirImportacaoRequest request) {
        return resposta(service.conferir(principal.organizacaoId(),principal.userId(),id,versao(ifMatch),request));
    }
    @PostMapping("/{id}/confirmacao")
    @Operation(summary="Confirma nota, estoque e financeiro atomicamente após conferência",
            description="Repetição sobre importação confirmada retorna a mesma nota. Com recebimento existente não repete estoque. Com pedido sem recebimento exige purchases:manage e registra recebimento na mesma transação.")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('fiscal:manage', authentication)")
    public ResponseEntity<ImportacaoNotaResponse> confirmar(@AuthenticationPrincipal UsuarioAutenticado principal,
            @PathVariable Long id,@RequestHeader("If-Match") String ifMatch,Authentication authentication) {
        return resposta(service.confirmar(principal.organizacaoId(),principal.userId(),id,versao(ifMatch),
                guard.hasPermissionInActiveContext("purchases:manage",authentication)));
    }
    private static ResponseEntity<ImportacaoNotaResponse> resposta(ImportacaoNotaResponse response) {
        return ResponseEntity.ok().eTag(etag(response.version())).body(response);
    }
    private static String etag(Long version) { return "\""+version+"\""; }
    private static long versao(String valor) {
        if (!valor.matches("\"[0-9]+\"")) throw new IllegalArgumentException("Informe apenas a versão esperada entre aspas em If-Match.");
        try { return Long.parseLong(valor.substring(1,valor.length()-1)); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Versão inválida em If-Match."); }
    }
}
