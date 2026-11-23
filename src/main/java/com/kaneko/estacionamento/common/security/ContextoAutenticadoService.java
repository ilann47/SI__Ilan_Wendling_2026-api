package com.kaneko.estacionamento.common.security;

import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.membership.MembroOrganizacao;
import com.kaneko.estacionamento.membership.MembroOrganizacaoRepository;
import com.kaneko.estacionamento.membership.StatusMembroOrganizacao;
import com.kaneko.estacionamento.membership.dto.OrganizacaoAcessivelResponse;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import com.kaneko.estacionamento.organizacao.StatusOrganizacao;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContextoAutenticadoService {

    private final MembroOrganizacaoRepository membershipRepository;
    private final OrganizacaoRepository organizacaoRepository;

    public ContextoAutenticadoService(
            MembroOrganizacaoRepository membershipRepository,
            OrganizacaoRepository organizacaoRepository) {
        this.membershipRepository = membershipRepository;
        this.organizacaoRepository = organizacaoRepository;
    }

    @Transactional(readOnly = true)
    public ContextoOrganizacao validarContextoToken(
            Long membershipId, Long usuarioId, Long organizacaoId, Long versao) {
        MembroOrganizacao membro = membershipRepository
                .findByIdAndUsuarioIdAndOrganizacaoIdAndStatus(
                        membershipId, usuarioId, organizacaoId, StatusMembroOrganizacao.ATIVO)
                .orElseThrow(TokenContextoInvalidoException::new);
        if (!membro.getVersion().equals(versao)
                || organizacaoRepository.findByIdAndStatus(organizacaoId, StatusOrganizacao.ATIVA).isEmpty()) {
            throw new TokenContextoInvalidoException();
        }
        return contexto(membro);
    }

    @Transactional(readOnly = true)
    public ContextoOrganizacao selecionar(Long usuarioId, Long organizacaoId) {
        MembroOrganizacao membro = membershipRepository
                .findFirstByUsuarioIdAndOrganizacaoIdAndStatusOrderByIdDesc(
                        usuarioId, organizacaoId, StatusMembroOrganizacao.ATIVO)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Organizacao acessivel nao encontrada para o usuario autenticado."));
        if (organizacaoRepository.findByIdAndStatus(organizacaoId, StatusOrganizacao.ATIVA).isEmpty()) {
            throw new RecursoNaoEncontradoException(
                    "Organizacao acessivel nao encontrada para o usuario autenticado.");
        }
        return contexto(membro);
    }

    @Transactional(readOnly = true)
    public List<OrganizacaoAcessivelResponse> listarOrganizacoes(Long usuarioId) {
        return membershipRepository.listarOrganizacoesAcessiveis(
                usuarioId, StatusMembroOrganizacao.ATIVO, StatusOrganizacao.ATIVA);
    }

    private static ContextoOrganizacao contexto(MembroOrganizacao membro) {
        return new ContextoOrganizacao(
                membro.getOrganizacaoId(), membro.getId(), membro.getVersion());
    }
}
