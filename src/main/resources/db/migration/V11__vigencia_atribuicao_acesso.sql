ALTER TABLE atribuicao_acesso
    ADD COLUMN vigente_de TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN vigente_ate TIMESTAMPTZ,
    ADD CONSTRAINT ck_atribuicao_vigencia
        CHECK (vigente_ate IS NULL OR vigente_ate > vigente_de);
