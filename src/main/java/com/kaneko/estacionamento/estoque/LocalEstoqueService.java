package com.kaneko.estacionamento.estoque;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.estoque.dto.LocalEstoqueRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalEstoqueService {
    private final LocalEstoqueRepository locais;
    private final SaldoEstoqueRepository saldos;
    private final AuditoriaService auditoria;

    public LocalEstoqueService(LocalEstoqueRepository locais, SaldoEstoqueRepository saldos,
            AuditoriaService auditoria) {
        this.locais = locais;
        this.saldos = saldos;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<LocalEstoque> listar(Long organizacaoId, Pageable pageable) {
        return locais.findAllByOrganizacaoId(organizacaoId, pageable);
    }

    @Transactional(readOnly = true)
    public LocalEstoque buscar(Long organizacaoId, Long id) {
        return locais.findByIdAndOrganizacaoId(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("LocalEstoque", id));
    }

    @Transactional
    public LocalEstoque criar(Long organizacaoId, LocalEstoqueRequest request) {
        validarNome(organizacaoId, request.nome(), null);
        LocalEstoque local = LocalEstoque.criar(organizacaoId, request.nome());
        if (Boolean.FALSE.equals(request.ativo())) {
            local.inativar();
        }
        local = locais.saveAndFlush(local);
        auditar(local, "LOCAL_ESTOQUE_CRIADO", null, snapshot(local), null, local.getVersion());
        return local;
    }

    @Transactional
    public LocalEstoque atualizar(Long organizacaoId, Long id, long versaoEsperada,
            LocalEstoqueRequest request) {
        LocalEstoque local = buscarParaAtualizar(organizacaoId, id);
        conferirVersao(local, versaoEsperada);
        validarNome(organizacaoId, request.nome(), id);
        boolean inativando = Boolean.TRUE.equals(local.getAtivo())
                && Boolean.FALSE.equals(request.ativo());
        if (inativando && saldos.possuiSaldoNaoZero(organizacaoId, id)) {
            throw new ConflitoException(
                    "O local nao pode ser inativado enquanto possuir saldo de estoque.");
        }
        Map<String, Object> anterior = snapshot(local);
        Long versaoAnterior = local.getVersion();
        local.alterar(request.nome(), request.ativo());
        local = locais.saveAndFlush(local);
        auditar(local, "LOCAL_ESTOQUE_ATUALIZADO", anterior, snapshot(local),
                versaoAnterior, local.getVersion());
        return local;
    }

    @Transactional
    public LocalEstoque excluir(Long organizacaoId, Long id, long versaoEsperada) {
        LocalEstoque local = buscarParaAtualizar(organizacaoId, id);
        conferirVersao(local, versaoEsperada);
        if (saldos.possuiSaldoNaoZero(organizacaoId, id)) {
            throw new ConflitoException(
                    "O local nao pode ser inativado enquanto possuir saldo de estoque.");
        }
        Map<String, Object> anterior = snapshot(local);
        Long versaoAnterior = local.getVersion();
        local.inativar();
        locais.flush();
        auditar(local, "LOCAL_ESTOQUE_DESATIVADO", anterior, snapshot(local),
                versaoAnterior, local.getVersion());
        return local;
    }

    private void validarNome(Long organizacaoId, String nome, Long excluirId) {
        if (locais.existeNomeNormalizado(organizacaoId, nome, excluirId)) {
            throw new ConflitoException("Ja existe um local de estoque com este nome.");
        }
    }

    private LocalEstoque buscarParaAtualizar(Long organizacaoId, Long id) {
        return locais.findParaAtualizar(id, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("LocalEstoque", id));
    }

    private static void conferirVersao(LocalEstoque local, long esperada) {
        if (local.getVersion() == null || local.getVersion() != esperada) {
            throw new VersaoEstoqueDivergenteException();
        }
    }

    private void auditar(LocalEstoque local, String acao, Map<String, Object> anterior,
            Map<String, Object> posterior, Long versaoAnterior, Long versaoPosterior) {
        auditoria.registrar(new RegistrarAuditoriaCommand(local.getOrganizacaoId(), acao,
                "LOCAL_ESTOQUE", local.getId(), null, null, null, anterior, posterior,
                versaoAnterior, versaoPosterior, ClasseRetencaoAuditoria.CONFIGURACAO));
    }

    private static Map<String, Object> snapshot(LocalEstoque local) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("nome", local.getNome());
        snapshot.put("ativo", local.getAtivo());
        return snapshot;
    }
}
