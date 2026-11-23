package com.kaneko.estacionamento.organizacao.venue;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.CatalogSearch;
import com.kaneko.estacionamento.geografia.CidadeRepository;
import com.kaneko.estacionamento.instalacoes.InstalacaoHttpVersion;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import com.kaneko.estacionamento.organizacao.StatusOrganizacao;
import com.kaneko.estacionamento.organizacao.venue.dto.CriarVenueRequest;
import com.kaneko.estacionamento.organizacao.venue.dto.VenueResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VenueService {
    private final VenueRepository venues;
    private final OrganizacaoRepository organizacoes;
    private final CidadeRepository cidades;
    private final AuditoriaService auditoria;

    public VenueService(
            VenueRepository venues, OrganizacaoRepository organizacoes,
            CidadeRepository cidades, AuditoriaService auditoria) {
        this.venues = venues;
        this.organizacoes = organizacoes;
        this.cidades = cidades;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<CatalogSearch.Option> listar(Long organizacaoId, String name, Long id, Pageable page) {
        return venues.findAll(CatalogSearch.ativos(organizacaoId, name, id), page)
                .map(venue -> new CatalogSearch.Option(venue.getId(), venue.getNome()));
    }

    @Transactional(readOnly = true)
    public Page<CatalogSearch.ManagementRow> listarGestao(Long org, String nome, Pageable page) {
        return venues.findAll(CatalogSearch.daOrganizacao(org, nome), page)
                .map(v -> new CatalogSearch.ManagementRow(v.getId(), v.getNome(), v.getAtivo()));
    }

    @Transactional(readOnly = true)
    public VenueResponse obter(Long org, Long id) {
        var venue = venues.findByIdAndOrganizacaoId(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Local", id));
        var cidade = cidades.findById(venue.getCidadeId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade", venue.getCidadeId()));
        return VenueResponse.from(venue, cidade.getNome());
    }

    @Transactional
    public VenueResponse editar(Long org, Long id, long versao, CriarVenueRequest request) {
        Venue venue = venues.buscarParaEdicao(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Local", id));
        InstalacaoHttpVersion.exigir(venue.getVersion(), versao);
        if (!venue.getNome().equalsIgnoreCase(request.name().trim())
                && venues.existeNomeNormalizado(org, request.name())) {
            throw new ConflitoException("Já existe um local com este nome na organização.");
        }
        var cidade = cidades.findById(request.cityId())
                .filter(c -> venue.getCidadeId().equals(c.getId()) || Boolean.TRUE.equals(c.getAtivo()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade ativa", request.cityId()));
        var antes = snapshot(venue);
        venue.atualizar(request.name(), request.address(), request.number(), request.complement(),
                request.district(), request.postalCode(), request.cityId(), request.timeZone());
        venues.flush();
        auditoria.registrar(new RegistrarAuditoriaCommand(
                org, "UNIDADE_ATUALIZADA", "VENUE", id, null, null, null,
                antes, snapshot(venue), versao, venue.getVersion(), ClasseRetencaoAuditoria.CONFIGURACAO));
        return VenueResponse.from(venue, cidade.getNome());
    }

    @Transactional
    public VenueResponse criar(Long organizacaoId, CriarVenueRequest request) {
        organizacoes.findByIdAndStatus(organizacaoId, StatusOrganizacao.ATIVA)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Organizacao ativa", organizacaoId));
        var cidade = cidades.findById(request.cityId())
                .filter(c -> Boolean.TRUE.equals(c.getAtivo()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade ativa", request.cityId()));
        if (venues.existeNomeNormalizado(organizacaoId, request.name())) {
            throw new ConflitoException("Ja existe um venue com este nome na organizacao.");
        }
        Venue venue = Venue.criar(
                organizacaoId, request.name(), request.address(), request.number(),
                request.complement(), request.district(), request.postalCode(),
                cidade.getId(), request.timeZone());
        venue = venues.saveAndFlush(venue);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "UNIDADE_CRIADA", "VENUE", venue.getId(), null, null, null,
                null, snapshot(venue), null, venue.getVersion(), ClasseRetencaoAuditoria.CONFIGURACAO));
        return VenueResponse.from(venue, cidade.getNome());
    }

    private static Map<String, Object> snapshot(Venue venue) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nome", venue.getNome());
        estado.put("tipo", venue.getTipo().name());
        estado.put("cidadeId", venue.getCidadeId());
        estado.put("endereco", venue.getEndereco());
        estado.put("numero", venue.getNumero());
        estado.put("complemento", venue.getComplemento());
        estado.put("bairro", venue.getBairro());
        estado.put("cep", venue.getCep());
        estado.put("fusoHorario", venue.getFusoHorario());
        estado.put("ativo", venue.getAtivo());
        return estado;
    }
}
