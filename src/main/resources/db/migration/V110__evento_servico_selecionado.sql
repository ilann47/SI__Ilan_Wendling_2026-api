-- Eventos anteriores à seleção explícita permanecem sem vínculo.
ALTER TABLE evento
    ADD COLUMN servico_id BIGINT,
    ADD CONSTRAINT fk_evento_servico_organizacao
        FOREIGN KEY (organizacao_id, servico_id) REFERENCES servico (organizacao_id, id);

CREATE INDEX ix_evento_organizacao_servico
    ON evento(organizacao_id, servico_id) WHERE servico_id IS NOT NULL;
