CREATE UNIQUE INDEX uq_forma_pagamento_legado_nome
    ON forma_pagamento (nome)
    WHERE organizacao_id IS NULL;

CREATE UNIQUE INDEX uq_condicao_pagamento_legado_nome
    ON condicao_pagamento (nome)
    WHERE organizacao_id IS NULL;

CREATE FUNCTION validar_owner_parcela_pagamento()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    owner_condicao BIGINT;
    owner_forma BIGINT;
BEGIN
    SELECT organizacao_id INTO owner_condicao
    FROM condicao_pagamento
    WHERE id = NEW.condicao_pagamento_id;

    IF NOT FOUND OR owner_condicao IS DISTINCT FROM NEW.organizacao_id THEN
        RAISE EXCEPTION 'owner da parcela diverge da condicao [fk_parcela_condicao_organizacao]'
            USING ERRCODE = '23503',
                  CONSTRAINT = 'fk_parcela_condicao_organizacao';
    END IF;

    IF NEW.forma_pagamento_id IS NOT NULL THEN
        SELECT organizacao_id INTO owner_forma
        FROM forma_pagamento
        WHERE id = NEW.forma_pagamento_id;

        IF NOT FOUND OR owner_forma IS DISTINCT FROM NEW.organizacao_id THEN
            RAISE EXCEPTION 'owner da parcela diverge da forma [fk_parcela_forma_organizacao]'
                USING ERRCODE = '23503',
                      CONSTRAINT = 'fk_parcela_forma_organizacao';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_parcela_pagamento_owner
BEFORE INSERT OR UPDATE OF organizacao_id, condicao_pagamento_id, forma_pagamento_id
ON parcela_condicao_pagamento
FOR EACH ROW EXECUTE FUNCTION validar_owner_parcela_pagamento();
