package com.kaneko.estacionamento.organizacao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CriarOrganizacaoRequest(
        @JsonProperty("document")
        @NotBlank(message = "O documento e obrigatorio.")
        @Size(max = 20, message = "O documento deve ter no maximo 20 caracteres.")
        String documento,

        @JsonProperty("legalName")
        @NotBlank(message = "A razao social e obrigatoria.")
        @Size(max = 160, message = "A razao social deve ter no maximo 160 caracteres.")
        String razaoSocial,

        @JsonProperty("tradeName")
        @Size(max = 160, message = "O nome fantasia deve ter no maximo 160 caracteres.")
        String nomeFantasia,

        @JsonProperty("currency")
        @NotBlank(message = "A moeda e obrigatoria.")
        @Size(max = 3, message = "A moeda deve ter tres caracteres.")
        String moeda,

        @JsonProperty("timeZone")
        @NotBlank(message = "O fuso horario e obrigatorio.")
        @Size(max = 60, message = "O fuso horario deve ter no maximo 60 caracteres.")
        String fusoHorario,

        @JsonProperty("region")
        @NotBlank(message = "A regiao e obrigatoria.")
        @Size(max = 8, message = "A regiao deve ter no maximo 8 caracteres.")
        String regiao,

        @JsonProperty("plan")
        @NotBlank(message = "O plano e obrigatorio.")
        @Size(max = 30, message = "O plano deve ter no maximo 30 caracteres.")
        String plano) {
}
