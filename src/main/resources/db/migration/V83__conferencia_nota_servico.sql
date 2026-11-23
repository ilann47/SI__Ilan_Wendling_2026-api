ALTER TABLE nota_servico
    ADD COLUMN itens_conferidos JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN parcelas_conferidas JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN cronograma_definido BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN motivo_cancelamento VARCHAR(500),
    ADD COLUMN cancelada_em TIMESTAMPTZ;

ALTER TABLE nota_servico
    ADD CONSTRAINT ck_nota_servico_itens_conferidos CHECK (jsonb_typeof(itens_conferidos) = 'array'),
    ADD CONSTRAINT ck_nota_servico_parcelas_conferidas CHECK (jsonb_typeof(parcelas_conferidas) = 'array');
