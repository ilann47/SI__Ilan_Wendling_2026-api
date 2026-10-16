package com.kaneko.estacionamento.estoque;

import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.estoque.dto.OrigemMovimentoResponse;
import com.kaneko.estacionamento.rbac.RbacService;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrigemMovimentoService {
    private final MovimentoEstoqueRepository movimentos;
    private final JdbcTemplate jdbc;
    private final RbacService rbac;
    public OrigemMovimentoService(MovimentoEstoqueRepository movimentos, JdbcTemplate jdbc, RbacService rbac) {
        this.movimentos = movimentos; this.jdbc = jdbc; this.rbac = rbac;
    }

    @Transactional(readOnly = true)
    public OrigemMovimentoResponse buscar(Long org, Long membership, Long id) {
        MovimentoEstoque m = movimentos.findByIdAndOrganizacaoId(id, org)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Movimento de estoque não encontrado."));
        String tipo = m.getOrigemTipo();
        if (m.getCompensadoId() != null) return new OrigemMovimentoResponse("COMPENSACAO_MOVIMENTO", m.getCompensadoId(),
                "Ver movimento original compensado", "/app/estoque?tab=razao&detail=" + m.getCompensadoId());
        if ("AJUSTE_MANUAL".equals(tipo)) return indisponivel(tipo, "Ajuste manual de estoque");
        String permissao = switch (tipo) {
            case "NOTA_ENTRADA", "NOTA_SAIDA" -> "fiscal:read";
            case "VENDA_ADMINISTRATIVA" -> "sales:read";
            case "ORDEM_COMPRA" -> "purchases:read";
            default -> null;
        };
        if (permissao == null) return indisponivel(tipo, "Origem sem documento navegável");
        Set<String> permissoes = Set.copyOf(rbac.permissoes(membership, org));
        if (!permissoes.contains(permissao)) return indisponivel(tipo, "Seu acesso não permite consultar o documento de origem.");
        if ("ORDEM_COMPRA".equals(tipo)) {
            List<OrigemMovimentoResponse> origens = jdbc.query("""
                SELECT r.id, c.id ordem_id, c.numero FROM item_recebimento_compra i
                JOIN recebimento_compra r ON r.id=i.recebimento_compra_id AND r.organizacao_id=i.organizacao_id
                JOIN ordem_compra c ON c.id=r.ordem_compra_id AND c.organizacao_id=r.organizacao_id
                WHERE i.organizacao_id=? AND i.movimento_estoque_id=?
                """, (rs, row) -> new OrigemMovimentoResponse("RECEBIMENTO_COMPRA", rs.getLong("id"),
                    "Recebimento da ordem " + rs.getString("numero"), "/app/ordens-compra?detail=" + rs.getLong("ordem_id") + "&recebimentoId=" + rs.getLong("id")), org, id);
            return origens.isEmpty() ? indisponivel(tipo, "Recebimento de origem não disponível") : origens.getFirst();
        }
        // Tabelas e caminhos vêm exclusivamente desta lista, nunca da requisição ou do banco.
        String tabela, descricao, caminho;
        switch (tipo) {
            case "NOTA_ENTRADA" -> { tabela = "nota_entrada"; descricao = "Nota de entrada "; caminho = "/app/notas-entrada/"; }
            case "NOTA_SAIDA" -> { tabela = "nota_saida"; descricao = "Nota de saída "; caminho = "/app/notas-saida?detail="; }
            case "VENDA_ADMINISTRATIVA" -> { tabela = "venda_administrativa"; descricao = "Pedido de venda "; caminho = "/app/vendas-administrativas?detail="; }
            default -> throw new IllegalStateException("Tipo de origem não suportado.");
        }
        Long origemId = primeiroId(m.getOrigemChave());
        if (origemId == null && chaveLegadaPorNumero(m.getOrigemChave())
                && ("NOTA_ENTRADA".equals(tipo) || "NOTA_SAIDA".equals(tipo))) {
            List<Long> ids = jdbc.query("SELECT id FROM " + tabela + " WHERE organizacao_id=? AND numero=?",
                    (rs, row) -> rs.getLong("id"), org, m.getOrigemChave());
            origemId = ids.isEmpty() ? null : ids.getFirst();
        }
        if (origemId == null) return indisponivel(tipo, "Documento de origem não identificado");
        List<String> numeros = jdbc.query("SELECT numero FROM " + tabela + " WHERE organizacao_id=? AND id=?",
                (rs, row) -> rs.getString("numero"), org, origemId);
        return numeros.isEmpty() ? indisponivel(tipo, "Documento de origem não disponível")
                : new OrigemMovimentoResponse(tipo, origemId, descricao + numeros.getFirst(), caminho + origemId);
    }

    private static Long primeiroId(String chave) {
        if (chave == null) return null;
        String primeiro = chave.split(":", 2)[0];
        if (!primeiro.matches("[1-9][0-9]*")) return null;
        try { return Long.valueOf(primeiro); } catch (NumberFormatException ex) { return null; }
    }
    private static boolean chaveLegadaPorNumero(String chave) {
        return chave != null && !chave.isBlank() && !chave.contains(":");
    }
    private static OrigemMovimentoResponse indisponivel(String tipo, String descricao) {
        return new OrigemMovimentoResponse(tipo, null, descricao, null);
    }
}
