package com.kaneko.estacionamento.usuario;

/**
 * Perfil de acesso de um usuario. Espelha {@code ck_usuario_perfil}.
 */
public enum PerfilUsuario {
    /** Acesso total (administracao). */
    ADMIN,
    /** Operacao do dia a dia (patio, conveniencia). */
    OPERADOR,
    /** Identidade global sem privilegios legados; recebe acesso por Membership/RBAC. */
    USUARIO
}
