package com.kaneko.estacionamento.inventario.disponibilidade;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.evento.Evento;
import com.kaneko.estacionamento.evento.EventoRepository;
import com.kaneko.estacionamento.evento.StatusEvento;
import com.kaneko.estacionamento.inventario.disponibilidade.dto.DisponibilidadeEventoResponse;
import com.kaneko.estacionamento.inventario.disponibilidade.dto.ItemDisponibilidadeResponse;
import com.kaneko.estacionamento.inventario.produto.CategoriaProduto;
import com.kaneko.estacionamento.inventario.produto.LotePreco;
import com.kaneko.estacionamento.inventario.produto.LotePrecoRepository;
import com.kaneko.estacionamento.inventario.produto.ProdutoDisponibilidadeView;
import com.kaneko.estacionamento.inventario.produto.ProdutoEstacionamentoRepository;
import com.kaneko.estacionamento.inventario.produto.StatusProdutoEstacionamento;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DisponibilidadeEventoService {
    private final EventoRepository eventos;
    private final ProdutoEstacionamentoRepository produtos;
    private final LotePrecoRepository lotes;

    public DisponibilidadeEventoService(
            EventoRepository eventos, ProdutoEstacionamentoRepository produtos,
            LotePrecoRepository lotes) {
        this.eventos = eventos;
        this.produtos = produtos;
        this.lotes = lotes;
    }

    @Transactional(readOnly = true)
    public DisponibilidadeEventoResponse consultar(
            Long organizacaoId, Long eventoId, CategoriaProduto categoria) {
        Evento evento = eventos.findByIdAndOrganizacaoId(eventoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento", eventoId));
        if (evento.getStatus() != StatusEvento.PUBLICADO
                && evento.getStatus() != StatusEvento.VENDAS_ABERTAS) {
            throw new ConflitoException(
                    "A disponibilidade somente pode ser consultada para evento publicado.");
        }
        OffsetDateTime instante = OffsetDateTime.now(ZoneOffset.UTC);
        List<ProdutoDisponibilidadeView> ofertas = produtos.projetarDisponibilidade(
                eventoId, organizacaoId, StatusProdutoEstacionamento.PUBLICADO, categoria);
        if (ofertas.isEmpty()) {
            return new DisponibilidadeEventoResponse(
                    eventoId, instante, 0, false, List.of());
        }
        List<Long> produtoIds = ofertas.stream()
                .map(ProdutoDisponibilidadeView::getProdutoId)
                .toList();
        Map<Long, LotePreco> loteSelecionado = new LinkedHashMap<>();
        for (LotePreco lote : lotes.buscarVigentesOrdenadosPorPrioridade(
                produtoIds, instante)) {
            loteSelecionado.putIfAbsent(lote.getProdutoId(), lote);
        }

        List<ItemDisponibilidadeResponse> itens = ofertas.stream()
                .filter(oferta -> loteSelecionado.containsKey(oferta.getProdutoId()))
                .map(oferta -> item(oferta, loteSelecionado.get(oferta.getProdutoId())))
                .toList();
        long total = itens.stream()
                .mapToLong(ItemDisponibilidadeResponse::availableQuantity)
                .sum();
        return new DisponibilidadeEventoResponse(eventoId, instante, total, false, itens);
    }

    private static ItemDisponibilidadeResponse item(
            ProdutoDisponibilidadeView produto, LotePreco lote) {
        int disponivel = Math.min(produto.getQuota(), lote.getQuantidade());
        return new ItemDisponibilidadeResponse(
                produto.getProdutoId(), produto.getAlocacaoId(), produto.getNome(),
                produto.getCategoria(), produto.getDireito(), produto.getInicioAcesso(),
                produto.getFimAcesso(), disponivel, lote.getId(), lote.getNome(),
                lote.getPreco(), lote.getMoeda(), lote.getFimVenda());
    }
}
