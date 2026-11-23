DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM funcionario f
        JOIN cargo c ON c.id = f.cargo_id
        WHERE c.organizacao_id IS DISTINCT FROM f.organizacao_id
    ) THEN
        RAISE EXCEPTION 'grafo Cargo-Funcionario possui owners divergentes [fk_funcionario_cargo_organizacao]'
            USING ERRCODE = '23503',
                  CONSTRAINT = 'fk_funcionario_cargo_organizacao';
    END IF;
END;
$$;

CREATE FUNCTION impedir_alteracao_owner_funcionario()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
        RAISE EXCEPTION 'owner de funcionario e imutavel; backfill exige UC732 [ck_funcionario_owner_imutavel]'
            USING ERRCODE = '23514',
                  CONSTRAINT = 'ck_funcionario_owner_imutavel';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_00_funcionario_owner_imutavel
BEFORE UPDATE OF organizacao_id
ON funcionario
FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_owner_funcionario();

CREATE FUNCTION impedir_alteracao_owner_cargo()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
        RAISE EXCEPTION 'owner de cargo e imutavel; backfill exige UC732 [ck_cargo_owner_imutavel]'
            USING ERRCODE = '23514',
                  CONSTRAINT = 'ck_cargo_owner_imutavel';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_00_cargo_owner_imutavel
BEFORE UPDATE OF organizacao_id
ON cargo
FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_owner_cargo();
