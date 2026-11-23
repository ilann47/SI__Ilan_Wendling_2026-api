package com.kaneko.estacionamento.membership.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.kaneko.estacionamento.membership.StatusMembroOrganizacao;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AtualizarMembershipRequest {

    @NotNull(message = "O status e obrigatorio.")
    private StatusMembroOrganizacao status;

    private boolean campoDesconhecido;

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String nome, Object valor) {
        campoDesconhecido = true;
    }

    @AssertTrue(message = "O PATCH de Membership aceita somente status.")
    public boolean isSemCamposDesconhecidos() {
        return !campoDesconhecido;
    }
}
