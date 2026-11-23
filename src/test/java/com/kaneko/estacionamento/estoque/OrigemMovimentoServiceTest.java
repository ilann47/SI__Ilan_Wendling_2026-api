package com.kaneko.estacionamento.estoque;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.kaneko.estacionamento.rbac.RbacService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OrigemMovimentoServiceTest {
    @Test void semPermissaoFiscalNaoConsultaNemExpoeIdDocumento() {
        var repository = mock(MovimentoEstoqueRepository.class); var jdbc = mock(JdbcTemplate.class); var rbac = mock(RbacService.class);
        var movimento = mock(MovimentoEstoque.class);
        when(movimento.getCompensadoId()).thenReturn(null);
        when(repository.findByIdAndOrganizacaoId(10L, 2L)).thenReturn(Optional.of(movimento));
        when(movimento.getOrigemTipo()).thenReturn("NOTA_ENTRADA");
        when(rbac.permissoes(3L,2L)).thenReturn(List.of("stock:read"));
        var response = new OrigemMovimentoService(repository,jdbc,rbac).buscar(2L,3L,10L);
        assertThat(response.id()).isNull(); assertThat(response.caminho()).isNull();
        assertThat(response.descricao()).contains("não permite"); verifyNoInteractions(jdbc);
    }

    @Test void chaveLegadaOuInvalidaNaoAdivinhaDocumento() {
        var repository = mock(MovimentoEstoqueRepository.class); var jdbc = mock(JdbcTemplate.class); var rbac = mock(RbacService.class);
        var movimento = mock(MovimentoEstoque.class);
        when(movimento.getCompensadoId()).thenReturn(null);
        when(repository.findByIdAndOrganizacaoId(10L, 2L)).thenReturn(Optional.of(movimento));
        when(movimento.getOrigemTipo()).thenReturn("NOTA_SAIDA");
        when(movimento.getOrigemChave()).thenReturn("999999999999999999999999999999999:1");
        when(rbac.permissoes(3L,2L)).thenReturn(List.of("stock:read","fiscal:read"));
        var response = new OrigemMovimentoService(repository,jdbc,rbac).buscar(2L,3L,10L);
        assertThat(response.caminho()).isNull(); verifyNoInteractions(jdbc);
    }
}
