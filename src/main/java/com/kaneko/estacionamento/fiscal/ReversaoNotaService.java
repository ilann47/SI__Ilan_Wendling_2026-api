package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.estoque.EstoqueService;
import com.kaneko.estacionamento.estoque.EstoqueService.ItemReversaoFiscal;
import com.kaneko.estacionamento.financeiro.BaixaFinanceiraRepository;
import com.kaneko.estacionamento.financeiro.ContaPagar;
import com.kaneko.estacionamento.financeiro.ContaPagarRepository;
import com.kaneko.estacionamento.financeiro.ContaPagarService;
import com.kaneko.estacionamento.financeiro.ContaReceber;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.financeiro.ContaReceberService;
import com.kaneko.estacionamento.financeiro.SituacaoContaPagar;
import com.kaneko.estacionamento.financeiro.SituacaoContaReceber;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaSaidaResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Compensação operacional interna; não representa cancelamento fiscal externo. */
@Service
public class ReversaoNotaService {
    private final NotaEntradaRepository entradas;
    private final NotaSaidaRepository saidas;
    private final ContaPagarRepository contasPagar;
    private final ContaReceberRepository contasReceber;
    private final BaixaFinanceiraRepository baixas;
    private final ContaPagarService pagar;
    private final ContaReceberService receber;
    private final EstoqueService estoque;
    private final AuditoriaService auditoria;

    public ReversaoNotaService(NotaEntradaRepository entradas, NotaSaidaRepository saidas,
            ContaPagarRepository contasPagar, ContaReceberRepository contasReceber,
            BaixaFinanceiraRepository baixas, ContaPagarService pagar,
            ContaReceberService receber, EstoqueService estoque, AuditoriaService auditoria) {
        this.entradas = entradas;
        this.saidas = saidas;
        this.contasPagar = contasPagar;
        this.contasReceber = contasReceber;
        this.baixas = baixas;
        this.pagar = pagar;
        this.receber = receber;
        this.estoque = estoque;
        this.auditoria = auditoria;
    }

    @Transactional
    public NotaEntradaResponse entrada(Long org, Long ator, Long id, long versao, String motivo) {
        exigirTenant(org, ator);
        String justificativa = normalizarMotivo(motivo);
        NotaEntrada nota = entradas.findParaAtualizar(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("NotaEntrada", id));
        exigirReversivel(nota.getSituacao(), nota.getVersion(), versao);
        List<ContaPagar> titulos = contasPagar.findIdsPorNota(org, id).stream()
                .map(titulo -> contasPagar.buscarParaBaixa(titulo, org)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("ContaPagar", titulo)))
                .toList();
        if (titulos.stream().anyMatch(t -> t.getValorPago().signum() > 0
                || (t.getSituacao() != SituacaoContaPagar.PENDENTE
                    && t.getSituacao() != SituacaoContaPagar.CANCELADA)
                || baixas.existsByContaPagarId(t.getId()))) {
            throw new ConflitoException("A nota possui título com pagamento ou baixa. Não é possível reverter.");
        }
        exigirTotalTitulos(nota.getValorTotal(), titulos.stream().map(ContaPagar::getValorOriginal).toList());
        var movimentos = estoque.reverterNotaFiscal(org, ator, id, nota.getLocalEstoque().getId(),
                true, nota.getItens().stream()
                        .map(i -> new ItemReversaoFiscal(i.getSequencia(), i.getProduto().getId(), i.getQuantidade()))
                        .toList(), justificativa);
        titulos.forEach(t -> pagar.cancelar(org, t.getId()));
        nota.setSituacao(SituacaoNota.CANCELADA);
        nota.setRevertidaEm(OffsetDateTime.now());
        nota.setMotivoReversao(justificativa);
        nota = entradas.saveAndFlush(nota);
        auditar(org, id, "NOTA_ENTRADA", versao, nota.getVersion(), justificativa,
                nota.getRevertidaEm(), movimentos, titulos.stream().map(ContaPagar::getId).toList());
        return NotaEntradaResponse.from(nota);
    }

    @Transactional
    public NotaSaidaResponse saida(Long org, Long ator, Long id, long versao, String motivo) {
        exigirTenant(org, ator);
        String justificativa = normalizarMotivo(motivo);
        NotaSaida nota = saidas.findParaAtualizar(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("NotaSaida", id));
        exigirReversivel(nota.getSituacao(), nota.getVersion(), versao);
        List<ContaReceber> titulos = contasReceber.findIdsPorNotaSaida(org, id).stream()
                .map(titulo -> contasReceber.buscarParaBaixa(titulo, org)
                        .orElseThrow(() -> new RecursoNaoEncontradoException("ContaReceber", titulo)))
                .toList();
        if (titulos.stream().anyMatch(t -> t.getValorRecebido().signum() > 0
                || (t.getSituacao() != SituacaoContaReceber.PENDENTE
                    && t.getSituacao() != SituacaoContaReceber.CANCELADA)
                || baixas.existsByContaReceberId(t.getId()))) {
            throw new ConflitoException("A nota possui título com recebimento ou baixa. Não é possível reverter.");
        }
        exigirTotalTitulos(nota.getValorTotal(), titulos.stream().map(ContaReceber::getValorOriginal).toList());
        var movimentos = estoque.reverterNotaFiscal(org, ator, id, nota.getLocalEstoque().getId(),
                false, nota.getItens().stream()
                        .map(i -> new ItemReversaoFiscal(i.getSequencia(), i.getProduto().getId(), i.getQuantidade()))
                        .toList(), justificativa);
        titulos.forEach(t -> receber.cancelar(org, t.getId()));
        nota.setSituacao(SituacaoNota.CANCELADA);
        nota.setRevertidaEm(OffsetDateTime.now());
        nota.setMotivoReversao(justificativa);
        nota = saidas.saveAndFlush(nota);
        auditar(org, id, "NOTA_SAIDA", versao, nota.getVersion(), justificativa,
                nota.getRevertidaEm(), movimentos, titulos.stream().map(ContaReceber::getId).toList());
        return NotaSaidaResponse.from(nota);
    }

    private static void exigirTenant(Long org, Long ator) {
        if (org == null || ator == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A reversão exige uma organização ativa.");
        }
    }

    private static void exigirReversivel(SituacaoNota situacao, Long atual, long versao) {
        if (atual == null || atual != versao) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "A nota foi alterada. Recarregue e revise antes de reverter.");
        }
        if (situacao != SituacaoNota.CONFIRMADA) {
            throw new ConflitoException("Somente uma nota confirmada pode ser revertida.");
        }
    }

    private static String normalizarMotivo(String motivo) {
        if (motivo == null || motivo.isBlank() || motivo.length() > 500) {
            throw new IllegalArgumentException("Informe o motivo da reversão (até 500 caracteres).");
        }
        return motivo.trim();
    }

    private static void exigirTotalTitulos(BigDecimal totalNota, List<BigDecimal> valores) {
        if (valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(totalNota) != 0) {
            throw new ConflitoException("Os títulos vinculados não conciliam com o total da nota.");
        }
    }

    private void auditar(Long org, Long id, String tipo, long versaoAnterior, Long versaoPosterior,
            String motivo, OffsetDateTime revertidaEm, List<Long> movimentos, List<Long> titulos) {
        auditoria.registrar(new RegistrarAuditoriaCommand(org, tipo + "_REVERTIDA", tipo, id,
                null, null, motivo, Map.of("situacao", "CONFIRMADA"),
                Map.of("situacao", "CANCELADA", "revertidaEm", revertidaEm.toString(),
                        "movimentosCompensatorios", movimentos, "titulosCancelados", titulos),
                versaoAnterior, versaoPosterior, ClasseRetencaoAuditoria.FISCAL));
    }
}
