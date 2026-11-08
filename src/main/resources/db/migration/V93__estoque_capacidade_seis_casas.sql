-- Preserva as verificações de consistência e não recria movimentos históricos.
SET LOCAL lock_timeout = '5s';

ALTER TABLE saldo_estoque ALTER COLUMN quantidade TYPE NUMERIC(18,6);

ALTER TABLE movimento_estoque
    ALTER COLUMN delta TYPE NUMERIC(18,6),
    ALTER COLUMN saldo_anterior TYPE NUMERIC(18,6),
    ALTER COLUMN saldo_posterior TYPE NUMERIC(18,6),
    ALTER COLUMN saldo_total_anterior TYPE NUMERIC(18,6),
    ALTER COLUMN saldo_total_posterior TYPE NUMERIC(18,6),
    ALTER COLUMN custo_unitario TYPE NUMERIC(19,6),
    ALTER COLUMN custo_medio_anterior TYPE NUMERIC(19,6),
    ALTER COLUMN custo_medio_posterior TYPE NUMERIC(19,6);

ALTER TABLE produto
    ALTER COLUMN valor_compra TYPE NUMERIC(19,6),
    ALTER COLUMN custo TYPE NUMERIC(19,6),
    ADD COLUMN quantidade_micros NUMERIC(18,0) CHECK (
        quantidade_micros >= 0 AND (organizacao_id IS NULL OR quantidade_micros = 0)
    );

CREATE OR REPLACE FUNCTION validar_integridade_saldo_razao_estoque()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE
    saldo_id BIGINT;
    quantidade_atual saldo_estoque.quantidade%TYPE;
    total_movimentos BIGINT;
    cadeia_valida BOOLEAN;
    ultimo_saldo saldo_estoque.quantidade%TYPE;
BEGIN
    IF TG_TABLE_NAME = 'saldo_estoque' THEN
        saldo_id := NEW.id;
    ELSE
        saldo_id := NEW.saldo_estoque_id;
    END IF;

    SELECT quantidade INTO quantidade_atual
    FROM saldo_estoque WHERE id = saldo_id;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT count(*),
           COALESCE(bool_and(
               (ordem = 1 AND saldo_anterior = 0)
               OR (ordem > 1 AND saldo_anterior = saldo_posterior_anterior)
           ), true),
           max(saldo_posterior) FILTER (WHERE ordem_inversa = 1)
      INTO total_movimentos, cadeia_valida, ultimo_saldo
    FROM (
        SELECT saldo_anterior, saldo_posterior,
               row_number() OVER (ORDER BY id) AS ordem,
               row_number() OVER (ORDER BY id DESC) AS ordem_inversa,
               lag(saldo_posterior) OVER (ORDER BY id) AS saldo_posterior_anterior
        FROM movimento_estoque
        WHERE saldo_estoque_id = saldo_id
    ) cadeia;

    IF NOT cadeia_valida
       OR (total_movimentos = 0 AND quantidade_atual <> 0)
       OR (total_movimentos > 0 AND quantidade_atual <> ultimo_saldo) THEN
        RAISE EXCEPTION 'saldo e razao de estoque estao divergentes [ck_saldo_estoque_razao_consistente]'
            USING ERRCODE = '23514',
                  CONSTRAINT = 'ck_saldo_estoque_razao_consistente';
    END IF;
    RETURN NULL;
END;
$$;
