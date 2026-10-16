ALTER TABLE importacao_nota_entrada
    ADD COLUMN registrar_novo_recebimento BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE importacao_nota_entrada
    ADD CONSTRAINT ck_importacao_xml_novo_recebimento
    CHECK (NOT registrar_novo_recebimento OR ordem_compra_id IS NOT NULL);
