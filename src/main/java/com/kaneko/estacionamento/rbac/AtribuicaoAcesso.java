package com.kaneko.estacionamento.rbac;

import com.kaneko.estacionamento.common.AuditableEntity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "atribuicao_acesso")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AtribuicaoAcesso extends AuditableEntity {
    @Column(name = "membro_organizacao_id", nullable = false, updatable = false) private Long membershipId;
    @Column(name = "papel_id", nullable = false, updatable = false) private Long papelId;
    @Column(name = "organizacao_id", nullable = false, updatable = false) private Long organizacaoId;
    @Column(name = "escopo_tipo", nullable = false, updatable = false, length = 30) private String escopoTipo;
    @Column(name = "escopo_id", nullable = false, updatable = false) private Long escopoId;
    @Column(name = "concedido_em", nullable = false, updatable = false) private OffsetDateTime concedidoEm;
    @Column(name = "vigente_de", nullable = false, updatable = false) private OffsetDateTime vigenteDe;
    @Column(name = "vigente_ate", updatable = false) private OffsetDateTime vigenteAte;
    @Column(name = "revogado_em") private OffsetDateTime revogadoEm;
    @Column(name = "motivo_revogacao", length = 300) private String motivoRevogacao;
    @Version @Column(nullable = false) private Long version;

    public static AtribuicaoAcesso conceder(Long membershipId, Long papelId, Long organizacaoId, Long escopoId) {
        return conceder(membershipId, papelId, organizacaoId, escopoId, OffsetDateTime.now(), null);
    }

    public static AtribuicaoAcesso conceder(Long membershipId, Long papelId, Long organizacaoId,
                                             Long escopoId, OffsetDateTime vigenteDe, OffsetDateTime vigenteAte) {
        if (membershipId == null || papelId == null || organizacaoId == null || !organizacaoId.equals(escopoId)) {
            throw new IllegalArgumentException("Membership, papel e escopo organizacional coerente sao obrigatorios.");
        }
        var a = new AtribuicaoAcesso();
        a.membershipId = membershipId; a.papelId = papelId; a.organizacaoId = organizacaoId;
        if (vigenteDe == null || (vigenteAte != null && !vigenteAte.isAfter(vigenteDe))) {
            throw new IllegalArgumentException("A vigencia da atribuicao e invalida.");
        }
        a.escopoTipo = "ORGANIZACAO"; a.escopoId = escopoId; a.concedidoEm = OffsetDateTime.now();
        a.vigenteDe = vigenteDe; a.vigenteAte = vigenteAte;
        return a;
    }

    public void revogar(String motivo) {
        if (revogadoEm != null) throw new IllegalStateException("A atribuicao ja foi revogada.");
        if (motivo == null || motivo.isBlank() || motivo.trim().length() > 300)
            throw new IllegalArgumentException("O motivo de revogacao e obrigatorio e deve ter ate 300 caracteres.");
        revogadoEm = OffsetDateTime.now(); motivoRevogacao = motivo.trim();
    }

    public boolean estaVigente() {
        OffsetDateTime agora = OffsetDateTime.now();
        return revogadoEm == null && !agora.isBefore(vigenteDe)
                && (vigenteAte == null || agora.isBefore(vigenteAte));
    }
}
