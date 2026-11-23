INSERT INTO permissao (codigo, nome)
VALUES ('operations:checkout', 'Operar entrada, saída e caixa do pátio');

INSERT INTO papel (codigo, nome, sistema)
VALUES ('CAIXA_PATIO', 'Caixa do pátio', TRUE);

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id FROM papel CROSS JOIN permissao
WHERE papel.codigo IN ('CAIXA_PATIO', 'ADMIN_ORGANIZACAO')
    AND permissao.codigo = 'operations:checkout';
