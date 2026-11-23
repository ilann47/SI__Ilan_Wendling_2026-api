package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
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
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final AuditoriaService auditoria;

    public TransportadoraService(TransportadoraRepository repository,
                                 CidadeRepository cidadeRepository,
                                 CondicaoPagamentoRepository condicaoPagamentoRepository,
                                 AuditoriaService auditoria) {
        this.repository = repository;
        this.cidadeRepository = cidadeRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<TransportadoraResponse> listar(
            Long organizacaoId, String nome, String documento, Boolean ativo, Pageable pageable) {
        Specification<Transportadora> filtro = FiltroSpecs.todos(
                FiltroSpecs.igual("organizacaoId", organizacaoId),
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.contemIgnoreCase("documento", documento),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(TransportadoraResponse::from);
    }

    @Transactional(readOnly = true)
    public Transportadora buscar(Long organizacaoId, Long id) {
        return repository.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Transportadora", id));
    }

    @Transactional
    public Transportadora criar(Long organizacaoId, TransportadoraRequest req) {
        String documento = Documentos.soDigitos(req.documento());
        if (repository.existeDocumento(organizacaoId, documento, null)) {
            throw new ConflitoException("Ja existe uma transportadora cadastrada com este documento.");
        }
        Transportadora t = new Transportadora();
        t.setOrganizacaoId(organizacaoId);
        aplicar(organizacaoId, t, req, documento);
        t = repository.saveAndFlush(t);
        registrar(t, "TRANSPORTADORA_CRIADA", null, snapshot(t));
        return t;
    }

    @Transactional
    public Transportadora atualizar(Long organizacaoId, Long id, TransportadoraRequest req) {
        Transportadora t = buscar(organizacaoId, id);
        String documento = Documentos.soDigitos(req.documento());
        if (repository.existeDocumento(organizacaoId, documento, id)) {
            throw new ConflitoException("Ja existe uma transportadora cadastrada com este documento.");
        }
        Map<String, Object> anterior = snapshot(t);
        aplicar(organizacaoId, t, req, documento);
        t = repository.saveAndFlush(t);
        registrar(t, "TRANSPORTADORA_ATUALIZADA", anterior, snapshot(t));
        return t;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        Transportadora t = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(t);
        t.setAtivo(false);
        repository.flush();
        registrar(t, "TRANSPORTADORA_DESATIVADA", anterior, snapshot(t));
    }

    private void aplicar(
            Long organizacaoId, Transportadora t, TransportadoraRequest req, String documento) {
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
        t.setCondicaoPagamento(req.condicaoPagamentoId() != null
                ? buscarCondicao(organizacaoId, req.condicaoPagamentoId()) : null);
        t.setObservacao(req.observacao());
        if (req.ativo() != null) {
            t.setAtivo(req.ativo());
        }
    }

    private Cidade buscarCidade(Long id) {
        return cidadeRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade", id));
    }

    private CondicaoPagamento buscarCondicao(Long organizacaoId, Long id) {
        return condicaoPagamentoRepository.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", id));
    }

    private void registrar(
            Transportadora t,
            String acao,
            Map<String, Object> anterior,
            Map<String, Object> posterior) {
        auditoria.registrar(new RegistrarAuditoriaCommand(
                t.getOrganizacaoId(), acao, "TRANSPORTADORA", t.getId(),
                null, null, null, anterior, posterior, null, null,
                ClasseRetencaoAuditoria.PRIVACIDADE));
    }

    private static Map<String, Object> snapshot(Transportadora t) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nome", t.getNome());
        estado.put("tipo", t.getTipo().name());
        estado.put("cidadeId", t.getCidade() == null ? null : t.getCidade().getId());
        estado.put("condicaoPagamentoId", t.getCondicaoPagamento() == null
                ? null : t.getCondicaoPagamento().getId());
        estado.put("ativo", t.getAtivo());
        return estado;
    }
}
