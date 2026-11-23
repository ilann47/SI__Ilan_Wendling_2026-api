package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.common.web.CatalogSearch;
import com.kaneko.estacionamento.geografia.CidadeService;
import com.kaneko.estacionamento.geografia.dto.CidadeResponse;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/installation-cities")
public class InstallationCityController {
    private final CidadeService cidades;

    public InstallationCityController(CidadeService cidades) {
        this.cidades = cidades;
    }

    @GetMapping
    @PreAuthorize("""
            @rbacGuard.hasPermissionInActiveContext('facilities:manage', authentication) or
            @rbacGuard.hasPermissionInActiveContext('organizations:admin', authentication)
            """)
    public Page<CidadeResponse> listar(@RequestParam(required = false) String name,
            @RequestParam(required = false) Long id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return cidades.listarAtivas(name, id, CatalogSearch.page(page, size));
    }
}
