package com.kaneko.estacionamento.fiscal.importacao;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record ResumoImportacaoResponse(Long id, Long version, StatusImportacaoNota status,
        String chave, String numero, String emitenteNome, BigDecimal total,
        OffsetDateTime createdAt, Long notaEntradaId) { }
