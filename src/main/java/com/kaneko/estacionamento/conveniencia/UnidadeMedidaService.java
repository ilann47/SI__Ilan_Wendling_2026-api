package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.conveniencia.dto.UnidadeMedidaRequest;
import com.kaneko.estacionamento.conveniencia.dto.UnidadeMedidaResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de UnidadeMedida.
 *
 * <p>Garante a unicidade da sigla (RN do cadastro, espelhando a restricao
 * {@code uq_unidade_medida_organizacao_sigla}) na criacao e na alteracao. A sigla e normalizada
 * para maiusculas antes da validacao e da persistencia.
 */
@Service
public class UnidadeMedidaService {

    private final UnidadeMedidaRepository repository;
    private final AuditoriaService auditoria;

    public UnidadeMedidaService(UnidadeMedidaRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<UnidadeMedidaResponse> listar(
            Long organizacaoId, String nome, Boolean ativo, Pageable pageable) {
        Specification<UnidadeMedida> filtro = FiltroSpecs.todos(
                FiltroSpecs.igual("organizacaoId", organizacaoId),
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(UnidadeMedidaResponse::from);
    }

    @Transactional(readOnly = true)
    public UnidadeMedida buscar(Long organizacaoId, Long id) {
        return repository.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("UnidadeMedida", id));
    }

    @Transactional
    public UnidadeMedida criar(Long organizacaoId, UnidadeMedidaRequest req) {
        String nome = req.nome().trim();
        String sigla = req.sigla().trim().toUpperCase(Locale.ROOT);
        if (repository.existeNomeNormalizado(organizacaoId, nome, null)) {
            throw new ConflitoException("Ja existe uma unidade de medida cadastrada com este nome.");
        }
        if (repository.existeSiglaNormalizada(organizacaoId, sigla, null)) {
            throw new ConflitoException("Ja existe uma unidade de medida cadastrada com esta sigla.");
        }
        UnidadeMedida unidade = new UnidadeMedida();
        unidade.setOrganizacaoId(organizacaoId);
        aplicar(unidade, req, nome, sigla);
        unidade = repository.saveAndFlush(unidade);
        registrar(unidade, "UNIDADE_MEDIDA_CRIADA", null, snapshot(unidade),
                null, unidade.getVersion());
        return unidade;
    }

    @Transactional
    public UnidadeMedida atualizar(
            Long organizacaoId, Long id, long versaoEsperada, UnidadeMedidaRequest req) {
        UnidadeMedida unidade = buscar(organizacaoId, id);
        conferirVersao(versaoEsperada, unidade.getVersion());
        String nome = req.nome().trim();
        String sigla = req.sigla().trim().toUpperCase(Locale.ROOT);
        if (repository.existeNomeNormalizado(organizacaoId, nome, id)) {
            throw new ConflitoException("Ja existe uma unidade de medida cadastrada com este nome.");
        }
        if (repository.existeSiglaNormalizada(organizacaoId, sigla, id)) {
            throw new ConflitoException("Ja existe uma unidade de medida cadastrada com esta sigla.");
        }
        Map<String, Object> anterior = snapshot(unidade);
        Long versaoAnterior = unidade.getVersion();
        aplicar(unidade, req, nome, sigla);
        unidade = repository.saveAndFlush(unidade);
        registrar(unidade, "UNIDADE_MEDIDA_ATUALIZADA", anterior, snapshot(unidade),
                versaoAnterior, unidade.getVersion());
        return unidade;
    }

    @Transactional
    public UnidadeMedida excluir(Long organizacaoId, Long id, long versaoEsperada) {
        UnidadeMedida unidade = buscar(organizacaoId, id);
        conferirVersao(versaoEsperada, unidade.getVersion());
        Map<String, Object> anterior = snapshot(unidade);
        Long versaoAnterior = unidade.getVersion();
        unidade.setAtivo(false);
        repository.flush();
        registrar(unidade, "UNIDADE_MEDIDA_DESATIVADA", anterior, snapshot(unidade),
                versaoAnterior, unidade.getVersion());
        return unidade;
    }

    private void aplicar(
            UnidadeMedida unidade, UnidadeMedidaRequest req, String nome, String sigla) {
        unidade.setNome(nome);
        unidade.setSigla(sigla);
        if (req.ativo() != null) {
            unidade.setAtivo(req.ativo());
        }
    }

    private void registrar(
            UnidadeMedida unidade, String acao, Map<String, Object> anterior,
            Map<String, Object> posterior, Long versaoAnterior, Long versaoPosterior) {
        auditoria.registrar(new RegistrarAuditoriaCommand(
                unidade.getOrganizacaoId(), acao, "UNIDADE_MEDIDA", unidade.getId(),
                null, null, null, anterior, posterior, versaoAnterior, versaoPosterior,
                ClasseRetencaoAuditoria.CONFIGURACAO));
    }

    private static void conferirVersao(long esperada, Long atual) {
        if (atual == null || esperada != atual) {
            throw new VersaoCatalogoConvenienciaDivergenteException(
                    esperada, atual == null ? -1 : atual);
        }
    }

    private static Map<String, Object> snapshot(UnidadeMedida unidade) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nome", unidade.getNome());
        estado.put("sigla", unidade.getSigla());
        estado.put("ativo", unidade.getAtivo());
        return estado;
    }
}
