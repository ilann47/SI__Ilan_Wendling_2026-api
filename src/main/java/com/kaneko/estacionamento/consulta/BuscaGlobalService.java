package com.kaneko.estacionamento.consulta;

import com.kaneko.estacionamento.consulta.dto.BuscaGlobalResponse;
import com.kaneko.estacionamento.consulta.dto.GrupoBuscaGlobal;
import com.kaneko.estacionamento.consulta.dto.ResultadoBuscaGlobal;
import com.kaneko.estacionamento.rbac.RbacService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuscaGlobalService {

    private final JdbcTemplate jdbc;
    private final RbacService rbac;

    public BuscaGlobalService(JdbcTemplate jdbc, RbacService rbac) {
        this.jdbc = jdbc;
        this.rbac = rbac;
    }

    @Transactional(readOnly = true)
    public BuscaGlobalResponse buscar(Long organizacaoId, Long membershipId, String termo, int limite) {
        String consulta = termo == null ? "" : termo.trim();
        if (consulta.length() < 2 || consulta.length() > 100) {
            throw new IllegalArgumentException("A busca deve possuir entre 2 e 100 caracteres.");
        }
        if (limite < 1 || limite > 10) {
            throw new IllegalArgumentException("O limite deve estar entre 1 e 10.");
        }
        Set<String> permissoes = Set.copyOf(rbac.permissoes(membershipId, organizacaoId));
        String padrao = "%" + consulta.toLowerCase() + "%";
        List<GrupoBuscaGlobal> grupos = new ArrayList<>();

        adicionar(grupos, "Clientes", permissoes.contains("customers:read"), limite, organizacaoId, padrao, """
                SELECT 'CLIENTE', c.id, c.nome, c.documento, CASE WHEN c.ativo THEN 'ATIVO' ELSE 'INATIVO' END,
                       concat('/app/clientes?detail=', c.id)
                FROM cliente c WHERE c.organizacao_id=? AND
                  (lower(c.nome) LIKE ? OR lower(coalesce(c.apelido,'')) LIKE ? OR lower(c.documento) LIKE ?)
                ORDER BY c.nome LIMIT ?
                """);
        adicionar(grupos, "Veículos", permissoes.contains("operations:read"), limite, organizacaoId, padrao, """
                SELECT 'VEICULO', v.id, v.placa, concat_ws(' · ', v.modelo, c.nome), CASE WHEN v.ativo THEN 'ATIVO' ELSE 'INATIVO' END,
                       concat('/app/veiculos?detail=', v.id)
                FROM veiculo v LEFT JOIN cliente c ON c.id=v.cliente_id AND c.organizacao_id=v.organizacao_id
                WHERE v.organizacao_id=? AND (lower(v.placa) LIKE ? OR lower(coalesce(v.modelo,'')) LIKE ? OR lower(coalesce(c.nome,'')) LIKE ?)
                ORDER BY v.placa LIMIT ?
                """);
        adicionar(grupos, "Pedidos", permissoes.contains("orders:read"), limite, organizacaoId, padrao, """
                SELECT 'PEDIDO', p.id, p.numero, concat_ws(' · ', e.nome, p.placa_veiculo), p.status, '/app/vendas'
                FROM pedido p JOIN evento e ON e.id=p.evento_id AND e.organizacao_id=p.organizacao_id
                WHERE p.organizacao_id=? AND (lower(p.numero) LIKE ? OR lower(coalesce(p.placa_veiculo,'')) LIKE ? OR lower(e.nome) LIKE ?)
                ORDER BY p.created_at DESC LIMIT ?
                """);
        adicionar(grupos, "Eventos", permissoes.contains("events:read"), limite, organizacaoId, padrao, """
                SELECT 'EVENTO', e.id, e.nome, coalesce(e.external_id,''), e.status, '/app/eventos'
                FROM evento e WHERE e.organizacao_id=? AND
                  (lower(e.nome) LIKE ? OR lower(coalesce(e.external_id,'')) LIKE ? OR CAST(e.id AS text) LIKE ?)
                ORDER BY e.inicio DESC LIMIT ?
                """);
        adicionar(grupos, "Movimentações", permissoes.contains("operations:read"), limite, organizacaoId, padrao, """
                SELECT 'MOVIMENTACAO', m.id, v.placa, concat_ws(' · ', v.modelo, m.tipo), m.status,
                       concat('/app/movimentacoes?detail=', m.id)
                FROM movimentacao m JOIN veiculo v ON v.id=m.veiculo_id AND v.organizacao_id=m.organizacao_id
                WHERE m.organizacao_id=? AND (lower(v.placa) LIKE ? OR lower(coalesce(v.modelo,'')) LIKE ? OR CAST(m.id AS text) LIKE ?)
                ORDER BY m.data_entrada DESC LIMIT ?
                """);
        adicionar(grupos, "Notas", permissoes.contains("fiscal:read"), limite, organizacaoId, padrao, """
                SELECT tipo,id,titulo,subtitulo,status,caminho FROM (
                  SELECT 'NOTA_ENTRADA' tipo,n.id,n.numero titulo,f.nome subtitulo,n.situacao status,concat('/app/notas-entrada/', n.id) caminho,n.data_emissao data
                    FROM nota_entrada n JOIN fornecedor f ON f.id=n.fornecedor_id AND f.organizacao_id=n.organizacao_id WHERE n.organizacao_id=?
                  UNION ALL SELECT 'NOTA_SAIDA',n.id,n.numero,c.nome,n.situacao,concat('/app/notas-saida?detail=', n.id),n.data_emissao
                    FROM nota_saida n JOIN cliente c ON c.id=n.cliente_id AND c.organizacao_id=n.organizacao_id WHERE n.organizacao_id=?
                  UNION ALL SELECT 'NOTA_SERVICO',n.id,n.numero,c.nome,n.situacao,concat('/app/notas-servico?detail=', n.id),n.data_emissao
                    FROM nota_servico n JOIN cliente c ON c.id=n.cliente_id AND c.organizacao_id=n.organizacao_id WHERE n.organizacao_id=?
                ) x WHERE lower(titulo) LIKE ? OR lower(subtitulo) LIKE ? OR CAST(id AS text) LIKE ? ORDER BY data DESC LIMIT ?
                """, true);
        adicionar(grupos, "Contas", permissoes.contains("finance:read"), limite, organizacaoId, padrao, """
                SELECT tipo,id,titulo,subtitulo,status,caminho FROM (
                  SELECT 'CONTA_PAGAR' tipo,cp.id,concat('Pagar #',cp.id) titulo,f.nome subtitulo,cp.situacao status,concat('/app/contas-pagar?detail=', cp.id) caminho,cp.data_vencimento data
                    FROM conta_pagar cp JOIN fornecedor f ON f.id=cp.fornecedor_id AND f.organizacao_id=cp.organizacao_id WHERE cp.organizacao_id=?
                  UNION ALL SELECT 'CONTA_RECEBER',cr.id,concat('Receber #',cr.id),c.nome,cr.situacao,concat('/app/contas-receber?detail=', cr.id),cr.data_vencimento
                    FROM conta_receber cr JOIN cliente c ON c.id=cr.cliente_id AND c.organizacao_id=cr.organizacao_id WHERE cr.organizacao_id=?
                ) x WHERE lower(titulo) LIKE ? OR lower(subtitulo) LIKE ? OR CAST(id AS text) LIKE ? ORDER BY data DESC LIMIT ?
                """, false);

        int total = grupos.stream().mapToInt(g -> g.resultados().size()).sum();
        return new BuscaGlobalResponse(consulta, total, grupos);
    }

    private void adicionar(List<GrupoBuscaGlobal> grupos, String grupo, boolean permitido, int limite,
                           Long organizacaoId, String padrao, String sql) {
        adicionar(grupos, grupo, permitido, limite, organizacaoId, padrao, sql, null);
    }

    private void adicionar(List<GrupoBuscaGlobal> grupos, String grupo, boolean permitido, int limite,
                           Long organizacaoId, String padrao, String sql, Boolean tresTenants) {
        if (!permitido) return;
        Object[] parametros = tresTenants == null
                ? new Object[]{organizacaoId, padrao, padrao, padrao, limite}
                : Boolean.TRUE.equals(tresTenants)
                    ? new Object[]{organizacaoId, organizacaoId, organizacaoId, padrao, padrao, padrao, limite}
                    : new Object[]{organizacaoId, organizacaoId, padrao, padrao, padrao, limite};
        List<ResultadoBuscaGlobal> resultados = jdbc.query(sql, (rs, row) -> new ResultadoBuscaGlobal(
                rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)), parametros);
        if (!resultados.isEmpty()) grupos.add(new GrupoBuscaGlobal(grupo, resultados));
    }
}
