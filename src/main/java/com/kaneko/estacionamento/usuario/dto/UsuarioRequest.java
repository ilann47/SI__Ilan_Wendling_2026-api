package com.kaneko.estacionamento.usuario.dto;

import com.kaneko.estacionamento.usuario.PerfilUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada de um usuario. A senha e obrigatoria e armazenada como hash;
 * em alteracoes, a senha informada substitui a anterior.
 */
public record UsuarioRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 120) String nome,

        @NotBlank(message = "O login é obrigatório.")
        @Size(min = 3, max = 60, message = "O login deve ter entre 3 e 60 caracteres.")
        String login,

        @NotBlank(message = "A senha é obrigatória.")
        @Size(min = 6, max = 72, message = "A senha deve ter entre 6 e 72 caracteres.")
        String senha,

        @Email(message = "E-mail inválido.")
        @Size(max = 120) String email,

        Long funcionarioId,

        PerfilUsuario perfil
) {
}
