CREATE TABLE patio_evento_cobranca (
    evento_id BIGINT PRIMARY KEY,
    organizacao_id BIGINT NOT NULL,
    modo VARCHAR(7) NOT NULL CHECK (modo IN ('ENTRADA', 'SAIDA')),
    valor_micros NUMERIC(14,0) NOT NULL CHECK (valor_micros > 0),
    FOREIGN KEY (evento_id, organizacao_id) REFERENCES evento(id, organizacao_id)
);

CREATE TABLE patio_evento_estadia (
    movimentacao_id BIGINT PRIMARY KEY,
    organizacao_id BIGINT NOT NULL,
    evento_id BIGINT NOT NULL,
    patio_id BIGINT NOT NULL,
    chave_entrada UUID NOT NULL,
    modo VARCHAR(7) NOT NULL CHECK (modo IN ('ENTRADA', 'SAIDA')),
    valor_micros NUMERIC(14,0) NOT NULL CHECK (valor_micros > 0),
    turno_recebimento_entrada_id BIGINT,
    meio_entrada VARCHAR(16),
    UNIQUE (organizacao_id, chave_entrada),
    UNIQUE (organizacao_id, movimentacao_id),
    FOREIGN KEY (organizacao_id, movimentacao_id) REFERENCES movimentacao(organizacao_id, id),
    FOREIGN KEY (evento_id, organizacao_id) REFERENCES evento(id, organizacao_id),
    FOREIGN KEY (evento_id, patio_id) REFERENCES alocacao_patio_evento(evento_id, patio_id),
    FOREIGN KEY (organizacao_id, turno_recebimento_entrada_id) REFERENCES patio_turno(organizacao_id, id),
    CHECK ((modo = 'ENTRADA' AND turno_recebimento_entrada_id IS NOT NULL AND meio_entrada IS NOT NULL
            AND meio_entrada IN ('DINHEIRO', 'PIX', 'CARTAO'))
        OR (modo = 'SAIDA' AND turno_recebimento_entrada_id IS NULL AND meio_entrada IS NULL))
);

CREATE INDEX ix_patio_evento_estadia_alocacao ON patio_evento_estadia(organizacao_id, evento_id, patio_id);
CREATE INDEX ix_patio_evento_estadia_turno ON patio_evento_estadia(organizacao_id, turno_recebimento_entrada_id)
    WHERE turno_recebimento_entrada_id IS NOT NULL;

CREATE TABLE patio_evento_fiscal (
    movimentacao_id BIGINT PRIMARY KEY,
    organizacao_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status = 'PENDENTE_EMISSAO'),
    valor_micros NUMERIC(14,0) NOT NULL CHECK (valor_micros > 0),
    criado_em TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (organizacao_id, movimentacao_id) REFERENCES patio_evento_estadia(organizacao_id, movimentacao_id)
);
