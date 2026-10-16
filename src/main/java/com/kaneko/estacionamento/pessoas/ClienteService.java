package com.kaneko.estacionamento.pessoas;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.util.Documentos;
import com.kaneko.estacionamento.geografia.Cidade;
import com.kaneko.estacionamento.geografia.CidadeRepository;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.pagamento.CondicaoPagamento;
import com.kaneko.estacionamento.pagamento.CondicaoPagamentoRepository;
import com.kaneko.estacionamento.pessoas.dto.ClienteRequest;
import com.kaneko.estacionamento.pessoas.dto.ClienteResponse;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    private final CondicaoPagamentoRepository condicaoPagamentoRepository;
    private final AuditoriaService auditoria;

    public ClienteService(ClienteRepository repository,
                          CidadeRepository cidadeRepository,
                          CondicaoPagamentoRepository condicaoPagamentoRepository,
                          AuditoriaService auditoria) {
        this.repository = repository;
        this.cidadeRepository = cidadeRepository;
        this.condicaoPagamentoRepository = condicaoPagamentoRepository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<ClienteResponse> listar(
            Long organizacaoId,
            String nome,
            String documento,
            Boolean ativo,
            Pageable pageable) {
        Specification<Cliente> filtro = FiltroSpecs.todos(
                FiltroSpecs.igual("organizacaoId", organizacaoId),
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.contemIgnoreCase("documento", documento),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(ClienteResponse::from);
    }

    @Transactional(readOnly = true)
    public Cliente buscar(Long organizacaoId, Long id) {
        return repository.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", id));
    }

    @Transactional
    public Cliente criar(Long organizacaoId, ClienteRequest req) {
        String documento = Documentos.soDigitos(req.documento());
        if (repository.existeDocumento(organizacaoId, documento, null)) {
            throw new ConflitoException("Ja existe um cliente cadastrado com este documento.");
        }
        Cliente cliente = new Cliente();
        cliente.setOrganizacaoId(organizacaoId);
        aplicar(organizacaoId, cliente, req, documento);
        cliente = repository.saveAndFlush(cliente);
        registrar(cliente, "CLIENTE_CRIADO", null, snapshot(cliente));
        return cliente;
    }

    @Transactional
    public Cliente atualizar(Long organizacaoId, Long id, ClienteRequest req) {
        Cliente cliente = buscar(organizacaoId, id);
        String documento = Documentos.soDigitos(req.documento());
        if (repository.existeDocumento(organizacaoId, documento, id)) {
            throw new ConflitoException("Ja existe um cliente cadastrado com este documento.");
        }
        Map<String, Object> anterior = snapshot(cliente);
        aplicar(organizacaoId, cliente, req, documento);
        cliente = repository.saveAndFlush(cliente);
        registrar(cliente, "CLIENTE_ATUALIZADO", anterior, snapshot(cliente));
        return cliente;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        Cliente cliente = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(cliente);
        cliente.setAtivo(false);
        repository.flush();
        registrar(cliente, "CLIENTE_DESATIVADO", anterior, snapshot(cliente));
    }

    private void aplicar(
            Long organizacaoId,
            Cliente cliente,
            ClienteRequest req,
            String documento) {
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
        cliente.setCondicaoPagamento(req.condicaoPagamentoId() != null
                ? buscarCondicao(organizacaoId, req.condicaoPagamentoId()) : null);
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

    private CondicaoPagamento buscarCondicao(Long organizacaoId, Long condicaoPagamentoId) {
        return condicaoPagamentoRepository.findByIdAndOrganizacaoId(
                        condicaoPagamentoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("CondicaoPagamento", condicaoPagamentoId));
    }

    private void registrar(
            Cliente cliente,
            String acao,
            Map<String, Object> anterior,
            Map<String, Object> posterior) {
        auditoria.registrar(new RegistrarAuditoriaCommand(
                cliente.getOrganizacaoId(), acao, "CLIENTE", cliente.getId(),
                null, null, null, anterior, posterior, null, null,
                ClasseRetencaoAuditoria.PRIVACIDADE));
    }

    private static Map<String, Object> snapshot(Cliente cliente) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nome", cliente.getNome());
        estado.put("tipo", cliente.getTipo().name());
        estado.put("cidadeId", cliente.getCidade() == null ? null : cliente.getCidade().getId());
        estado.put("condicaoPagamentoId", cliente.getCondicaoPagamento() == null
                ? null : cliente.getCondicaoPagamento().getId());
        estado.put("limiteCredito", cliente.getLimiteCredito());
        estado.put("ativo", cliente.getAtivo());
        return estado;
    }
}
