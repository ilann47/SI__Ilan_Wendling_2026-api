package com.kaneko.estacionamento.opcoes;

import static com.kaneko.estacionamento.opcoes.OperationalOptionsController.Option;

import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.rbac.RbacService;
import java.sql.Types;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OperationalOptionsService {
    private static final String LOOKUP = """
            WITH options AS (%s)
            %s FROM options
            WHERE (:id IS NULL OR id = :id)
              AND (:q = '' OR strpos(translate(lower(label), :accents, :plain), :q) > 0 OR CAST(id AS text) = :q)
            """;
    private static final String ACCENTS = "áàâãäéèêëíìîïóòôõöúùûüç";
    private static final String PLAIN = "aaaaaeeeeiiiiooooouuuuc";

    private final NamedParameterJdbcTemplate jdbc;
    private final RbacService rbac;

    OperationalOptionsService(NamedParameterJdbcTemplate jdbc, RbacService rbac) {
        this.jdbc = jdbc;
        this.rbac = rbac;
    }

    @Transactional(readOnly = true)
    public Page<Option> list(UsuarioAutenticado principal, String kind, String q,
                             Long id, Long eventId, Pageable page) {
        if (principal == null || !principal.possuiOrganizacaoAtiva()) {
            throw new AuthorizationDeniedException("Contexto organizacional obrigatório.");
        }
        Set<String> permissions = Set.copyOf(rbac.permissoes(principal.membershipId(), principal.organizacaoId()));
        boolean access = allows(permissions, "access:validate", "access:checkin", "access:checkout");
        boolean readOrders = permissions.contains("orders:read");
        boolean confirmOrders = permissions.contains("orders:manual-confirm");
        boolean cancelOrders = permissions.contains("orders:cancel");
        boolean readMembers = permissions.contains("organizations:admin");
        boolean revokeRoles = permissions.contains("roles:revoke");
        boolean grantRoles = permissions.contains("roles:grant");

        Source source = switch (kind) {
            case "access-events" -> permitted(access, Source.ACCESS_EVENTS);
            case "audit-events" -> permitted(permissions.contains("audit:read"), Source.AUDIT_EVENTS);
            case "access-facilities" -> permitted(access, Source.ACCESS_FACILITIES);
            case "blocking-credentials" -> permitted(permissions.contains("credentials:block"), Source.BLOCKING_CREDENTIALS);
            case "sales-products" -> permitted(permissions.contains("inventory:hold"), Source.SALES_PRODUCTS);
            case "sales-holds" -> permitted(permissions.contains("inventory:hold"), Source.SALES_HOLDS);
            case "ordering-holds" -> permitted(permissions.contains("orders:create"), Source.ORDERING_HOLDS);
            case "sales-orders" -> permitted(readOrders || confirmOrders || cancelOrders, Source.SALES_ORDERS);
            case "issuing-orders" -> permitted(permissions.contains("credentials:issue"), Source.ISSUING_ORDERS);
            case "sales-credentials" -> permitted(permissions.contains("credentials:issue"), Source.SALES_CREDENTIALS);
            case "memberships" -> permitted(readMembers || grantRoles || revokeRoles, Source.MEMBERSHIPS);
            default -> throw new RecursoNaoEncontradoException("Tipo de seletor não encontrado.");
        };

        var params = new MapSqlParameterSource()
                .addValue("org", principal.organizacaoId(), Types.BIGINT)
                .addValue("actor", principal.userId(), Types.BIGINT)
                .addValue("readOrders", readOrders)
                .addValue("confirmOrders", confirmOrders)
                .addValue("cancelOrders", cancelOrders)
                .addValue("readMembers", readMembers)
                .addValue("grantRoles", grantRoles)
                .addValue("revokeRoles", revokeRoles)
                .addValue("q", Normalizer.normalize(q.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}+", ""))
                .addValue("accents", ACCENTS + ACCENTS.toUpperCase(Locale.ROOT))
                .addValue("plain", PLAIN + PLAIN)
                .addValue("id", id, Types.BIGINT)
                .addValue("eventId", eventId, Types.BIGINT)
                .addValue("size", page.getPageSize())
                .addValue("offset", page.getOffset());
        List<Option> content = jdbc.query(LOOKUP.formatted(source.sql, "SELECT id, label, version")
                        + " ORDER BY lower(label), id LIMIT :size OFFSET :offset", params,
                (rs, row) -> new Option(rs.getLong("id"), rs.getString("label"), rs.getLong("version")));
        long total = jdbc.queryForObject(LOOKUP.formatted(source.sql, "SELECT count(*)"), params, Long.class);
        return new PageImpl<>(content, page, total);
    }

    private static boolean allows(Set<String> permissions, String... codes) {
        for (String code : codes) {
            if (permissions.contains(code)) return true;
        }
        return false;
    }

    private static Source permitted(boolean allowed, Source source) {
        if (!allowed) throw new AuthorizationDeniedException("Permissão insuficiente para consultar estas opções.");
        return source;
    }

    private enum Source {
        ACCESS_EVENTS("""
                SELECT e.id, e.nome AS label, e.version FROM evento e
                WHERE e.organizacao_id = :org AND e.status = 'EM_OPERACAO'
                """),
        AUDIT_EVENTS("""
                SELECT e.id, e.nome AS label, e.version FROM evento e
                WHERE e.organizacao_id = :org AND EXISTS (
                    SELECT 1 FROM tentativa_acesso t WHERE t.organizacao_id = :org AND t.evento_id = e.id
                )
                """),
        ACCESS_FACILITIES("""
                SELECT p.id, p.nome AS label, p.version FROM patio p
                WHERE p.organizacao_id = :org AND p.ativo
                  AND EXISTS (
                    SELECT 1 FROM alocacao_patio_evento a
                    JOIN evento e ON e.id = a.evento_id AND e.organizacao_id = a.organizacao_id
                    WHERE a.organizacao_id = p.organizacao_id AND a.patio_id = p.id
                      AND e.status = 'EM_OPERACAO' AND (:eventId IS NULL OR e.id = :eventId)
                  )
                """),
        BLOCKING_CREDENTIALS("""
                SELECT c.id, concat(c.codigo_publico, ' · ', e.nome) AS label, c.version
                FROM credencial_acesso c JOIN evento e ON e.id = c.evento_id AND e.organizacao_id = c.organizacao_id
                WHERE c.organizacao_id = :org AND c.status <> 'BLOQUEADA'
                """),
        SALES_PRODUCTS("""
                SELECT p.id, concat(p.nome, ' · ', e.nome) AS label, p.version
                FROM produto_estacionamento p JOIN evento e ON e.id = p.evento_id AND e.organizacao_id = p.organizacao_id
                WHERE p.organizacao_id = :org AND p.status = 'PUBLICADO' AND e.status = 'VENDAS_ABERTAS'
                """),
        SALES_HOLDS(holds("")),
        ORDERING_HOLDS(holds("AND r.status = 'MANTIDA' AND r.expira_em > CURRENT_TIMESTAMP")),
        SALES_ORDERS(orders("""
                AND (:readOrders OR (:confirmOrders AND p.status = 'AGUARDANDO_PAGAMENTO')
                    OR (:cancelOrders AND p.status IN ('CRIADO','AGUARDANDO_PAGAMENTO','CONFIRMADO','ATENDIDO')))
                """)),
        ISSUING_ORDERS(orders("AND p.status = 'CONFIRMADO'")),
        SALES_CREDENTIALS("""
                SELECT c.id, concat(c.codigo_publico, ' · ', e.nome) AS label, c.version
                FROM credencial_acesso c JOIN evento e ON e.id = c.evento_id AND e.organizacao_id = c.organizacao_id
                WHERE c.organizacao_id = :org AND c.ator_id = :actor
                """),
        MEMBERSHIPS("""
                SELECT m.id, u.nome AS label, m.version
                FROM membro_organizacao m JOIN usuario u ON u.id = m.usuario_id
                WHERE m.organizacao_id = :org AND (:readMembers OR (:grantRoles AND m.status = 'ATIVO')
                  OR (:revokeRoles AND EXISTS (
                      SELECT 1 FROM atribuicao_acesso a
                      WHERE a.organizacao_id = :org AND a.membro_organizacao_id = m.id AND a.revogado_em IS NULL
                  )))
                """);

        private final String sql;

        Source(String sql) {
            this.sql = sql;
        }

        private static String holds(String eligibility) {
            return """
                    SELECT r.id, concat('Reserva ', r.id, ' · ', p.nome, ' · ', e.nome) AS label, r.version
                    FROM reserva_inventario r
                    JOIN produto_estacionamento p ON p.id = r.produto_id AND p.organizacao_id = r.organizacao_id
                    JOIN evento e ON e.id = r.evento_id AND e.organizacao_id = r.organizacao_id
                    WHERE r.organizacao_id = :org AND r.ator_id = :actor
                    """ + eligibility;
        }

        private static String orders(String eligibility) {
            return """
                    SELECT p.id, concat(p.numero, ' · ', e.nome) AS label, p.version
                    FROM pedido p JOIN evento e ON e.id = p.evento_id AND e.organizacao_id = p.organizacao_id
                    WHERE p.organizacao_id = :org AND p.ator_id = :actor
                    """ + eligibility;
        }
    }
}
