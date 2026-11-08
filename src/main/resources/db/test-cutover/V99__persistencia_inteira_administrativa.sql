-- Persistência canônica em milionésimos: cadastros, pátio, pagamentos e despesas avulsas.
-- A conversão reaproveita o valor salvo, sem reprocessar documentos ou cobranças.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

DO $$
BEGIN
    IF current_database() <> 'estacionamento_kaneko_fiscal_20261105_test'
        OR inet_server_addr() IS DISTINCT FROM inet '127.0.0.1' THEN
        RAISE EXCEPTION 'V99 restrita ao banco fiscal local de teste';
    END IF;
END $$;

-- Os limites absolutos de percentual precisam ser expressos na nova unidade.
ALTER TABLE condicao_pagamento DROP CONSTRAINT ck_condicao_percentuais;
ALTER TABLE parcela_condicao_pagamento DROP CONSTRAINT ck_parcela_valores;

ALTER TABLE cargo
    ALTER COLUMN salario_base TYPE NUMERIC(14,0) USING kaneko_micros(salario_base),
    ALTER COLUMN carga_horaria TYPE NUMERIC(10,0) USING kaneko_micros(carga_horaria);
ALTER TABLE cargo RENAME COLUMN salario_base TO salario_base_micros;
ALTER TABLE cargo RENAME COLUMN carga_horaria TO carga_horaria_micros;

ALTER TABLE cliente
    ALTER COLUMN limite_credito TYPE NUMERIC(19,0) USING kaneko_micros(limite_credito);
ALTER TABLE cliente RENAME COLUMN limite_credito TO limite_credito_micros;

ALTER TABLE contas_pagar_avulsa
    ALTER COLUMN valor_original TYPE NUMERIC(19,0) USING kaneko_micros(valor_original),
    ALTER COLUMN valor_pago TYPE NUMERIC(19,0) USING kaneko_micros(valor_pago),
    ALTER COLUMN valor_desconto TYPE NUMERIC(19,0) USING kaneko_micros(valor_desconto),
    ALTER COLUMN valor_juros TYPE NUMERIC(19,0) USING kaneko_micros(valor_juros),
    ALTER COLUMN valor_multa TYPE NUMERIC(19,0) USING kaneko_micros(valor_multa),
    ALTER COLUMN valor_total TYPE NUMERIC(19,0) USING kaneko_micros(valor_total);
ALTER TABLE contas_pagar_avulsa RENAME COLUMN valor_original TO valor_original_micros;
ALTER TABLE contas_pagar_avulsa RENAME COLUMN valor_pago TO valor_pago_micros;
ALTER TABLE contas_pagar_avulsa RENAME COLUMN valor_desconto TO valor_desconto_micros;
ALTER TABLE contas_pagar_avulsa RENAME COLUMN valor_juros TO valor_juros_micros;
ALTER TABLE contas_pagar_avulsa RENAME COLUMN valor_multa TO valor_multa_micros;
ALTER TABLE contas_pagar_avulsa RENAME COLUMN valor_total TO valor_total_micros;

ALTER TABLE fornecedor
    ALTER COLUMN limite_credito TYPE NUMERIC(19,0) USING kaneko_micros(limite_credito);
ALTER TABLE fornecedor RENAME COLUMN limite_credito TO limite_credito_micros;

ALTER TABLE funcionario
    ALTER COLUMN salario TYPE NUMERIC(14,0) USING kaneko_micros(salario);
ALTER TABLE funcionario RENAME COLUMN salario TO salario_micros;

ALTER TABLE mensalista
    ALTER COLUMN valor_mensal TYPE NUMERIC(14,0) USING kaneko_micros(valor_mensal);
ALTER TABLE mensalista RENAME COLUMN valor_mensal TO valor_mensal_micros;

ALTER TABLE movimentacao
    ALTER COLUMN valor_cobrado TYPE NUMERIC(14,0) USING kaneko_micros(valor_cobrado);
ALTER TABLE movimentacao RENAME COLUMN valor_cobrado TO valor_cobrado_micros;

ALTER TABLE tarifa
    ALTER COLUMN valor_hora TYPE NUMERIC(14,0) USING kaneko_micros(valor_hora),
    ALTER COLUMN valor_diaria TYPE NUMERIC(14,0) USING kaneko_micros(valor_diaria),
    ALTER COLUMN valor_mensalidade TYPE NUMERIC(14,0) USING kaneko_micros(valor_mensalidade);
ALTER TABLE tarifa RENAME COLUMN valor_hora TO valor_hora_micros;
ALTER TABLE tarifa RENAME COLUMN valor_diaria TO valor_diaria_micros;
ALTER TABLE tarifa RENAME COLUMN valor_mensalidade TO valor_mensalidade_micros;

ALTER TABLE veiculo_frota
    ALTER COLUMN capacidade TYPE NUMERIC(14,0) USING kaneko_micros(capacidade);
ALTER TABLE veiculo_frota RENAME COLUMN capacidade TO capacidade_micros;

ALTER TABLE condicao_pagamento
    ALTER COLUMN percentual_juros TYPE NUMERIC(10,0) USING COALESCE(percentual_juros_micros, kaneko_micros(percentual_juros)),
    ALTER COLUMN percentual_multa TYPE NUMERIC(10,0) USING COALESCE(percentual_multa_micros, kaneko_micros(percentual_multa)),
    ALTER COLUMN percentual_desconto TYPE NUMERIC(9,0) USING COALESCE(percentual_desconto_micros, kaneko_micros(percentual_desconto));
ALTER TABLE condicao_pagamento
    DROP COLUMN percentual_juros_micros,
    DROP COLUMN percentual_multa_micros,
    DROP COLUMN percentual_desconto_micros;
ALTER TABLE condicao_pagamento RENAME COLUMN percentual_juros TO percentual_juros_micros;
ALTER TABLE condicao_pagamento RENAME COLUMN percentual_multa TO percentual_multa_micros;
ALTER TABLE condicao_pagamento RENAME COLUMN percentual_desconto TO percentual_desconto_micros;

ALTER TABLE parcela_condicao_pagamento
    ALTER COLUMN percentual TYPE NUMERIC(9,0) USING COALESCE(percentual_micros, kaneko_micros(percentual));
ALTER TABLE parcela_condicao_pagamento
    DROP COLUMN percentual_micros;
ALTER TABLE parcela_condicao_pagamento RENAME COLUMN percentual TO percentual_micros;

ALTER TABLE condicao_pagamento ADD CONSTRAINT ck_condicao_percentuais CHECK (
    percentual_juros_micros >= 0 AND percentual_multa_micros >= 0
    AND percentual_desconto_micros BETWEEN 0 AND 100000000
);
ALTER TABLE parcela_condicao_pagamento ADD CONSTRAINT ck_parcela_valores CHECK (
    numero >= 1 AND dias >= 0 AND percentual_micros BETWEEN 0 AND 100000000
);
