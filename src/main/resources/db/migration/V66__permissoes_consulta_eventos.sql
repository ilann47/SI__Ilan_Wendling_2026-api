INSERT INTO permissao (codigo, nome) VALUES
    ('events:read', 'Consultar eventos'),
    ('inventory:read', 'Consultar alocacoes e inventario'),
    ('pricing:read', 'Consultar produtos e precos de estacionamento');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel CROSS JOIN permissao
WHERE papel.codigo IN ('ADMIN_ORGANIZACAO', 'GESTOR', 'ORGANIZADOR', 'SUPERVISOR', 'OPERADOR')
  AND permissao.codigo IN ('events:read', 'inventory:read');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel CROSS JOIN permissao
WHERE papel.codigo IN ('ADMIN_ORGANIZACAO', 'GESTOR', 'ORGANIZADOR')
  AND permissao.codigo = 'pricing:read';
