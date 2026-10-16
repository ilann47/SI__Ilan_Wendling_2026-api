DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM categoria
        GROUP BY lower(btrim(nome)) HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'categorias historicas colidem apos normalizacao [uq_categoria_legado_nome]';
    END IF;
    IF EXISTS (
        SELECT 1 FROM marca
        GROUP BY lower(btrim(nome)) HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'marcas historicas colidem apos normalizacao [uq_marca_legado_nome]';
    END IF;
    IF EXISTS (
        SELECT 1 FROM unidade_medida
        GROUP BY upper(btrim(sigla)) HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'unidades historicas colidem apos normalizacao [uq_unidade_medida_legado_sigla]';
    END IF;
END;
$$;

ALTER TABLE categoria ADD COLUMN organizacao_id BIGINT;
ALTER TABLE marca ADD COLUMN organizacao_id BIGINT;
ALTER TABLE unidade_medida ADD COLUMN organizacao_id BIGINT;

ALTER TABLE categoria
    ADD CONSTRAINT fk_categoria_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_categoria_organizacao_id UNIQUE (organizacao_id, id);
ALTER TABLE marca
    ADD CONSTRAINT fk_marca_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_marca_organizacao_id UNIQUE (organizacao_id, id);
ALTER TABLE unidade_medida
    ADD CONSTRAINT fk_unidade_medida_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_unidade_medida_organizacao_id UNIQUE (organizacao_id, id);

ALTER TABLE categoria DROP CONSTRAINT uq_categoria_nome;
ALTER TABLE marca DROP CONSTRAINT uq_marca_nome;
ALTER TABLE unidade_medida DROP CONSTRAINT uq_unidade_sigla;

CREATE UNIQUE INDEX uq_categoria_organizacao_nome
    ON categoria (organizacao_id, lower(btrim(nome)))
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_categoria_legado_nome
    ON categoria (lower(btrim(nome)))
    WHERE organizacao_id IS NULL;
CREATE INDEX ix_categoria_organizacao_ativo
    ON categoria (organizacao_id, ativo);

CREATE UNIQUE INDEX uq_marca_organizacao_nome
    ON marca (organizacao_id, lower(btrim(nome)))
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_marca_legado_nome
    ON marca (lower(btrim(nome)))
    WHERE organizacao_id IS NULL;
CREATE INDEX ix_marca_organizacao_ativo
    ON marca (organizacao_id, ativo);

CREATE UNIQUE INDEX uq_unidade_medida_organizacao_sigla
    ON unidade_medida (organizacao_id, upper(btrim(sigla)))
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_unidade_medida_legado_sigla
    ON unidade_medida (upper(btrim(sigla)))
    WHERE organizacao_id IS NULL;
CREATE INDEX ix_unidade_medida_organizacao_ativo
    ON unidade_medida (organizacao_id, ativo);

CREATE FUNCTION impedir_alteracao_owner_categoria()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
        RAISE EXCEPTION 'owner de categoria e imutavel; backfill exige UC732 [ck_categoria_owner_imutavel]'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_categoria_owner_imutavel';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_00_categoria_owner_imutavel
BEFORE UPDATE OF organizacao_id ON categoria
FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_owner_categoria();

CREATE FUNCTION impedir_alteracao_owner_marca()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
        RAISE EXCEPTION 'owner de marca e imutavel; backfill exige UC732 [ck_marca_owner_imutavel]'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_marca_owner_imutavel';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_00_marca_owner_imutavel
BEFORE UPDATE OF organizacao_id ON marca
FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_owner_marca();

CREATE FUNCTION impedir_alteracao_owner_unidade_medida()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
        RAISE EXCEPTION 'owner de unidade de medida e imutavel; backfill exige UC732 [ck_unidade_medida_owner_imutavel]'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_unidade_medida_owner_imutavel';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_00_unidade_medida_owner_imutavel
BEFORE UPDATE OF organizacao_id ON unidade_medida
FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_owner_unidade_medida();

CREATE FUNCTION validar_produto_catalogo_historico()
RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    owner_marca BIGINT;
    owner_unidade BIGINT;
    owner_categoria BIGINT;
BEGIN
    SELECT organizacao_id INTO owner_marca FROM marca WHERE id = NEW.marca_id;
    IF NOT FOUND OR owner_marca IS NOT NULL THEN
        RAISE EXCEPTION 'produto global exige marca sem owner [fk_produto_marca_owner]'
            USING ERRCODE = '23503', CONSTRAINT = 'fk_produto_marca_owner';
    END IF;

    SELECT organizacao_id INTO owner_unidade
    FROM unidade_medida WHERE id = NEW.unidade_medida_id;
    IF NOT FOUND OR owner_unidade IS NOT NULL THEN
        RAISE EXCEPTION 'produto global exige unidade sem owner [fk_produto_unidade_owner]'
            USING ERRCODE = '23503', CONSTRAINT = 'fk_produto_unidade_owner';
    END IF;

    IF NEW.categoria_id IS NOT NULL THEN
        SELECT organizacao_id INTO owner_categoria
        FROM categoria WHERE id = NEW.categoria_id;
        IF NOT FOUND OR owner_categoria IS NOT NULL THEN
            RAISE EXCEPTION 'produto global exige categoria sem owner [fk_produto_categoria_owner]'
                USING ERRCODE = '23503', CONSTRAINT = 'fk_produto_categoria_owner';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_produto_catalogo_owner
BEFORE INSERT OR UPDATE OF marca_id, unidade_medida_id, categoria_id
ON produto
FOR EACH ROW EXECUTE FUNCTION validar_produto_catalogo_historico();

INSERT INTO permissao (codigo, nome) VALUES
    ('catalog:read', 'Consultar catalogo de conveniencia'),
    ('catalog:manage', 'Administrar catalogo de conveniencia');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel
CROSS JOIN permissao
WHERE papel.codigo = 'ADMIN_ORGANIZACAO'
  AND permissao.codigo IN ('catalog:read', 'catalog:manage');
