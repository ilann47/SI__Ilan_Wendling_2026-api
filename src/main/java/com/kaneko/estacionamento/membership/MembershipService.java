package com.kaneko.estacionamento.membership;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.membership.dto.AtualizarMembershipRequest;
import com.kaneko.estacionamento.membership.dto.MembershipResponse;
import com.kaneko.estacionamento.organizacao.Organizacao;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import com.kaneko.estacionamento.organizacao.StatusOrganizacao;
import com.kaneko.estacionamento.usuario.Usuario;
import com.kaneko.estacionamento.usuario.UsuarioRepository;
import com.kaneko.estacionamento.rbac.RbacService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MembershipService {

    private final MembroOrganizacaoRepository repository;
    private final OrganizacaoRepository organizacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final RbacService rbacService;
    private final AuditoriaService auditoria;

    public MembershipService(
            MembroOrganizacaoRepository repository,
            OrganizacaoRepository organizacaoRepository,
            UsuarioRepository usuarioRepository, RbacService rbacService, AuditoriaService auditoria) {
        this.repository = repository;
        this.organizacaoRepository = organizacaoRepository;
        this.usuarioRepository = usuarioRepository;
        this.rbacService = rbacService;
        this.auditoria = auditoria;
    }

    @Transactional
    public MembershipResponse criar(Long organizacaoId, Long usuarioId) {
        Organizacao organizacao = organizacaoRepository.findByIdForUpdate(organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Organizacao", organizacaoId));
        if (organizacao.getStatus() != StatusOrganizacao.ATIVA) {
            throw new ConflitoException("Somente uma organizacao ativa aceita Memberships.");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario", usuarioId));
        if (!Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new ConflitoException("Um usuario inativo nao pode receber Membership.");
        }

        if (repository.existsByOrganizacaoIdAndUsuarioIdAndStatusNot(
                organizacaoId, usuarioId, StatusMembroOrganizacao.ENCERRADO)) {
            throw new ConflitoException("Ja existe uma Membership corrente para este usuario e organizacao.");
        }

        boolean primeiro = repository.countByOrganizacaoIdAndStatusNot(
                organizacaoId, StatusMembroOrganizacao.ENCERRADO) == 0;
        MembroOrganizacao membro = MembroOrganizacao.adicionar(
                organizacaoId, usuarioId, OrigemMembroOrganizacao.ADMINISTRATIVA);
        membro = repository.saveAndFlush(membro);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "MEMBERSHIP_CRIADA", "MEMBERSHIP", membro.getId(),
                null, null, null, null, snapshot(membro), null, membro.getVersion(),
                ClasseRetencaoAuditoria.SEGURANCA));
        if (primeiro) rbacService.concederAdminInicial(membro.getId(), organizacaoId);
        return MembershipResponse.from(membro);
    }

    @Transactional(readOnly = true)
    public MembershipResponse buscar(Long organizacaoId, Long membershipId) {
        return MembershipResponse.from(buscarEntidade(organizacaoId, membershipId));
    }

    @Transactional
    public MembershipResponse atualizar(
            Long organizacaoId,
            Long membershipId,
            long versaoEsperada,
            AtualizarMembershipRequest request) {
        MembroOrganizacao membro = buscarEntidade(organizacaoId, membershipId);
        if (membro.getVersion().longValue() != versaoEsperada) {
            throw new VersaoMembershipDivergenteException(versaoEsperada, membro.getVersion());
        }
        Map<String, Object> anterior = snapshot(membro);
        Long versaoAnterior = membro.getVersion();

        if (request.getStatus() == StatusMembroOrganizacao.SUSPENSO
                || request.getStatus() == StatusMembroOrganizacao.ENCERRADO) {
            rbacService.validarDesativacaoMembership(membershipId, organizacaoId);
        }

        switch (request.getStatus()) {
            case ATIVO -> membro.reativar();
            case SUSPENSO -> membro.suspender();
            case ENCERRADO -> membro.encerrar();
        }
        repository.flush();
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, acao(request.getStatus()), "MEMBERSHIP", membro.getId(),
                null, null, null, anterior, snapshot(membro), versaoAnterior, membro.getVersion(),
                ClasseRetencaoAuditoria.SEGURANCA));
        return MembershipResponse.from(membro);
    }

    private MembroOrganizacao buscarEntidade(Long organizacaoId, Long membershipId) {
        return repository.findByIdAndOrganizacaoId(membershipId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Membership", membershipId));
    }

    private static String acao(StatusMembroOrganizacao status) {
        return switch (status) {
            case ATIVO -> "MEMBERSHIP_REATIVADA";
            case SUSPENSO -> "MEMBERSHIP_SUSPENSA";
            case ENCERRADO -> "MEMBERSHIP_ENCERRADA";
        };
    }

    private static Map<String, Object> snapshot(MembroOrganizacao membro) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("usuarioId", membro.getUsuarioId());
        estado.put("status", membro.getStatus().name());
        estado.put("origem", membro.getOrigem().name());
        estado.put("entrouEm", membro.getEntrouEm().toString());
        if (membro.getSuspensoEm() != null) estado.put("suspensoEm", membro.getSuspensoEm().toString());
        if (membro.getEncerradoEm() != null) estado.put("encerradoEm", membro.getEncerradoEm().toString());
        return estado;
    }
}
