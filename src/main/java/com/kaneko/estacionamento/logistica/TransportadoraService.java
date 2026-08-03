package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.util.Documentos;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.geografia.Cidade;
import com.kaneko.estacionamento.geografia.CidadeRepository;
import com.kaneko.estacionamento.logistica.dto.TransportadoraRequest;
import com.kaneko.estacionamento.logistica.dto.TransportadoraResponse;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Transportadora: unicidade do documento
 * (normalizado para so-digitos) e validacao das referencias opcionais.
 */
@Service
public class TransportadoraService {

    private final TransportadoraRepository repository;
    private final CidadeRepository cidadeRepository;
    private final CondicaoPagamentoRepository condicaoPagamentoRepository;

    public TransportadoraService(TransportadoraRepository repository,
                                 CidadeRepository cidadeRepository,
                                 CondicaoPagamentoRepository condicaoPagamentoRepository) {
        this.repository = repository;
        this.cidadeRepository = cidadeRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
    }

    @Transactional(readOnly = true)
    public Page<TransportadoraResponse> listar(String nome, String documento, Boolean ativo, Pageable pageable) {
        Specification<Transportadora> filtro = FiltroSpecs.todos(
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.contemIgnoreCase("documento", documento),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(TransportadoraResponse::from);
    }

    @Transactional(readOnly = true)
    public Transportadora buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Transportadora", id));
    }

    @Transactional
    public Transportadora criar(TransportadoraRequest req) {
        String documento = Documentos.soDigitos(req.documento());
        if (repository.existsByDocumento(documento)) {
            throw new ConflitoException("Ja existe uma transportadora cadastrada com este documento.");
        }
        Transportadora t = new Transportadora();
        aplicar(t, req, documento);
        return repository.save(t);
    }

    @Transactional
    public Transportadora atualizar(Long id, TransportadoraRequest req) {
        Transportadora t = buscar(id);
        String documento = Documentos.soDigitos(req.documento());
        if (repository.existsByDocumentoAndIdNot(documento, id)) {
            throw new ConflitoException("Ja existe uma transportadora cadastrada com este documento.");
        }
        aplicar(t, req, documento);
        return repository.save(t);
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    private void aplicar(Transportadora t, TransportadoraRequest req, String documento) {
        t.setNome(req.nome().trim());
        t.setNomeFantasia(req.nomeFantasia());
        t.setTipo(req.tipo() != null ? req.tipo() : TipoPessoa.JURIDICA);
        t.setDocumento(documento);
        t.setRgInscricaoEstadual(req.rgInscricaoEstadual());
        t.setTelefone(req.telefone());
        t.setEmail(req.email());
        t.setEndereco(req.endereco());
        t.setNumero(req.numero());
        t.setComplemento(req.complemento());
        t.setBairro(req.bairro());
        t.setCep(req.cep());
        t.setCidade(req.cidadeId() != null ? buscarCidade(req.cidadeId()) : null);
        t.setCondicaoPagamento(req.condicaoPagamentoId() != null ? buscarCondicao(req.condicaoPagamentoId()) : null);
        t.setObservacao(req.observacao());
        if (req.ativo() != null) {
            t.setAtivo(req.ativo());
        }
    }

    private Cidade buscarCidade(Long id) {
        return cidadeRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade", id));
    }

    private CondicaoPagamento buscarCondicao(Long id) {
        return condicaoPagamentoRepository.findByIdAndOrganizacaoIdIsNull(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", id));
    }
}
