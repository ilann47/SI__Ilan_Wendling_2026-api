package com.kaneko.estacionamento.pessoas;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.geografia.Cidade;
import com.kaneko.estacionamento.geografia.CidadeRepository;
import com.kaneko.estacionamento.geografia.Pais;
import com.kaneko.estacionamento.geografia.PaisRepository;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pessoas.dto.ClienteRequest;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Cliente.
 *
 * <p>Garante a unicidade do documento (CPF/CNPJ) e valida as referencias
 * opcionais a cidade, nacionalidade (pais) e condicao de pagamento quando
 * informadas.
 */
@Service
public class ClienteService {

    private final ClienteRepository repository;
    private final CidadeRepository cidadeRepository;
    private final PaisRepository paisRepository;
    private final CondicaoPagamentoRepository condicaoPagamentoRepository;

    public ClienteService(ClienteRepository repository,
                          CidadeRepository cidadeRepository,
                          PaisRepository paisRepository,
                          CondicaoPagamentoRepository condicaoPagamentoRepository) {
        this.repository = repository;
        this.cidadeRepository = cidadeRepository;
        this.paisRepository = paisRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
    }

    @Transactional(readOnly = true)
    public Page<Cliente> listar(Pageable pageable) {
        return repository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Cliente buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", id));
    }

    @Transactional
    public Cliente criar(ClienteRequest req) {
        String documento = req.documento().trim();
        if (repository.existsByDocumento(documento)) {
            throw new ConflitoException("Ja existe um cliente cadastrado com este documento.");
        }
        Cliente cliente = new Cliente();
        aplicar(cliente, req, documento);
        return repository.save(cliente);
    }

    @Transactional
    public Cliente atualizar(Long id, ClienteRequest req) {
        Cliente cliente = buscar(id);
        String documento = req.documento().trim();
        if (repository.existsByDocumentoAndIdNot(documento, id)) {
            throw new ConflitoException("Ja existe um cliente cadastrado com este documento.");
        }
        aplicar(cliente, req, documento);
        return repository.save(cliente);
    }

    @Transactional
    public void excluir(Long id) {
        repository.delete(buscar(id));
    }

    private void aplicar(Cliente cliente, ClienteRequest req, String documento) {
        cliente.setNome(req.nome().trim());
        cliente.setApelido(req.apelido());
        cliente.setTipo(req.tipo() != null ? req.tipo() : TipoPessoa.FISICA);
        cliente.setDocumento(documento);
        cliente.setRgInscricaoEstadual(req.rgInscricaoEstadual());
        cliente.setDataNascimento(req.dataNascimento());
        cliente.setSexo(req.sexo());
        cliente.setEstadoCivil(req.estadoCivil());
        cliente.setTelefone(req.telefone());
        cliente.setEmail(req.email());
        cliente.setEndereco(req.endereco());
        cliente.setNumero(req.numero());
        cliente.setComplemento(req.complemento());
        cliente.setBairro(req.bairro());
        cliente.setCep(req.cep());
        cliente.setCidade(req.cidadeId() != null ? buscarCidade(req.cidadeId()) : null);
        cliente.setNacionalidade(req.nacionalidadeId() != null ? buscarPais(req.nacionalidadeId()) : null);
        cliente.setCondicaoPagamento(req.condicaoPagamentoId() != null ? buscarCondicao(req.condicaoPagamentoId()) : null);
        cliente.setLimiteCredito(req.limiteCredito() != null ? req.limiteCredito() : BigDecimal.ZERO);
        cliente.setObservacao(req.observacao());
        if (req.ativo() != null) {
            cliente.setAtivo(req.ativo());
        }
    }

    private Cidade buscarCidade(Long cidadeId) {
        return cidadeRepository.findById(cidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cidade", cidadeId));
    }

    private Pais buscarPais(Long paisId) {
        return paisRepository.findById(paisId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pais", paisId));
    }

    private CondicaoPagamento buscarCondicao(Long condicaoPagamentoId) {
        return condicaoPagamentoRepository.findById(condicaoPagamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", condicaoPagamentoId));
    }
}
