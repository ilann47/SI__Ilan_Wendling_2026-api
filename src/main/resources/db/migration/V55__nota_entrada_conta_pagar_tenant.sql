ALTER TABLE nota_entrada
    ADD COLUMN organizacao_id BIGINT,
    ADD COLUMN local_estoque_id BIGINT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE item_nota_entrada ADD COLUMN organizacao_id BIGINT;

ALTER TABLE conta_pagar
    ADD COLUMN organizacao_id BIGINT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE nota_entrada DROP CONSTRAINT uq_nota_entrada;
CREATE UNIQUE INDEX uq_nota_entrada_tenant
    ON nota_entrada (organizacao_id, numero, serie, fornecedor_id)
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_nota_entrada_legado
    ON nota_entrada (numero, serie, fornecedor_id)
    WHERE organizacao_id IS NULL;

ALTER TABLE nota_entrada
    ADD CONSTRAINT fk_nota_entrada_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_nota_entrada_fornecedor_tenant
        FOREIGN KEY (organizacao_id, fornecedor_id)
        REFERENCES fornecedor(organizacao_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_nota_entrada_condicao_tenant
        FOREIGN KEY (organizacao_id, condicao_pagamento_id)
        REFERENCES condicao_pagamento(organizacao_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_nota_entrada_local
        FOREIGN KEY (local_estoque_id) REFERENCES local_estoque(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_nota_entrada_local_tenant
        FOREIGN KEY (local_estoque_id, organizacao_id)
        REFERENCES local_estoque(id, organizacao_id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_nota_entrada_organizacao_id UNIQUE (organizacao_id, id),
    ADD CONSTRAINT ck_nota_entrada_version CHECK (version >= 0),
    ADD CONSTRAINT ck_nota_entrada_local_contextual CHECK (
        (organizacao_id IS NULL AND local_estoque_id IS NULL)
        OR (organizacao_id IS NOT NULL AND local_estoque_id IS NOT NULL)
    );

ALTER TABLE item_nota_entrada
    ADD CONSTRAINT fk_item_nota_entrada_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_item_entrada_nota_tenant
        FOREIGN KEY (organizacao_id, nota_entrada_id)
        REFERENCES nota_entrada(organizacao_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_item_entrada_produto_tenant
        FOREIGN KEY (organizacao_id, produto_id)
        REFERENCES produto(organizacao_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_item_entrada_owner_contextual
        CHECK (organizacao_id IS NOT NULL OR nota_entrada_id IS NOT NULL);

ALTER TABLE conta_pagar
    ADD CONSTRAINT fk_conta_pagar_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_conta_pagar_fornecedor_tenant
        FOREIGN KEY (organizacao_id, fornecedor_id)
        REFERENCES fornecedor(organizacao_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_conta_pagar_nota_tenant
        FOREIGN KEY (organizacao_id, nota_entrada_id)
        REFERENCES nota_entrada(organizacao_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_conta_pagar_condicao_tenant
        FOREIGN KEY (organizacao_id, condicao_pagamento_id)
        REFERENCES condicao_pagamento(organizacao_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_conta_pagar_forma_tenant
        FOREIGN KEY (organizacao_id, forma_pagamento_id)
        REFERENCES forma_pagamento(organizacao_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_conta_pagar_organizacao_id UNIQUE (organizacao_id, id),
    ADD CONSTRAINT ck_conta_pagar_version CHECK (version >= 0);

CREATE INDEX ix_nota_entrada_organizacao_situacao
    ON nota_entrada (organizacao_id, situacao, data_emissao DESC);
CREATE INDEX ix_conta_pagar_organizacao_situacao_vencimento
    ON conta_pagar (organizacao_id, situacao, data_vencimento);

DROP TRIGGER tg_nota_entrada_fornecedor_owner ON nota_entrada;
DROP TRIGGER tg_conta_pagar_fornecedor_owner ON conta_pagar;
DROP TRIGGER tg_item_entrada_produto_owner ON item_nota_entrada;

CREATE FUNCTION validar_nota_entrada_owner_v55()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
    SELECT organizacao_id INTO owner_ref FROM fornecedor WHERE id=NEW.fornecedor_id;
    IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
        IF NEW.organizacao_id IS NULL THEN
            RAISE EXCEPTION 'consumer global exige fornecedor sem owner [fk_nota_entrada_fornecedor_owner]'
                USING ERRCODE='23503', CONSTRAINT='fk_nota_entrada_fornecedor_owner';
        END IF;
        RAISE EXCEPTION 'owner da nota diverge do fornecedor [fk_nota_entrada_fornecedor_tenant]'
            USING ERRCODE='23503', CONSTRAINT='fk_nota_entrada_fornecedor_tenant';
    END IF;
    IF NEW.condicao_pagamento_id IS NOT NULL THEN
        SELECT organizacao_id INTO owner_ref FROM condicao_pagamento
        WHERE id=NEW.condicao_pagamento_id;
        IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
            RAISE EXCEPTION 'owner da nota diverge da condicao [fk_nota_entrada_condicao_tenant]'
                USING ERRCODE='23503', CONSTRAINT='fk_nota_entrada_condicao_tenant';
        END IF;
    END IF;
    IF NEW.local_estoque_id IS NOT NULL THEN
        SELECT organizacao_id INTO owner_ref FROM local_estoque WHERE id=NEW.local_estoque_id;
        IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
            RAISE EXCEPTION 'owner da nota diverge do local [fk_nota_entrada_local_tenant]'
                USING ERRCODE='23503', CONSTRAINT='fk_nota_entrada_local_tenant';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_nota_entrada_owner_v55
BEFORE INSERT OR UPDATE OF organizacao_id, fornecedor_id, condicao_pagamento_id, local_estoque_id
ON nota_entrada FOR EACH ROW EXECUTE FUNCTION validar_nota_entrada_owner_v55();

CREATE FUNCTION validar_item_nota_entrada_owner_v55()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
    SELECT organizacao_id INTO owner_ref FROM nota_entrada WHERE id=NEW.nota_entrada_id;
    IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
        RAISE EXCEPTION 'owner do item diverge da nota [fk_item_entrada_nota_tenant]'
            USING ERRCODE='23503', CONSTRAINT='fk_item_entrada_nota_tenant';
    END IF;
    SELECT organizacao_id INTO owner_ref FROM produto WHERE id=NEW.produto_id;
    IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
        IF NEW.organizacao_id IS NULL THEN
            RAISE EXCEPTION 'consumer global exige produto sem owner [fk_item_entrada_produto_owner]'
                USING ERRCODE='23503', CONSTRAINT='fk_item_entrada_produto_owner';
        END IF;
        RAISE EXCEPTION 'owner do item diverge do produto [fk_item_entrada_produto_tenant]'
            USING ERRCODE='23503', CONSTRAINT='fk_item_entrada_produto_tenant';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_item_nota_entrada_owner_v55
BEFORE INSERT OR UPDATE OF organizacao_id, nota_entrada_id, produto_id
ON item_nota_entrada FOR EACH ROW EXECUTE FUNCTION validar_item_nota_entrada_owner_v55();

CREATE FUNCTION validar_conta_pagar_owner_v55()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
    SELECT organizacao_id INTO owner_ref FROM fornecedor WHERE id=NEW.fornecedor_id;
    IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
        IF NEW.organizacao_id IS NULL THEN
            RAISE EXCEPTION 'consumer global exige fornecedor sem owner [fk_conta_pagar_fornecedor_owner]'
                USING ERRCODE='23503', CONSTRAINT='fk_conta_pagar_fornecedor_owner';
        END IF;
        RAISE EXCEPTION 'owner da conta diverge do fornecedor [fk_conta_pagar_fornecedor_tenant]'
            USING ERRCODE='23503', CONSTRAINT='fk_conta_pagar_fornecedor_tenant';
    END IF;
    IF NEW.nota_entrada_id IS NOT NULL THEN
        SELECT organizacao_id INTO owner_ref FROM nota_entrada WHERE id=NEW.nota_entrada_id;
        IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
            RAISE EXCEPTION 'owner da conta diverge da nota [fk_conta_pagar_nota_tenant]'
                USING ERRCODE='23503', CONSTRAINT='fk_conta_pagar_nota_tenant';
        END IF;
    END IF;
    IF NEW.condicao_pagamento_id IS NOT NULL THEN
        SELECT organizacao_id INTO owner_ref FROM condicao_pagamento
        WHERE id=NEW.condicao_pagamento_id;
        IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
            RAISE EXCEPTION 'owner da conta diverge da condicao [fk_conta_pagar_condicao_tenant]'
                USING ERRCODE='23503', CONSTRAINT='fk_conta_pagar_condicao_tenant';
        END IF;
    END IF;
    IF NEW.forma_pagamento_id IS NOT NULL THEN
        SELECT organizacao_id INTO owner_ref FROM forma_pagamento
        WHERE id=NEW.forma_pagamento_id;
        IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
            RAISE EXCEPTION 'owner da conta diverge da forma [fk_conta_pagar_forma_tenant]'
                USING ERRCODE='23503', CONSTRAINT='fk_conta_pagar_forma_tenant';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_conta_pagar_owner_v55
BEFORE INSERT OR UPDATE OF organizacao_id, fornecedor_id, nota_entrada_id,
    condicao_pagamento_id, forma_pagamento_id
ON conta_pagar FOR EACH ROW EXECUTE FUNCTION validar_conta_pagar_owner_v55();

CREATE FUNCTION impedir_alteracao_owner_nota_entrada()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
        RAISE EXCEPTION 'owner da nota de entrada e imutavel [ck_nota_entrada_owner_imutavel]'
            USING ERRCODE='23514', CONSTRAINT='ck_nota_entrada_owner_imutavel';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_00_nota_entrada_owner_imutavel
BEFORE UPDATE OF organizacao_id ON nota_entrada
FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_owner_nota_entrada();

CREATE FUNCTION impedir_alteracao_owner_item_nota_entrada()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id
       OR NEW.nota_entrada_id IS DISTINCT FROM OLD.nota_entrada_id THEN
        RAISE EXCEPTION 'owner do item de entrada e imutavel [ck_item_nota_entrada_owner_imutavel]'
            USING ERRCODE='23514', CONSTRAINT='ck_item_nota_entrada_owner_imutavel';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_00_item_nota_entrada_owner_imutavel
BEFORE UPDATE OF organizacao_id, nota_entrada_id ON item_nota_entrada
FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_owner_item_nota_entrada();

CREATE FUNCTION impedir_alteracao_owner_conta_pagar()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
        RAISE EXCEPTION 'owner da conta a pagar e imutavel [ck_conta_pagar_owner_imutavel]'
            USING ERRCODE='23514', CONSTRAINT='ck_conta_pagar_owner_imutavel';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_00_conta_pagar_owner_imutavel
BEFORE UPDATE OF organizacao_id ON conta_pagar
FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_owner_conta_pagar();

INSERT INTO permissao (codigo, nome) VALUES
    ('fiscal:read', 'Consultar documentos fiscais'),
    ('fiscal:manage', 'Administrar documentos fiscais'),
    ('finance:read', 'Consultar financeiro'),
    ('finance:manage', 'Administrar financeiro');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel CROSS JOIN permissao
WHERE papel.codigo = 'ADMIN_ORGANIZACAO'
  AND permissao.codigo IN ('fiscal:read', 'fiscal:manage',
                           'finance:read', 'finance:manage');
