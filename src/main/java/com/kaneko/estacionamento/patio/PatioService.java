package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.geografia.Cidade;
import com.kaneko.estacionamento.geografia.CidadeRepository;
import com.kaneko.estacionamento.patio.dto.PatioRequest;
import com.kaneko.estacionamento.patio.dto.PatioResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Patio. A ocupacao atual de cada patio e
 * calculada na hora, contando as {@link Movimentacao movimentacoes} ABERTAS
 * vinculadas a ele.
 */
@Service
public class PatioService {

    private final PatioRepository repository;
    private final CidadeRepository cidadeRepository;
    private final MovimentacaoRepository movimentacaoRepository;

    public PatioService(PatioRepository repository,
                        CidadeRepository cidadeRepository,
                        MovimentacaoRepository movimentacaoRepository) {
        this.repository = repository;
        this.cidadeRepository = cidadeRepository;
        this.movimentacaoRepository = movimentacaoRepository;
    }

    @Transactional(readOnly = true)
    public Page<PatioResponse> listar(String nome, Boolean ativo, Pageable pageable) {
        Specification<Patio> filtro = FiltroSpecs.todos(
                (root, query, cb) -> cb.isNull(root.get("organizacaoId")),
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Patio buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Patio", id));
    }

    @Transactional(readOnly = true)
    public PatioResponse buscarResponse(Long id) {
        return toResponse(buscar(id));
    }

    @Transactional
    public PatioResponse criar(PatioRequest req) {
        Patio p = new Patio();
        aplicar(p, req);
        return toResponse(repository.save(p));
    }

    @Transactional
    public PatioResponse atualizar(Long id, PatioRequest req) {
        Patio p = buscar(id);
        aplicar(p, req);
        return toResponse(repository.save(p));
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    /** Monta a resposta com a ocupacao (vagas ocupadas) calculada na hora. */
    private PatioResponse toResponse(Patio p) {
        long ocupadas = movimentacaoRepository.countByPatioIdAndStatus(p.getId(), StatusMovimentacao.ABERTO);
        return PatioResponse.from(p, ocupadas);
    }

    private void aplicar(Patio p, PatioRequest req) {
        p.setNome(req.nome().trim());
        p.setEndereco(req.endereco());
        p.setNumero(req.numero());
        p.setComplemento(req.complemento());
        p.setBairro(req.bairro());
        p.setCep(req.cep());
        p.setCidade(req.cidadeId() != null ? buscarCidade(req.cidadeId()) : null);
        p.setCapacidadeVagas(req.capacidadeVagas());
        p.setCapacidadeOperacional(req.capacidadeVagas());
        p.setObservacao(req.observacao());
        if (req.ativo() != null) {
            p.setAtivo(req.ativo());
        }
    }

    private Cidade buscarCidade(Long id) {
        return cidadeRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade", id));
    }
}
