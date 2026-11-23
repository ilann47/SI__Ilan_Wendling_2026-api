DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM nota_entrada consumer
        JOIN fornecedor fornecedor ON fornecedor.id = consumer.fornecedor_id
        WHERE fornecedor.organizacao_id IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'nota de entrada global referencia fornecedor tenant [fk_nota_entrada_fornecedor_owner]'
            USING ERRCODE = '23503',
                  CONSTRAINT = 'fk_nota_entrada_fornecedor_owner';
    END IF;
END;
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM conta_pagar consumer
        JOIN fornecedor fornecedor ON fornecedor.id = consumer.fornecedor_id
        WHERE fornecedor.organizacao_id IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'conta a pagar global referencia fornecedor tenant [fk_conta_pagar_fornecedor_owner]'
            USING ERRCODE = '23503',
                  CONSTRAINT = 'fk_conta_pagar_fornecedor_owner';
    END IF;
END;
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM contas_pagar_avulsa consumer
        JOIN fornecedor fornecedor ON fornecedor.id = consumer.fornecedor_id
        WHERE fornecedor.organizacao_id IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'conta a pagar avulsa global referencia fornecedor tenant [fk_contas_pagar_avulsa_fornecedor_owner]'
            USING ERRCODE = '23503',
                  CONSTRAINT = 'fk_contas_pagar_avulsa_fornecedor_owner';
    END IF;
END;
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM produto_fornecedor consumer
        JOIN fornecedor fornecedor ON fornecedor.id = consumer.fornecedor_id
        WHERE fornecedor.organizacao_id IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'produto-fornecedor global referencia fornecedor tenant [fk_produto_fornecedor_fornecedor_owner]'
            USING ERRCODE = '23503',
                  CONSTRAINT = 'fk_produto_fornecedor_fornecedor_owner';
    END IF;
END;
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM fornecedor fornecedor
        JOIN condicao_pagamento condicao
          ON condicao.id = fornecedor.condicao_pagamento_id
        WHERE condicao.organizacao_id IS DISTINCT FROM fornecedor.organizacao_id
    ) THEN
        RAISE EXCEPTION 'fornecedor possui condicao de pagamento com owner divergente [fk_fornecedor_condicao_organizacao]'
            USING ERRCODE = '23503',
                  CONSTRAINT = 'fk_fornecedor_condicao_organizacao';
    END IF;
END;
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM fornecedor fornecedor
        JOIN transportadora transportadora
          ON transportadora.id = fornecedor.transportadora_id
        WHERE transportadora.organizacao_id IS DISTINCT FROM fornecedor.organizacao_id
    ) THEN
        RAISE EXCEPTION 'fornecedor possui transportadora com owner divergente [fk_fornecedor_transportadora_organizacao]'
            USING ERRCODE = '23503',
                  CONSTRAINT = 'fk_fornecedor_transportadora_organizacao';
    END IF;
END;
$$;

CREATE FUNCTION impedir_alteracao_owner_fornecedor()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
        RAISE EXCEPTION 'owner de fornecedor e imutavel; backfill exige UC732 [ck_fornecedor_owner_imutavel]'
            USING ERRCODE = '23514',
                  CONSTRAINT = 'ck_fornecedor_owner_imutavel';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_00_fornecedor_owner_imutavel
BEFORE UPDATE OF organizacao_id
ON fornecedor
FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_owner_fornecedor();
