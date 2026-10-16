package com.kaneko.estacionamento.pagamento;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.pagamento.dto.FormaPagamentoRequest;
import com.kaneko.estacionamento.pagamento.dto.FormaPagamentoResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Forma de Pagamento.
 *
 * <p>Garante a unicidade do nome (RN do cadastro, espelhando a restricao
 * {@code uq_forma_pagamento_nome}) na criacao e na alteracao.
 */
@Service
public class FormaPagamentoService {

    private final FormaPagamentoRepository repository;
    private final AuditoriaService auditoria;

    public FormaPagamentoService(
            FormaPagamentoRepository repository,
            AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<FormaPagamentoResponse> listar(
            Long organizacaoId, String nome, Boolean ativo, Pageable pageable) {
        Specification<FormaPagamento> filtro = FiltroSpecs.todos(
                FiltroSpecs.igual("organizacaoId", organizacaoId),
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(FormaPagamentoResponse::from);
    }

    @Transactional(readOnly = true)
    public FormaPagamento buscar(Long organizacaoId, Long id) {
        return repository.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Forma de pagamento", id));
    }

    @Transactional
    public FormaPagamento criar(Long organizacaoId, FormaPagamentoRequest req) {
        String nome = req.nome().trim();
        if (repository.existeNomeNormalizado(organizacaoId, nome, null)) {
            throw new ConflitoException("Ja existe uma forma de pagamento cadastrada com este nome.");
        }
        FormaPagamento forma = new FormaPagamento();
        forma.setOrganizacaoId(organizacaoId);
        aplicar(forma, req, nome);
        forma = repository.saveAndFlush(forma);
        registrar(forma, "FORMA_PAGAMENTO_CRIADA", null, snapshot(forma));
        return forma;
    }

    @Transactional
    public FormaPagamento atualizar(Long organizacaoId, Long id, FormaPagamentoRequest req) {
        FormaPagamento forma = buscar(organizacaoId, id);
        String nome = req.nome().trim();
        if (repository.existeNomeNormalizado(organizacaoId, nome, id)) {
            throw new ConflitoException("Ja existe uma forma de pagamento cadastrada com este nome.");
        }
        Map<String, Object> anterior = snapshot(forma);
        aplicar(forma, req, nome);
        forma = repository.saveAndFlush(forma);
        registrar(forma, "FORMA_PAGAMENTO_ATUALIZADA", anterior, snapshot(forma));
        return forma;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        FormaPagamento forma = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(forma);
        forma.setAtivo(false);
        repository.flush();
        registrar(forma, "FORMA_PAGAMENTO_DESATIVADA", anterior, snapshot(forma));
    }

    private void aplicar(FormaPagamento forma, FormaPagamentoRequest req, String nome) {
        forma.setNome(nome);
        forma.setDescricao(req.descricao().trim());
        if (req.ativo() != null) {
            forma.setAtivo(req.ativo());
        }
    }

    private void registrar(
            FormaPagamento forma,
            String acao,
            Map<String, Object> anterior,
            Map<String, Object> posterior) {
        auditoria.registrar(new RegistrarAuditoriaCommand(
                forma.getOrganizacaoId(), acao, "FORMA_PAGAMENTO", forma.getId(),
                null, null, null, anterior, posterior, null, null,
                ClasseRetencaoAuditoria.CONFIGURACAO));
    }

    private static Map<String, Object> snapshot(FormaPagamento forma) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nome", forma.getNome());
        estado.put("descricao", forma.getDescricao());
        estado.put("ativo", forma.getAtivo());
        return estado;
    }
}
