package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.compras.OrdemCompraRepository;
import com.kaneko.estacionamento.compras.RecebimentoCompraRepository;
import com.kaneko.estacionamento.compras.dto.RecebimentoCompraResponse;
import com.kaneko.estacionamento.estoque.MovimentoEstoqueRepository;
import com.kaneko.estacionamento.estoque.dto.MovimentoEstoqueResponse;
import com.kaneko.estacionamento.financeiro.ContaPagarRepository;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarResponse;
import com.kaneko.estacionamento.fiscal.dto.DocumentosCompraResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompraDocumentoService {
    private final OrdemCompraRepository ordens;
    private final RecebimentoCompraRepository recebimentos;
    private final NotaEntradaRepository notas;
    private final ContaPagarRepository contas;
    private final MovimentoEstoqueRepository movimentos;

    public CompraDocumentoService(OrdemCompraRepository ordens,
            RecebimentoCompraRepository recebimentos, NotaEntradaRepository notas,
            ContaPagarRepository contas, MovimentoEstoqueRepository movimentos) {
        this.ordens = ordens; this.recebimentos = recebimentos; this.notas = notas;
        this.contas = contas; this.movimentos = movimentos;
    }

    @Transactional(readOnly = true)
    public DocumentosCompraResponse buscar(Long org, Long ordemId,
            boolean lerFiscal, boolean lerFinanceiro, boolean lerEstoque) {
        ordens.findByIdAndOrganizacaoId(ordemId, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("OrdemCompra", ordemId));
        var entregas = recebimentos.findAllByOrganizacaoIdAndOrdemCompraIdOrderByRecebidoEmDesc(org, ordemId);
        var documentos = lerFiscal || lerFinanceiro
                ? notas.findByOrganizacaoIdAndRecebimentoCompraOrdemCompraIdOrderByIdAsc(org, ordemId)
                : List.<NotaEntrada>of();
        var ids = documentos.stream().map(NotaEntrada::getId).toList();
        var titulos = !lerFinanceiro ? null : ids.isEmpty() ? List.<ContaPagarResponse>of()
                : contas.findByOrganizacaoIdAndNotaEntradaIdInOrderByDataVencimentoAsc(org, ids)
                    .stream().map(ContaPagarResponse::from).toList();
        var movimentoIds = entregas.stream().flatMap(r -> r.getItens().stream())
                .map(i -> i.getMovimentoEstoqueId()).distinct().toList();
        var razao = !lerEstoque ? null : movimentoIds.isEmpty() ? List.<MovimentoEstoqueResponse>of()
                : movimentos.listarPorIds(org, movimentoIds).stream().map(MovimentoEstoqueResponse::from).toList();
        return new DocumentosCompraResponse(entregas.stream().map(RecebimentoCompraResponse::from).toList(),
                lerFiscal ? documentos.stream().map(NotaEntradaResponse::from).toList() : null,
                titulos, razao);
    }
}
