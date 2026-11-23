DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM funcionario
        GROUP BY regexp_replace(cpf, '[^0-9]', '', 'g')
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'CPFs de funcionario colidem apos normalizacao';
    END IF;
END;
$$;

ALTER TABLE funcionario
    ADD COLUMN organizacao_id BIGINT;

DROP TRIGGER tg_funcionario_cargo_owner ON funcionario;
DROP FUNCTION validar_cargo_funcionario_legado();

ALTER TABLE funcionario
    DROP CONSTRAINT fk_funcionario_cargo,
    DROP CONSTRAINT uq_funcionario_cpf,
    ADD CONSTRAINT fk_funcionario_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_funcionario_organizacao_id
        UNIQUE (organizacao_id, id),
    ADD CONSTRAINT fk_funcionario_cargo_organizacao
        FOREIGN KEY (organizacao_id, cargo_id)
        REFERENCES cargo(organizacao_id, id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_funcionario_organizacao_cpf
    ON funcionario (organizacao_id, regexp_replace(cpf, '[^0-9]', '', 'g'))
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_funcionario_legado_cpf
    ON funcionario (regexp_replace(cpf, '[^0-9]', '', 'g'))
    WHERE organizacao_id IS NULL;
CREATE INDEX ix_funcionario_organizacao_ativo
    ON funcionario (organizacao_id, ativo);

CREATE FUNCTION validar_owner_funcionario_cargo()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    owner_cargo BIGINT;
BEGIN
    SELECT organizacao_id INTO owner_cargo
    FROM cargo
    WHERE id = NEW.cargo_id;

    IF NOT FOUND OR owner_cargo IS DISTINCT FROM NEW.organizacao_id THEN
        RAISE EXCEPTION 'owner do funcionario diverge do cargo [fk_funcionario_cargo_organizacao]'
            USING ERRCODE = '23503',
                  CONSTRAINT = 'fk_funcionario_cargo_organizacao';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_funcionario_cargo_owner
BEFORE INSERT OR UPDATE OF organizacao_id, cargo_id
ON funcionario
FOR EACH ROW EXECUTE FUNCTION validar_owner_funcionario_cargo();

CREATE FUNCTION validar_funcionario_usuario_global()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    owner_funcionario BIGINT;
BEGIN
    IF NEW.funcionario_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT organizacao_id INTO owner_funcionario
    FROM funcionario
    WHERE id = NEW.funcionario_id;

    IF NOT FOUND OR owner_funcionario IS NOT NULL THEN
        RAISE EXCEPTION 'usuario global exige funcionario sem owner [fk_usuario_funcionario_owner]'
            USING ERRCODE = '23503',
                  CONSTRAINT = 'fk_usuario_funcionario_owner';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_usuario_funcionario_owner
BEFORE INSERT OR UPDATE OF funcionario_id
ON usuario
FOR EACH ROW EXECUTE FUNCTION validar_funcionario_usuario_global();

CREATE FUNCTION proteger_owner_funcionario_com_usuario_global()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.organizacao_id IS NOT NULL
       AND EXISTS (SELECT 1 FROM usuario WHERE funcionario_id = NEW.id) THEN
        RAISE EXCEPTION 'funcionario vinculado a usuario global nao pode receber owner [fk_usuario_funcionario_owner]'
            USING ERRCODE = '23503',
                  CONSTRAINT = 'fk_usuario_funcionario_owner';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_funcionario_usuario_global_owner
BEFORE UPDATE OF organizacao_id
ON funcionario
FOR EACH ROW EXECUTE FUNCTION proteger_owner_funcionario_com_usuario_global();
