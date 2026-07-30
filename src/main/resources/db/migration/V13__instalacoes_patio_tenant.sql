ALTER TABLE patio ADD COLUMN organizacao_id BIGINT;
ALTER TABLE patio ADD COLUMN capacidade_operacional INTEGER;
ALTER TABLE patio ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE patio SET capacidade_operacional = capacidade_vagas;

ALTER TABLE patio ALTER COLUMN capacidade_operacional SET NOT NULL;
ALTER TABLE patio ADD CONSTRAINT fk_patio_organizacao
    FOREIGN KEY (organizacao_id) REFERENCES organizacao (id) ON DELETE RESTRICT;
ALTER TABLE patio ADD CONSTRAINT ck_patio_capacidade_operacional
    CHECK (capacidade_operacional >= 0 AND capacidade_operacional <= capacidade_vagas);
ALTER TABLE patio ADD CONSTRAINT ck_patio_version CHECK (version >= 0);

CREATE INDEX ix_patio_organizacao ON patio (organizacao_id);
CREATE INDEX ix_patio_organizacao_ativo ON patio (organizacao_id, ativo)
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_patio_organizacao_nome_normalizado
    ON patio (organizacao_id, lower(btrim(nome)))
    WHERE organizacao_id IS NOT NULL;

INSERT INTO permissao (codigo, nome)
VALUES ('facilities:manage', 'Administrar instalacoes fisicas');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel CROSS JOIN permissao
WHERE papel.codigo = 'ADMIN_ORGANIZACAO'
  AND permissao.codigo = 'facilities:manage';
