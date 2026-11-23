ALTER TABLE cargo
    ADD COLUMN organizacao_id BIGINT;

ALTER TABLE cargo
    ADD CONSTRAINT fk_cargo_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_cargo_organizacao_id
        UNIQUE (organizacao_id, id);

ALTER TABLE cargo DROP CONSTRAINT uq_cargo_nome;

CREATE UNIQUE INDEX uq_cargo_organizacao_nome
    ON cargo (organizacao_id, lower(btrim(nome)))
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_cargo_legado_nome
    ON cargo (nome)
    WHERE organizacao_id IS NULL;
CREATE INDEX ix_cargo_organizacao_ativo
    ON cargo (organizacao_id, ativo);

CREATE FUNCTION validar_cargo_funcionario_legado()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    owner_cargo BIGINT;
BEGIN
    SELECT organizacao_id INTO owner_cargo
    FROM cargo
    WHERE id = NEW.cargo_id;

    IF NOT FOUND OR owner_cargo IS NOT NULL THEN
        RAISE EXCEPTION 'funcionario global exige cargo sem owner [fk_funcionario_cargo_owner]'
            USING ERRCODE = '23503', CONSTRAINT = 'fk_funcionario_cargo_owner';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_funcionario_cargo_owner
BEFORE INSERT OR UPDATE OF cargo_id
ON funcionario
FOR EACH ROW EXECUTE FUNCTION validar_cargo_funcionario_legado();

INSERT INTO permissao (codigo, nome) VALUES
    ('workforce:read', 'Consultar cargos e estrutura de RH'),
    ('workforce:manage', 'Administrar cargos e estrutura de RH');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel
CROSS JOIN permissao
WHERE papel.codigo = 'ADMIN_ORGANIZACAO'
  AND permissao.codigo IN ('workforce:read', 'workforce:manage');
