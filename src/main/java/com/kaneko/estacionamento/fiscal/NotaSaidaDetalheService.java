package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.estoque.MovimentoEstoqueRepository;
import com.kaneko.estacionamento.estoque.dto.MovimentoEstoqueResponse;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.financeiro.dto.ContaReceberResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaSaidaDetalheResponse;
import com.kaneko.estacionamento.fiscal.dto.NotaSaidaResponse;
import com.kaneko.estacionamento.fiscal.dto.ParcelaNotaEntradaResponse;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotaSaidaDetalheService {
    private final NotaSaidaService notas;
    private final ContaReceberRepository contas;
    private final MovimentoEstoqueRepository movimentos;

    public NotaSaidaDetalheService(NotaSaidaService notas, ContaReceberRepository contas,
                                   MovimentoEstoqueRepository movimentos) {
        this.notas = notas;
        this.contas = contas;
        this.movimentos = movimentos;
    }

    @Transactional(readOnly = true)
    public NotaSaidaDetalheResponse buscar(Long org, Long id, boolean lerFinanceiro, boolean lerEstoque) {
        NotaSaida nota = notas.buscar(org, id);
        List<ContaReceberResponse> titulos = null;
        List<ParcelaNotaEntradaResponse> parcelas = null;
        if (lerFinanceiro) {
            titulos = contas.findByNotaSaidaIdAndOrganizacaoId(id, org)
                    .stream().map(ContaReceberResponse::from)
                    .sorted(Comparator.comparingInt(ContaReceberResponse::numeroParcela)).toList();
            int total = nota.getParcelas().size();
            parcelas = nota.getParcelas().stream().map(p -> new ParcelaNotaEntradaResponse(
                    p.getNumeroParcela(), total, p.getValor(), p.getDataVencimento(),
                    p.getFormaPagamento() == null ? null : p.getFormaPagamento().getId(), p.getFormaPagamentoNome()))
                    .toList();
        }
        List<MovimentoEstoqueResponse> razao = null;
        if (lerEstoque) {
            var originais = movimentos.listarPorOrigem(org, "NOTA_SAIDA", id + ":");
            var reversoes = movimentos.listarPorOrigem(org, "REVERSAO_NOTA_SAIDA", id + ":");
            razao = Stream.concat(originais.stream(), reversoes.stream())
                    .sorted(Comparator.comparingLong(m -> m.getId()))
                    .map(MovimentoEstoqueResponse::from).toList();
        }
        return new NotaSaidaDetalheResponse(NotaSaidaResponse.from(nota), parcelas, titulos, razao);
    }
}
