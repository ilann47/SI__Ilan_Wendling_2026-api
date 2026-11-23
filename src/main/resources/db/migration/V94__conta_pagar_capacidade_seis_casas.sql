-- Mantém as restrições do título e a compatibilidade dos relatórios SQL.
SET LOCAL lock_timeout = '5s';

ALTER TABLE conta_pagar
    ALTER COLUMN valor_original TYPE NUMERIC(19,6),
    ALTER COLUMN valor_desconto TYPE NUMERIC(19,6),
    ALTER COLUMN valor_juros TYPE NUMERIC(19,6),
    ALTER COLUMN valor_multa TYPE NUMERIC(19,6),
    ALTER COLUMN valor_total TYPE NUMERIC(19,6);
