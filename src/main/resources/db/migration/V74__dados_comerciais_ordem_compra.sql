ALTER TABLE ordem_compra
    ADD COLUMN condicao_pagamento_id BIGINT,
    ADD COLUMN transportadora_id BIGINT,
    ADD COLUMN local_estoque_entrega_id BIGINT,
    ADD COLUMN comprador_id BIGINT,
    ADD COLUMN tipo_frete VARCHAR(20) NOT NULL DEFAULT 'SEM_FRETE',
    ADD COLUMN referencia_fornecedor VARCHAR(60),
    ADD COLUMN observacao_interna VARCHAR(1000);

ALTER TABLE ordem_compra
    ADD CONSTRAINT fk_ordem_compra_condicao_tenant
        FOREIGN KEY (organizacao_id, condicao_pagamento_id)
        REFERENCES condicao_pagamento(organizacao_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_ordem_compra_transportadora_tenant
        FOREIGN KEY (organizacao_id, transportadora_id)
        REFERENCES transportadora(organizacao_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_ordem_compra_local_entrega_tenant
        FOREIGN KEY (local_estoque_entrega_id, organizacao_id)
        REFERENCES local_estoque(id, organizacao_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_ordem_compra_comprador
        FOREIGN KEY (comprador_id) REFERENCES usuario(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_ordem_compra_tipo_frete
        CHECK (tipo_frete IN ('SEM_FRETE', 'CIF', 'FOB'));

UPDATE ordem_compra
SET valor_desconto = 0,
    valor_total = subtotal + valor_frete + valor_seguro + outras_despesas
WHERE valor_desconto <> 0;

ALTER TABLE ordem_compra DROP CONSTRAINT ck_ordem_compra_valores;
ALTER TABLE ordem_compra ADD CONSTRAINT ck_ordem_compra_valores CHECK (
    subtotal >= 0
    AND valor_frete >= 0
    AND valor_seguro >= 0
    AND outras_despesas >= 0
    AND valor_desconto = 0
    AND valor_total = subtotal + valor_frete + valor_seguro + outras_despesas
);

CREATE INDEX ix_ordem_compra_condicao
    ON ordem_compra(organizacao_id, condicao_pagamento_id)
    WHERE condicao_pagamento_id IS NOT NULL;
CREATE INDEX ix_ordem_compra_transportadora
    ON ordem_compra(organizacao_id, transportadora_id)
    WHERE transportadora_id IS NOT NULL;
CREATE INDEX ix_ordem_compra_local_entrega
    ON ordem_compra(organizacao_id, local_estoque_entrega_id)
    WHERE local_estoque_entrega_id IS NOT NULL;
