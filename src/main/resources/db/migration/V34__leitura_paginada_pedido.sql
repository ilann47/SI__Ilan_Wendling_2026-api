CREATE INDEX ix_pedido_ator_cursor
    ON pedido (organizacao_id, ator_id, id DESC);

INSERT INTO permissao (codigo, nome)
VALUES ('orders:read', 'Consultar pedidos proprios');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel CROSS JOIN permissao
WHERE papel.codigo IN ('ADMIN_ORGANIZACAO', 'GESTOR', 'ORGANIZADOR')
  AND permissao.codigo = 'orders:read';
