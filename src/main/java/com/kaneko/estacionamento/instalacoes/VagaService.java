package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.instalacoes.dto.CriarVagaItemRequest;
import com.kaneko.estacionamento.instalacoes.dto.ImportarVagasRequest;
import com.kaneko.estacionamento.instalacoes.dto.ImportarVagasResponse;
import com.kaneko.estacionamento.instalacoes.dto.ItemImportacaoVagaResponse;
import com.kaneko.estacionamento.instalacoes.dto.StatusImportacaoVaga;
import com.kaneko.estacionamento.instalacoes.dto.VagaDetalhe;
import com.kaneko.estacionamento.instalacoes.dto.VagaResumo;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VagaService {
    private static final String RECURSO = "VAGA";

    private final SetorRepository setores;
    private final VagaRepository vagas;
    private final PatioInstalacaoRepository patios;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public VagaService(
            SetorRepository setores, VagaRepository vagas, PatioInstalacaoRepository patios,
            AuditoriaService auditoria, OutboxService outbox) {
        this.setores = setores;
        this.vagas = vagas;
        this.patios = patios;
        this.auditoria = auditoria;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public Page<VagaResumo> listar(Long org, Long setorId, String codigo, Pageable page) {
        String termo = codigo == null ? "" : codigo.strip();
        if (termo.length() > 40) {
            throw new IllegalArgumentException("A busca por código aceita até 40 caracteres.");
        }
        return vagas.listar(org, setorId, termo.toUpperCase(Locale.ROOT), page);
    }

    @Transactional(readOnly = true)
    public VagaDetalhe obter(Long org, Long id) {
        Vaga vaga = vagas.findByIdAndOrganizacaoId(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", id));
        Setor setor = setores.findByIdAndOrganizacaoId(vaga.getSetorId(), org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Setor", vaga.getSetorId()));
        return detalhe(vaga, setor);
    }

    @Transactional
    public VagaDetalhe editar(Long org, Long id, long versao, CriarVagaItemRequest request) {
        Long setorId = vagas.buscarSetor(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", id));
        // Mesmo lock da importação e da alteração das categorias do setor.
        Setor setor = setores.buscarParaEdicao(setorId, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Setor", setorId));
        Vaga vaga = vagas.buscarParaEdicao(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", id));
        InstalacaoHttpVersion.exigir(vaga.getVersion(), versao);
        String codigo = Setor.normalizarCodigo(request.code());
        if (!setor.categorias().contains(request.type())) {
            throw new ConflitoException("A categoria da vaga não é aceita neste setor.");
        }
        if (!codigo.equals(Setor.normalizarCodigo(vaga.getCodigo()))
                && !vagas.buscarPorCodigosNormalizados(setorId, List.of(codigo)).isEmpty()) {
            throw new ConflitoException("Já existe uma vaga com este código neste setor.");
        }
        if (!codigo.equals(vaga.getCodigo()) || !vaga.equivale(request.type(), request.accessible(), request.position())) {
            var antes = snapshot(vaga);
            vaga.atualizar(codigo, request.type(), request.accessible(), request.position());
            vagas.flush();
            auditoria.registrar(new RegistrarAuditoriaCommand(
                    org, "VAGA_ATUALIZADA", RECURSO, id, null, setor.getPatioId(), null,
                    antes, snapshot(vaga), versao, vaga.getVersion(), ClasseRetencaoAuditoria.CONFIGURACAO));
        }
        return detalhe(vaga, setor);
    }

    @Transactional
    public VagaDetalhe alterarSituacao(Long org, Long id, long versao, StatusVaga situacao) {
        Vaga vaga = vagas.buscarParaEdicao(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", id));
        InstalacaoHttpVersion.exigir(vaga.getVersion(), versao);
        Setor setor = setores.findByIdAndOrganizacaoId(vaga.getSetorId(), org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Setor", vaga.getSetorId()));
        if (vaga.getStatus() != situacao) {
            var antes = snapshot(vaga);
            vaga.alterarSituacao(situacao);
            vagas.flush();
            auditoria.registrar(new RegistrarAuditoriaCommand(
                    org, "VAGA_SITUACAO_ALTERADA", RECURSO, id, null, setor.getPatioId(), null,
                    antes, snapshot(vaga), versao, vaga.getVersion(), ClasseRetencaoAuditoria.CONFIGURACAO));
        }
        return detalhe(vaga, setor);
    }

    private VagaDetalhe detalhe(Vaga vaga, Setor setor) {
        var patio = patios.findByIdAndOrganizacaoId(setor.getPatioId(), setor.getOrganizacaoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pátio", setor.getPatioId()));
        return new VagaDetalhe(VagaResumo.from(vaga, setor.getNome(), patio.getNome()), setor.categorias());
    }

    @Transactional
    public VagaDetalhe criar(Long org, Long setorId, CriarVagaItemRequest request) {
        Setor setor = bloquearSetorAtivo(org, setorId);
        String codigo = Setor.normalizarCodigo(request.code());
        Vaga.normalizarPosicao(request.position());
        if (!setor.categorias().contains(request.type())) {
            throw new ConflitoException("A categoria da vaga não é aceita neste setor.");
        }
        if (!vagas.buscarPorCodigosNormalizados(setorId, List.of(codigo)).isEmpty()) {
            throw new ConflitoException("Já existe uma vaga com este código neste setor.");
        }
        if (vagas.countBySetorId(setorId) >= setor.getCapacidade()) {
            throw new ConflitoException("O setor já atingiu sua capacidade de vagas.");
        }
        return detalhe(salvarNovaVaga(setor, codigo, request), setor);
    }

    @Transactional
    public ImportarVagasResponse importar(
            Long organizacaoId, Long setorId, ImportarVagasRequest request) {
        Setor setor = bloquearSetorAtivo(organizacaoId, setorId);
        List<ItemNormalizado> itens = normalizar(request.spaces());
        List<String> codigos = itens.stream().map(ItemNormalizado::codigo).distinct().toList();
        Map<String, Vaga> existentes = vagas.buscarPorCodigosNormalizados(setorId, codigos)
                .stream().collect(Collectors.toMap(
                        vaga -> Setor.normalizarCodigo(vaga.getCodigo()), Function.identity()));
        long quantidadeAtual = vagas.countBySetorId(setorId);
        List<ItemImportacaoVagaResponse> respostas = new ArrayList<>(itens.size());
        int criadas = 0;
        int repetidas = 0;
        int conflitos = 0;

        for (ItemNormalizado item : itens) {
            CriarVagaItemRequest entrada = item.request();
            if (!setor.categorias().contains(entrada.type())) {
                respostas.add(ItemImportacaoVagaResponse.conflito(
                        item.indice(), entrada, item.codigo(),
                        "A categoria da vaga nao e aceita pelo setor."));
                conflitos++;
                continue;
            }
            Vaga existente = existentes.get(item.codigo());
            if (existente != null) {
                if (existente.equivale(
                        entrada.type(), entrada.accessible(), entrada.position())) {
                    respostas.add(ItemImportacaoVagaResponse.sucesso(
                            item.indice(), StatusImportacaoVaga.EXISTENTE, existente));
                    repetidas++;
                } else {
                    respostas.add(ItemImportacaoVagaResponse.conflito(
                            item.indice(), entrada, item.codigo(),
                            "O codigo ja existe com dados diferentes."));
                    conflitos++;
                }
                continue;
            }
            if (quantidadeAtual >= setor.getCapacidade()) {
                respostas.add(ItemImportacaoVagaResponse.conflito(
                        item.indice(), entrada, item.codigo(),
                        "A capacidade do setor foi atingida."));
                conflitos++;
                continue;
            }
            Vaga vaga = salvarNovaVaga(setor, item.codigo(), entrada);
            existentes.put(item.codigo(), vaga);
            quantidadeAtual++;
            criadas++;
            respostas.add(ItemImportacaoVagaResponse.sucesso(
                    item.indice(), StatusImportacaoVaga.CRIADA, vaga));
        }
        return new ImportarVagasResponse(
                setorId, itens.size(), criadas, repetidas, conflitos,
                List.copyOf(respostas));
    }

    private Setor bloquearSetorAtivo(Long org, Long setorId) {
        return setores.findAtivoByIdAndOrganizacaoIdForUpdate(setorId, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Setor ativo", setorId));
    }

    private Vaga salvarNovaVaga(Setor setor, String codigo, CriarVagaItemRequest request) {
        Vaga vaga = vagas.saveAndFlush(Vaga.criar(setor.getOrganizacaoId(), setor.getId(),
                codigo, request.type(), request.accessible(), request.position()));
        registrarCriacao(vaga, setor.getPatioId());
        return vaga;
    }

    private static List<ItemNormalizado> normalizar(List<CriarVagaItemRequest> itens) {
        List<ItemNormalizado> normalizados = new ArrayList<>(itens.size());
        for (int indice = 0; indice < itens.size(); indice++) {
            CriarVagaItemRequest item = itens.get(indice);
            String codigo = Setor.normalizarCodigo(item.code());
            Vaga.normalizarPosicao(item.position());
            normalizados.add(new ItemNormalizado(indice, codigo, item));
        }
        return normalizados;
    }

    private static Map<String, Object> snapshot(Vaga vaga) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("setorId", vaga.getSetorId());
        estado.put("codigo", vaga.getCodigo());
        estado.put("tipo", vaga.getTipo().name());
        estado.put("acessivel", vaga.getAcessivel());
        if (vaga.getPosicao() != null) estado.put("posicao", vaga.getPosicao());
        estado.put("status", vaga.getStatus().name());
        return estado;
    }

    private void registrarCriacao(Vaga vaga, Long patioId) {
        var estado = snapshot(vaga);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                vaga.getOrganizacaoId(), "VAGA_CRIADA", RECURSO, vaga.getId(),
                null, patioId, null, null, estado, null, vaga.getVersion(),
                ClasseRetencaoAuditoria.CONFIGURACAO));
        outbox.publicar(new PublicarEventoOutboxCommand(
                "VagaCriada", vaga.getOrganizacaoId(), RECURSO, vaga.getId(),
                vaga.getVersion(), null, 1, estado));
    }

    private record ItemNormalizado(
            int indice, String codigo, CriarVagaItemRequest request) {}
}
