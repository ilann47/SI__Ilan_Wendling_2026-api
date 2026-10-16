package com.kaneko.estacionamento.rh;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.rh.dto.CargoRequest;
import com.kaneko.estacionamento.rh.dto.CargoResponse;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Cargo (unicidade do nome).
 */
@Service
public class CargoService {

    private final CargoRepository repository;
    private final AuditoriaService auditoria;

    public CargoService(CargoRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<CargoResponse> listar(
            Long organizacaoId, String nome, Boolean ativo, Pageable pageable) {
        Specification<Cargo> filtro = FiltroSpecs.todos(
                FiltroSpecs.igual("organizacaoId", organizacaoId),
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(CargoResponse::from);
    }

    @Transactional(readOnly = true)
    public Cargo buscar(Long organizacaoId, Long id) {
        return repository.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cargo", id));
    }

    @Transactional
    public Cargo criar(Long organizacaoId, CargoRequest req) {
        String nome = req.nome().trim();
        if (repository.existeNomeNormalizado(organizacaoId, nome, null)) {
            throw new ConflitoException("Ja existe um cargo cadastrado com este nome.");
        }
        Cargo cargo = new Cargo();
        cargo.setOrganizacaoId(organizacaoId);
        aplicar(cargo, req, nome);
        cargo = repository.saveAndFlush(cargo);
        registrar(cargo, "CARGO_CRIADO", null, snapshot(cargo));
        return cargo;
    }

    @Transactional
    public Cargo atualizar(Long organizacaoId, Long id, CargoRequest req) {
        Cargo cargo = buscar(organizacaoId, id);
        String nome = req.nome().trim();
        if (repository.existeNomeNormalizado(organizacaoId, nome, id)) {
            throw new ConflitoException("Ja existe um cargo cadastrado com este nome.");
        }
        Map<String, Object> anterior = snapshot(cargo);
        aplicar(cargo, req, nome);
        cargo = repository.saveAndFlush(cargo);
        registrar(cargo, "CARGO_ATUALIZADO", anterior, snapshot(cargo));
        return cargo;
    }

    @Transactional
    public void excluir(Long organizacaoId, Long id) {
        Cargo cargo = buscar(organizacaoId, id);
        Map<String, Object> anterior = snapshot(cargo);
        cargo.setAtivo(false);
        repository.flush();
        registrar(cargo, "CARGO_DESATIVADO", anterior, snapshot(cargo));
    }

    private void aplicar(Cargo cargo, CargoRequest req, String nome) {
        cargo.setNome(nome);
        cargo.setDescricao(req.descricao());
        cargo.setSalarioBase(req.salarioBase() != null ? req.salarioBase() : BigDecimal.ZERO);
        cargo.setCargaHoraria(req.cargaHoraria());
        cargo.setRequerCnh(req.requerCnh() != null ? req.requerCnh() : Boolean.FALSE);
        if (req.ativo() != null) {
            cargo.setAtivo(req.ativo());
        }
    }

    private void registrar(
            Cargo cargo,
            String acao,
            Map<String, Object> anterior,
            Map<String, Object> posterior) {
        auditoria.registrar(new RegistrarAuditoriaCommand(
                cargo.getOrganizacaoId(), acao, "CARGO", cargo.getId(),
                null, null, null, anterior, posterior, null, null,
                ClasseRetencaoAuditoria.RH));
    }

    private static Map<String, Object> snapshot(Cargo cargo) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nome", cargo.getNome());
        estado.put("descricao", cargo.getDescricao());
        estado.put("salarioBase", cargo.getSalarioBase());
        estado.put("cargaHoraria", cargo.getCargaHoraria());
        estado.put("requerCnh", cargo.getRequerCnh());
        estado.put("ativo", cargo.getAtivo());
        return estado;
    }
}
