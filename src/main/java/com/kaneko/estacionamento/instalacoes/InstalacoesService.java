package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.geografia.CidadeRepository;
import com.kaneko.estacionamento.instalacoes.dto.CriarPatioInstalacaoRequest;
import com.kaneko.estacionamento.instalacoes.dto.PatioInstalacaoResponse;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import com.kaneko.estacionamento.organizacao.StatusOrganizacao;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstalacoesService {
    private final PatioInstalacaoRepository patios;
    private final OrganizacaoRepository organizacoes;
    private final CidadeRepository cidades;
    private final AuditoriaService auditoria;

    public InstalacoesService(
            PatioInstalacaoRepository patios, OrganizacaoRepository organizacoes,
            CidadeRepository cidades, AuditoriaService auditoria) {
        this.patios = patios;
        this.organizacoes = organizacoes;
        this.cidades = cidades;
        this.auditoria = auditoria;
    }

    @Transactional
    public PatioInstalacaoResponse criar(Long organizacaoId, CriarPatioInstalacaoRequest request) {
        organizacoes.findByIdAndStatus(organizacaoId, StatusOrganizacao.ATIVA)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Organizacao ativa", organizacaoId));
        var cidade = cidades.findById(request.cityId())
                .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade ativa", request.cityId()));
        if (patios.existeNomeNormalizado(organizacaoId, request.name())) {
            throw new ConflitoException("Ja existe um patio com este nome na organizacao.");
        }
        PatioInstalacao patio = PatioInstalacao.criar(
                organizacaoId, request.name(), request.address(), request.number(), request.complement(),
                request.district(), request.postalCode(), cidade.getId(), request.physicalCapacity(),
                request.operationalCapacity(), request.notes(), request.active());
        patio = patios.saveAndFlush(patio);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "PATIO_CRIADO", "PATIO", patio.getId(), null, patio.getId(), null,
                null, snapshot(patio), null, patio.getVersion(), ClasseRetencaoAuditoria.CONFIGURACAO));
        return PatioInstalacaoResponse.from(patio);
    }

    private static Map<String, Object> snapshot(PatioInstalacao patio) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nome", patio.getNome());
        estado.put("cidadeId", patio.getCidadeId());
        estado.put("capacidadeFisica", patio.getCapacidadeFisica());
        estado.put("capacidadeOperacional", patio.getCapacidadeOperacional());
        estado.put("ativo", patio.getAtivo());
        return estado;
    }
}
