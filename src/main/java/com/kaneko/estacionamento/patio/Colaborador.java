package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.common.BaseEntity;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
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
 * Colaborador — membro da equipe de um {@link Patio}. Mapeia {@code colaborador}.
 *
 * <p>E-mail unico por patio ({@code uq_colaborador_patio_email}). O fluxo de
 * convite nasce em {@link StatusColaborador#CONVIDADO}; o perfil reutiliza
 * {@link PerfilUsuario} (ADMIN/OPERADOR).
 */
@Entity
@Table(name = "colaborador")
@Getter
@Setter
public class Colaborador extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patio_id", nullable = false)
    private Patio patio;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "email", nullable = false, length = 120)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "perfil", nullable = false, length = 20)
    private PerfilUsuario perfil = PerfilUsuario.OPERADOR;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusColaborador status = StatusColaborador.CONVIDADO;
}
