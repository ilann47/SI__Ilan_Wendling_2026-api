package com.kaneko.estacionamento.financeiro;

import com.kaneko.estacionamento.common.exception.ConflitoException;
import com.kaneko.estacionamento.common.security.UsuarioAutenticado;
import com.kaneko.estacionamento.financeiro.dto.BaixaRequest;
import com.kaneko.estacionamento.financeiro.dto.BaixaFinanceiraResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Chamado somente após bloquear e validar o ownership do título na transação de baixa. */
@Service
public class BaixaFinanceiraService {
    private final BaixaFinanceiraRepository repository;
    public BaixaFinanceiraService(BaixaFinanceiraRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean repetida(boolean pagar, Long id, String chave, BaixaRequest req) {
        if (chave == null) return false; // Compatibilidade: consumidores novos devem enviar Idempotency-Key.
        if (chave.isBlank() || chave.length() > 160 || !chave.matches("[A-Za-z0-9._:-]+")) {
            throw new ConflitoException("Idempotency-Key deve conter de 1 a 160 caracteres alfanuméricos, ponto, hífen, dois-pontos ou sublinhado.");
        }
        var registro = pagar ? repository.findByContaPagarIdAndChave(id, chave)
                : repository.findByContaReceberIdAndChave(id, chave);
        if (registro.isEmpty()) return false;
        if (!Objects.equals(registro.get().getRequestHash(), hash(req))) {
            throw new ConflitoException("Esta chave de baixa já foi utilizada com outro valor ou data.");
        }
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(boolean pagar, Long org, Long id, String chave, BaixaRequest req) {
        BaixaFinanceira b = new BaixaFinanceira();
        b.setOrganizacaoId(org);
        if (pagar) b.setContaPagarId(id); else b.setContaReceberId(id);
        b.setValor(req.valor()); b.setData(req.data() == null ? LocalDate.now() : req.data());
        b.setChave(chave == null ? UUID.randomUUID().toString() : chave); b.setRequestHash(hash(req));
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UsuarioAutenticado ator) {
            b.setAtorId(ator.userId()); b.setAtorNome(ator.nome());
        }
        repository.saveAndFlush(b);
    }

    public List<BaixaFinanceiraResponse> historico(boolean pagar, Long id) {
        return (pagar ? repository.findByContaPagarIdOrderByIdAsc(id)
                : repository.findByContaReceberIdOrderByIdAsc(id)).stream().map(BaixaFinanceiraResponse::from).toList();
    }

    private String hash(BaixaRequest req) {
        String payload = req.valor().stripTrailingZeros().toPlainString() + ":" + req.data();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
