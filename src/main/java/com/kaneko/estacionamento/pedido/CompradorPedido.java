package com.kaneko.estacionamento.pedido;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "comprador_pedido")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompradorPedido {
    @Id
    @Column(name = "pedido_id")
    private Long pedidoId;
    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;
    @Column(nullable = false, length = 180)
    private String nome;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(length = 14)
    private String documento;

    static CompradorPedido criar(
            Pedido pedido, String nome, String email, String documento) {
        if (pedido == null) {
            throw new IllegalArgumentException("O pedido do comprador e obrigatorio.");
        }
        CompradorPedido comprador = new CompradorPedido();
        comprador.pedido = pedido;
        comprador.nome = texto(nome, "nome do comprador", 180);
        comprador.email = email(email);
        comprador.documento = documento(documento);
        return comprador;
    }

    private static String email(String valor) {
        String email = texto(valor, "email do comprador", 254).toLowerCase(Locale.ROOT);
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("O email do comprador e invalido.");
        }
        return email;
    }

    private static String documento(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String digitos = valor.replaceAll("[^0-9]", "");
        if (digitos.length() != 11 && digitos.length() != 14) {
            throw new IllegalArgumentException("O documento do comprador e invalido.");
        }
        return digitos;
    }

    private static String texto(String valor, String campo, int limite) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O " + campo + " e obrigatorio.");
        }
        String texto = valor.trim();
        if (texto.length() > limite) {
            throw new IllegalArgumentException("O " + campo + " excede " + limite + " caracteres.");
        }
        return texto;
    }
}
