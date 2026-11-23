package com.kaneko.estacionamento.logistica;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.logistica.dto.TransportadoraVeiculoFrotaRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransportadoraVeiculoFrotaService {

    private final TransportadoraVeiculoFrotaRepository repository;
    private final TransportadoraRepository transportadoras;
    private final VeiculoFrotaRepository veiculos;
    private final AuditoriaService auditoria;

    public TransportadoraVeiculoFrotaService(
            TransportadoraVeiculoFrotaRepository repository,
            TransportadoraRepository transportadoras,
            VeiculoFrotaRepository veiculos,
            AuditoriaService auditoria) {
        this.repository = repository;
        this.transportadoras = transportadoras;
        this.veiculos = veiculos;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<TransportadoraVeiculoFrota> listar(Long organizacaoId, Pageable pageable) {
        return repository.findByOrganizacaoId(organizacaoId, pageable);
    }

    @Transactional(readOnly = true)
    public TransportadoraVeiculoFrota buscar(Long organizacaoId, Long id) {
        return repository.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("TransportadoraVeiculoFrota", id));
    }

    @Transactional
    public TransportadoraVeiculoFrota criar(
            Long organizacaoId, TransportadoraVeiculoFrotaRequest req) {
        Transportadora transportadora = buscarTransportadora(organizacaoId, req.transportadoraId());
        VeiculoFrota veiculo = buscarVeiculo(organizacaoId, req.veiculoFrotaId());
        validarUnicidade(organizacaoId, transportadora.getId(), veiculo.getId(), null);
        TransportadoraVeiculoFrota vinculo = new TransportadoraVeiculoFrota();
        vinculo.setOrganizacaoId(organizacaoId);
        vinculo.setTransportadora(transportadora);
        vinculo.setVeiculoFrota(veiculo);
        vinculo = repository.saveAndFlush(vinculo);
        registrar(vinculo, "FROTA_TRANSPORTADORA_VINCULADA", null, snapshot(vinculo));
        return vinculo;
    }

    @Transactional
    public TransportadoraVeiculoFrota atualizar(
            Long organizacaoId, Long id, TransportadoraVeiculoFrotaRequest req) {
        TransportadoraVeiculoFrota vinculo = buscar(organizacaoId, id);
        Transportadora transportadora = buscarTransportadora(organizacaoId, req.transportadoraId());
        VeiculoFrota veiculo = buscarVeiculo(organizacaoId, req.veiculoFrotaId());
        validarUnicidade(organizacaoId, transportadora.getId(), veiculo.getId(), id);
        Map<String, Object> anterior = snapshot(vinculo);
        vinculo.setTransportadora(transportadora);
        vinculo.setVeiculoFrota(veiculo);
        vinculo = repository.saveAndFlush(vinculo);
        registrar(vinculo, "FROTA_TRANSPORTADORA_ATUALIZADA", anterior, snapshot(vinculo));
        return vinculo;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        TransportadoraVeiculoFrota vinculo = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(vinculo);
        vinculo.setAtivo(false);
        repository.flush();
        registrar(vinculo, "FROTA_TRANSPORTADORA_DESATIVADA", anterior, snapshot(vinculo));
    }

    private void validarUnicidade(
            Long organizacaoId, Long transportadoraId, Long veiculoId, Long idIgnorado) {
        if (repository.existePar(organizacaoId, transportadoraId, veiculoId, idIgnorado)) {
            throw new ConflitoException("Este veiculo ja esta vinculado a esta transportadora.");
        }
    }

    private Transportadora buscarTransportadora(Long organizacaoId, Long id) {
        return transportadoras.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Transportadora", id));
    }

    private VeiculoFrota buscarVeiculo(Long organizacaoId, Long id) {
        return veiculos.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("VeiculoFrota", id));
    }

    private void registrar(
            TransportadoraVeiculoFrota vinculo, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior) {
        auditoria.registrar(new RegistrarAuditoriaCommand(
                vinculo.getOrganizacaoId(), acao, "TRANSPORTADORA_VEICULO_FROTA", vinculo.getId(),
                null, null, null, anterior, posterior, null, null,
                ClasseRetencaoAuditoria.CONFIGURACAO));
    }

    private static Map<String, Object> snapshot(TransportadoraVeiculoFrota vinculo) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("transportadoraId", vinculo.getTransportadora().getId());
        estado.put("veiculoFrotaId", vinculo.getVeiculoFrota().getId());
        estado.put("ativo", vinculo.getAtivo());
        return estado;
    }
}
