package com.kaneko.estacionamento.instalacoes;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.outbox.OutboxService;
import com.kaneko.estacionamento.common.outbox.PublicarEventoOutboxCommand;
import com.kaneko.estacionamento.instalacoes.dto.CriarSetorRequest;
import com.kaneko.estacionamento.instalacoes.dto.SetorResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetorService {
    private static final String RECURSO = "SETOR";

    private final PatioInstalacaoRepository patios;
    private final SetorRepository setores;
    private final AuditoriaService auditoria;
    private final OutboxService outbox;

    public SetorService(
            PatioInstalacaoRepository patios, SetorRepository setores,
            AuditoriaService auditoria, OutboxService outbox) {
        this.patios = patios;
        this.setores = setores;
        this.auditoria = auditoria;
        this.outbox = outbox;
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
