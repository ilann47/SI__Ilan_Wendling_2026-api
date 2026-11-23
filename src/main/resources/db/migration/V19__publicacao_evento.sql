INSERT INTO permissao (codigo, nome)
VALUES ('events:publish', 'Publicar eventos');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel CROSS JOIN permissao
WHERE papel.codigo IN ('ADMIN_ORGANIZACAO', 'GESTOR')
  AND permissao.codigo = 'events:publish';
