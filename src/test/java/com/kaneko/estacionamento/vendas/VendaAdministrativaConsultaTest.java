package com.kaneko.estacionamento.vendas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class VendaAdministrativaConsultaTest {
    @Mock VendaAdministrativaRepository repository;
    @InjectMocks VendaAdministrativaService service;

    @Test
    void filtraNumeroESituacaoNoTenantAntesDaPaginacaoPreservandoOrdenacaoETotal() {
        var pageable = PageRequest.of(0, 2, Sort.by("numero"));
        when(repository.findPageIdsFiltrados(8L, "ab_25%", List.of(StatusVendaAdministrativa.CONFIRMADA), pageable))
                .thenReturn(new PageImpl<>(List.of(12L, 11L), pageable, 9));
        var first = new VendaAdministrativa(); first.setId(12L);
        var second = new VendaAdministrativa(); second.setId(11L);
        when(repository.findAllByOrganizacaoIdAndIdIn(8L, List.of(12L, 11L))).thenReturn(List.of(second, first));
        var result = service.listar(8L, "  AB_25%  ", StatusVendaAdministrativa.CONFIRMADA, pageable);
        assertThat(result.getContent()).extracting(VendaAdministrativa::getId).containsExactly(12L, 11L);
        assertThat(result.getTotalElements()).isEqualTo(9);
        verify(repository, never()).findPageIdsByOrganizacaoId(anyLong(), any());
    }

    @Test
    void preservaConsultaLegadaSemFiltros() {
        var pageable = PageRequest.of(0, 10);
        when(repository.findPageIdsByOrganizacaoId(8L, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));
        assertThat(service.listar(8L, pageable)).isEmpty();
        verify(repository, never()).findPageIdsFiltrados(anyLong(), any(), any(), any());
    }
}
