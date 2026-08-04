package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.patio.dto.TarifaRequest;
import com.kaneko.estacionamento.patio.dto.TarifaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/**
 * Regras de negocio do cadastro de Tarifa.
 */
@Service
public class TarifaService {

    /** Tolerancia padrao (minutos) quando nao informada — espelha o DEFAULT do schema. */
    private static final int TOLERANCIA_PADRAO = 15;

    private final TarifaRepository repository;
    private final AuditoriaService auditoria;

    public TarifaService(TarifaRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<TarifaResponse> listar(Long organizacaoId, String descricao, Boolean ativo,
            Pageable pageable) {
        Specification<Tarifa> filtro = FiltroSpecs.todos(
                organizacaoId == null
                        ? (root, query, cb) -> cb.isNull(root.get("organizacaoId"))
                        : FiltroSpecs.igual("organizacaoId", organizacaoId),
                FiltroSpecs.contemIgnoreCase("descricao", descricao),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(TarifaResponse::from);
    }

    @Transactional(readOnly = true)
    public Tarifa buscar(Long organizacaoId, Long id) {
        return (organizacaoId == null ? repository.findByIdAndOrganizacaoIdIsNull(id)
                : repository.findByIdAndOrganizacaoId(id, organizacaoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tarifa", id));
    }

    @Transactional
    public Tarifa criar(Long organizacaoId, TarifaRequest req) {
        Tarifa tarifa = new Tarifa();
        tarifa.setOrganizacaoId(organizacaoId);
        aplicar(tarifa, req);
        tarifa = repository.saveAndFlush(tarifa);
        auditar(tarifa, "TARIFA_CRIADA", null, snapshot(tarifa));
        return tarifa;
    }

    @Transactional
    public Tarifa atualizar(Long organizacaoId, Long id, TarifaRequest req) {
        Tarifa tarifa = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(tarifa);
        aplicar(tarifa, req);
        tarifa = repository.saveAndFlush(tarifa);
        auditar(tarifa, "TARIFA_ATUALIZADA", anterior, snapshot(tarifa));
        return tarifa;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        Tarifa tarifa = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(tarifa);
        tarifa.setAtivo(false);
        repository.flush();
        auditar(tarifa, "TARIFA_DESATIVADA", anterior, snapshot(tarifa));
    }

    private void aplicar(Tarifa tarifa, TarifaRequest req) {
        tarifa.setDescricao(req.descricao().trim());
        tarifa.setValorHora(req.valorHora());
        tarifa.setValorDiaria(req.valorDiaria());
        tarifa.setToleranciaMin(req.toleranciaMin() != null ? req.toleranciaMin() : TOLERANCIA_PADRAO);
        tarifa.setValorMensalidade(req.valorMensalidade());
        if (req.ativo() != null) {
            tarifa.setAtivo(req.ativo());
        }
    }

    private void auditar(Tarifa tarifa, String acao,
            Map<String, Object> anterior, Map<String, Object> posterior) {
        if (tarifa.getOrganizacaoId() == null) return;
        auditoria.registrar(new RegistrarAuditoriaCommand(tarifa.getOrganizacaoId(), acao,
                "TARIFA", tarifa.getId(), null, null, null, anterior, posterior,
                null, tarifa.getVersion(), ClasseRetencaoAuditoria.CONFIGURACAO));
    }

    private static Map<String, Object> snapshot(Tarifa tarifa) {
        return Map.of("descricao", tarifa.getDescricao(), "valorHora", tarifa.getValorHora(),
                "valorDiaria", tarifa.getValorDiaria(),
                "valorMensalidade", tarifa.getValorMensalidade(),
                "toleranciaMin", tarifa.getToleranciaMin(), "ativo", tarifa.getAtivo());
    }
}
