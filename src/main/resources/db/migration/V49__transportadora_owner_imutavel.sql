CREATE FUNCTION impedir_alteracao_owner_transportadora()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
        RAISE EXCEPTION 'owner de transportadora e imutavel; backfill exige UC732 [ck_transportadora_owner_imutavel]'
            USING ERRCODE = '23514',
                  CONSTRAINT = 'ck_transportadora_owner_imutavel';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_00_transportadora_owner_imutavel
BEFORE UPDATE OF organizacao_id
ON transportadora
FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_owner_transportadora();
