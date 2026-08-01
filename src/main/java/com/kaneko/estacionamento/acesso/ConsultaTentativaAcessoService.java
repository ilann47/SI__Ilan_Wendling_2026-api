package com.kaneko.estacionamento.acesso;

import com.kaneko.estacionamento.acesso.dto.PaginaTentativasAcessoResponse;
import com.kaneko.estacionamento.acesso.dto.TentativaAcessoResponse;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaTentativaAcessoService {
    private final TentativaAcessoRepository tentativas;

    public ConsultaTentativaAcessoService(TentativaAcessoRepository tentativas) {
        this.tentativas = tentativas;
    }

    @Transactional(readOnly = true)
    public PaginaTentativasAcessoResponse listar(
            Long organizacaoId, Long cursor, Long eventoId,
            StatusTentativaAcesso decisao, MotivoDecisaoAcesso motivo,
            int limite) {
        validar(cursor, eventoId, decisao, limite);
        List<TentativaAcesso> encontrados = tentativas.buscarFeed(
                organizacaoId, cursor, eventoId, decisao, motivo,
                PageRequest.of(0, limite + 1));
        boolean temMais = encontrados.size() > limite;
        List<TentativaAcesso> pagina = temMais
                ? encontrados.subList(0, limite) : encontrados;
        List<TentativaAcessoResponse> itens = pagina.stream()
                .map(TentativaAcessoResponse::from)
                .toList();
        Long proximoCursor = temMais ? pagina.getLast().getId() : null;
        return new PaginaTentativasAcessoResponse(
                itens, proximoCursor, temMais);
    }

    private static void validar(
            Long cursor, Long eventoId, StatusTentativaAcesso decisao,
            int limite) {
        if (cursor != null && cursor < 1) {
            throw new IllegalArgumentException("O cursor deve ser positivo.");
        }
        if (eventoId != null && eventoId < 1) {
            throw new IllegalArgumentException("O evento deve ser positivo.");
        }
        if (decisao == StatusTentativaAcesso.RECEBIDA
                || decisao == StatusTentativaAcesso.AVALIANDO) {
            throw new IllegalArgumentException(
                    "A decisao deve ser AUTORIZADA ou RECUSADA.");
        }
        if (limite < 1 || limite > 100) {
            throw new IllegalArgumentException(
                    "O limite deve estar entre 1 e 100.");
        }
    }
}
