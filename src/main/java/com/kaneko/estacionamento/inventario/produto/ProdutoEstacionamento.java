package com.kaneko.estacionamento.inventario.produto;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "produto_estacionamento")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProdutoEstacionamento extends AuditableEntity {
    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(name = "evento_id", nullable = false, updatable = false)
    private Long eventoId;
    @Column(name = "alocacao_id", nullable = false, updatable = false)
    private Long alocacaoId;
    @Column(nullable = false, length = 160)
    private String nome;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CategoriaProduto categoria;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DireitoProduto direito;
    @Column(name = "inicio_acesso", nullable = false)
    private OffsetDateTime inicioAcesso;
    @Column(name = "fim_acesso", nullable = false)
    private OffsetDateTime fimAcesso;
    @Column(nullable = false)
    private Integer quota;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusProdutoEstacionamento status;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "produto_estacionamento_beneficio",
            joinColumns = @JoinColumn(name = "produto_id"))
    @OrderColumn(name = "ordem")
    @Column(name = "codigo", nullable = false, length = 60)
    @Getter(AccessLevel.NONE)
    private List<String> beneficios = new ArrayList<>();
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "produto_estacionamento_restricao",
            joinColumns = @JoinColumn(name = "produto_id"))
    @OrderColumn(name = "ordem")
    @Column(name = "codigo", nullable = false, length = 60)
    @Getter(AccessLevel.NONE)
    private List<String> restricoes = new ArrayList<>();
    @Version
    @Column(nullable = false)
    private Long version;

    public static ProdutoEstacionamento criar(
            Long organizacaoId, Long eventoId, Long alocacaoId, String nome,
            CategoriaProduto categoria, DireitoProduto direito,
            OffsetDateTime inicioAcesso, OffsetDateTime fimAcesso, Integer quota,
            List<String> beneficios, List<String> restricoes) {
        if (organizacaoId == null || eventoId == null || alocacaoId == null) {
            throw new IllegalArgumentException(
                    "Organizacao, evento e alocacao sao obrigatorios.");
        }
        validarDados(nome, categoria, direito, inicioAcesso, fimAcesso, quota,
                beneficios, restricoes);
        ProdutoEstacionamento produto = new ProdutoEstacionamento();
        produto.organizacaoId = organizacaoId;
        produto.eventoId = eventoId;
        produto.alocacaoId = alocacaoId;
        produto.nome = textoObrigatorio(nome, "nome", 160);
        produto.categoria = categoria;
        produto.direito = direito;
        produto.inicioAcesso = inicioAcesso;
        produto.fimAcesso = fimAcesso;
        produto.quota = quota;
        produto.status = StatusProdutoEstacionamento.RASCUNHO;
        produto.beneficios = codigos(beneficios, "beneficio");
        produto.restricoes = codigos(restricoes, "restricao");
        return produto;
    }

    public List<String> getBeneficios() {
        return Collections.unmodifiableList(beneficios);
    }

    public List<String> getRestricoes() {
        return Collections.unmodifiableList(restricoes);
    }

    static void validarDados(
            String nome, CategoriaProduto categoria, DireitoProduto direito,
            OffsetDateTime inicioAcesso, OffsetDateTime fimAcesso, Integer quota,
            List<String> beneficios, List<String> restricoes) {
        textoObrigatorio(nome, "nome", 160);
        if (categoria == null || direito == null) {
            throw new IllegalArgumentException("Categoria e direito sao obrigatorios.");
        }
        if (inicioAcesso == null || fimAcesso == null
                || !fimAcesso.toInstant().isAfter(inicioAcesso.toInstant())) {
            throw new IllegalArgumentException(
                    "O fim da janela de acesso deve ser posterior ao inicio.");
        }
        if (quota == null || quota < 1) {
            throw new IllegalArgumentException("A quota deve ser de pelo menos uma vaga.");
        }
        codigos(beneficios, "beneficio");
        codigos(restricoes, "restricao");
    }

    private static String textoObrigatorio(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " e obrigatorio.");
        }
        String texto = valor.trim();
        if (texto.length() > limite) {
            throw new IllegalArgumentException(
                    "O campo " + campo + " excede " + limite + " caracteres.");
        }
        return texto;
    }

    private static List<String> codigos(List<String> valores, String campo) {
        if (valores == null || valores.isEmpty()) {
            return new ArrayList<>();
        }
        LinkedHashSet<String> normalizados = new LinkedHashSet<>();
        for (String valor : valores) {
            if (valor == null) {
                throw new IllegalArgumentException("O codigo de " + campo + " e invalido.");
            }
            String codigo = valor.trim().toUpperCase(Locale.ROOT);
            if (!codigo.matches("[A-Z][A-Z0-9_]{0,59}")) {
                throw new IllegalArgumentException("O codigo de " + campo + " e invalido.");
            }
            normalizados.add(codigo);
        }
        return new ArrayList<>(normalizados);
    }
}
