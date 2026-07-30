ALTER TABLE credencial_acesso
    ADD CONSTRAINT fk_credencial_evento_tenant
        FOREIGN KEY (evento_id, organizacao_id)
        REFERENCES evento(id, organizacao_id) ON DELETE RESTRICT;

ALTER TABLE item_pedido
    ADD CONSTRAINT uq_item_pedido_id_pedido_patio
        UNIQUE (id, pedido_id, patio_id);

ALTER TABLE credencial_acesso
    DROP CONSTRAINT fk_credencial_item_pedido;
ALTER TABLE credencial_acesso
    ADD CONSTRAINT fk_credencial_item_pedido_patio
        FOREIGN KEY (item_pedido_id, pedido_id, patio_id)
        REFERENCES item_pedido(id, pedido_id, patio_id) ON DELETE RESTRICT;
