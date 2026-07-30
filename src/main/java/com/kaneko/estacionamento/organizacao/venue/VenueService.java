package com.kaneko.estacionamento.organizacao.venue;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.geografia.CidadeRepository;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import com.kaneko.estacionamento.organizacao.StatusOrganizacao;
import com.kaneko.estacionamento.organizacao.venue.dto.CriarVenueRequest;
import com.kaneko.estacionamento.organizacao.venue.dto.VenueResponse;
import java.util.LinkedHashMap;
import java.util.Map;
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
        return VenueResponse.from(venue);
    }

    private static Map<String, Object> snapshot(Venue venue) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nome", venue.getNome());
        estado.put("tipo", venue.getTipo().name());
        estado.put("cidadeId", venue.getCidadeId());
        estado.put("fusoHorario", venue.getFusoHorario());
        estado.put("ativo", venue.getAtivo());
        return estado;
    }
}
