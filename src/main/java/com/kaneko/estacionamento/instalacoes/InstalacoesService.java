package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.CatalogSearch;
import com.kaneko.estacionamento.geografia.CidadeRepository;
import com.kaneko.estacionamento.instalacoes.dto.CriarPatioInstalacaoRequest;
import com.kaneko.estacionamento.instalacoes.dto.PatioInstalacaoResponse;
import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEventoRepository;
import com.kaneko.estacionamento.inventario.alocacao.CapacidadeTemporal;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import com.kaneko.estacionamento.organizacao.StatusOrganizacao;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstalacoesService {
    private final PatioInstalacaoRepository patios;
    private final OrganizacaoRepository organizacoes;
    private final CidadeRepository cidades;
    private final AuditoriaService auditoria;
    private final SetorRepository setores;
    private final AlocacaoPatioEventoRepository alocacoes;

    public InstalacoesService(
            PatioInstalacaoRepository patios, OrganizacaoRepository organizacoes,
            CidadeRepository cidades, AuditoriaService auditoria, SetorRepository setores,
            AlocacaoPatioEventoRepository alocacoes) {
        this.patios = patios;
        this.organizacoes = organizacoes;
        this.cidades = cidades;
        this.auditoria = auditoria;
        this.setores = setores;
        this.alocacoes = alocacoes;
    }

    @Transactional(readOnly = true)
    public Page<CatalogSearch.Option> listar(Long organizacaoId, String name, Long id, Pageable page) {
        return patios.findAll(CatalogSearch.ativos(organizacaoId, name, id), page)
                .map(patio -> new CatalogSearch.Option(patio.getId(), patio.getNome()));
    }

    @Transactional(readOnly = true)
    public Page<LinhaGestao> listarGestao(Long org, String nome, Pageable page) {
        return patios.findAll(CatalogSearch.daOrganizacao(org, nome), page)
                .map(p -> new LinhaGestao(p.getId(), p.getNome(), p.getAtivo(),
                        p.getCapacidadeFisica(), p.getCapacidadeOperacional()));
    }

    public record LinhaGestao(Long id, String name, boolean active, int physicalCapacity, int operationalCapacity) {}

    @Transactional(readOnly = true)
    public PatioInstalacaoResponse obter(Long org, Long id) {
        var patio = patios.findByIdAndOrganizacaoId(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pátio", id));
        var cidade = cidades.findById(patio.getCidadeId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade", patio.getCidadeId()));
        return PatioInstalacaoResponse.from(patio, cidade.getNome());
    }

    @Transactional(readOnly = true)
    public CapacidadeTemporal.Saldo disponibilidade(Long org, Long id, OffsetDateTime inicio,
                                                   OffsetDateTime fim, boolean incluirHorarios) {
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("O fim do período deve ser posterior ao início.");
        }
        if (incluirHorarios && Duration.between(inicio, fim).compareTo(Duration.ofDays(45)) > 0) {
            throw new IllegalArgumentException("A consulta de horários aceita um período de até 45 dias.");
        }
        var patio = patios.findByIdAndOrganizacaoId(id, org)
                .filter(p -> Boolean.TRUE.equals(p.getAtivo()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pátio ativo", id));
        return CapacidadeTemporal.calcular(patio.getCapacidadeOperacional(), inicio, fim,
                alocacoes.buscarSobrepostas(org, id, inicio, fim, null), incluirHorarios);
    }

    @Transactional
    public PatioInstalacaoResponse editar(Long org, Long id, long versao, CriarPatioInstalacaoRequest request) {
        PatioInstalacao patio = patios.buscarParaEdicao(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pátio", id));
        InstalacaoHttpVersion.exigir(patio.getVersion(), versao);
        if (request.active() != null && !request.active().equals(patio.getAtivo())) {
            throw new IllegalArgumentException("A situação do pátio não pode ser alterada nesta edição.");
        }
        if (!patio.getNome().equalsIgnoreCase(request.name().trim())
                && patios.existeNomeNormalizado(org, request.name())) {
            throw new ConflitoException("Já existe um pátio com este nome na organização.");
        }
        var cidade = cidades.findById(request.cityId())
                .filter(c -> patio.getCidadeId().equals(c.getId()) || Boolean.TRUE.equals(c.getAtivo()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade ativa", request.cityId()));
        if (request.physicalCapacity() < patio.getCapacidadeFisica()
                && request.physicalCapacity() < setores.somarCapacidadePorPatio(id)) {
            throw new ConflitoException("A capacidade física não pode ser menor que a soma dos setores.");
        }
        if (request.operationalCapacity() < patio.getCapacidadeOperacional()
                && request.operationalCapacity() < picoDeAlocacoes(org, id)) {
            throw new ConflitoException("A capacidade operacional é menor que as vagas já alocadas para eventos.");
        }
        var antes = snapshot(patio);
        patio.atualizar(request.name(), request.address(), request.number(), request.complement(),
                request.district(), request.postalCode(), request.cityId(), request.physicalCapacity(),
                request.operationalCapacity(), request.notes());
        patios.flush();
        auditoria.registrar(new RegistrarAuditoriaCommand(
                org, "PATIO_ATUALIZADO", "PATIO", id, null, id, null,
                antes, snapshot(patio), versao, patio.getVersion(), ClasseRetencaoAuditoria.CONFIGURACAO));
        return PatioInstalacaoResponse.from(patio, cidade.getNome());
    }

    private long picoDeAlocacoes(Long org, Long id) {
        var agora = OffsetDateTime.now();
        var variacoes = new TreeMap<Instant, Long>();
        for (var alocacao : alocacoes.findAllByOrganizacaoIdAndPatioIdAndFimAfter(org, id, agora)) {
            var inicio = alocacao.getInicio().isBefore(agora) ? agora : alocacao.getInicio();
            long capacidade = alocacao.getCapacidadeOperacional();
            variacoes.merge(inicio.toInstant(), capacidade, Long::sum);
            variacoes.merge(alocacao.getFim().toInstant(), -capacidade, Long::sum);
        }
        long pico = 0;
        long acumulado = 0;
        for (long delta : variacoes.values()) {
            acumulado += delta;
            pico = Math.max(pico, acumulado);
        }
        return pico;
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
        return PatioInstalacaoResponse.from(patio, cidade.getNome());
    }

    private static Map<String, Object> snapshot(PatioInstalacao patio) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nome", patio.getNome());
        estado.put("cidadeId", patio.getCidadeId());
        estado.put("endereco", patio.getEndereco());
        estado.put("numero", patio.getNumero());
        estado.put("complemento", patio.getComplemento());
        estado.put("bairro", patio.getBairro());
        estado.put("cep", patio.getCep());
        estado.put("observacao", patio.getObservacao());
        estado.put("capacidadeFisica", patio.getCapacidadeFisica());
        estado.put("capacidadeOperacional", patio.getCapacidadeOperacional());
        estado.put("ativo", patio.getAtivo());
        return estado;
    }
}
