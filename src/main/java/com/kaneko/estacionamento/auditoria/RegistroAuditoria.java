package com.kaneko.estacionamento.auditoria;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "registro_auditoria")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RegistroAuditoria {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "organizacao_id", updatable = false) private Long organizacaoId;
    @Column(nullable = false, updatable = false, length = 100) private String acao;
    @Column(name = "recurso_tipo", nullable = false, updatable = false, length = 80) private String recursoTipo;
    @Column(name = "recurso_id", nullable = false, updatable = false) private Long recursoId;
    @Enumerated(EnumType.STRING) @Column(name = "ator_tipo", nullable = false, updatable = false, length = 30)
    private TipoAtorAuditoria atorTipo;
    @Column(name = "ator_id", updatable = false) private Long atorId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 30)
    private OrigemAuditoria origem;
    @Column(name = "evento_id", updatable = false) private Long eventoId;
    @Column(name = "patio_id", updatable = false) private Long patioId;
    @Column(name = "dispositivo_id", updatable = false, length = 120) private String dispositivoId;
    @Column(name = "ip_origem", updatable = false, length = 45) private String ipOrigem;
    @Column(name = "trace_id", nullable = false, updatable = false, length = 100) private String traceId;
    @Column(updatable = false, length = 500) private String motivo;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "estado_anterior", updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> estadoAnterior;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "estado_posterior", updatable = false, columnDefinition = "jsonb")
    private Map<String, Object> estadoPosterior;
    @Column(name = "versao_anterior", updatable = false) private Long versaoAnterior;
    @Column(name = "versao_posterior", updatable = false) private Long versaoPosterior;
    @Enumerated(EnumType.STRING)
    @Column(name = "classe_retencao", nullable = false, updatable = false, length = 30)
    private ClasseRetencaoAuditoria classeRetencao;
    @Column(name = "ocorrido_em", nullable = false, updatable = false) private OffsetDateTime ocorridoEm;

    static RegistroAuditoria registrar(RegistrarAuditoriaCommand c, ContextoAuditoria contexto) {
        if (c == null || contexto == null || c.recursoId() == null || c.classeRetencao() == null) {
            throw new IllegalArgumentException("Comando, contexto, recurso e classe de retencao sao obrigatorios.");
        }
        var registro = new RegistroAuditoria();
        registro.organizacaoId = c.organizacaoId();
        registro.acao = codigo(c.acao(), "acao", 100);
        registro.recursoTipo = codigo(c.recursoTipo(), "recurso", 80);
        registro.recursoId = c.recursoId();
        registro.atorTipo = contexto.atorTipo(); registro.atorId = contexto.atorId();
        registro.origem = contexto.origem(); registro.eventoId = c.eventoId(); registro.patioId = c.patioId();
        registro.dispositivoId = contexto.dispositivoId(); registro.ipOrigem = contexto.ipOrigem();
        registro.traceId = contexto.traceId(); registro.motivo = textoOpcional(c.motivo(), 500);
        registro.estadoAnterior = copiar(c.estadoAnterior()); registro.estadoPosterior = copiar(c.estadoPosterior());
        registro.versaoAnterior = versao(c.versaoAnterior()); registro.versaoPosterior = versao(c.versaoPosterior());
        registro.classeRetencao = c.classeRetencao(); registro.ocorridoEm = OffsetDateTime.now(ZoneOffset.UTC);
        return registro;
    }

    private static String codigo(String valor, String campo, int limite) {
        if (valor == null || !valor.matches("[A-Z][A-Z0-9_]{2," + (limite - 1) + "}")) {
            throw new IllegalArgumentException("O codigo de " + campo + " e invalido.");
        }
        return valor;
    }

    private static String textoOpcional(String valor, int limite) {
        if (valor == null || valor.isBlank()) return null;
        String texto = valor.trim();
        if (texto.length() > limite) throw new IllegalArgumentException("O motivo excede " + limite + " caracteres.");
        return texto;
    }

    private static Long versao(Long valor) {
        if (valor != null && valor < 0) throw new IllegalArgumentException("A versao nao pode ser negativa.");
        return valor;
    }

    private static Map<String, Object> copiar(Map<String, Object> estado) {
        return estado == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(estado));
    }
}
