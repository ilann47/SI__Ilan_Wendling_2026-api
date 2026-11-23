-- Registros anteriores continuam consultáveis e não são faturados retroativamente.
ALTER TABLE ordem_servico
    ADD COLUMN movimentacao_patio_id BIGINT,
    ALTER COLUMN cliente_id DROP NOT NULL,
    ADD CONSTRAINT ck_ordem_servico_cliente_ou_estadia
        CHECK (cliente_id IS NOT NULL OR movimentacao_patio_id IS NOT NULL),
    ADD CONSTRAINT fk_ordem_servico_movimentacao_patio
        FOREIGN KEY (organizacao_id, movimentacao_patio_id)
        REFERENCES movimentacao (organizacao_id, id),
    ADD CONSTRAINT uq_ordem_servico_estadia_fiscal
        UNIQUE (organizacao_id, movimentacao_patio_id, id);

CREATE UNIQUE INDEX uq_ordem_servico_movimentacao_patio
    ON ordem_servico (organizacao_id, movimentacao_patio_id)
    WHERE movimentacao_patio_id IS NOT NULL;

-- A OS automática guarda o serviço prestado como item de descrição/valor congelados.
ALTER TABLE item_ordem_servico ALTER COLUMN servico_id DROP NOT NULL;

ALTER TABLE patio_evento_fiscal
    ADD COLUMN ordem_servico_id BIGINT,
    ADD CONSTRAINT fk_patio_evento_fiscal_ordem_estadia
        FOREIGN KEY (organizacao_id, movimentacao_id, ordem_servico_id)
        REFERENCES ordem_servico (organizacao_id, movimentacao_patio_id, id);

CREATE UNIQUE INDEX uq_patio_evento_fiscal_ordem
    ON patio_evento_fiscal (ordem_servico_id) WHERE ordem_servico_id IS NOT NULL;
