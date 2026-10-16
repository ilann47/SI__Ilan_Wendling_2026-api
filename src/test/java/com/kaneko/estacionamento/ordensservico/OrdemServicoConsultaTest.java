package com.kaneko.estacionamento.ordensservico;

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

@ExtendWith(MockitoExtension.class)
class OrdemServicoConsultaTest {
    @Mock OrdemServicoRepository repository;
    @InjectMocks OrdemServicoService service;

    @Test
    void filtraNumeroESituacaoSemPerderTotalDaPaginaVazia() {
        var pageable = PageRequest.of(4, 10);
        when(repository.findPageIdsFiltrados(3L, "os-18", List.of(StatusOrdemServico.CONCLUIDA), pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 31));
        var result = service.listar(3L, " OS-18 ", StatusOrdemServico.CONCLUIDA, pageable);
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(31);
        verify(repository, never()).findAllByOrganizacaoIdAndIdIn(anyLong(), any());
    }

    @Test
    void preservaConsultaLegadaSemFiltros() {
        var pageable = PageRequest.of(0, 10);
        when(repository.findPageIdsByOrganizacaoId(3L, pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));
        assertThat(service.listar(3L, pageable)).isEmpty();
        verify(repository, never()).findPageIdsFiltrados(anyLong(), any(), any(), any());
    }
}
