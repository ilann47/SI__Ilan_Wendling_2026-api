package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.compras.RecebimentoCompra;
import com.kaneko.estacionamento.compras.RecebimentoCompraRepository;
import com.kaneko.estacionamento.compras.dto.RecebimentoCompraResponse;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.estoque.MovimentoEstoqueRepository;
import com.kaneko.estacionamento.estoque.MovimentoEstoqueView;
import com.kaneko.estacionamento.estoque.dto.MovimentoEstoqueResponse;
import com.kaneko.estacionamento.financeiro.ContaPagarRepository;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaDetalheResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaResponse;
import com.kaneko.estacionamento.fiscal.dto.OrdemCompraVinculadaResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta a consulta agregada da nota sem adicionar efeitos ao agregado fiscal. */
@Service
public class NotaEntradaDetalheService {

    private final NotaEntradaService notaEntradaService;
    private final ContaPagarRepository contaPagarRepository;
    private final RecebimentoCompraRepository recebimentoCompraRepository;
    private final MovimentoEstoqueRepository movimentoEstoqueRepository;

    public NotaEntradaDetalheService(
            NotaEntradaService notaEntradaService,
            ContaPagarRepository contaPagarRepository,
            RecebimentoCompraRepository recebimentoCompraRepository,
            MovimentoEstoqueRepository movimentoEstoqueRepository) {
        this.notaEntradaService = notaEntradaService;
        this.contaPagarRepository = contaPagarRepository;
        this.recebimentoCompraRepository = recebimentoCompraRepository;
        this.movimentoEstoqueRepository = movimentoEstoqueRepository;
    }

    @Transactional(readOnly = true)
    public NotaEntradaDetalheResponse buscar(Long organizacaoId, Long notaId) {
        return buscar(organizacaoId, notaId, true, true, true);
    }

    @Transactional(readOnly = true)
    public NotaEntradaDetalheResponse buscar(Long organizacaoId, Long notaId,
            boolean lerCompras, boolean lerFinanceiro, boolean lerEstoque) {
        NotaEntrada nota = notaEntradaService.buscar(organizacaoId, notaId);
        RecebimentoCompra recebimento = buscarRecebimento(nota, organizacaoId);

        List<ContaPagarResponse> contas = !lerFinanceiro ? null : contaPagarRepository
                .findByNotaEntradaIdAndOrganizacaoIdOrderByNumeroParcelaAsc(
                        notaId, organizacaoId)
                .stream()
                .map(ContaPagarResponse::from)
                .toList();

        List<MovimentoEstoqueView> movimentos = !lerEstoque ? List.of() : recebimento == null
                ? movimentoEstoqueRepository.listarPorOrigem(
                        organizacaoId, "NOTA_ENTRADA", notaId + ":")
                : listarMovimentosDoRecebimento(organizacaoId, recebimento);

        return new NotaEntradaDetalheResponse(
                NotaEntradaResponse.from(nota),
                recebimento == null || !lerCompras ? null
                        : OrdemCompraVinculadaResponse.from(recebimento.getOrdemCompra()),
                recebimento == null || !lerCompras ? null : RecebimentoCompraResponse.from(recebimento),
                contas,
                lerEstoque ? movimentos.stream().map(MovimentoEstoqueResponse::from).toList() : null);
    }

    private RecebimentoCompra buscarRecebimento(NotaEntrada nota, Long organizacaoId) {
        if (nota.getRecebimentoCompra() == null) {
            return null;
        }
        return recebimentoCompraRepository.findByIdAndOrganizacaoId(
                nota.getRecebimentoCompra().getId(), organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "RecebimentoCompra", nota.getRecebimentoCompra().getId()));
    }

    private List<MovimentoEstoqueView> listarMovimentosDoRecebimento(
            Long organizacaoId, RecebimentoCompra recebimento) {
        List<Long> movimentoIds = recebimento.getItens().stream()
                .map(item -> item.getMovimentoEstoqueId())
                .distinct()
                .toList();
        if (movimentoIds.isEmpty()) {
            return List.of();
        }
        return movimentoEstoqueRepository.listarPorIds(organizacaoId, movimentoIds);
    }
}
