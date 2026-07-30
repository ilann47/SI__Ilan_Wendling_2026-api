package com.kaneko.estacionamento.inventario.produto;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.idempotencia.IdempotenciaRepository;
import com.kaneko.estacionamento.inventario.alocacao.AlocacaoPatioEventoRepository;
import com.kaneko.estacionamento.inventario.produto.dto.CriarLotePrecoRequest;
import com.kaneko.estacionamento.inventario.produto.dto.LotePrecoResponse;
import com.kaneko.estacionamento.organizacao.OrganizacaoRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LotePrecoService {
    private static final String OPERACAO_CRIAR = "LOTE_PRECO_CRIAR";
    private static final String RECURSO = "LOTE_PRECO";

    private final LotePrecoRepository lotes;
    private final ProdutoEstacionamentoRepository produtos;
    private final OrganizacaoRepository organizacoes;
    private final AlocacaoPatioEventoRepository alocacoes;
    private final IdempotenciaRepository idempotencia;
    private final AuditoriaService auditoria;

    public LotePrecoService(
            LotePrecoRepository lotes, ProdutoEstacionamentoRepository produtos,
            OrganizacaoRepository organizacoes, AlocacaoPatioEventoRepository alocacoes,
            IdempotenciaRepository idempotencia, AuditoriaService auditoria) {
        this.lotes = lotes;
        this.produtos = produtos;
        this.organizacoes = organizacoes;
        this.alocacoes = alocacoes;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
    }

    @Transactional
    public LotePrecoResponse criar(
            Long organizacaoId, Long atorId, Long produtoId,
            String chaveIdempotencia, CriarLotePrecoRequest request) {
        String chave = validarChave(chaveIdempotencia);
        validarRequest(request);
        String requestHash = hash(produtoId, request);

        idempotencia.bloquear(organizacaoId, atorId, OPERACAO_CRIAR, chave);
        var anterior = idempotencia.buscar(
                organizacaoId, atorId, OPERACAO_CRIAR, chave);
        if (anterior.isPresent()) {
            if (!requestHash.equals(anterior.get().requestHash())) {
                throw new ConflitoException(
                        "A chave de idempotencia ja foi usada com outro payload.");
            }
            return LotePrecoResponse.from(
                    lotes.findByIdAndOrganizacaoId(anterior.get().recursoId(), organizacaoId)
                            .orElseThrow(() -> new ConflitoException(
                                    "O resultado idempotente nao esta mais disponivel.")));
        }

        ProdutoEstacionamento produto = produtos.findByIdAndOrganizacaoIdForUpdate(
                        produtoId, organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Produto de estacionamento", produtoId));
        if (produto.getStatus() != StatusProdutoEstacionamento.RASCUNHO) {
            throw new ConflitoException(
                    "Somente produto em rascunho pode receber lote de preco.");
        }
        String moedaOrganizacao = organizacoes.findById(organizacaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Organizacao", organizacaoId))
                .getMoeda();
        if (!moedaOrganizacao.equalsIgnoreCase(request.currency().trim())) {
            throw new IllegalArgumentException(
                    "A moeda do lote deve ser a moeda da organizacao.");
        }
        if (request.quantity() > produto.getQuota()) {
            throw new ConflitoException(
                    "A quantidade do lote nao pode exceder a quota do produto.");
        }
        if (lotes.existeNomeNormalizado(produtoId, request.name())) {
            throw new ConflitoException("Ja existe lote com este nome no produto.");
        }
        if (lotes.existeSobreposicaoAmbigua(
                produtoId, request.priority(), request.salesStartsAt(), request.salesEndsAt())) {
            throw new ConflitoException(
                    "Lotes sobrepostos devem possuir prioridades diferentes.");
        }

        LotePreco lote = LotePreco.criar(
                organizacaoId, produtoId, request.name(), request.price(), request.currency(),
                request.salesStartsAt(), request.salesEndsAt(), request.quantity(),
                request.priority());
        lote = lotes.saveAndFlush(lote);
        Long patioId = alocacoes.findByIdAndOrganizacaoId(
                        produto.getAlocacaoId(), organizacaoId)
                .map(alocacao -> alocacao.getPatioId())
                .orElse(null);
        Map<String, Object> estado = snapshot(lote);
        auditoria.registrar(new RegistrarAuditoriaCommand(
                organizacaoId, "LOTE_PRECO_CRIADO", RECURSO, lote.getId(),
                produto.getEventoId(), patioId, null, null, estado,
                null, lote.getVersion(), ClasseRetencaoAuditoria.CONFIGURACAO));
        idempotencia.registrar(
                organizacaoId, atorId, OPERACAO_CRIAR, chave,
                requestHash, RECURSO, lote.getId());
        return LotePrecoResponse.from(lote);
    }

    private static void validarRequest(CriarLotePrecoRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("O lote de preco e obrigatorio.");
        }
        LotePreco.validarDados(
                request.name(), request.price(), request.currency(), request.salesStartsAt(),
                request.salesEndsAt(), request.quantity(), request.priority());
    }

    private static String validarChave(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O cabecalho Idempotency-Key e obrigatorio.");
        }
        String chave = valor.trim();
        if (chave.length() > 100) {
            throw new IllegalArgumentException(
                    "Idempotency-Key deve ter no maximo 100 caracteres.");
        }
        return chave;
    }

    private static String hash(Long produtoId, CriarLotePrecoRequest request) {
        String canonico = campo(produtoId)
                + campo(request.name().trim())
                + campo(request.price().stripTrailingZeros().toPlainString())
                + campo(request.currency().trim().toUpperCase(java.util.Locale.ROOT))
                + campo(request.salesStartsAt().toInstant())
                + campo(request.salesEndsAt().toInstant())
                + campo(request.quantity())
                + campo(request.priority());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonico.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel.", ex);
        }
    }

    private static String campo(Object valor) {
        String texto = String.valueOf(valor);
        return texto.length() + ":" + texto;
    }

    private static Map<String, Object> snapshot(LotePreco lote) {
        Map<String, Object> estado = new LinkedHashMap<>();
        estado.put("produtoId", lote.getProdutoId());
        estado.put("nome", lote.getNome());
        estado.put("preco", lote.getPreco().toPlainString());
        estado.put("moeda", lote.getMoeda());
        estado.put("inicioVenda", lote.getInicioVenda().toInstant().toString());
        estado.put("fimVenda", lote.getFimVenda().toInstant().toString());
        estado.put("quantidade", lote.getQuantidade());
        estado.put("prioridade", lote.getPrioridade());
        return estado;
    }
}
