package com.kaneko.estacionamento.rbac;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.membership.MembroOrganizacaoRepository;
import com.kaneko.estacionamento.membership.StatusMembroOrganizacao;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import com.kaneko.estacionamento.rbac.dto.AtribuicaoAcessoResponse;
import java.util.List;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RbacService {
    public static final String ADMIN_ORGANIZACAO = "ADMIN_ORGANIZACAO";
    private final AtribuicaoAcessoRepository atribuicoes;
    private final PapelRepository papeis;
    private final MembroOrganizacaoRepository memberships;
    private final OrganizacaoRepository organizacoes;
    private final AuditoriaService auditoria;

    public RbacService(AtribuicaoAcessoRepository a, PapelRepository p, MembroOrganizacaoRepository m,
                       OrganizacaoRepository o, AuditoriaService auditoria) {
        atribuicoes = a; papeis = p; memberships = m; organizacoes = o; this.auditoria = auditoria;
    }

    @Transactional
    public void concederAdminInicial(Long membershipId, Long orgId) {
        concederInterno(membershipId, orgId, ADMIN_ORGANIZACAO);
    }

    @Transactional
    public AtribuicaoAcessoResponse conceder(Long membershipId, Long orgId, String roleCode) {
        return conceder(membershipId, orgId, roleCode, null, null);
    }

    @Transactional
    public AtribuicaoAcessoResponse conceder(Long membershipId, Long orgId, String roleCode,
                                              OffsetDateTime inicio, OffsetDateTime fim) {
        return concederInterno(membershipId, orgId, roleCode, inicio, fim);
    }

    private AtribuicaoAcessoResponse concederInterno(Long membershipId, Long orgId, String roleCode) {
        return concederInterno(membershipId, orgId, roleCode, null, null);
    }

    private AtribuicaoAcessoResponse concederInterno(Long membershipId, Long orgId, String roleCode,
                                                      OffsetDateTime inicio, OffsetDateTime fim) {
        memberships.findByIdAndOrganizacaoId(membershipId, orgId)
                .filter(m -> m.getStatus() == StatusMembroOrganizacao.ATIVO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Membership ativa", membershipId));
        Papel papel = papeis.findByCodigo(roleCode == null ? "" : roleCode.trim().toUpperCase())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Papel nao encontrado."));
        if (atribuicoes.existsByMembershipIdAndPapelIdAndOrganizacaoIdAndRevogadoEmIsNull(
                membershipId, papel.getId(), orgId)) throw new ConflitoException("O papel ja esta atribuido.");
        OffsetDateTime vigenteDe = inicio == null ? OffsetDateTime.now() : inicio;
        var salva = atribuicoes.saveAndFlush(AtribuicaoAcesso.conceder(
                membershipId, papel.getId(), orgId, orgId, vigenteDe, fim));
        auditoria.registrar(new RegistrarAuditoriaCommand(
                orgId, "PAPEL_ATRIBUIDO", "ATRIBUICAO_ACESSO", salva.getId(),
                null, null, null, null, snapshot(salva, papel.getCodigo()), null, salva.getVersion(),
                ClasseRetencaoAuditoria.SEGURANCA));
        return AtribuicaoAcessoResponse.from(salva, papel.getCodigo());
    }

    @Transactional
    public void revogar(Long membershipId, Long orgId, Long assignmentId, long version, String motivo) {
        bloquearOrganizacao(orgId);
        var a = atribuicoes.findByIdAndMembershipIdAndOrganizacaoId(assignmentId, membershipId, orgId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Atribuicao", assignmentId));
        if (!a.getVersion().equals(version)) throw new VersaoAtribuicaoDivergenteException();
        Papel papel = papeis.findById(a.getPapelId()).orElseThrow();
        OffsetDateTime agora = OffsetDateTime.now();
        if (ADMIN_ORGANIZACAO.equals(papel.getCodigo())
                && a.estaVigente()
                && atribuicoes.contarAdministradoresAtivos(orgId, agora) <= 1)
            throw new ConflitoException("A organizacao deve manter ao menos um administrador ativo.");
        Map<String, Object> anterior = snapshot(a, papel.getCodigo());
        Long versaoAnterior = a.getVersion();
        a.revogar(motivo); atribuicoes.flush();
        auditoria.registrar(new RegistrarAuditoriaCommand(
                orgId, "ACESSO_REVOGADO", "ATRIBUICAO_ACESSO", a.getId(),
                null, null, motivo, anterior, snapshot(a, papel.getCodigo()), versaoAnterior, a.getVersion(),
                ClasseRetencaoAuditoria.SEGURANCA));
    }

    @Transactional(readOnly = true)
    public List<String> permissoes(Long membershipId, Long orgId) {
        return atribuicoes.permissoesEfetivas(membershipId, orgId, OffsetDateTime.now());
    }

    @Transactional
    public void validarDesativacaoMembership(Long membershipId, Long orgId) {
        bloquearOrganizacao(orgId);
        OffsetDateTime agora = OffsetDateTime.now();
        boolean membershipEhAdmin = atribuicoes.possuiPapelEfetivo(
                membershipId, orgId, ADMIN_ORGANIZACAO, agora);
        if (membershipEhAdmin && atribuicoes.contarAdministradoresAtivos(orgId, agora) <= 1) {
            throw new ConflitoException("A organizacao deve manter ao menos um administrador ativo.");
        }
    }

    private void bloquearOrganizacao(Long orgId) {
        organizacoes.findByIdForUpdate(orgId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Organizacao", orgId));
    }

    private static Map<String, Object> snapshot(AtribuicaoAcesso atribuicao, String papel) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("membershipId", atribuicao.getMembershipId());
        estado.put("papel", papel);
        estado.put("escopoTipo", atribuicao.getEscopoTipo());
        estado.put("escopoId", atribuicao.getEscopoId());
        estado.put("vigenteDe", atribuicao.getVigenteDe().toString());
        if (atribuicao.getVigenteAte() != null) estado.put("vigenteAte", atribuicao.getVigenteAte().toString());
        if (atribuicao.getRevogadoEm() != null) estado.put("revogadoEm", atribuicao.getRevogadoEm().toString());
        return estado;
    }
}
