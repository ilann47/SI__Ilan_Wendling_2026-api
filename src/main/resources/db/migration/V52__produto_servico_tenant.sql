DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM produto WHERE codigo_barras IS NOT NULL
        GROUP BY btrim(codigo_barras) HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'codigos de barras historicos colidem apos normalizacao [uq_produto_legado_codigo_barras]';
    END IF;
END;
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM servico
        GROUP BY lower(btrim(nome)) HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'servicos historicos colidem apos normalizacao [uq_servico_legado_nome]';
    END IF;
END;
$$;

ALTER TABLE produto ADD COLUMN organizacao_id BIGINT, ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE servico ADD COLUMN organizacao_id BIGINT, ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE produto
    ADD CONSTRAINT fk_produto_organizacao FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_produto_organizacao_id UNIQUE (organizacao_id,id),
    ADD CONSTRAINT ck_produto_version CHECK (version >= 0);
ALTER TABLE servico
    ADD CONSTRAINT fk_servico_organizacao FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_servico_organizacao_id UNIQUE (organizacao_id,id),
    ADD CONSTRAINT ck_servico_version CHECK (version >= 0);

DROP TRIGGER tg_produto_catalogo_owner ON produto;
ALTER TABLE produto
    DROP CONSTRAINT fk_produto_marca,
    DROP CONSTRAINT fk_produto_unidade,
    DROP CONSTRAINT fk_produto_categoria,
    ADD CONSTRAINT fk_produto_marca_organizacao FOREIGN KEY (organizacao_id,marca_id)
        REFERENCES marca(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_produto_unidade_organizacao FOREIGN KEY (organizacao_id,unidade_medida_id)
        REFERENCES unidade_medida(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_produto_categoria_organizacao FOREIGN KEY (organizacao_id,categoria_id)
        REFERENCES categoria(organizacao_id,id) ON DELETE RESTRICT;

CREATE FUNCTION validar_produto_catalogo_mesmo_owner()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
    SELECT organizacao_id INTO owner_ref FROM marca WHERE id=NEW.marca_id;
    IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
        RAISE EXCEPTION 'owner do produto diverge da marca [fk_produto_marca_organizacao]'
          USING ERRCODE='23503', CONSTRAINT='fk_produto_marca_organizacao';
    END IF;
    SELECT organizacao_id INTO owner_ref FROM unidade_medida WHERE id=NEW.unidade_medida_id;
    IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
        RAISE EXCEPTION 'owner do produto diverge da unidade [fk_produto_unidade_organizacao]'
          USING ERRCODE='23503', CONSTRAINT='fk_produto_unidade_organizacao';
    END IF;
    IF NEW.categoria_id IS NOT NULL THEN
        SELECT organizacao_id INTO owner_ref FROM categoria WHERE id=NEW.categoria_id;
        IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
            RAISE EXCEPTION 'owner do produto diverge da categoria [fk_produto_categoria_organizacao]'
              USING ERRCODE='23503', CONSTRAINT='fk_produto_categoria_organizacao';
        END IF;
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER tg_produto_catalogo_owner BEFORE INSERT OR UPDATE OF organizacao_id,marca_id,unidade_medida_id,categoria_id
ON produto FOR EACH ROW EXECUTE FUNCTION validar_produto_catalogo_mesmo_owner();

ALTER TABLE produto DROP CONSTRAINT uq_produto_codigo_barras;
CREATE UNIQUE INDEX uq_produto_organizacao_codigo_barras ON produto(organizacao_id,btrim(codigo_barras))
 WHERE organizacao_id IS NOT NULL AND codigo_barras IS NOT NULL;
CREATE UNIQUE INDEX uq_produto_legado_codigo_barras ON produto(btrim(codigo_barras))
 WHERE organizacao_id IS NULL AND codigo_barras IS NOT NULL;
CREATE INDEX ix_produto_organizacao_ativo ON produto(organizacao_id,ativo);

ALTER TABLE servico DROP CONSTRAINT uq_servico_nome;
CREATE UNIQUE INDEX uq_servico_organizacao_nome ON servico(organizacao_id,lower(btrim(nome))) WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_servico_legado_nome ON servico(lower(btrim(nome))) WHERE organizacao_id IS NULL;
CREATE INDEX ix_servico_organizacao_ativo ON servico(organizacao_id,ativo);

CREATE FUNCTION impedir_alteracao_owner_produto() RETURNS trigger LANGUAGE plpgsql
SET search_path FROM CURRENT AS $$ BEGIN
 IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
  RAISE EXCEPTION 'owner de produto e imutavel [ck_produto_owner_imutavel]' USING ERRCODE='23514',CONSTRAINT='ck_produto_owner_imutavel';
 END IF; RETURN NEW; END; $$;
CREATE TRIGGER tg_00_produto_owner_imutavel BEFORE UPDATE OF organizacao_id ON produto FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_owner_produto();
CREATE FUNCTION impedir_alteracao_owner_servico() RETURNS trigger LANGUAGE plpgsql
SET search_path FROM CURRENT AS $$ BEGIN
 IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
  RAISE EXCEPTION 'owner de servico e imutavel [ck_servico_owner_imutavel]' USING ERRCODE='23514',CONSTRAINT='ck_servico_owner_imutavel';
 END IF; RETURN NEW; END; $$;
CREATE TRIGGER tg_00_servico_owner_imutavel BEFORE UPDATE OF organizacao_id ON servico FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_owner_servico();

CREATE FUNCTION validar_consumer_produto_historico() RETURNS trigger LANGUAGE plpgsql
SET search_path FROM CURRENT AS $$
DECLARE owner_produto BIGINT; constraint_name TEXT;
BEGIN
 SELECT organizacao_id INTO owner_produto FROM produto WHERE id=NEW.produto_id;
 IF TG_TABLE_NAME='item_nota_entrada' THEN constraint_name='fk_item_entrada_produto_owner';
 ELSIF TG_TABLE_NAME='item_nota_saida' THEN constraint_name='fk_item_saida_produto_owner';
 ELSE constraint_name='fk_produto_fornecedor_produto_owner'; END IF;
 IF NOT FOUND OR owner_produto IS NOT NULL THEN
  RAISE EXCEPTION 'consumer global exige produto sem owner [%]',constraint_name USING ERRCODE='23503',CONSTRAINT=constraint_name;
 END IF; RETURN NEW; END; $$;
CREATE TRIGGER tg_item_entrada_produto_owner BEFORE INSERT OR UPDATE OF produto_id ON item_nota_entrada FOR EACH ROW EXECUTE FUNCTION validar_consumer_produto_historico();
CREATE TRIGGER tg_item_saida_produto_owner BEFORE INSERT OR UPDATE OF produto_id ON item_nota_saida FOR EACH ROW EXECUTE FUNCTION validar_consumer_produto_historico();
CREATE TRIGGER tg_produto_fornecedor_produto_owner BEFORE INSERT OR UPDATE OF produto_id ON produto_fornecedor FOR EACH ROW EXECUTE FUNCTION validar_consumer_produto_historico();

CREATE FUNCTION validar_nota_servico_historico() RETURNS trigger LANGUAGE plpgsql
SET search_path FROM CURRENT AS $$
DECLARE owner_servico BIGINT;
BEGIN
 IF NEW.servico_id IS NULL THEN RETURN NEW; END IF;
 SELECT organizacao_id INTO owner_servico FROM servico WHERE id=NEW.servico_id;
 IF NOT FOUND OR owner_servico IS NOT NULL THEN
  RAISE EXCEPTION 'nota global exige servico sem owner [fk_nota_servico_servico_owner]' USING ERRCODE='23503',CONSTRAINT='fk_nota_servico_servico_owner';
 END IF; RETURN NEW; END; $$;
CREATE TRIGGER tg_nota_servico_owner BEFORE INSERT OR UPDATE OF servico_id ON nota_servico FOR EACH ROW EXECUTE FUNCTION validar_nota_servico_historico();

CREATE FUNCTION proteger_produto_com_consumers() RETURNS trigger LANGUAGE plpgsql
SET search_path FROM CURRENT AS $$ BEGIN
 IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
  IF EXISTS(SELECT 1 FROM item_nota_entrada WHERE produto_id=OLD.id) THEN RAISE EXCEPTION 'produto referenciado por entrada [fk_item_entrada_produto_owner]' USING ERRCODE='23503',CONSTRAINT='fk_item_entrada_produto_owner'; END IF;
  IF EXISTS(SELECT 1 FROM item_nota_saida WHERE produto_id=OLD.id) THEN RAISE EXCEPTION 'produto referenciado por saida [fk_item_saida_produto_owner]' USING ERRCODE='23503',CONSTRAINT='fk_item_saida_produto_owner'; END IF;
  IF EXISTS(SELECT 1 FROM produto_fornecedor WHERE produto_id=OLD.id) THEN RAISE EXCEPTION 'produto referenciado por fornecedor [fk_produto_fornecedor_produto_owner]' USING ERRCODE='23503',CONSTRAINT='fk_produto_fornecedor_produto_owner'; END IF;
 END IF; RETURN NEW; END; $$;
CREATE TRIGGER tg_000_produto_consumers BEFORE UPDATE OF organizacao_id ON produto FOR EACH ROW EXECUTE FUNCTION proteger_produto_com_consumers();
CREATE FUNCTION proteger_servico_com_nota() RETURNS trigger LANGUAGE plpgsql
SET search_path FROM CURRENT AS $$ BEGIN
 IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id AND EXISTS(SELECT 1 FROM nota_servico WHERE servico_id=OLD.id) THEN
  RAISE EXCEPTION 'servico referenciado por nota [fk_nota_servico_servico_owner]' USING ERRCODE='23503',CONSTRAINT='fk_nota_servico_servico_owner';
 END IF; RETURN NEW; END; $$;
CREATE TRIGGER tg_000_servico_nota BEFORE UPDATE OF organizacao_id ON servico FOR EACH ROW EXECUTE FUNCTION proteger_servico_com_nota();
