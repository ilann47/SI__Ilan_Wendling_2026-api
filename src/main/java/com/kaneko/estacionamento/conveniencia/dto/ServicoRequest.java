package com.kaneko.estacionamento.conveniencia.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Dados de entrada para criacao ou alteracao de um Servico.
 *
 * <p>As restricoes refletem o schema: {@code nome} obrigatorio e unico
 * (uq_servico_nome), demais campos opcionais com seus limites de tamanho e o
 * valor padrao nao-negativo quando informado.
 */
public record ServicoRequest(

        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 100, message = "O nome deve ter no máximo 100 caracteres.")
        String nome,

        @Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.")
        String descricao,

        @PositiveOrZero(message = "O valor padrão não pode ser negativo.")
        BigDecimal valorPadrao,

        Boolean ativo
) {
    @JsonAnySetter public void rejeitarCampoDesconhecido(String campo, Object valor) {
        throw new IllegalArgumentException("O campo '" + campo + "' nao e aceito neste contrato.");
    }
}
