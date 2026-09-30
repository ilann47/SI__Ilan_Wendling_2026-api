package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.estoque.MovimentoEstoqueRepository;
import com.kaneko.estacionamento.estoque.dto.MovimentoEstoqueResponse;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.financeiro.dto.ContaReceberResponse;
import com.kaneko.estacionamento.fiscal.dto.*;
import com.kaneko.estacionamento.vendas.StatusVendaAdministrativa;
import com.kaneko.estacionamento.vendas.VendaAdministrativaRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VendaDocumentoService {
    private static final String OPERACAO = "GERAR_NOTA_VENDA";
    private final VendaAdministrativaRepository vendas;
    private final NotaSaidaRepository notas;
    private final NotaSaidaService fiscal;
    private final ContaReceberRepository contas;
    private final MovimentoEstoqueRepository movimentos;
    private final IdempotenciaRepository idempotencia;

    public VendaDocumentoService(VendaAdministrativaRepository vendas,
            NotaSaidaRepository notas, NotaSaidaService fiscal, ContaReceberRepository contas,
            MovimentoEstoqueRepository movimentos, IdempotenciaRepository idempotencia) {
        this.vendas = vendas; this.notas = notas; this.fiscal = fiscal;
        this.contas = contas; this.movimentos = movimentos; this.idempotencia = idempotencia;
    }

    @Transactional(readOnly = true)
    public DocumentosVendaResponse documentos(Long org, Long id,
            boolean lerFiscal, boolean lerFinanceiro, boolean lerEstoque) {
        vendas.findByIdAndOrganizacaoId(id, org).orElseThrow(() ->
                new RecursoNaoEncontradoException("VendaAdministrativa", id));
        var nota = lerFiscal || lerEstoque
                ? notas.findByOrganizacaoIdAndVendaAdministrativaId(org, id)
                : java.util.Optional.<NotaSaida>empty();
        java.util.List<MovimentoEstoqueResponse> movimentosResposta = null;
        if (lerEstoque) {
            var encontrados = movimentos.listarPorOrigem(org, "VENDA_ADMINISTRATIVA", id + ":");
            if (encontrados.isEmpty() && nota.isPresent()) {
                String numeroNota = nota.get().getNumero();
                encontrados = movimentos.listarPorOrigem(org, "NOTA_SAIDA", numeroNota).stream()
                        .filter(movimento -> numeroNota.equals(movimento.getOrigemChave()))
                        .toList();
            }
            movimentosResposta = encontrados.stream().map(MovimentoEstoqueResponse::from).toList();
        }
        return new DocumentosVendaResponse(
                lerFiscal ? nota.map(NotaSaidaResponse::from).orElse(null) : null,
                lerFinanceiro ? contas.findByVendaAdministrativaIdAndOrganizacaoIdOrderByNumeroParcelaAsc(id, org)
                        .stream().map(ContaReceberResponse::from).toList() : null,
                movimentosResposta);
    }

    @Transactional
    public NotaSaidaResponse gerar(Long org, Long ator, Long id, String chave,
            GerarNotaVendaRequest request) {
        if (chave == null || chave.isBlank() || chave.length() > 128)
            throw new IllegalArgumentException("Informe uma chave de idempotência de até 128 caracteres.");
        String hash = hash(id + ":" + request);
        idempotencia.bloquear(org, ator, OPERACAO, chave);
        var anterior = idempotencia.buscar(org, ator, OPERACAO, chave);
        if (anterior.isPresent()) {
            if (!hash.equals(anterior.get().requestHash()))
                throw new ConflitoException("A chave de idempotência já foi usada com outros dados.");
            return NotaSaidaResponse.from(fiscal.buscar(org, anterior.get().recursoId()));
        }
        var venda = vendas.findParaAtualizar(id, org).orElseThrow(() ->
                new RecursoNaoEncontradoException("VendaAdministrativa", id));
        if (venda.getStatus() != StatusVendaAdministrativa.CONFIRMADA)
            throw new ConflitoException("Confirme o pedido antes de gerar a nota de saída.");
        if (notas.existsByOrganizacaoIdAndVendaAdministrativaId(org, id))
            throw new ConflitoException("Este pedido já possui nota de saída. Consulte o documento vinculado.");
        var itens = venda.getItens().stream().map(item -> new ItemNotaSaidaRequest(
                item.getProduto().getId(), item.getQuantidade(), item.getValorUnitario(),
                item.getValorDesconto(), null)).toList();
        var nota = fiscal.criar(org, new NotaSaidaRequest(request.numero(), request.modelo(),
                request.serie(), venda.getCliente().getId(),
                venda.getCondicaoPagamento() == null ? null : venda.getCondicaoPagamento().getId(),
                venda.getLocalEstoque().getId(), id, request.dataEmissao(), request.dataSaida(),
                null, null, null, null, venda.getValorDesconto(), venda.getObservacao(), itens));
        idempotencia.registrar(org, ator, OPERACAO, chave, hash, "NOTA_SAIDA", nota.getId());
        return NotaSaidaResponse.from(nota);
    }

    private static String hash(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
