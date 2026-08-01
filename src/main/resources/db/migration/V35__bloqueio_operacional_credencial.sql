INSERT INTO permissao (codigo, nome)
VALUES ('credentials:block', 'Bloquear credencial de acesso');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel CROSS JOIN permissao
WHERE papel.codigo = 'SUPERVISOR'
  AND permissao.codigo = 'credentials:block';
