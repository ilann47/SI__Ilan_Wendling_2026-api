-- Colunas novas, sem alterar nem atualizar os valores antigos.
SET LOCAL lock_timeout = '5s';
ALTER TABLE produto
    ADD COLUMN valor_venda_micros NUMERIC(19,0) CHECK (valor_venda_micros >= 0),
    ADD COLUMN quantidade_minima_micros NUMERIC(18,0) CHECK (quantidade_minima_micros >= 0);
