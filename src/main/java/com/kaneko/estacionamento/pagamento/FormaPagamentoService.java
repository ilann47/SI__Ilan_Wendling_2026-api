package com.kaneko.estacionamento.pagamento;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.pagamento.dto.FormaPagamentoRequest;
import com.kaneko.estacionamento.pagamento.dto.FormaPagamentoResponse;
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

    public FormaPagamentoService(FormaPagamentoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<FormaPagamentoResponse> listar(String nome, Boolean ativo, Pageable pageable) {
        Specification<FormaPagamento> filtro = FiltroSpecs.todos(
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(FormaPagamentoResponse::from);
    }

    @Transactional(readOnly = true)
    public FormaPagamento buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Forma de pagamento", id));
    }

    @Transactional
    public FormaPagamento criar(FormaPagamentoRequest req) {
        String nome = req.nome().trim();
        if (repository.existsByNome(nome)) {
            throw new ConflitoException("Ja existe uma forma de pagamento cadastrada com este nome.");
        }
        FormaPagamento forma = new FormaPagamento();
        aplicar(forma, req, nome);
        return repository.save(forma);
    }

    @Transactional
    public FormaPagamento atualizar(Long id, FormaPagamentoRequest req) {
        FormaPagamento forma = buscar(id);
        String nome = req.nome().trim();
        if (repository.existsByNomeAndIdNot(nome, id)) {
            throw new ConflitoException("Ja existe uma forma de pagamento cadastrada com este nome.");
        }
        aplicar(forma, req, nome);
        return repository.save(forma);
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    private void aplicar(FormaPagamento forma, FormaPagamentoRequest req, String nome) {
        forma.setNome(nome);
        forma.setDescricao(req.descricao().trim());
        if (req.ativo() != null) {
            forma.setAtivo(req.ativo());
        }
    }
}
