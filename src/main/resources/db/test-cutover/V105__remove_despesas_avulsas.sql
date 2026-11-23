-- Corte destrutivo restrito ao schema público do PostgreSQL fiscal local de teste.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

DO $$
BEGIN
    IF current_database() <> 'estacionamento_kaneko_fiscal_20261105_test'
        OR current_schema() IS DISTINCT FROM 'public'
        OR inet_server_addr() IS DISTINCT FROM inet '127.0.0.1' THEN
        RAISE EXCEPTION 'V105 restrita ao schema publico do banco fiscal local de teste';
    END IF;
END $$;

CREATE OR REPLACE FUNCTION public.validar_fornecedor_historico_em_consumer()
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
    FROM public.fornecedor
    WHERE id = NEW.fornecedor_id;

    nome_constraint := CASE TG_TABLE_NAME
        WHEN 'nota_entrada' THEN 'fk_nota_entrada_fornecedor_owner'
        WHEN 'conta_pagar' THEN 'fk_conta_pagar_fornecedor_owner'
        WHEN 'produto_fornecedor' THEN 'fk_produto_fornecedor_fornecedor_owner'
    END;

    IF NOT FOUND OR owner_fornecedor IS NOT NULL THEN
        RAISE EXCEPTION 'consumer global exige fornecedor sem owner [%]', nome_constraint
            USING ERRCODE = '23503', CONSTRAINT = nome_constraint;
    END IF;

    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.impedir_backfill_parcial_fornecedor()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.organizacao_id IS NULL AND NEW.organizacao_id IS NOT NULL
       AND (
           EXISTS (SELECT 1 FROM public.nota_entrada WHERE fornecedor_id = OLD.id)
           OR EXISTS (SELECT 1 FROM public.conta_pagar WHERE fornecedor_id = OLD.id)
           OR EXISTS (SELECT 1 FROM public.produto_fornecedor WHERE fornecedor_id = OLD.id)
       ) THEN
        RAISE EXCEPTION 'backfill do fornecedor exige migracao atomica do grafo [ck_fornecedor_backfill_dependencias]'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_fornecedor_backfill_dependencias';
    END IF;
    RETURN NEW;
END;
$$;

DROP TABLE public.contas_pagar_avulsa RESTRICT;
DROP FUNCTION public.impedir_owner_despesa_avulsa_v62() RESTRICT;
DROP FUNCTION public.validar_despesa_avulsa_owner_v62() RESTRICT;
