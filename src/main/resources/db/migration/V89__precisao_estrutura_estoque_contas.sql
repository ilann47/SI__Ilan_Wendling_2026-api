-- Expansão aditiva: ainda não altera a leitura, o cálculo ou as linhas históricas.
SET LOCAL lock_timeout = '5s';

ALTER TABLE saldo_estoque
    ADD COLUMN quantidade_micros NUMERIC(18,0) CHECK (quantidade_micros >= 0);

ALTER TABLE movimento_estoque
    ADD COLUMN delta_micros NUMERIC(18,0) CHECK (delta_micros <> 0),
    ADD COLUMN saldo_anterior_micros NUMERIC(18,0) CHECK (saldo_anterior_micros >= 0),
    ADD COLUMN saldo_posterior_micros NUMERIC(18,0) CHECK (saldo_posterior_micros >= 0),
    ADD COLUMN custo_unitario_micros NUMERIC(19,0) CHECK (custo_unitario_micros >= 0),
    ADD COLUMN saldo_total_anterior_micros NUMERIC(18,0) CHECK (saldo_total_anterior_micros >= 0),
    ADD COLUMN saldo_total_posterior_micros NUMERIC(18,0) CHECK (saldo_total_posterior_micros >= 0),
    ADD COLUMN custo_medio_anterior_micros NUMERIC(19,0) CHECK (custo_medio_anterior_micros >= 0),
    ADD COLUMN custo_medio_posterior_micros NUMERIC(19,0) CHECK (custo_medio_posterior_micros >= 0),
    ADD CONSTRAINT ck_movimento_estoque_cadeia_micros CHECK (
        num_nonnulls(delta_micros, saldo_anterior_micros, saldo_posterior_micros) = 0 OR (
            num_nonnulls(delta_micros, saldo_anterior_micros, saldo_posterior_micros) = 3
            AND saldo_posterior_micros = saldo_anterior_micros + delta_micros
            AND ((tipo = 'RECEBIMENTO' AND delta_micros > 0)
              OR (tipo = 'BAIXA' AND delta_micros < 0)
              OR tipo IN ('AJUSTE','COMPENSACAO'))
        )
    );

ALTER TABLE produto
    ADD COLUMN valor_compra_micros NUMERIC(19,0) CHECK (valor_compra_micros >= 0),
    ADD COLUMN custo_micros NUMERIC(19,0) CHECK (custo_micros >= 0);

ALTER TABLE conta_pagar
    ADD COLUMN valor_original_micros NUMERIC(19,0) CHECK (valor_original_micros >= 0),
    ADD COLUMN valor_pago_micros NUMERIC(19,0) CHECK (valor_pago_micros >= 0),
    ADD COLUMN valor_desconto_micros NUMERIC(19,0) CHECK (valor_desconto_micros >= 0),
    ADD COLUMN valor_juros_micros NUMERIC(19,0) CHECK (valor_juros_micros >= 0),
    ADD COLUMN valor_multa_micros NUMERIC(19,0) CHECK (valor_multa_micros >= 0),
    ADD COLUMN valor_total_micros NUMERIC(19,0) CHECK (valor_total_micros >= 0);

ALTER TABLE baixa_financeira
    ADD COLUMN valor_micros NUMERIC(19,0) CHECK (valor_micros > 0);
