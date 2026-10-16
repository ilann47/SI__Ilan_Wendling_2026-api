package com.kaneko.estacionamento.conveniencia;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.web.FiltroSpecs;
import com.kaneko.estacionamento.conveniencia.dto.MarcaRequest;
import com.kaneko.estacionamento.conveniencia.dto.MarcaResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regras de negocio do cadastro de Marca.
 *
 * <p>Garante a unicidade do nome (RN do cadastro, espelhando a restricao
 * {@code uq_marca_organizacao_nome}) na criacao e na alteracao.
 */
@Service
public class MarcaService {

    private final MarcaRepository repository;
    private final AuditoriaService auditoria;

    public MarcaService(MarcaRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<MarcaResponse> listar(
            Long organizacaoId, String nome, Boolean ativo, Pageable pageable) {
        Specification<Marca> filtro = FiltroSpecs.todos(
                FiltroSpecs.igual("organizacaoId", organizacaoId),
                FiltroSpecs.contemIgnoreCase("nome", nome),
                FiltroSpecs.igual("ativo", ativo));
        return repository.findAll(filtro, pageable).map(MarcaResponse::from);
    }

    @Transactional(readOnly = true)
    public Marca buscar(Long organizacaoId, Long id) {
        return repository.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Marca", id));
    }

    @Transactional
    public Marca criar(Long organizacaoId, MarcaRequest req) {
        String nome = req.nome().trim();
        if (repository.existeNomeNormalizado(organizacaoId, nome, null)) {
            throw new ConflitoException("Ja existe uma marca cadastrada com este nome.");
        }
        Marca marca = new Marca();
        marca.setOrganizacaoId(organizacaoId);
        aplicar(marca, req, nome);
        marca = repository.saveAndFlush(marca);
        registrar(marca, "MARCA_CRIADA", null, snapshot(marca), null, marca.getVersion());
        return marca;
    }

    @Transactional
    public Marca atualizar(Long organizacaoId, Long id, long versaoEsperada, MarcaRequest req) {
        Marca marca = buscar(organizacaoId, id);
        conferirVersao(versaoEsperada, marca.getVersion());
        String nome = req.nome().trim();
        if (repository.existeNomeNormalizado(organizacaoId, nome, id)) {
            throw new ConflitoException("Ja existe uma marca cadastrada com este nome.");
        }
        Map<String, Object> anterior = snapshot(marca);
        Long versaoAnterior = marca.getVersion();
        aplicar(marca, req, nome);
        marca = repository.saveAndFlush(marca);
        registrar(marca, "MARCA_ATUALIZADA", anterior, snapshot(marca),
                versaoAnterior, marca.getVersion());
        return marca;
    }

    @Transactional
    public Marca excluir(Long organizacaoId, Long id, long versaoEsperada) {
        Marca marca = buscar(organizacaoId, id);
        conferirVersao(versaoEsperada, marca.getVersion());
        Map<String, Object> anterior = snapshot(marca);
        Long versaoAnterior = marca.getVersion();
        marca.setAtivo(false);
        repository.flush();
        registrar(marca, "MARCA_DESATIVADA", anterior, snapshot(marca),
                versaoAnterior, marca.getVersion());
        return marca;
    }

    private void aplicar(Marca marca, MarcaRequest req, String nome) {
        marca.setNome(nome);
        if (req.ativo() != null) {
            marca.setAtivo(req.ativo());
        }
    }

    private void registrar(
            Marca marca, String acao, Map<String, Object> anterior,
            Map<String, Object> posterior, Long versaoAnterior, Long versaoPosterior) {
        auditoria.registrar(new RegistrarAuditoriaCommand(
                marca.getOrganizacaoId(), acao, "MARCA", marca.getId(),
                null, null, null, anterior, posterior, versaoAnterior, versaoPosterior,
                ClasseRetencaoAuditoria.CONFIGURACAO));
    }

    private static void conferirVersao(long esperada, Long atual) {
        if (atual == null || esperada != atual) {
            throw new VersaoCatalogoConvenienciaDivergenteException(
                    esperada, atual == null ? -1 : atual);
        }
    }

    private static Map<String, Object> snapshot(Marca marca) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("nome", marca.getNome());
        estado.put("ativo", marca.getAtivo());
        return estado;
    }
}
