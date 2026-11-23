-- XML original e totais do fornecedor permanecem intactos. A conciliação
-- nova usa a precisão do estoque e grava a mesma unidade inteira da nota.
SET LOCAL lock_timeout = '5s';

ALTER TABLE importacao_nota_entrada
    ADD COLUMN total_micros NUMERIC(19,0) CHECK (total_micros >= 0);

ALTER TABLE item_importacao_nota_entrada
    ALTER COLUMN quantidade TYPE NUMERIC(18,6),
    ALTER COLUMN valor_unitario TYPE NUMERIC(19,6),
    ALTER COLUMN quantidade_conferida TYPE NUMERIC(18,6),
    ADD COLUMN fator_conversao_micros NUMERIC(18,0) CHECK (fator_conversao_micros > 0),
    ADD COLUMN quantidade_micros NUMERIC(18,0) CHECK (quantidade_micros > 0),
    ADD COLUMN valor_unitario_micros NUMERIC(19,0) CHECK (valor_unitario_micros >= 0),
    ADD COLUMN quantidade_conferida_micros NUMERIC(18,0) CHECK (quantidade_conferida_micros >= 0);

ALTER TABLE parcela_importacao_nota
    ALTER COLUMN valor TYPE NUMERIC(19,6),
    ADD COLUMN valor_micros NUMERIC(19,0) CHECK (valor_micros > 0);
