package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.financeiro.dto.FinanceiroFiltro;
import com.kaneko.estacionamento.financeiro.dto.FinanceiroResumoResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Projeção financeira compartilhada. Predicados únicos impedem divergência página/totais. */
@Service
@Transactional(readOnly = true)
public class FinanceiroConsultaService {
    private final EntityManager em;
    public FinanceiroConsultaService(EntityManager em) { this.em = em; }

    public <T> Page<T> listar(Class<T> tipo, Long org, FinanceiroFiltro filtro, Pageable page) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<T> query = cb.createQuery(tipo);
        Root<T> root = query.from(tipo);
        boolean pagar = tipo == ContaPagar.class;
        root.fetch(pagar ? "fornecedor" : "cliente", JoinType.LEFT);
        root.fetch("formaPagamento", JoinType.LEFT);
        root.fetch("condicaoPagamento", JoinType.LEFT);
        for (String origem : origens(pagar)) root.fetch(origem, JoinType.LEFT);
        query.where(predicados(root, cb, org, filtro, pagar));
        List<Order> orders = new ArrayList<>();
        Set<String> permitidos = Set.of("id", "dataEmissao", "dataVencimento", "valorOriginal", "valorTotal",
                "valorPago", "valorRecebido", "situacao", "numeroParcela", "createdAt");
        for (Sort.Order order : page.getSort()) {
            String property = order.getProperty();
            if (!permitidos.contains(property) || (pagar && property.equals("valorRecebido"))
                    || (!pagar && property.equals("valorPago"))) {
                throw new ConflitoException("Ordenação financeira não suportada: " + property);
            }
            orders.add(order.isAscending() ? cb.asc(root.get(property)) : cb.desc(root.get(property)));
        }
        if (orders.isEmpty()) orders.add(cb.asc(root.get("dataVencimento")));
        orders.add(cb.asc(root.get("id")));
        query.orderBy(orders);
        var typed = em.createQuery(query);
        if (page.isPaged()) typed.setFirstResult(Math.toIntExact(page.getOffset())).setMaxResults(page.getPageSize());
        CriteriaQuery<Long> count = cb.createQuery(Long.class);
        Root<T> countRoot = count.from(tipo);
        count.select(cb.count(countRoot)).where(predicados(countRoot, cb, org, filtro, pagar));
        return new PageImpl<>(typed.getResultList(), page, em.createQuery(count).getSingleResult());
    }

    public FinanceiroResumoResponse resumo(Class<?> tipo, Long org, FinanceiroFiltro filtro) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<?> root = query.from(tipo);
        boolean pagar = tipo == ContaPagar.class;
        String baixado = pagar ? "valorPago" : "valorRecebido";
        Expression<BigDecimal> saldo = cb.<BigDecimal>selectCase()
                .when(cb.equal(root.get("situacao"), pagar ? SituacaoContaPagar.CANCELADA : SituacaoContaReceber.CANCELADA), BigDecimal.ZERO)
                .otherwise(cb.diff(root.<BigDecimal>get("valorTotal"), root.<BigDecimal>get(baixado)));
        query.multiselect(cb.count(root), cb.coalesce(cb.sum(root.<BigDecimal>get("valorOriginal")), BigDecimal.ZERO),
                cb.coalesce(cb.sum(root.<BigDecimal>get("valorTotal")), BigDecimal.ZERO),
                cb.coalesce(cb.sum(root.<BigDecimal>get(baixado)), BigDecimal.ZERO),
                cb.coalesce(cb.sum(saldo), BigDecimal.ZERO));
        query.where(predicados(root, cb, org, filtro, pagar));
        Tuple r = em.createQuery(query).getSingleResult();
        return new FinanceiroResumoResponse(r.get(0, Long.class), r.get(1, BigDecimal.class),
                r.get(2, BigDecimal.class), r.get(3, BigDecimal.class), r.get(4, BigDecimal.class));
    }

    private Predicate[] predicados(Root<?> root, CriteriaBuilder cb, Long org, FinanceiroFiltro f, boolean pagar) {
        List<Predicate> p = new ArrayList<>();
        p.add(org == null ? cb.isNull(root.get("organizacaoId")) : cb.equal(root.get("organizacaoId"), org));
        String pessoa = pagar ? "fornecedor" : "cliente";
        Long pessoaId = pagar ? f.fornecedorId() : f.clienteId();
        if (pessoaId != null) p.add(cb.equal(root.get(pessoa).get("id"), pessoaId));
        if (f.situacao() != null && !f.situacao().isBlank()) {
            try {
                p.add(cb.equal(root.get("situacao"), pagar ? SituacaoContaPagar.valueOf(f.situacao())
                        : SituacaoContaReceber.valueOf(f.situacao())));
            } catch (IllegalArgumentException ex) { throw new ConflitoException("Situação financeira inválida."); }
        }
        if (f.dataVencimentoDe() != null) p.add(cb.greaterThanOrEqualTo(root.get("dataVencimento"), f.dataVencimentoDe()));
        if (f.dataVencimentoAte() != null) p.add(cb.lessThanOrEqualTo(root.get("dataVencimento"), f.dataVencimentoAte()));
        Map<String, Long> ids = new HashMap<>();
        ids.put("notaEntrada", f.notaEntradaId()); ids.put("notaSaida", f.notaSaidaId());
        ids.put("notaServico", f.notaServicoId()); ids.put("vendaAdministrativa", f.vendaAdministrativaId());
        ids.forEach((campo, id) -> { if (id != null) p.add(origens(pagar).contains(campo)
                ? cb.equal(root.get(campo).get("id"), id) : cb.disjunction()); });
        if (f.origem() != null) {
            String campo = switch (f.origem()) {
                case NOTA_ENTRADA -> "notaEntrada"; case NOTA_SAIDA -> "notaSaida";
                case NOTA_SERVICO -> "notaServico"; case VENDA -> "vendaAdministrativa";
                case MENSALIDADE -> "mensalista"; case MANUAL -> null;
            };
            if (campo == null) for (String origem : origens(pagar)) p.add(cb.isNull(root.get(origem)));
            else p.add(origens(pagar).contains(campo) ? cb.isNotNull(root.get(campo)) : cb.disjunction());
        }
        if (f.search() != null && !f.search().isBlank()) {
            String escaped = f.search().trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_");
            String like = "%" + escaped + "%";
            List<Predicate> search = new ArrayList<>();
            var person = root.join(pessoa, JoinType.LEFT);
            search.add(cb.like(cb.lower(person.get("nome")), like, '\\'));
            search.add(cb.like(cb.lower(person.get("documento")), like, '\\'));
            search.add(cb.like(cb.lower(root.get("observacao")), like, '\\'));
            for (String origem : origens(pagar)) {
                if (origem.startsWith("nota")) search.add(cb.like(cb.lower(root.join(origem, JoinType.LEFT).get("numero")), like, '\\'));
            }
            if (f.search().trim().matches("[0-9]{1,18}")) search.add(cb.equal(root.get("id"), Long.parseLong(f.search().trim())));
            p.add(cb.or(search.toArray(Predicate[]::new)));
        }
        return p.toArray(Predicate[]::new);
    }

    private List<String> origens(boolean pagar) {
        return pagar ? List.of("notaEntrada") : List.of("notaSaida", "notaServico", "vendaAdministrativa", "mensalista");
    }
}
