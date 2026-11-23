package com.kaneko.estacionamento.organizacao.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kaneko.estacionamento.organizacao.Organizacao;
import com.kaneko.estacionamento.organizacao.StatusOrganizacao;
import java.time.OffsetDateTime;

public record OrganizacaoResponse(
        Long id,
        @JsonProperty("document") String documento,
        @JsonProperty("legalName") String razaoSocial,
        @JsonProperty("tradeName") String nomeFantasia,
        @JsonProperty("currency") String moeda,
        @JsonProperty("timeZone") String fusoHorario,
        @JsonProperty("region") String regiao,
        @JsonProperty("plan") String plano,
        StatusOrganizacao status,
        Long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static OrganizacaoResponse from(Organizacao organizacao) {
        return new OrganizacaoResponse(
                organizacao.getId(),
                organizacao.getDocumento(),
                organizacao.getRazaoSocial(),
                organizacao.getNomeFantasia(),
                organizacao.getMoeda(),
                organizacao.getFusoHorario(),
                organizacao.getRegiao(),
                organizacao.getPlano(),
                organizacao.getStatus(),
                organizacao.getVersion(),
                organizacao.getCreatedAt(),
                organizacao.getUpdatedAt());
    }
}
