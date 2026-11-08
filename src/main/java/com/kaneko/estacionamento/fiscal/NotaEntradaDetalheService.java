package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.estoque.MovimentoEstoqueRepository;
import com.kaneko.estacionamento.estoque.MovimentoEstoqueView;
import com.kaneko.estacionamento.estoque.dto.MovimentoEstoqueResponse;
import com.kaneko.estacionamento.financeiro.ContaPagarRepository;
import com.kaneko.estacionamento.financeiro.dto.ContaPagarResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaDetalheResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaEntradaResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monta a consulta agregada da nota sem adicionar efeitos ao agregado fiscal. */
@Service
public class NotaEntradaDetalheService {

    private final NotaEntradaService notaEntradaService;
    private final ContaPagarRepository contaPagarRepository;
    private final MovimentoEstoqueRepository movimentoEstoqueRepository;

    public NotaEntradaDetalheService(
            NotaEntradaService notaEntradaService,
            ContaPagarRepository contaPagarRepository,
            MovimentoEstoqueRepository movimentoEstoqueRepository) {
        this.notaEntradaService = notaEntradaService;
        this.contaPagarRepository = contaPagarRepository;
        this.movimentoEstoqueRepository = movimentoEstoqueRepository;
    }

    @Transactional(readOnly = true)
    public NotaEntradaDetalheResponse buscar(Long organizacaoId, Long notaId) {
        return buscar(organizacaoId, notaId, true, true);
    }

    @Transactional(readOnly = true)
    public NotaEntradaDetalheResponse buscar(Long organizacaoId, Long notaId,
            boolean lerFinanceiro, boolean lerEstoque) {
        NotaEntrada nota = notaEntradaService.buscar(organizacaoId, notaId);

        List<ContaPagarResponse> contas = !lerFinanceiro ? null : contaPagarRepository
                .findByNotaEntradaIdAndOrganizacaoIdOrderByNumeroParcelaAsc(
                        notaId, organizacaoId)
                .stream()
                .map(ContaPagarResponse::from)
                .toList();

        List<MovimentoEstoqueView> movimentos = List.of();
        if (lerEstoque) {
            List<Long> ids = movimentoEstoqueRepository.listarPorOrigem(
                    organizacaoId, "NOTA_ENTRADA", notaId + ":").stream()
                    .map(MovimentoEstoqueView::getId).toList();
            if (!ids.isEmpty()) movimentos = movimentoEstoqueRepository.listarPorIds(organizacaoId,
                    notaEntradaService.incluirCompensacoes(organizacaoId, ids));
        }

        return new NotaEntradaDetalheResponse(
                NotaEntradaResponse.from(nota),
                contas,
                lerEstoque ? movimentos.stream().map(MovimentoEstoqueResponse::from).toList() : null,
                !lerFinanceiro ? null : notaEntradaService.resumo(nota));
    }
}
