package com.kaneko.estacionamento.fornecedor;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.util.Documentos;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.fornecedor.dto.EmailRequest;
import com.kaneko.estacionamento.fornecedor.dto.FornecedorRequest;
import com.kaneko.estacionamento.fornecedor.dto.FornecedorResponse;
import com.kaneko.estacionamento.fornecedor.dto.TelefoneRequest;
import com.kaneko.estacionamento.geografia.Cidade;
import com.kaneko.estacionamento.geografia.CidadeRepository;
import com.kaneko.estacionamento.logistica.Transportadora;
import com.kaneko.estacionamento.logistica.TransportadoraRepository;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pessoas.TipoPessoa;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do fornecedor (agregado com e-mails e telefones).
 *
 * <p>Garante a unicidade do documento, valida as referencias opcionais
 * (cidade, nacionalidade, condicao de pagamento) e a regra de no maximo um
 * contato principal por colecao. A sincronizacao dos contatos muta a colecao
 * (clear/add) para preservar o {@code orphanRemoval}.
 */
@Service
public class FornecedorService {

    private final FornecedorRepository repository;
    private final CidadeRepository cidadeRepository;
    private final CondicaoPagamentoRepository condicaoPagamentoRepository;
    private final TransportadoraRepository transportadoraRepository;

    public FornecedorService(FornecedorRepository repository,
                             CidadeRepository cidadeRepository,
                             CondicaoPagamentoRepository condicaoPagamentoRepository,
                             TransportadoraRepository transportadoraRepository) {
        this.repository = repository;
        this.cidadeRepository = cidadeRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.transportadoraRepository = transportadoraRepository;
    }

    @Transactional(readOnly = true)
    public Page<FornecedorResponse> listar(String nome, String documento, Boolean ativo, Pageable pageable) {
        Specification<Fornecedor> filtro = FiltroSpecs.todos(
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.contemIgnoreCase("documento", documento),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(FornecedorResponse::from);
    }

    @Transactional(readOnly = true)
    public Fornecedor buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fornecedor", id));
    }

    @Transactional
    public Fornecedor criar(FornecedorRequest req) {
        String documento = Documentos.soDigitos(req.documento());
        if (repository.existsByDocumento(documento)) {
            throw new ConflitoException("Ja existe um fornecedor cadastrado com este documento.");
        }
        Fornecedor fornecedor = new Fornecedor();
        aplicar(fornecedor, req, documento);
        return repository.save(fornecedor);
    }

    @Transactional
    public Fornecedor atualizar(Long id, FornecedorRequest req) {
        Fornecedor fornecedor = buscar(id);
        String documento = Documentos.soDigitos(req.documento());
        if (repository.existsByDocumentoAndIdNot(documento, id)) {
            throw new ConflitoException("Ja existe um fornecedor cadastrado com este documento.");
        }
        aplicar(fornecedor, req, documento);
        return repository.save(fornecedor);
    }

    @Transactional
    public void excluir(Long id) {
        buscar(id).setAtivo(false);
    }

    private void aplicar(Fornecedor f, FornecedorRequest req, String documento) {
        f.setNome(req.nome().trim());
        f.setNomeFantasia(req.nomeFantasia());
        f.setApelido(req.apelido());
        f.setTipo(req.tipo() != null ? req.tipo() : TipoPessoa.JURIDICA);
        f.setDocumento(documento);
        f.setRgInscricaoEstadual(req.rgInscricaoEstadual());
        f.setEndereco(req.endereco());
        f.setNumero(req.numero());
        f.setComplemento(req.complemento());
        f.setBairro(req.bairro());
        f.setCep(req.cep());
        f.setCidade(req.cidadeId() != null ? buscarCidade(req.cidadeId()) : null);
        f.setCondicaoPagamento(req.condicaoPagamentoId() != null ? buscarCondicao(req.condicaoPagamentoId()) : null);
        f.setTransportadora(req.transportadoraId() != null ? buscarTransportadora(req.transportadoraId()) : null);
        f.setLimiteCredito(req.limiteCredito() != null ? req.limiteCredito() : BigDecimal.ZERO);
        f.setObservacao(req.observacao());
        if (req.ativo() != null) {
            f.setAtivo(req.ativo());
        }
        sincronizarEmails(f, req.emails());
        sincronizarTelefones(f, req.telefones());
    }

    private void sincronizarEmails(Fornecedor f, List<EmailRequest> emails) {
        f.getEmails().clear();
        if (emails == null) {
            return;
        }
        long principais = emails.stream().filter(e -> Boolean.TRUE.equals(e.principal())).count();
        if (principais > 1) {
            throw new ConflitoException("Apenas um e-mail pode ser marcado como principal.");
        }
        for (EmailRequest er : emails) {
            FornecedorEmail e = new FornecedorEmail();
            e.setFornecedor(f);
            e.setEmail(er.email().trim());
            e.setTipo(er.tipo());
            e.setPrincipal(er.principal() != null ? er.principal() : Boolean.FALSE);
            f.getEmails().add(e);
        }
    }

    private void sincronizarTelefones(Fornecedor f, List<TelefoneRequest> telefones) {
        f.getTelefones().clear();
        if (telefones == null) {
            return;
        }
        long principais = telefones.stream().filter(t -> Boolean.TRUE.equals(t.principal())).count();
        if (principais > 1) {
            throw new ConflitoException("Apenas um telefone pode ser marcado como principal.");
        }
        for (TelefoneRequest tr : telefones) {
            FornecedorTelefone t = new FornecedorTelefone();
            t.setFornecedor(f);
            t.setTelefone(tr.telefone().trim());
            t.setTipo(tr.tipo());
            t.setPrincipal(tr.principal() != null ? tr.principal() : Boolean.FALSE);
            f.getTelefones().add(t);
        }
    }

    private Cidade buscarCidade(Long id) {
        return cidadeRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade", id));
    }

    private CondicaoPagamento buscarCondicao(Long id) {
        return condicaoPagamentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", id));
    }

    private Transportadora buscarTransportadora(Long id) {
        return transportadoraRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Transportadora", id));
    }
}
