-- Cutover exclusivo do PostgreSQL local de teste; preserva os valores já registrados.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

DO $$
BEGIN
    IF current_database() <> 'estacionamento_kaneko_fiscal_20261105_test'
        OR inet_server_addr() IS DISTINCT FROM inet '127.0.0.1' THEN
        RAISE EXCEPTION 'V97 restrita ao banco fiscal local de teste';
    END IF;
END $$;

-- Os demais CHECKs (positividade, limites entre valores) acompanham as colunas.
ALTER TABLE item_nota_saida DROP CONSTRAINT ck_item_saida_valores;

ALTER TABLE nota_saida
    ALTER COLUMN valor_produtos TYPE NUMERIC(19,0) USING kaneko_micros(valor_produtos),
    ALTER COLUMN valor_frete TYPE NUMERIC(19,0) USING kaneko_micros(valor_frete),
    ALTER COLUMN valor_seguro TYPE NUMERIC(19,0) USING kaneko_micros(valor_seguro),
    ALTER COLUMN outras_despesas TYPE NUMERIC(19,0) USING kaneko_micros(outras_despesas),
    ALTER COLUMN valor_desconto TYPE NUMERIC(19,0) USING kaneko_micros(valor_desconto),
    ALTER COLUMN valor_total TYPE NUMERIC(19,0) USING kaneko_micros(valor_total);
ALTER TABLE nota_saida RENAME COLUMN valor_produtos TO valor_produtos_micros;
ALTER TABLE nota_saida RENAME COLUMN valor_frete TO valor_frete_micros;
ALTER TABLE nota_saida RENAME COLUMN valor_seguro TO valor_seguro_micros;
ALTER TABLE nota_saida RENAME COLUMN outras_despesas TO outras_despesas_micros;
ALTER TABLE nota_saida RENAME COLUMN valor_desconto TO valor_desconto_micros;
ALTER TABLE nota_saida RENAME COLUMN valor_total TO valor_total_micros;

ALTER TABLE item_nota_saida
    ALTER COLUMN quantidade TYPE NUMERIC(18,0) USING kaneko_micros(quantidade),
    ALTER COLUMN valor_unitario TYPE NUMERIC(19,0) USING kaneko_micros(valor_unitario),
    ALTER COLUMN valor_desconto TYPE NUMERIC(19,0) USING kaneko_micros(valor_desconto),
    ALTER COLUMN percentual_desconto TYPE NUMERIC(9,0) USING kaneko_micros(percentual_desconto),
    ALTER COLUMN valor_total TYPE NUMERIC(19,0) USING kaneko_micros(valor_total),
    ALTER COLUMN rateio_frete TYPE NUMERIC(19,0) USING kaneko_micros(rateio_frete),
    ALTER COLUMN rateio_seguro TYPE NUMERIC(19,0) USING kaneko_micros(rateio_seguro),
    ALTER COLUMN rateio_outras TYPE NUMERIC(19,0) USING kaneko_micros(rateio_outras),
    ALTER COLUMN custo_preco_final TYPE NUMERIC(19,0) USING kaneko_micros(custo_preco_final);
ALTER TABLE item_nota_saida RENAME COLUMN quantidade TO quantidade_micros;
ALTER TABLE item_nota_saida RENAME COLUMN valor_unitario TO valor_unitario_micros;
ALTER TABLE item_nota_saida RENAME COLUMN valor_desconto TO valor_desconto_micros;
ALTER TABLE item_nota_saida RENAME COLUMN percentual_desconto TO percentual_desconto_micros;
ALTER TABLE item_nota_saida RENAME COLUMN valor_total TO valor_total_micros;
ALTER TABLE item_nota_saida RENAME COLUMN rateio_frete TO rateio_frete_micros;
ALTER TABLE item_nota_saida RENAME COLUMN rateio_seguro TO rateio_seguro_micros;
ALTER TABLE item_nota_saida RENAME COLUMN rateio_outras TO rateio_outras_micros;
ALTER TABLE item_nota_saida RENAME COLUMN custo_preco_final TO custo_preco_final_micros;

ALTER TABLE parcela_nota_saida
    ALTER COLUMN valor TYPE NUMERIC(19,0) USING kaneko_micros(valor);
ALTER TABLE parcela_nota_saida RENAME COLUMN valor TO valor_micros;

ALTER TABLE conta_receber
    ALTER COLUMN valor_original TYPE NUMERIC(19,0) USING kaneko_micros(valor_original),
    ALTER COLUMN valor_recebido TYPE NUMERIC(19,0) USING kaneko_micros(valor_recebido),
    ALTER COLUMN valor_desconto TYPE NUMERIC(19,0) USING kaneko_micros(valor_desconto),
    ALTER COLUMN valor_juros TYPE NUMERIC(19,0) USING kaneko_micros(valor_juros),
    ALTER COLUMN valor_multa TYPE NUMERIC(19,0) USING kaneko_micros(valor_multa),
    ALTER COLUMN valor_total TYPE NUMERIC(19,0) USING kaneko_micros(valor_total);
ALTER TABLE conta_receber RENAME COLUMN valor_original TO valor_original_micros;
ALTER TABLE conta_receber RENAME COLUMN valor_recebido TO valor_recebido_micros;
ALTER TABLE conta_receber RENAME COLUMN valor_desconto TO valor_desconto_micros;
ALTER TABLE conta_receber RENAME COLUMN valor_juros TO valor_juros_micros;
ALTER TABLE conta_receber RENAME COLUMN valor_multa TO valor_multa_micros;
ALTER TABLE conta_receber RENAME COLUMN valor_total TO valor_total_micros;

ALTER TABLE item_nota_saida ADD CONSTRAINT ck_item_saida_valores CHECK (
    valor_unitario_micros >= 0 AND valor_desconto_micros >= 0 AND valor_total_micros >= 0
    AND percentual_desconto_micros BETWEEN 0 AND 100000000
);
