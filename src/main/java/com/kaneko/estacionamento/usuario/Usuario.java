package com.kaneko.estacionamento.usuario;

import com.kaneko.estacionamento.common.BaseEntity;
import com.kaneko.estacionamento.rh.Funcionario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Usuario do sistema (credencial de acesso). Mapeia {@code usuario}.
 *
 * <p>A senha e armazenada como hash BCrypt — nunca em texto puro. O login e o
 * email (quando informado) sao unicos (case-insensitive). Cada funcionario pode
 * ter no maximo um usuario vinculado.
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
public class Usuario extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "login", nullable = false, length = 60)
    private String login;

    @Column(name = "senha", nullable = false, length = 255)
    private String senha;

    @Column(name = "email", length = 120)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "funcionario_id")
    private Funcionario funcionario;

    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false, length = 20)
    private PerfilUsuario perfil = PerfilUsuario.OPERADOR;
}
