ALTER TABLE nota_entrada
    ADD COLUMN revertida_em TIMESTAMPTZ,
    ADD COLUMN motivo_reversao VARCHAR(500),
    ADD CONSTRAINT ck_nota_entrada_reversao CHECK (
        (revertida_em IS NULL AND motivo_reversao IS NULL)
        OR (organizacao_id IS NOT NULL AND situacao = 'CANCELADA'
            AND revertida_em IS NOT NULL AND motivo_reversao IS NOT NULL
            AND length(btrim(motivo_reversao)) > 0));

ALTER TABLE nota_saida
    ADD COLUMN revertida_em TIMESTAMPTZ,
    ADD COLUMN motivo_reversao VARCHAR(500),
    ADD CONSTRAINT ck_nota_saida_reversao CHECK (
        (revertida_em IS NULL AND motivo_reversao IS NULL)
        OR (organizacao_id IS NOT NULL AND venda_administrativa_id IS NULL
            AND situacao = 'CANCELADA' AND revertida_em IS NOT NULL
            AND motivo_reversao IS NOT NULL AND length(btrim(motivo_reversao)) > 0));

CREATE INDEX ix_movimento_estoque_tenant_produto_sequencia
    ON movimento_estoque (organizacao_id, produto_id, id);
