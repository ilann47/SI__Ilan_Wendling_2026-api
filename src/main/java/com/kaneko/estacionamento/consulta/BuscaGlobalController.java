package com.kaneko.estacionamento.consulta;

import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.consulta.dto.BuscaGlobalResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class BuscaGlobalController {
    private final BuscaGlobalService service;

    public BuscaGlobalController(BuscaGlobalService service) {
        this.service = service;
    }

    @GetMapping
    public BuscaGlobalResponse buscar(
            @AuthenticationPrincipal UsuarioAutenticado principal,
            @RequestParam("q") String termo,
            @RequestParam(defaultValue = "5") int limit) {
        return service.buscar(principal.organizacaoId(), principal.membershipId(), termo, limit);
    }
}
