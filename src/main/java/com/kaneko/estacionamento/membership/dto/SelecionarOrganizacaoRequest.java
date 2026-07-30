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
public class SelecionarOrganizacaoRequest {

    @JsonProperty("organizationId")
    @NotNull(message = "A organizacao e obrigatoria.")
    private Long organizacaoId;

    private boolean campoDesconhecido;

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String nome, Object valor) {
        campoDesconhecido = true;
    }

    @AssertTrue(message = "A selecao de organizacao aceita somente organizationId.")
    public boolean isSemCamposDesconhecidos() {
        return !campoDesconhecido;
    }
}
