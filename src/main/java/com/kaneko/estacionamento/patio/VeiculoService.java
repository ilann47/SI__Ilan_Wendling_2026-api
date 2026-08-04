package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.patio.dto.VeiculoRequest;
import com.kaneko.estacionamento.patio.dto.VeiculoResponse;
import com.kaneko.estacionamento.pessoas.Cliente;
import com.kaneko.estacionamento.pessoas.ClienteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/**
 * Regras de negocio do cadastro de Veiculo.
 *
 * <p>Garante a unicidade da placa (normalizada para maiusculas) e valida o
 * cliente referenciado quando informado.
 */
@Service
public class VeiculoService {

    private final VeiculoRepository repository;
    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoria;

    public VeiculoService(VeiculoRepository repository, ClienteRepository clienteRepository,
            AuditoriaService auditoria) {
        this.repository = repository;
        this.clienteRepository = clienteRepository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<VeiculoResponse> listar(Long organizacaoId, String placa, Long clienteId,
            Boolean ativo, Pageable pageable) {
        Specification<Veiculo> filtro = FiltroSpecs.todos(
                organizacaoId == null
                        ? (root, query, cb) -> cb.isNull(root.get("organizacaoId"))
                        : FiltroSpecs.igual("organizacaoId", organizacaoId),
                FiltroSpecs.contemIgnoreCase("placa", placa),
                FiltroSpecs.fkId("cliente", clienteId),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(VeiculoResponse::from);
    }

    @Transactional(readOnly = true)
    public Veiculo buscar(Long organizacaoId, Long id) {
        return (organizacaoId == null ? repository.findByIdAndOrganizacaoIdIsNull(id)
                : repository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Veiculo", id));
    }

    @Transactional
    public Veiculo criar(Long organizacaoId, VeiculoRequest req) {
        String placa = normalizarPlaca(req.placa());
        boolean existe = organizacaoId == null
                ? repository.existsByOrganizacaoIdIsNullAndPlaca(placa)
                : repository.existsByOrganizacaoIdAndPlaca(organizacaoId, placa);
        if (existe) {
            throw new ConflitoException("Ja existe um veiculo cadastrado com esta placa.");
        }
        Veiculo veiculo = new Veiculo();
        veiculo.setOrganizacaoId(organizacaoId);
        aplicar(organizacaoId, veiculo, req, placa);
        veiculo = repository.saveAndFlush(veiculo);
        auditar(veiculo, "VEICULO_CRIADO", null, snapshot(veiculo));
        return veiculo;
    }

    @Transactional
    public Veiculo atualizar(Long organizacaoId, Long id, VeiculoRequest req) {
        Veiculo veiculo = buscar(organizacaoId, id);
        String placa = normalizarPlaca(req.placa());
        boolean existe = organizacaoId == null
                ? repository.existsByOrganizacaoIdIsNullAndPlacaAndIdNot(placa, id)
                : repository.existsByOrganizacaoIdAndPlacaAndIdNot(organizacaoId, placa, id);
        if (existe) {
            throw new ConflitoException("Ja existe um veiculo cadastrado com esta placa.");
        }
        Map<String, Object> anterior = snapshot(veiculo);
        aplicar(organizacaoId, veiculo, req, placa);
        veiculo = repository.saveAndFlush(veiculo);
        auditar(veiculo, "VEICULO_ATUALIZADO", anterior, snapshot(veiculo));
        return veiculo;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        Veiculo veiculo = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(veiculo);
        veiculo.setAtivo(false);
        repository.flush();
        auditar(veiculo, "VEICULO_DESATIVADO", anterior, snapshot(veiculo));
    }

    private void aplicar(Long organizacaoId, Veiculo veiculo, VeiculoRequest req, String placa) {
        veiculo.setPlaca(placa);
        veiculo.setModelo(req.modelo());
        veiculo.setCor(req.cor());
        veiculo.setTipo(req.tipo() != null ? req.tipo() : TipoVeiculo.CARRO);
        veiculo.setCliente(req.clienteId() != null
                ? buscarCliente(organizacaoId, req.clienteId()) : null);
        veiculo.setObservacao(req.observacao());
        if (req.ativo() != null) {
            veiculo.setAtivo(req.ativo());
        }
    }

    private String normalizarPlaca(String placa) {
        return placa.trim().toUpperCase();
    }

    private Cliente buscarCliente(Long organizacaoId, Long clienteId) {
        return (organizacaoId == null
                ? clienteRepository.findByIdAndOrganizacaoIdIsNull(clienteId)
                : clienteRepository.findByIdAndOrganizacaoId(clienteId, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente", clienteId));
    }

    private void auditar(Veiculo veiculo, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior) {
        if (veiculo.getOrganizacaoId() == null) return;
        auditoria.registrar(new RegistrarAuditoriaCommand(veiculo.getOrganizacaoId(), acao,
                "VEICULO", veiculo.getId(), null, null, null, anterior, posterior,
                null, veiculo.getVersion(), ClasseRetencaoAuditoria.OPERACIONAL));
    }

    private static Map<String, Object> snapshot(Veiculo veiculo) {
        return Map.of("placa", veiculo.getPlaca(), "tipo", veiculo.getTipo().name(),
                "clienteId", veiculo.getCliente() == null ? 0L : veiculo.getCliente().getId(),
                "ativo", veiculo.getAtivo());
    }
}
