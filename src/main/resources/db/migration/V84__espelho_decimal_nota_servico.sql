-- Primeira etapa da escala fixa: espelha apenas o cabeçalho da Nota de Serviço.
-- Não muda os valores de origem, não atualiza notas nem reemite parcelas.
-- A leitura e a escrita atuais continuam nas colunas de origem.
SET LOCAL lock_timeout = '5s';

ALTER TABLE nota_servico
    ADD COLUMN valor_servico_micros NUMERIC(19,0) GENERATED ALWAYS AS (valor_servico * 1000000) STORED,
    ADD COLUMN aliquota_iss_micros NUMERIC(9,0) GENERATED ALWAYS AS (aliquota_iss * 1000000) STORED,
    ADD COLUMN valor_iss_micros NUMERIC(19,0) GENERATED ALWAYS AS (valor_iss * 1000000) STORED,
    ADD COLUMN valor_desconto_micros NUMERIC(19,0) GENERATED ALWAYS AS (valor_desconto * 1000000) STORED,
    ADD COLUMN valor_total_micros NUMERIC(19,0) GENERATED ALWAYS AS (valor_total * 1000000) STORED;
