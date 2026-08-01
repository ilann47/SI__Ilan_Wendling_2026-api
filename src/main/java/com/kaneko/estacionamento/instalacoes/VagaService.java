package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.instalacoes.dto.CriarVagaItemRequest;
import com.kaneko.estacionamento.instalacoes.dto.ImportarVagasRequest;
import com.kaneko.estacionamento.instalacoes.dto.ImportarVagasResponse;
import com.kaneko.estacionamento.instalacoes.dto.ItemImportacaoVagaResponse;
import com.kaneko.estacionamento.instalacoes.dto.StatusImportacaoVaga;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VagaService {
    private static final String RECURSO = "VAGA";

    private final SetorRepository setores;
    private final VagaRepository vagas;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public VagaService(
            SetorRepository setores, VagaRepository vagas,
            AuditoriaService auditoria, OutboxService outbox) {
        this.setores = setores;
        this.vagas = vagas;
        this.auditoria = auditoria;
        this.outbox = outbox;
    }

    @Transactional
    public ImportarVagasResponse importar(
            Long organizacaoId, Long setorId, ImportarVagasRequest request) {
        Setor setor = setores.findAtivoByIdAndOrganizacaoIdForUpdate(
                        setorId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Setor ativo", setorId));
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
            Vaga vaga = Vaga.criar(
                    organizacaoId, setorId, item.codigo(), entrada.type(),
                    entrada.accessible(), entrada.position());
            vaga = vagas.saveAndFlush(vaga);
            existentes.put(item.codigo(), vaga);
            quantidadeAtual++;
            criadas++;
            registrarCriacao(vaga, setor.getPatioId());
            respostas.add(ItemImportacaoVagaResponse.sucesso(
                    item.indice(), StatusImportacaoVaga.CRIADA, vaga));
        }
        return new ImportarVagasResponse(
                setorId, itens.size(), criadas, repetidas, conflitos,
                List.copyOf(respostas));
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

    private void registrarCriacao(Vaga vaga, Long patioId) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("setorId", vaga.getSetorId());
        estado.put("codigo", vaga.getCodigo());
        estado.put("tipo", vaga.getTipo().name());
        estado.put("acessivel", vaga.getAcessivel());
        if (vaga.getPosicao() != null) estado.put("posicao", vaga.getPosicao());
        estado.put("status", vaga.getStatus().name());
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
