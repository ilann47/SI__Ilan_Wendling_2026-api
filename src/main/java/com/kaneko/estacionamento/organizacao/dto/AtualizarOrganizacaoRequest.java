package com.kaneko.estacionamento.organizacao.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Contrato PATCH de lista permitida. Os campos de identidade, estado e versao
 * sao declarados apenas para que sua presenca seja recusada explicitamente.
 */
@Getter
@Setter
@NoArgsConstructor
public class AtualizarOrganizacaoRequest {

    @JsonProperty("legalName")
    @Size(min = 1, max = 160, message = "A razao social deve ter entre 1 e 160 caracteres.")
    private String razaoSocial;

    @JsonProperty("tradeName")
    @Size(max = 160, message = "O nome fantasia deve ter no maximo 160 caracteres.")
    private String nomeFantasia;

    @JsonProperty("currency")
    @Size(min = 3, max = 3, message = "A moeda deve ter tres caracteres.")
    private String moeda;

    @JsonProperty("timeZone")
    @Size(min = 1, max = 60, message = "O fuso horario deve ter entre 1 e 60 caracteres.")
    private String fusoHorario;

    @JsonProperty("region")
    @Size(min = 1, max = 8, message = "A regiao deve ter entre 1 e 8 caracteres.")
    private String regiao;

    @JsonProperty("plan")
    @Size(min = 1, max = 30, message = "O plano deve ter entre 1 e 30 caracteres.")
    private String plano;

    @JsonProperty("id")
    private Object id;

    @JsonProperty("document")
    private Object documento;

    @JsonProperty("status")
    private Object status;

    @JsonProperty("version")
    private Object version;

    @AssertTrue(message = "ID, documento, status e versao nao podem ser alterados pelo PATCH.")
    public boolean isSemCamposImutaveis() {
        return id == null && documento == null && status == null && version == null;
    }

    @AssertTrue(message = "Informe ao menos um campo alteravel.")
    public boolean isPossuiAlteracao() {
        return razaoSocial != null || nomeFantasia != null || moeda != null
                || fusoHorario != null || regiao != null || plano != null;
    }

    @JsonAnySetter
    public void rejeitarCampoDesconhecido(String nome, Object valor) {
        throw new IllegalArgumentException("O campo '" + nome + "' nao e permitido no PATCH de organizacao.");
    }
}
