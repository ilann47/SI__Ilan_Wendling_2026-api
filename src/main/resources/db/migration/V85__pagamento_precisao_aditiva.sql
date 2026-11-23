-- Colunas canônicas para novas escritas. Não altera tipo ou conteúdo das
-- colunas existentes: leitores legados permanecem disponíveis para históricos.
SET LOCAL lock_timeout = '5s';

CREATE FUNCTION kaneko_micros(valor numeric) RETURNS numeric
LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE AS $$
DECLARE inteiro numeric := valor * 1000000;
BEGIN
    IF valor::text IN ('NaN', 'Infinity', '-Infinity') OR inteiro <> trunc(inteiro) THEN
        RAISE EXCEPTION 'Valor deve ser finito e ter no máximo seis casas decimais'
            USING ERRCODE = '22003';
    END IF;
    RETURN trunc(inteiro);
END; $$;

ALTER TABLE condicao_pagamento
    ADD COLUMN percentual_juros_micros NUMERIC(10,0) CHECK (percentual_juros_micros >= 0),
    ADD COLUMN percentual_multa_micros NUMERIC(10,0) CHECK (percentual_multa_micros >= 0),
    ADD COLUMN percentual_desconto_micros NUMERIC(9,0)
        CHECK (percentual_desconto_micros BETWEEN 0 AND 100000000);

ALTER TABLE parcela_condicao_pagamento
    ADD COLUMN percentual_micros NUMERIC(9,0) CHECK (percentual_micros BETWEEN 0 AND 100000000),
    ALTER COLUMN percentual SET DEFAULT 0;
