-- Separa o preco de compra (bruto, valor unitario pago) do preco de custo
-- (com rateio de frete/seguro/outras). Ambos passam a ser mantidos pela Nota
-- de Entrada; o custo inicia igual ao valor de compra atual.
ALTER TABLE produto ADD COLUMN custo NUMERIC(15,2) NOT NULL DEFAULT 0;
UPDATE produto SET custo = valor_compra;

-- O percentual de lucro passa a ser derivado ((venda-custo)/custo) e pode ser
-- negativo quando a venda fica abaixo do custo (prejuizo), por isso sai do CHECK.
ALTER TABLE produto DROP CONSTRAINT ck_produto_valores;
ALTER TABLE produto ADD CONSTRAINT ck_produto_valores
    CHECK (valor_compra >= 0 AND custo >= 0 AND valor_venda >= 0
           AND quantidade >= 0 AND quantidade_minima >= 0);
