ALTER TABLE ordem_compra DROP CONSTRAINT ck_ordem_compra_status;
ALTER TABLE ordem_compra
    ADD CONSTRAINT ck_ordem_compra_status CHECK (status IN (
        'RASCUNHO','APROVADA','PARCIALMENTE_RECEBIDA','RECEBIDA','REJEITADA','CANCELADA'));

ALTER TABLE ordem_compra DROP CONSTRAINT ck_ordem_compra_cancelamento;
ALTER TABLE ordem_compra
    ADD CONSTRAINT ck_ordem_compra_cancelamento CHECK (
        (status IN ('CANCELADA','REJEITADA') AND motivo_cancelamento IS NOT NULL
            AND length(btrim(motivo_cancelamento)) BETWEEN 1 AND 300)
        OR (status NOT IN ('CANCELADA','REJEITADA') AND motivo_cancelamento IS NULL));
