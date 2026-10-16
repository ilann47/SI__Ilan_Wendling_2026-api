package com.kaneko.estacionamento.consulta;

import com.kaneko.estacionamento.consulta.dto.PendenciasOperacionaisResponse;
import com.kaneko.estacionamento.consulta.dto.PendenciasOperacionaisResponse.Grupo;
import com.kaneko.estacionamento.consulta.dto.PendenciasOperacionaisResponse.Item;
import com.kaneko.estacionamento.rbac.RbacService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PendenciasOperacionaisService {
    private final JdbcTemplate jdbc;
    private final RbacService rbac;
    public PendenciasOperacionaisService(JdbcTemplate jdbc, RbacService rbac) { this.jdbc = jdbc; this.rbac = rbac; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PendenciasOperacionaisResponse listar(Long org, Long membership, int limite) {
        if (limite < 1 || limite > 10) throw new IllegalArgumentException("O limite deve estar entre 1 e 10.");
        Set<String> permissoes = Set.copyOf(rbac.permissoes(membership, org));
        String fuso = jdbc.queryForObject("SELECT fuso_horario FROM organizacao WHERE id=?", String.class, org);
        LocalDate hoje = LocalDate.now(ZoneId.of(fuso));
        List<Grupo> grupos = new ArrayList<>();
        if (permissoes.contains("finance:read")) {
            grupos.add(consultar("DESPESA_AVULSA_VENCIDA", limite, """
                SELECT c.id, concat('Pagar despesa ',c.descricao) titulo, coalesce(f.nome,'Despesa avulsa') contraparte,
                       concat('/app/contas-pagar-avulsas?detail=',c.id) caminho, c.data_vencimento data,
                       c.valor_total-c.valor_pago valor, count(*) OVER() total
                FROM contas_pagar_avulsa c LEFT JOIN fornecedor f ON f.id=c.fornecedor_id AND f.organizacao_id=c.organizacao_id
                WHERE c.organizacao_id=? AND c.situacao IN ('PENDENTE','PARCIAL')
                  AND c.valor_total>c.valor_pago AND c.data_vencimento<?
                ORDER BY c.data_vencimento,c.id LIMIT ?
                """, org, hoje, limite));
            grupos.add(consultar("CONTA_PAGAR_VENCIDA", limite, """
                SELECT c.id, concat('Pagar ',f.nome) titulo, f.nome contraparte,
                       concat('/app/contas-pagar?detail=',c.id) caminho, c.data_vencimento data,
                       c.valor_total-c.valor_pago valor, count(*) OVER() total
                FROM conta_pagar c JOIN fornecedor f ON f.id=c.fornecedor_id AND f.organizacao_id=c.organizacao_id
                WHERE c.organizacao_id=? AND c.situacao IN ('PENDENTE','PARCIAL')
                  AND c.valor_total>c.valor_pago AND c.data_vencimento<?
                ORDER BY c.data_vencimento,c.id LIMIT ?
                """, org, hoje, limite));
            grupos.add(consultar("CONTA_RECEBER_VENCIDA", limite, """
                SELECT c.id, concat('Receber de ',p.nome) titulo, p.nome contraparte,
                       concat('/app/contas-receber?detail=',c.id) caminho, c.data_vencimento data,
                       c.valor_total-c.valor_recebido valor, count(*) OVER() total
                FROM conta_receber c JOIN cliente p ON p.id=c.cliente_id AND p.organizacao_id=c.organizacao_id
                WHERE c.organizacao_id=? AND c.situacao IN ('PENDENTE','PARCIAL')
                  AND c.valor_total>c.valor_recebido AND c.data_vencimento<?
                ORDER BY c.data_vencimento,c.id LIMIT ?
                """, org, hoje, limite));
        }
        if (permissoes.contains("purchases:read")) grupos.add(consultar("COMPRA_RECEBER", limite, """
            SELECT c.id, concat('Ordem ',c.numero,' aguarda recebimento') titulo, f.nome contraparte,
                   concat('/app/ordens-compra?detail=',c.id) caminho, coalesce(c.previsao_entrega,c.data_emissao) data,
                   c.valor_total valor, count(*) OVER() total
            FROM ordem_compra c JOIN fornecedor f ON f.id=c.fornecedor_id AND f.organizacao_id=c.organizacao_id
            WHERE c.organizacao_id=? AND c.status IN ('APROVADA','PARCIALMENTE_RECEBIDA')
            ORDER BY coalesce(c.previsao_entrega,c.data_emissao),c.id LIMIT ?
            """, org, limite));
        if (permissoes.contains("sales:read")) grupos.add(consultar("VENDA_SEM_NOTA", limite, """
            SELECT v.id, concat('Venda ',v.numero,' confirmada sem nota') titulo, c.nome contraparte,
                   concat('/app/vendas-administrativas?detail=',v.id) caminho, v.data_emissao data,
                   v.valor_total valor, count(*) OVER() total
            FROM venda_administrativa v JOIN cliente c ON c.id=v.cliente_id AND c.organizacao_id=v.organizacao_id
            WHERE v.organizacao_id=? AND v.status='CONFIRMADA' AND NOT EXISTS (
              SELECT 1 FROM nota_saida n WHERE n.organizacao_id=v.organizacao_id AND n.venda_administrativa_id=v.id)
            ORDER BY v.data_emissao,v.id LIMIT ?
            """, org, limite));
        if (permissoes.contains("service_orders:read")) grupos.add(consultar("SERVICO_SEM_NOTA", limite, """
            SELECT s.id, concat('Ordem ',s.numero,' concluída sem nota') titulo, c.nome contraparte,
                   concat('/app/ordens-servico?detail=',s.id) caminho, s.data_abertura data,
                   s.valor_total valor, count(*) OVER() total
            FROM ordem_servico s JOIN cliente c ON c.id=s.cliente_id AND c.organizacao_id=s.organizacao_id
            WHERE s.organizacao_id=? AND s.status='CONCLUIDA' AND NOT EXISTS (
              SELECT 1 FROM nota_servico n WHERE n.organizacao_id=s.organizacao_id AND n.ordem_servico_id=s.id)
            ORDER BY s.data_abertura,s.id LIMIT ?
            """, org, limite));
        return new PendenciasOperacionaisResponse(hoje, grupos.stream().mapToLong(Grupo::total).sum(), limite, List.copyOf(grupos));
    }

    private Grupo consultar(String tipo, int limite, String sql, Object... argumentos) {
        return jdbc.query(sql, rs -> {
            List<Item> itens = new ArrayList<>(limite); long total = 0;
            while (rs.next()) {
                total = rs.getLong("total");
                itens.add(new Item(rs.getLong("id"),rs.getString("titulo"),rs.getString("contraparte"),
                        rs.getString("caminho"),rs.getObject("data",LocalDate.class),rs.getBigDecimal("valor")));
            }
            return new Grupo(tipo,total,List.copyOf(itens));
        }, argumentos);
    }
}
