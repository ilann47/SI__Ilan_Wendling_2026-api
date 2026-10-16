package com.kaneko.estacionamento.organizacao.venue;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.DateTimeException;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "unidade_operacional")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Venue extends AuditableEntity {

    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;
    @Column(nullable = false, length = 160)
    private String nome;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private TipoUnidadeOperacional tipo;
    @Column(nullable = false, length = 120)
    private String endereco;
    @Column(length = 10)
    private String numero;
    @Column(length = 100)
    private String complemento;
    @Column(nullable = false, length = 60)
    private String bairro;
    @Column(nullable = false, length = 9)
    private String cep;
    @Column(name = "cidade_id", nullable = false)
    private Long cidadeId;
    @Column(name = "fuso_horario", nullable = false, length = 60)
    private String fusoHorario;
    @Column(nullable = false)
    private Boolean ativo;
    @Version
    @Column(nullable = false)
    private Long version;

    public static Venue criar(
            Long organizacaoId, String nome, String endereco, String numero,
            String complemento, String bairro, String cep, Long cidadeId,
            String fusoHorario) {
        if (organizacaoId == null || cidadeId == null) {
            throw new IllegalArgumentException("Organizacao e cidade sao obrigatorias.");
        }
        Venue venue = new Venue();
        venue.organizacaoId = organizacaoId;
        venue.nome = textoObrigatorio(nome, "nome", 160);
        venue.tipo = TipoUnidadeOperacional.VENUE;
        venue.endereco = textoObrigatorio(endereco, "endereco", 120);
        venue.numero = textoOpcional(numero, "numero", 10);
        venue.complemento = textoOpcional(complemento, "complemento", 100);
        venue.bairro = textoObrigatorio(bairro, "bairro", 60);
        venue.cep = textoObrigatorio(cep, "CEP", 9);
        venue.cidadeId = cidadeId;
        venue.fusoHorario = validarFuso(fusoHorario);
        venue.ativo = true;
        return venue;
    }

    private static String validarFuso(String valor) {
        String fuso = textoObrigatorio(valor, "fuso horario", 60);
        try {
            return ZoneId.of(fuso).getId();
        } catch (DateTimeException ex) {
            throw new IllegalArgumentException("O fuso horario deve ser um identificador IANA valido.", ex);
        }
    }

    private static String textoObrigatorio(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " e obrigatorio.");
        }
        String texto = valor.trim();
        if (texto.length() > limite) {
            throw new IllegalArgumentException("O campo " + campo + " excede " + limite + " caracteres.");
        }
        return texto;
    }

    private static String textoOpcional(String valor, String campo, int limite) {
        return valor == null || valor.isBlank() ? null : textoObrigatorio(valor, campo, limite);
    }
}
