package com.kaneko.estacionamento.patio.dto;

import com.kaneko.estacionamento.patio.StatusColaborador;
import com.kaneko.estacionamento.usuario.PerfilUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados de entrada para convidar/alterar um colaborador de um patio. Sem o
 * status, um novo colaborador nasce CONVIDADO; sem o perfil, OPERADOR.
 */
public record ColaboradorRequest(

        @NotNull(message = "O pátio é obrigatório.")
        Long patioId,

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String nome,

        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "E-mail inválido.")
        @Size(max = 120, message = "O e-mail deve ter no máximo 120 caracteres.")
        String email,

        PerfilUsuario perfil,

        StatusColaborador status,

        Boolean ativo
) {
}
