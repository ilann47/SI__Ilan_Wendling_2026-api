DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM produto_fornecedor pf
        JOIN produto p ON p.id = pf.produto_id
        JOIN fornecedor f ON f.id = pf.fornecedor_id
        WHERE p.organizacao_id IS NOT NULL
           OR f.organizacao_id IS NOT NULL
           OR p.organizacao_id IS DISTINCT FROM f.organizacao_id
    ) THEN
        RAISE EXCEPTION 'produto-fornecedor historico possui owner incompatível [fk_produto_fornecedor_owner_preflight]'
            USING ERRCODE = '23503', CONSTRAINT = 'fk_produto_fornecedor_owner_preflight';
    END IF;
END;
$$;

ALTER TABLE produto_fornecedor
    ADD COLUMN organizacao_id BIGINT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE produto_fornecedor
    ADD CONSTRAINT fk_produto_fornecedor_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_produto_fornecedor_organizacao_id
        UNIQUE (organizacao_id, id),
    ADD CONSTRAINT fk_produto_fornecedor_produto_organizacao
        FOREIGN KEY (organizacao_id, produto_id)
        REFERENCES produto(organizacao_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_produto_fornecedor_fornecedor_organizacao
        FOREIGN KEY (organizacao_id, fornecedor_id)
        REFERENCES fornecedor(organizacao_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_produto_fornecedor_version CHECK (version >= 0);

DROP TRIGGER tg_produto_fornecedor_produto_owner ON produto_fornecedor;
DROP TRIGGER tg_produto_fornecedor_fornecedor_owner ON produto_fornecedor;

CREATE FUNCTION validar_produto_fornecedor_mesmo_owner()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE
    owner_produto BIGINT;
    owner_fornecedor BIGINT;
BEGIN
    SELECT organizacao_id INTO owner_produto FROM produto WHERE id = NEW.produto_id;
    IF NOT FOUND OR owner_produto IS DISTINCT FROM NEW.organizacao_id THEN
        RAISE EXCEPTION 'owner do vínculo diverge do produto [fk_produto_fornecedor_produto_organizacao]'
            USING ERRCODE = '23503', CONSTRAINT = 'fk_produto_fornecedor_produto_organizacao';
    END IF;

    SELECT organizacao_id INTO owner_fornecedor FROM fornecedor WHERE id = NEW.fornecedor_id;
    IF NOT FOUND OR owner_fornecedor IS DISTINCT FROM NEW.organizacao_id THEN
        RAISE EXCEPTION 'owner do vínculo diverge do fornecedor [fk_produto_fornecedor_fornecedor_organizacao]'
            USING ERRCODE = '23503', CONSTRAINT = 'fk_produto_fornecedor_fornecedor_organizacao';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_produto_fornecedor_owner
BEFORE INSERT OR UPDATE OF organizacao_id, produto_id, fornecedor_id
ON produto_fornecedor
FOR EACH ROW EXECUTE FUNCTION validar_produto_fornecedor_mesmo_owner();

CREATE FUNCTION impedir_alteracao_identidade_produto_fornecedor()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
        RAISE EXCEPTION 'owner do vínculo produto-fornecedor é imutável [ck_produto_fornecedor_owner_imutavel]'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_produto_fornecedor_owner_imutavel';
    END IF;
    IF NEW.produto_id IS DISTINCT FROM OLD.produto_id
       OR NEW.fornecedor_id IS DISTINCT FROM OLD.fornecedor_id THEN
        RAISE EXCEPTION 'pontas do vínculo produto-fornecedor são imutáveis [ck_produto_fornecedor_identidade_imutavel]'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_produto_fornecedor_identidade_imutavel';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_00_produto_fornecedor_identidade_imutavel
BEFORE UPDATE OF organizacao_id, produto_id, fornecedor_id
ON produto_fornecedor
FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_identidade_produto_fornecedor();

CREATE INDEX ix_produto_fornecedor_organizacao_ativo
    ON produto_fornecedor(organizacao_id, ativo);
CREATE INDEX ix_produto_fornecedor_organizacao_produto
    ON produto_fornecedor(organizacao_id, produto_id);
