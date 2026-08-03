DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM fornecedor
        GROUP BY organizacao_id, regexp_replace(documento, '[^0-9]', '', 'g')
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'documentos de fornecedor colidem apos normalizacao';
    END IF;
END;
$$;

DROP INDEX uq_fornecedor_organizacao_documento;
DROP INDEX uq_fornecedor_legado_documento;

CREATE UNIQUE INDEX uq_fornecedor_organizacao_documento
    ON fornecedor (organizacao_id, regexp_replace(documento, '[^0-9]', '', 'g'))
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_fornecedor_legado_documento
    ON fornecedor (regexp_replace(documento, '[^0-9]', '', 'g'))
    WHERE organizacao_id IS NULL;

CREATE FUNCTION validar_fornecedor_historico_em_consumer()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    owner_fornecedor BIGINT;
    nome_constraint TEXT;
BEGIN
    IF NEW.fornecedor_id IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT organizacao_id INTO owner_fornecedor
    FROM fornecedor
    WHERE id = NEW.fornecedor_id;

    nome_constraint := CASE TG_TABLE_NAME
        WHEN 'nota_entrada' THEN 'fk_nota_entrada_fornecedor_owner'
        WHEN 'conta_pagar' THEN 'fk_conta_pagar_fornecedor_owner'
        WHEN 'contas_pagar_avulsa' THEN 'fk_contas_pagar_avulsa_fornecedor_owner'
        WHEN 'produto_fornecedor' THEN 'fk_produto_fornecedor_fornecedor_owner'
    END;

    IF NOT FOUND OR owner_fornecedor IS NOT NULL THEN
        RAISE EXCEPTION 'consumer global exige fornecedor sem owner [%]', nome_constraint
            USING ERRCODE = '23503', CONSTRAINT = nome_constraint;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_nota_entrada_fornecedor_owner
BEFORE INSERT OR UPDATE OF fornecedor_id ON nota_entrada
FOR EACH ROW EXECUTE FUNCTION validar_fornecedor_historico_em_consumer();

CREATE TRIGGER tg_conta_pagar_fornecedor_owner
BEFORE INSERT OR UPDATE OF fornecedor_id ON conta_pagar
FOR EACH ROW EXECUTE FUNCTION validar_fornecedor_historico_em_consumer();

CREATE TRIGGER tg_contas_pagar_avulsa_fornecedor_owner
BEFORE INSERT OR UPDATE OF fornecedor_id ON contas_pagar_avulsa
FOR EACH ROW EXECUTE FUNCTION validar_fornecedor_historico_em_consumer();

CREATE TRIGGER tg_produto_fornecedor_fornecedor_owner
BEFORE INSERT OR UPDATE OF fornecedor_id ON produto_fornecedor
FOR EACH ROW EXECUTE FUNCTION validar_fornecedor_historico_em_consumer();

CREATE FUNCTION impedir_backfill_parcial_fornecedor()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.organizacao_id IS NULL AND NEW.organizacao_id IS NOT NULL
       AND (
           EXISTS (SELECT 1 FROM nota_entrada WHERE fornecedor_id = OLD.id)
           OR EXISTS (SELECT 1 FROM conta_pagar WHERE fornecedor_id = OLD.id)
           OR EXISTS (SELECT 1 FROM contas_pagar_avulsa WHERE fornecedor_id = OLD.id)
           OR EXISTS (SELECT 1 FROM produto_fornecedor WHERE fornecedor_id = OLD.id)
       ) THEN
        RAISE EXCEPTION 'backfill do fornecedor exige migracao atomica do grafo [ck_fornecedor_backfill_dependencias]'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_fornecedor_backfill_dependencias';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_fornecedor_backfill_dependencias
BEFORE UPDATE OF organizacao_id ON fornecedor
FOR EACH ROW EXECUTE FUNCTION impedir_backfill_parcial_fornecedor();

CREATE FUNCTION impedir_backfill_parent_referenciado_por_fornecedor()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    possui_referencia BOOLEAN;
    nome_constraint TEXT;
BEGIN
    IF OLD.organizacao_id IS NULL AND NEW.organizacao_id IS NOT NULL THEN
        IF TG_TABLE_NAME = 'condicao_pagamento' THEN
            SELECT EXISTS (
                SELECT 1 FROM fornecedor
                WHERE organizacao_id IS NULL AND condicao_pagamento_id = OLD.id
            ) INTO possui_referencia;
            nome_constraint := 'ck_condicao_backfill_fornecedor';
        ELSE
            SELECT EXISTS (
                SELECT 1 FROM fornecedor
                WHERE organizacao_id IS NULL AND transportadora_id = OLD.id
            ) INTO possui_referencia;
            nome_constraint := 'ck_transportadora_backfill_fornecedor';
        END IF;

        IF possui_referencia THEN
            RAISE EXCEPTION 'backfill do parent exige migracao atomica do fornecedor [%]', nome_constraint
                USING ERRCODE = '23514', CONSTRAINT = nome_constraint;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_condicao_backfill_fornecedor
BEFORE UPDATE OF organizacao_id ON condicao_pagamento
FOR EACH ROW EXECUTE FUNCTION impedir_backfill_parent_referenciado_por_fornecedor();

CREATE TRIGGER tg_transportadora_backfill_fornecedor
BEFORE UPDATE OF organizacao_id ON transportadora
FOR EACH ROW EXECUTE FUNCTION impedir_backfill_parent_referenciado_por_fornecedor();
