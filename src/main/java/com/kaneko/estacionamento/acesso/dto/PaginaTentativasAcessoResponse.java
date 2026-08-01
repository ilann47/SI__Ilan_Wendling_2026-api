package com.kaneko.estacionamento.acesso.dto;

import java.util.List;

public record PaginaTentativasAcessoResponse(
        List<TentativaAcessoResponse> items,
        Long nextCursor,
        boolean hasMore) {
}
