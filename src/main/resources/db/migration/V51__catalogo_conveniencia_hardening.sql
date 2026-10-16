DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM unidade_medida
        WHERE organizacao_id IS NOT NULL
        GROUP BY organizacao_id, lower(btrim(nome)) HAVING count(*) > 1
    ) OR EXISTS (
        SELECT 1 FROM unidade_medida
        WHERE organizacao_id IS NULL
        GROUP BY lower(btrim(nome)) HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'nomes de unidades historicas colidem apos normalizacao [uq_unidade_medida_legado_nome]';
    END IF;
END;
$$;

ALTER TABLE categoria ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE marca ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE unidade_medida ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE categoria ADD CONSTRAINT ck_categoria_version CHECK (version >= 0);
ALTER TABLE marca ADD CONSTRAINT ck_marca_version CHECK (version >= 0);
ALTER TABLE unidade_medida ADD CONSTRAINT ck_unidade_medida_version CHECK (version >= 0);

CREATE UNIQUE INDEX uq_unidade_medida_organizacao_nome
    ON unidade_medida (organizacao_id, lower(btrim(nome)))
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_unidade_medida_legado_nome
    ON unidade_medida (lower(btrim(nome)))
    WHERE organizacao_id IS NULL;

CREATE FUNCTION proteger_categoria_referenciada_por_produto()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id
       AND EXISTS (SELECT 1 FROM produto WHERE categoria_id = OLD.id) THEN
        RAISE EXCEPTION 'categoria referenciada por produto global nao pode mudar de owner [fk_produto_categoria_owner]'
            USING ERRCODE = '23503', CONSTRAINT = 'fk_produto_categoria_owner';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_000_categoria_produto_owner
BEFORE UPDATE OF organizacao_id ON categoria
FOR EACH ROW EXECUTE FUNCTION proteger_categoria_referenciada_por_produto();

CREATE FUNCTION proteger_marca_referenciada_por_produto()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id
       AND EXISTS (SELECT 1 FROM produto WHERE marca_id = OLD.id) THEN
        RAISE EXCEPTION 'marca referenciada por produto global nao pode mudar de owner [fk_produto_marca_owner]'
            USING ERRCODE = '23503', CONSTRAINT = 'fk_produto_marca_owner';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_000_marca_produto_owner
BEFORE UPDATE OF organizacao_id ON marca
FOR EACH ROW EXECUTE FUNCTION proteger_marca_referenciada_por_produto();

CREATE FUNCTION proteger_unidade_referenciada_por_produto()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id
       AND EXISTS (SELECT 1 FROM produto WHERE unidade_medida_id = OLD.id) THEN
        RAISE EXCEPTION 'unidade referenciada por produto global nao pode mudar de owner [fk_produto_unidade_owner]'
            USING ERRCODE = '23503', CONSTRAINT = 'fk_produto_unidade_owner';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_000_unidade_produto_owner
BEFORE UPDATE OF organizacao_id ON unidade_medida
FOR EACH ROW EXECUTE FUNCTION proteger_unidade_referenciada_por_produto();
