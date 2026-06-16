package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.conveniencia.dto.ServicoRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Servico.
 *
 * <p>Garante a unicidade do nome (RN do cadastro, espelhando a restricao
 * {@code uq_servico_nome}) na criacao e na alteracao.
 */
@Service
public class ServicoService {

    private final ServicoRepository repository;

    public ServicoService(ServicoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<Servico> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Servico buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Servico", id));
    }

    @Transactional
    public Servico criar(ServicoRequest req) {
        String nome = req.nome().trim();
        if (repository.existsByNome(nome)) {
            throw new ConflitoException("Ja existe um servico cadastrado com este nome.");
        }
        Servico servico = new Servico();
        aplicar(servico, req, nome);
        return repository.save(servico);
    }

    @Transactional
    public Servico atualizar(Long id, ServicoRequest req) {
        Servico servico = buscar(id);
        String nome = req.nome().trim();
        if (repository.existsByNomeAndIdNot(nome, id)) {
            throw new ConflitoException("Ja existe um servico cadastrado com este nome.");
        }
        aplicar(servico, req, nome);
        return repository.save(servico);
    }

    @Transactional
    public void excluir(Long id) {
        Servico servico = buscar(id);
        repository.delete(servico);
    }

    private void aplicar(Servico servico, ServicoRequest req, String nome) {
        servico.setNome(nome);
        servico.setDescricao(req.descricao());
        servico.setValorPadrao(req.valorPadrao());
        if (req.ativo() != null) {
            servico.setAtivo(req.ativo());
        }
    }
}
