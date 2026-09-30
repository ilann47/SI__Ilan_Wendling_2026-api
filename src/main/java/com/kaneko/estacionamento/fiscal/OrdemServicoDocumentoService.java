package com.kaneko.estacionamento.fiscal;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.financeiro.ContaReceberRepository;
import com.kaneko.estacionamento.financeiro.dto.ContaReceberResponse;
import com.kaneko.estacionamento.fiscal.dto.DocumentosOrdemServicoResponse;
import com.kaneko.estacionamento.fiscal.dto.GerarNotaOrdemServicoRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaServicoRequest;
import com.kaneko.estacionamento.fiscal.dto.NotaServicoResponse;
import com.kaneko.estacionamento.ordensservico.OrdemServicoRepository;
import com.kaneko.estacionamento.ordensservico.StatusOrdemServico;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrdemServicoDocumentoService {
    private static final String OPERACAO = "GERAR_NOTA_ORDEM_SERVICO";
    private final OrdemServicoRepository ordens;
    private final NotaServicoRepository notas;
    private final NotaServicoService fiscal;
    private final ContaReceberRepository contas;
    private final IdempotenciaRepository idempotencia;

    public OrdemServicoDocumentoService(OrdemServicoRepository ordens,
            NotaServicoRepository notas, NotaServicoService fiscal,
            ContaReceberRepository contas, IdempotenciaRepository idempotencia) {
        this.ordens = ordens;
        this.notas = notas;
        this.fiscal = fiscal;
        this.contas = contas;
        this.idempotencia = idempotencia;
    }

    @Transactional(readOnly = true)
    public DocumentosOrdemServicoResponse documentos(Long org, Long id,
            boolean lerFiscal, boolean lerFinanceiro) {
        ordens.findByIdAndOrganizacaoId(id, org).orElseThrow(() ->
                new RecursoNaoEncontradoException("OrdemServico", id));
        var nota = notas.findByOrganizacaoIdAndOrdemServicoId(org, id);
        return new DocumentosOrdemServicoResponse(
                lerFiscal ? nota.map(NotaServicoResponse::from).orElse(null) : null,
                !lerFinanceiro ? null : nota.map(n -> contas
                        .findByNotaServicoIdAndOrganizacaoId(n.getId(), org).stream()
                        .map(ContaReceberResponse::from).toList()).orElse(List.of()));
    }

    @Transactional
    public NotaServicoResponse gerar(Long org, Long ator, Long id, String chave,
            GerarNotaOrdemServicoRequest request) {
        if (chave == null || chave.isBlank() || chave.length() > 128)
            throw new IllegalArgumentException("Informe uma chave de idempotência de até 128 caracteres.");
        String hash = hash(id + ":" + request);
        idempotencia.bloquear(org, ator, OPERACAO, chave);
        var anterior = idempotencia.buscar(org, ator, OPERACAO, chave);
        if (anterior.isPresent()) {
            if (!hash.equals(anterior.get().requestHash()))
                throw new ConflitoException("A chave de idempotência já foi usada com outros dados.");
            return NotaServicoResponse.from(fiscal.buscar(org, anterior.get().recursoId()));
        }
        var ordem = ordens.findParaAtualizar(id, org).orElseThrow(() ->
                new RecursoNaoEncontradoException("OrdemServico", id));
        if (ordem.getStatus() != StatusOrdemServico.CONCLUIDA)
            throw new ConflitoException("Conclua a ordem de serviço antes de preparar sua nota.");
        if (notas.existsByOrdemServicoId(id))
            throw new ConflitoException("Esta ordem já possui nota. Consulte o documento vinculado.");
        var nota = fiscal.criar(org, new NotaServicoRequest(request.numero(), request.modelo(),
                request.serie(), ordem.getCliente().getId(), null, null, null, id,
                request.condicaoPagamentoId(), request.formaPagamentoId(), request.dataEmissao(),
                ordem.getSubtotal(), request.aliquotaIss(), ordem.getValorDesconto(), ordem.getObservacao()));
        idempotencia.registrar(org, ator, OPERACAO, chave, hash, "NOTA_SERVICO", nota.getId());
        return NotaServicoResponse.from(nota);
    }

    private static String hash(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
