package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.logistica.dto.VeiculoFrotaRequest;
import com.kaneko.estacionamento.logistica.dto.VeiculoFrotaResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VeiculoFrotaService {

    private final VeiculoFrotaRepository repository;
    private final AuditoriaService auditoria;

    public VeiculoFrotaService(VeiculoFrotaRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<VeiculoFrotaResponse> listar(
            Long organizacaoId, String placa, String marca, Boolean ativo, Pageable pageable) {
        Specification<VeiculoFrota> filtro = FiltroSpecs.todos(
                FiltroSpecs.igual("organizacaoId", organizacaoId),
                FiltroSpecs.contemIgnoreCase("placa", placa),
                FiltroSpecs.contemIgnoreCase("marca", marca),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(VeiculoFrotaResponse::from);
    }

    @Transactional(readOnly = true)
    public VeiculoFrota buscar(Long organizacaoId, Long id) {
        return repository.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("VeiculoFrota", id));
    }

    @Transactional
    public VeiculoFrota criar(Long organizacaoId, VeiculoFrotaRequest req) {
        String placa = normalizarPlaca(req.placa());
        validarUnicidade(organizacaoId, placa, null);
        VeiculoFrota veiculo = new VeiculoFrota();
        veiculo.setOrganizacaoId(organizacaoId);
        aplicar(veiculo, req, placa);
        veiculo = repository.saveAndFlush(veiculo);
        registrar(veiculo, "VEICULO_FROTA_CRIADO", null, snapshot(veiculo));
        return veiculo;
    }

    @Transactional
    public VeiculoFrota atualizar(Long organizacaoId, Long id, VeiculoFrotaRequest req) {
        VeiculoFrota veiculo = buscar(organizacaoId, id);
        String placa = normalizarPlaca(req.placa());
        validarUnicidade(organizacaoId, placa, id);
        Map<String, Object> anterior = snapshot(veiculo);
        aplicar(veiculo, req, placa);
        veiculo = repository.saveAndFlush(veiculo);
        registrar(veiculo, "VEICULO_FROTA_ATUALIZADO", anterior, snapshot(veiculo));
        return veiculo;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        VeiculoFrota veiculo = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(veiculo);
        veiculo.setAtivo(false);
        repository.flush();
        registrar(veiculo, "VEICULO_FROTA_DESATIVADO", anterior, snapshot(veiculo));
    }

    private void validarUnicidade(Long organizacaoId, String placa, Long idIgnorado) {
        if (repository.existePlaca(organizacaoId, placa, idIgnorado)) {
            throw new ConflitoException("Ja existe um veiculo de frota com esta placa.");
        }
    }

    private void aplicar(VeiculoFrota veiculo, VeiculoFrotaRequest req, String placa) {
        veiculo.setPlaca(placa);
        veiculo.setModelo(req.modelo());
        veiculo.setMarca(req.marca());
        veiculo.setAno(req.ano());
        veiculo.setCapacidade(req.capacidade());
        veiculo.setObservacao(req.observacao());
        if (req.ativo() != null) {
            veiculo.setAtivo(req.ativo());
        }
    }

    private static String normalizarPlaca(String placa) {
        return placa.trim().toUpperCase(Locale.ROOT);
    }

    private void registrar(
            VeiculoFrota veiculo, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior) {
        auditoria.registrar(new RegistrarAuditoriaCommand(
                veiculo.getOrganizacaoId(), acao, "VEICULO_FROTA", veiculo.getId(),
                null, null, null, anterior, posterior, null, null,
                ClasseRetencaoAuditoria.PRIVACIDADE));
    }

    private static Map<String, Object> snapshot(VeiculoFrota veiculo) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("modelo", veiculo.getModelo());
        estado.put("marca", veiculo.getMarca());
        estado.put("ano", veiculo.getAno());
        estado.put("capacidade", veiculo.getCapacidade());
        estado.put("ativo", veiculo.getAtivo());
        return estado;
    }
}
