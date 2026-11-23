package com.kaneko.estacionamento.rbac;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kaneko.estacionamento.membership.MembroOrganizacaoRepository;
import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.organizacao.Organizacao;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RbacServiceLockingTest {

    @Test
    void serializaValidacaoDoUltimoAdministradorPelaOrganizacao() {
        AtribuicaoAcessoRepository atribuicoes = mock(AtribuicaoAcessoRepository.class);
        PapelRepository papeis = mock(PapelRepository.class);
        MembroOrganizacaoRepository memberships = mock(MembroOrganizacaoRepository.class);
        OrganizacaoRepository organizacoes = mock(OrganizacaoRepository.class);
        AuditoriaService auditoria = mock(AuditoriaService.class);
        when(organizacoes.findByIdForUpdate(30L)).thenReturn(Optional.of(mock(Organizacao.class)));

        var service = new RbacService(atribuicoes, papeis, memberships, organizacoes, auditoria);
        service.validarDesativacaoMembership(10L, 30L);

        verify(organizacoes).findByIdForUpdate(30L);
    }
}
