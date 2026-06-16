package com.kaneko.estacionamento.common.web;

import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Fabrica de {@link Specification} reutilizaveis para os filtros opcionais das
 * listagens. Cada metodo devolve {@code null} quando o valor do filtro nao foi
 * informado, e {@link #todos} combina apenas os filtros presentes — evitando o
 * padrao {@code :param IS NULL OR ...} (que no PostgreSQL falha por nao inferir
 * o tipo do parametro).
 */
public final class FiltroSpecs {

    private FiltroSpecs() {
    }

    /** Casa quando o campo textual contem o valor (case-insensitive). */
    public static <T> Specification<T> contemIgnoreCase(String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String termo = "%" + valor.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get(campo)), termo);
    }

    /** Igualdade exata (usado para enums, boolean de situacao, etc.). */
    public static <T> Specification<T> igual(String campo, Object valor) {
        if (valor == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(campo), valor);
    }

    /** Igualdade pelo id de um relacionamento (FK), ex.: {@code estado.pais.id}. */
    public static <T> Specification<T> fkId(String relacao, Long id) {
        if (id == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(relacao).get("id"), id);
    }

    /** Combina (AND) apenas os filtros nao nulos; {@code null} significa "sem filtro". */
    @SafeVarargs
    public static <T> Specification<T> todos(Specification<T>... specs) {
        Specification<T> resultado = null;
        for (Specification<T> spec : specs) {
            if (spec != null) {
                resultado = (resultado == null) ? spec : resultado.and(spec);
            }
        }
        return resultado;
    }
}
