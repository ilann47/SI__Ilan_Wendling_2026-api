package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.common.web.CatalogSearch;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.instalacoes.dto.CriarSetorRequest;
import com.kaneko.estacionamento.instalacoes.dto.SetorResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetorService {
    private static final String RECURSO = "SETOR";

    private final PatioInstalacaoRepository patios;
    private final SetorRepository setores;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;
    private final VagaRepository vagas;

    public SetorService(
            PatioInstalacaoRepository patios, SetorRepository setores,
            AuditoriaService auditoria, OutboxService outbox, VagaRepository vagas) {
        this.patios = patios;
        this.setores = setores;
        this.auditoria = auditoria;
        this.outbox = outbox;
        this.vagas = vagas;
    }

    @Transactional(readOnly = true)
    public Page<Opcao> listar(Long organizacaoId, String name, Long id, Long patioId, Pageable page) {
        Specification<Setor> patioAtivo = (root, query, cb) -> {
            var sub = query.subquery(Long.class);
            var patio = sub.from(PatioInstalacao.class);
            return cb.exists(sub.select(patio.get("id")).where(
                    cb.equal(patio.get("id"), root.get("patioId")),
                    cb.equal(patio.get("organizacaoId"), organizacaoId), cb.isTrue(patio.get("ativo"))));
        };
        var encontrados = setores.findAll(FiltroSpecs.todos(
                CatalogSearch.ativos(organizacaoId, name, id), FiltroSpecs.igual("patioId", patioId), patioAtivo), page);
        if (encontrados.isEmpty()) return new PageImpl<>(List.of(), page, encontrados.getTotalElements());
        var patioIds = encontrados.stream().map(Setor::getPatioId).collect(Collectors.toSet());
        Map<Long, String> nomes = patios.findAll(FiltroSpecs.todos(
                CatalogSearch.ativos(organizacaoId, null, null), (root, query, cb) -> root.get("id").in(patioIds)))
                .stream().collect(Collectors.toMap(PatioInstalacao::getId, PatioInstalacao::getNome));
        return encontrados.map(s -> new Opcao(s.getId(), s.getNome(), s.getCodigo(), s.getPatioId(), nomes.get(s.getPatioId())));
    }

    public record Opcao(Long id, String name, String code, Long parkingFacilityId, String parkingFacilityName) {}

    public record LinhaGestao(Long id, String name, String code, String parkingFacilityName,
            int capacity, boolean active) {}

    public record DetalheGestao(@com.fasterxml.jackson.annotation.JsonUnwrapped SetorResponse setor,
            String parkingFacilityName) {}

    @Transactional(readOnly = true)
    public Page<LinhaGestao> listarGestao(Long org, String nome, Long id, Pageable page) {
        var encontrados = setores.findAll(FiltroSpecs.todos(
                CatalogSearch.<Setor>daOrganizacao(org, nome), FiltroSpecs.igual("id", id)), page);
        var ids = encontrados.stream().map(Setor::getPatioId).collect(Collectors.toSet());
        Map<Long, String> nomes = ids.isEmpty() ? Map.of() : patios.findAllByOrganizacaoIdAndIdIn(org, ids)
                .stream().collect(Collectors.toMap(PatioInstalacao::getId, PatioInstalacao::getNome));
        return encontrados.map(s -> new LinhaGestao(s.getId(), s.getNome(), s.getCodigo(),
                nomes.get(s.getPatioId()), s.getCapacidade(), s.getAtivo()));
    }

    @Transactional(readOnly = true)
    public DetalheGestao obter(Long org, Long id) {
        Setor setor = setores.findByIdAndOrganizacaoId(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Setor", id));
        PatioInstalacao patio = patios.findByIdAndOrganizacaoId(setor.getPatioId(), org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pátio", setor.getPatioId()));
        return new DetalheGestao(SetorResponse.from(setor), patio.getNome());
    }

    @Transactional
    public DetalheGestao editar(Long org, Long id, long versao, CriarSetorRequest request) {
        Long patioId = setores.buscarPatio(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Setor", id));
        PatioInstalacao patio = patios.buscarParaEdicao(patioId, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pátio", patioId));
        Setor setor = setores.buscarParaEdicao(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Setor", id));
        InstalacaoHttpVersion.exigir(setor.getVersion(), versao);
        String codigo = Setor.normalizarCodigo(request.code());
        if (!codigo.equalsIgnoreCase(setor.getCodigo().trim()) && setores.existeCodigoNormalizado(patioId, codigo)) {
            throw new ConflitoException("Já existe um setor com este código no pátio.");
        }
        if (request.capacity() > setor.getCapacidade()
                && setores.somarCapacidadePorPatio(patioId) - setor.getCapacidade() + request.capacity() > patio.getCapacidadeFisica()) {
            throw new ConflitoException("A soma das capacidades dos setores excede a capacidade física do pátio.");
        }
        if (request.capacity() < setor.getCapacidade() && request.capacity() < vagas.countBySetorId(id)) {
            throw new ConflitoException("A capacidade do setor não pode ser menor que a quantidade de vagas cadastradas.");
        }
        if (!request.categories().containsAll(setor.categorias())
                && !request.categories().containsAll(vagas.buscarCategoriasEmUso(id))) {
            throw new ConflitoException("Não é possível remover uma categoria usada por uma vaga deste setor.");
        }
        var antes = snapshot(setor);
        setor.atualizar(codigo, request.name(), request.capacity(), request.categories());
        setores.flush();
        auditoria.registrar(new RegistrarAuditoriaCommand(
                org, "SETOR_ATUALIZADO", RECURSO, id, null, patioId, null, antes, snapshot(setor),
                versao, setor.getVersion(), ClasseRetencaoAuditoria.CONFIGURACAO));
        return new DetalheGestao(SetorResponse.from(setor), patio.getNome());
    }

    @Transactional
    public SetorResponse criar(
            Long organizacaoId, Long patioId, CriarSetorRequest request) {
        PatioInstalacao patio = patios.findAtivoByIdAndOrganizacaoIdForUpdate(
                        patioId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Patio ativo", patioId));
        String codigo = Setor.normalizarCodigo(request.code());
        if (setores.existeCodigoNormalizado(patioId, codigo)) {
            throw new ConflitoException(
                    "Ja existe um setor com este codigo no patio.");
        }
        long capacidadeAtual = setores.somarCapacidadePorPatio(patioId);
        if (capacidadeAtual + request.capacity() > patio.getCapacidadeFisica()) {
            throw new ConflitoException(
                    "A soma das capacidades dos setores excede a capacidade fisica do patio.");
        }

        Setor setor = Setor.criar(
                organizacaoId, patioId, codigo, request.name(),
                request.capacity(), request.categories());
        setor = setores.saveAndFlush(setor);
        Map<String, Object> estado = snapshot(setor);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "SETOR_CRIADO", RECURSO, setor.getId(),
                null, patioId, null, null, estado, null, setor.getVersion(),
                ClasseRetencaoAuditoria.CONFIGURACAO));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "SetorCriado", organizacaoId, RECURSO, setor.getId(),
                setor.getVersion(), null, 1, estado));
        return SetorResponse.from(setor);
    }

    private static Map<String, Object> snapshot(Setor setor) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("patioId", setor.getPatioId());
        estado.put("codigo", setor.getCodigo());
        estado.put("nome", setor.getNome());
        estado.put("capacidade", setor.getCapacidade());
        estado.put("categorias", setor.categorias().stream().map(Enum::name).toList());
        estado.put("ativo", setor.getAtivo());
        return estado;
    }
}
