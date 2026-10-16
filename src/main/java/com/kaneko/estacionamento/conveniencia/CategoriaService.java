package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.conveniencia.dto.CategoriaRequest;
import com.kaneko.estacionamento.conveniencia.dto.CategoriaResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Categoria.
 *
 * <p>Garante a unicidade do nome (RN do cadastro, espelhando a restricao
 * {@code uq_categoria_organizacao_nome}) na criacao e na alteracao.
 */
@Service
public class CategoriaService {

    private final CategoriaRepository repository;
    private final AuditoriaService auditoria;

    public CategoriaService(CategoriaRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<CategoriaResponse> listar(
            Long organizacaoId, String nome, Boolean ativo, Pageable pageable) {
        Specification<Categoria> filtro = FiltroSpecs.todos(
                FiltroSpecs.igual("organizacaoId", organizacaoId),
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(CategoriaResponse::from);
    }

    @Transactional(readOnly = true)
    public Categoria buscar(Long organizacaoId, Long id) {
        return repository.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria", id));
    }

    @Transactional
    public Categoria criar(Long organizacaoId, CategoriaRequest req) {
        String nome = req.nome().trim();
        if (repository.existeNomeNormalizado(organizacaoId, nome, null)) {
            throw new ConflitoException("Ja existe uma categoria cadastrada com este nome.");
        }
        Categoria categoria = new Categoria();
        categoria.setOrganizacaoId(organizacaoId);
        aplicar(categoria, req, nome);
        categoria = repository.saveAndFlush(categoria);
        registrar(categoria, "CATEGORIA_CRIADA", null, snapshot(categoria),
                null, categoria.getVersion());
        return categoria;
    }

    @Transactional
    public Categoria atualizar(
            Long organizacaoId, Long id, long versaoEsperada, CategoriaRequest req) {
        Categoria categoria = buscar(organizacaoId, id);
        conferirVersao(versaoEsperada, categoria.getVersion());
        String nome = req.nome().trim();
        if (repository.existeNomeNormalizado(organizacaoId, nome, id)) {
            throw new ConflitoException("Ja existe uma categoria cadastrada com este nome.");
        }
        Map<String, Object> anterior = snapshot(categoria);
        Long versaoAnterior = categoria.getVersion();
        aplicar(categoria, req, nome);
        categoria = repository.saveAndFlush(categoria);
        registrar(categoria, "CATEGORIA_ATUALIZADA", anterior, snapshot(categoria),
                versaoAnterior, categoria.getVersion());
        return categoria;
    }

    @Transactional
    public Categoria excluir(Long organizacaoId, Long id, long versaoEsperada) {
        Categoria categoria = buscar(organizacaoId, id);
        conferirVersao(versaoEsperada, categoria.getVersion());
        Map<String, Object> anterior = snapshot(categoria);
        Long versaoAnterior = categoria.getVersion();
        categoria.setAtivo(false);
        repository.flush();
        registrar(categoria, "CATEGORIA_DESATIVADA", anterior, snapshot(categoria),
                versaoAnterior, categoria.getVersion());
        return categoria;
    }

    private void aplicar(Categoria categoria, CategoriaRequest req, String nome) {
        categoria.setNome(nome);
        categoria.setDescricao(req.descricao());
        if (req.ativo() != null) {
            categoria.setAtivo(req.ativo());
        }
    }

    private void registrar(
            Categoria categoria, String acao, Map<String, Object> anterior,
            Map<String, Object> posterior, Long versaoAnterior, Long versaoPosterior) {
        auditoria.registrar(new RegistrarAuditoriaCommand(
                categoria.getOrganizacaoId(), acao, "CATEGORIA", categoria.getId(),
                null, null, null, anterior, posterior, versaoAnterior, versaoPosterior,
                ClasseRetencaoAuditoria.CONFIGURACAO));
    }

    private static void conferirVersao(long esperada, Long atual) {
        if (atual == null || esperada != atual) {
            throw new VersaoCatalogoConvenienciaDivergenteException(
                    esperada, atual == null ? -1 : atual);
        }
    }

    private static Map<String, Object> snapshot(Categoria categoria) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nome", categoria.getNome());
        estado.put("descricao", categoria.getDescricao());
        estado.put("ativo", categoria.getAtivo());
        return estado;
    }
}
