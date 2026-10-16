CREATE INDEX ix_tentativa_tenant_cursor
    ON tentativa_acesso (organizacao_id, id DESC);

INSERT INTO permissao (codigo, nome)
VALUES ('audit:read', 'Consultar trilhas operacionais auditaveis');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel CROSS JOIN permissao
WHERE papel.codigo IN ('ADMIN_ORGANIZACAO', 'GESTOR')
  AND permissao.codigo = 'audit:read';
