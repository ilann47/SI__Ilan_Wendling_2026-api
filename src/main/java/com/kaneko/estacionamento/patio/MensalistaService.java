package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.financeiro.ContaReceber;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.financeiro.SituacaoContaReceber;
import com.kaneko.estacionamento.patio.dto.MensalistaRequest;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/**
 * Regras de negocio do contrato de Mensalista.
 *
 * <p>Valida as referencias a cliente e veiculo, a coerencia do periodo
 * ({@code data_fim >= data_inicio}) e a regra de no maximo um contrato ATIVO
 * por veiculo (espelhando {@code uq_mensalista_veiculo_ativo}).
 */
@Service
public class MensalistaService {

    private final MensalistaRepository repository;
    private final ClienteRepository clienteRepository;
    private final VeiculoRepository veiculoRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final AuditoriaService auditoria;

    public MensalistaService(MensalistaRepository repository,
                             ClienteRepository clienteRepository,
                             VeiculoRepository veiculoRepository,
                             ContaReceberRepository contaReceberRepository,
                             AuditoriaService auditoria) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.veiculoRepository = veiculoRepository;
        this.contaReceberRepository = contaReceberRepository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<Mensalista> listar(Long organizacaoId, Pageable pageable) {
        return organizacaoId == null ? repository.findByOrganizacaoIdIsNull(pageable)
                : repository.findByOrganizacaoId(organizacaoId, pageable);
    }

    @Transactional(readOnly = true)
    public Mensalista buscar(Long organizacaoId, Long id) {
        return (organizacaoId == null ? repository.findByIdAndOrganizacaoIdIsNull(id)
                : repository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Mensalista", id));
    }

    @Transactional
    public Mensalista criar(Long organizacaoId, MensalistaRequest req) {
        Cliente cliente = buscarCliente(organizacaoId, req.clienteId());
        Veiculo veiculo = buscarVeiculo(organizacaoId, req.veiculoId());
        StatusMensalista status = req.status() != null ? req.status() : StatusMensalista.ATIVO;
        LocalDate inicio = req.dataInicio() != null ? req.dataInicio() : LocalDate.now();
        validarPeriodo(inicio, req.dataFim());
        if (status == StatusMensalista.ATIVO
                && existeAtivo(organizacaoId, veiculo.getId(), null)) {
            throw new ConflitoException("Ja existe um contrato de mensalista ATIVO para este veiculo.");
        }
        Mensalista mensalista = new Mensalista();
        mensalista.setOrganizacaoId(organizacaoId);
        mensalista.setCliente(cliente);
        mensalista.setVeiculo(veiculo);
        aplicar(mensalista, req, status, inicio);
        mensalista = repository.saveAndFlush(mensalista);
        auditar(mensalista, "MENSALISTA_CRIADO", null, snapshot(mensalista));
        return mensalista;
    }

    @Transactional
    public Mensalista atualizar(Long organizacaoId, Long id, MensalistaRequest req) {
        Mensalista mensalista = buscar(organizacaoId, id);
        Cliente cliente = buscarCliente(organizacaoId, req.clienteId());
        Veiculo veiculo = buscarVeiculo(organizacaoId, req.veiculoId());
        StatusMensalista status = req.status() != null ? req.status() : StatusMensalista.ATIVO;
        LocalDate inicio = req.dataInicio() != null ? req.dataInicio() : mensalista.getDataInicio();
        validarPeriodo(inicio, req.dataFim());
        if (status == StatusMensalista.ATIVO
                && existeAtivo(organizacaoId, veiculo.getId(), id)) {
            throw new ConflitoException("Ja existe um contrato de mensalista ATIVO para este veiculo.");
        }
        Map<String, Object> anterior = snapshot(mensalista);
        mensalista.setCliente(cliente);
        mensalista.setVeiculo(veiculo);
        aplicar(mensalista, req, status, inicio);
        mensalista = repository.saveAndFlush(mensalista);
        auditar(mensalista, "MENSALISTA_ATUALIZADO", anterior, snapshot(mensalista));
        return mensalista;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        Mensalista mensalista = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(mensalista);
        repository.delete(mensalista);
        repository.flush();
        auditar(mensalista, "MENSALISTA_CANCELADO", anterior, snapshot(mensalista));
    }

    /**
     * Gera a mensalidade (titulo a receber) de um contrato ATIVO, vinculada ao
     * mensalista — conecta o patio ao financeiro (requisito 4.30). A competencia
     * e controlada pela data de vencimento informada.
     */
    @Transactional
    public ContaReceber gerarMensalidade(Long organizacaoId, Long id, LocalDate vencimento) {
        Mensalista mensalista = buscar(organizacaoId, id);
        if (mensalista.getStatus() != StatusMensalista.ATIVO) {
            throw new ConflitoException("Apenas contratos ATIVOS podem gerar mensalidade.");
        }
        LocalDate emissao = LocalDate.now();
        LocalDate venc = vencimento != null ? vencimento : emissao;
        if (venc.isBefore(emissao)) {
            throw new ConflitoException("A data de vencimento não pode ser anterior à emissão.");
        }
        ContaReceber conta = new ContaReceber();
        conta.setOrganizacaoId(organizacaoId);
        conta.setCliente(mensalista.getCliente());
        conta.setMensalista(mensalista);
        conta.setValorOriginal(mensalista.getValorMensal());
        conta.setValorTotal(mensalista.getValorMensal());
        conta.setValorRecebido(BigDecimal.ZERO);
        conta.setDataEmissao(emissao);
        conta.setDataVencimento(venc);
        conta.setSituacao(SituacaoContaReceber.PENDENTE);
        return contaReceberRepository.save(conta);
    }

    private void aplicar(Mensalista mensalista, MensalistaRequest req, StatusMensalista status, LocalDate inicio) {
        mensalista.setValorMensal(req.valorMensal());
        mensalista.setDataInicio(inicio);
        mensalista.setDataFim(req.dataFim());
        mensalista.setStatus(status);
    }

    private void validarPeriodo(LocalDate inicio, LocalDate fim) {
        if (fim != null && fim.isBefore(inicio)) {
            throw new ConflitoException("A data de término não pode ser anterior à data de início.");
        }
    }

    private Cliente buscarCliente(Long organizacaoId, Long clienteId) {
        Cliente cliente = (organizacaoId == null
                ? clienteRepository.findByIdAndOrganizacaoIdIsNull(clienteId)
                : clienteRepository.findByIdAndOrganizacaoId(clienteId, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", clienteId));
        if (Boolean.FALSE.equals(cliente.getAtivo())) {
            throw new ConflitoException(
                    "O cliente esta inativo e nao pode ser vinculado a um contrato de mensalista.");
        }
        return cliente;
    }

    private Veiculo buscarVeiculo(Long organizacaoId, Long veiculoId) {
        return (organizacaoId == null ? veiculoRepository.findByIdAndOrganizacaoIdIsNull(veiculoId)
                : veiculoRepository.findByIdAndOrganizacaoId(veiculoId, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veiculo", veiculoId));
    }

    private boolean existeAtivo(Long organizacaoId, Long veiculoId, Long ignorarId) {
        if (organizacaoId == null) return ignorarId == null
                ? repository.existsByVeiculoIdAndStatus(veiculoId, StatusMensalista.ATIVO)
                : repository.existsByVeiculoIdAndStatusAndIdNot(
                        veiculoId, StatusMensalista.ATIVO, ignorarId);
        return ignorarId == null
                ? repository.existsByOrganizacaoIdAndVeiculoIdAndStatus(
                        organizacaoId, veiculoId, StatusMensalista.ATIVO)
                : repository.existsByOrganizacaoIdAndVeiculoIdAndStatusAndIdNot(
                        organizacaoId, veiculoId, StatusMensalista.ATIVO, ignorarId);
    }

    private void auditar(Mensalista mensalista, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior) {
        if (mensalista.getOrganizacaoId() == null) return;
        auditoria.registrar(new RegistrarAuditoriaCommand(mensalista.getOrganizacaoId(), acao,
                "MENSALISTA", mensalista.getId(), null, null, null, anterior, posterior,
                null, mensalista.getVersion(), ClasseRetencaoAuditoria.OPERACIONAL));
    }

    private static Map<String, Object> snapshot(Mensalista mensalista) {
        return Map.of("clienteId", mensalista.getCliente().getId(),
                "veiculoId", mensalista.getVeiculo().getId(),
                "valorMensal", mensalista.getValorMensal(),
                "status", mensalista.getStatus().name());
    }
}
