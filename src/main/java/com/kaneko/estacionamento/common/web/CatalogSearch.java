package com.kaneko.estacionamento.common.web;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/** Critérios comuns dos catálogos pesquisáveis, com ordem estável e limite por página. */
public final class CatalogSearch {
    private static final String ACENTOS = "áàâãäéèêëíìîïóòôõöúùûüç";
    private static final String SIMPLES = "aaaaaeeeeiiiiooooouuuuc";

    private CatalogSearch() {}

    public static PageRequest page(int page, int size) {
        return page(page, size, Sort.by("nome", "id"));
    }

    public static PageRequest page(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Informe página não negativa e tamanho entre 1 e 100.");
        }
        return PageRequest.of(page, size, sort);
    }

    public static <T> Specification<T> ativos(Long organizacaoId, String name, Long id) {
        return FiltroSpecs.todos(CatalogSearch.<T>daOrganizacao(organizacaoId, name),
                FiltroSpecs.igual("ativo", true), FiltroSpecs.igual("id", id));
    }

    public static <T> Specification<T> daOrganizacao(Long organizacaoId, String name) {
        return FiltroSpecs.todos(FiltroSpecs.igual("organizacaoId", organizacaoId), porNome(name));
    }

    public static <T> Specification<T> porNome(String name) {
        if (name == null || name.isBlank()) return null;
        if (name.length() > 160) throw new IllegalArgumentException("A busca por nome aceita até 160 caracteres.");
        String termo = Normalizer.normalize(name.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return (root, query, cb) -> cb.gt(cb.locate(cb.function("translate", String.class,
                cb.lower(root.<String>get("nome")),
                // Bancos com locale C não reduzem necessariamente Á/É/Ç em lower().
                cb.literal(ACENTOS + ACENTOS.toUpperCase(Locale.ROOT)),
                cb.literal(SIMPLES + SIMPLES)), termo), 0);
    }

    public record Option(Long id, String name) {}
    public record ManagementRow(Long id, String name, boolean active) {}
}
