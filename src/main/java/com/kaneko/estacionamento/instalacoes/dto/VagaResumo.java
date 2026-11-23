package com.kaneko.estacionamento.instalacoes.dto;

import com.kaneko.estacionamento.instalacoes.CategoriaSetor;
import com.kaneko.estacionamento.instalacoes.StatusVaga;
import com.kaneko.estacionamento.instalacoes.Vaga;

public record VagaResumo(Long id, String code, Long sectorId, String sectorName, String parkingFacilityName,
        CategoriaSetor type, Boolean accessible, String position, StatusVaga status, Long version) {
    public static VagaResumo from(Vaga vaga, String setor, String patio) {
        return new VagaResumo(vaga.getId(), vaga.getCodigo(), vaga.getSetorId(), setor, patio,
                vaga.getTipo(), vaga.getAcessivel(), vaga.getPosicao(), vaga.getStatus(), vaga.getVersion());
    }
}
