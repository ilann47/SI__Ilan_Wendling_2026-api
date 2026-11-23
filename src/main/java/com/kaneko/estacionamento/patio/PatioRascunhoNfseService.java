package com.kaneko.estacionamento.patio;

import com.kaneko.estacionamento.auditoria.AuditoriaService;
import com.kaneko.estacionamento.auditoria.ClasseRetencaoAuditoria;
import com.kaneko.estacionamento.auditoria.RegistrarAuditoriaCommand;
import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.exception.RecursoNaoEncontradoException;
import com.kaneko.estacionamento.pessoas.ClienteTomadorService;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PatioRascunhoNfseService {
    public record Rascunho(long movimentacaoId, Long ordemServicoId, String ordemServicoNumero,
            String eventoNome, String patioNome, String placa, String valor, String status,
            OffsetDateTime criadoEm, long version, boolean tomadorInformado, PatioTomador tomador,
            Long servicoId, String servicoNome) {}

    private final PatioRascunhoNfseRepository rascunhos;
    private final AuditoriaService auditoria;
    private final ClienteTomadorService clientesTomadores;

    PatioRascunhoNfseService(PatioRascunhoNfseRepository rascunhos, AuditoriaService auditoria,
                            ClienteTomadorService clientesTomadores) {
        this.rascunhos = rascunhos;
        this.auditoria = auditoria;
        this.clientesTomadores = clientesTomadores;
    }

    @Transactional(readOnly = true)
    public Page<Rascunho> listar(long org, String busca, Pageable page) {
        return rascunhos.listar(org, busca.strip(), page);
    }

    @Transactional(readOnly = true)
    public Rascunho buscar(long org, long mov) {
        return buscar(org, mov, false);
    }

    @Transactional
    public Rascunho identificar(long org, long mov, long version, PatioTomador entrada) {
        PatioTomador tomador = PatioTomador.normalizar(entrada);
        Rascunho atual = buscar(org, mov, true);
        if (version != atual.version()) {
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "O rascunho foi alterado. Recarregue a página.");
        }
        if (!"PENDENTE_EMISSAO".equals(atual.status())) {
            throw new ConflitoException("Somente rascunhos pendentes podem ser alterados.");
        }
        if (Objects.equals(atual.tomador(), tomador)) return atual;
        rascunhos.atualizarTomador(org, mov, tomador);
        if (tomador != null) clientesTomadores.cadastrarSeNovo(org, tomador.documento(), tomador.nome());
        auditoria.registrar(new RegistrarAuditoriaCommand(org, "PATIO_RASCUNHO_NFSE_TOMADOR_ATUALIZADO",
                "MOVIMENTACAO", mov, null, null, null, null, Map.of("identificado", tomador != null),
                null, null, ClasseRetencaoAuditoria.OPERACIONAL));
        return buscar(org, mov, false);
    }

    private Rascunho buscar(long org, long mov, boolean bloquear) {
        return rascunhos.buscar(org, mov, bloquear)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Rascunho de NFS-e da estadia", mov));
    }
}
