package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patio")
@PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:checkout', authentication) or @rbacGuard.hasPermissionInActiveContext('operations:manage', authentication)")
public class PatioOperacaoController {
    private final PatioOperacaoService service;
    PatioOperacaoController(PatioOperacaoService service){this.service=service;}
    public record Correcao(@NotNull UUID chave,@NotNull PatioOperacaoService.Acao acao,
        @NotBlank @Size(min=10,max=300) String motivo,@Size(max=PatioPlaca.MAXIMO) String placa,PatioCaixaService.Meio meio){}
    @GetMapping("/busca-saida")
    public List<PatioOperacaoRepository.VeiculoAberto> buscar(@AuthenticationPrincipal UsuarioAutenticado ator,
        @RequestParam @Size(max=PatioPlaca.MAXIMO) String placa,@RequestParam(required=false) @Positive Long patioId,
        @RequestParam(required=false) @Positive Long eventoId){return service.buscar(ator,placa,patioId,eventoId);}
    @GetMapping("/comprovantes")
    public List<PatioCaixaService.Comprovante> listar(@AuthenticationPrincipal UsuarioAutenticado ator,
        @RequestParam(required=false) @Size(max=PatioPlaca.MAXIMO) String placa){return service.comprovantes(ator,placa);}
    @GetMapping("/movimentacoes/{id}/comprovante")
    public PatioCaixaService.Comprovante comprovante(@AuthenticationPrincipal UsuarioAutenticado ator,@PathVariable long id){
        return service.comprovante(ator,id);
    }
    @PostMapping("/movimentacoes/{id}/correcao")
    @PreAuthorize("@rbacGuard.hasPermissionInActiveContext('operations:manage', authentication)")
    public PatioOperacaoService.Resultado corrigir(@AuthenticationPrincipal UsuarioAutenticado ator,@PathVariable long id,
        @Valid @RequestBody Correcao req){return service.corrigir(ator,id,req.chave(),req.acao(),req.motivo(),req.placa(),req.meio());}
}
