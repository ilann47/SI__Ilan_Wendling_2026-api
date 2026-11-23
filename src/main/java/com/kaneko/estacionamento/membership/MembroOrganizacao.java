package com.kaneko.estacionamento.membership;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Vinculo temporal entre uma identidade global e um tenant empresarial. */
@Entity
@Table(name = "membro_organizacao")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MembroOrganizacao extends AuditableEntity {

    @Column(name = "organizacao_id", nullable = false, updatable = false)
    private Long organizacaoId;

    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusMembroOrganizacao status;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 30, updatable = false)
    private OrigemMembroOrganizacao origem;

    @Column(name = "entrou_em", nullable = false, updatable = false)
    private OffsetDateTime entrouEm;

    @Column(name = "suspenso_em")
    private OffsetDateTime suspensoEm;

    @Column(name = "encerrado_em")
    private OffsetDateTime encerradoEm;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static MembroOrganizacao adicionar(
            Long organizacaoId, Long usuarioId, OrigemMembroOrganizacao origem) {
        if (organizacaoId == null || usuarioId == null || origem == null) {
            throw new IllegalArgumentException("Organizacao, usuario e origem sao obrigatorios.");
        }
        MembroOrganizacao membro = new MembroOrganizacao();
        membro.organizacaoId = organizacaoId;
        membro.usuarioId = usuarioId;
        membro.origem = origem;
        membro.status = StatusMembroOrganizacao.ATIVO;
        membro.entrouEm = OffsetDateTime.now();
        return membro;
    }

    public void suspender() {
        exigirNaoEncerrado();
        if (status != StatusMembroOrganizacao.ATIVO) {
            throw new IllegalStateException("Somente uma Membership ativa pode ser suspensa.");
        }
        status = StatusMembroOrganizacao.SUSPENSO;
        suspensoEm = OffsetDateTime.now();
    }

    public void reativar() {
        exigirNaoEncerrado();
        if (status != StatusMembroOrganizacao.SUSPENSO) {
            throw new IllegalStateException("Somente uma Membership suspensa pode ser reativada.");
        }
        status = StatusMembroOrganizacao.ATIVO;
        suspensoEm = null;
    }

    public void encerrar() {
        exigirNaoEncerrado();
        status = StatusMembroOrganizacao.ENCERRADO;
        suspensoEm = null;
        encerradoEm = OffsetDateTime.now();
    }

    private void exigirNaoEncerrado() {
        if (status == StatusMembroOrganizacao.ENCERRADO) {
            throw new IllegalStateException("ENCERRADO e um estado terminal para Membership.");
        }
    }
}
