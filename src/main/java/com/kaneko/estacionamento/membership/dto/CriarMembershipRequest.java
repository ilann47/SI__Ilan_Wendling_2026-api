package com.kaneko.estacionamento.membership.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CriarMembershipRequest {

    @JsonProperty("userId")
    @NotNull(message = "O usuario e obrigatorio.")
    private Long usuarioId;

    private boolean campoDesconhecido;

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String nome, Object valor) {
        campoDesconhecido = true;
    }

    @AssertTrue(message = "A criacao de Membership aceita somente userId.")
    public boolean isSemCamposDesconhecidos() {
        return !campoDesconhecido;
    }
}
