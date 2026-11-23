ALTER TABLE nota_saida
    ADD COLUMN organizacao_id BIGINT,
    ADD COLUMN local_estoque_id BIGINT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE item_nota_saida ADD COLUMN organizacao_id BIGINT;
ALTER TABLE conta_receber
    ADD COLUMN organizacao_id BIGINT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE nota_saida DROP CONSTRAINT uq_nota_saida;
CREATE UNIQUE INDEX uq_nota_saida_tenant
    ON nota_saida(organizacao_id,numero,serie,cliente_id)
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_nota_saida_legado
    ON nota_saida(numero,serie,cliente_id) WHERE organizacao_id IS NULL;

ALTER TABLE nota_saida
    ADD CONSTRAINT fk_nota_saida_organizacao FOREIGN KEY(organizacao_id)
        REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_nota_saida_cliente_tenant FOREIGN KEY(organizacao_id,cliente_id)
        REFERENCES cliente(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_nota_saida_condicao_tenant FOREIGN KEY(organizacao_id,condicao_pagamento_id)
        REFERENCES condicao_pagamento(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_nota_saida_local FOREIGN KEY(local_estoque_id)
        REFERENCES local_estoque(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_nota_saida_local_tenant FOREIGN KEY(local_estoque_id,organizacao_id)
        REFERENCES local_estoque(id,organizacao_id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_nota_saida_organizacao_id UNIQUE(organizacao_id,id),
    ADD CONSTRAINT ck_nota_saida_version CHECK(version>=0),
    ADD CONSTRAINT ck_nota_saida_local_contextual CHECK(
        (organizacao_id IS NULL AND local_estoque_id IS NULL) OR
        (organizacao_id IS NOT NULL AND local_estoque_id IS NOT NULL));

ALTER TABLE item_nota_saida
    ADD CONSTRAINT fk_item_saida_organizacao FOREIGN KEY(organizacao_id)
        REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_item_saida_nota_tenant FOREIGN KEY(organizacao_id,nota_saida_id)
        REFERENCES nota_saida(organizacao_id,id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_item_saida_produto_tenant FOREIGN KEY(organizacao_id,produto_id)
        REFERENCES produto(organizacao_id,id) ON DELETE RESTRICT;

ALTER TABLE conta_receber
    ADD CONSTRAINT fk_conta_receber_organizacao FOREIGN KEY(organizacao_id)
        REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_conta_receber_cliente_tenant FOREIGN KEY(organizacao_id,cliente_id)
        REFERENCES cliente(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_conta_receber_nota_tenant FOREIGN KEY(organizacao_id,nota_saida_id)
        REFERENCES nota_saida(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_conta_receber_condicao_tenant FOREIGN KEY(organizacao_id,condicao_pagamento_id)
        REFERENCES condicao_pagamento(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_conta_receber_forma_tenant FOREIGN KEY(organizacao_id,forma_pagamento_id)
        REFERENCES forma_pagamento(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_conta_receber_organizacao_id UNIQUE(organizacao_id,id),
    ADD CONSTRAINT ck_conta_receber_version CHECK(version>=0),
    ADD CONSTRAINT ck_conta_receber_mensalista_contextual
        CHECK(organizacao_id IS NULL OR mensalista_id IS NULL);

CREATE INDEX ix_nota_saida_organizacao_situacao
    ON nota_saida(organizacao_id,situacao,data_emissao DESC);
CREATE INDEX ix_conta_receber_organizacao_situacao_vencimento
    ON conta_receber(organizacao_id,situacao,data_vencimento);

DROP TRIGGER tg_item_saida_produto_owner ON item_nota_saida;

CREATE FUNCTION validar_nota_saida_owner_v56() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
 SELECT organizacao_id INTO owner_ref FROM cliente WHERE id=NEW.cliente_id;
 IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
  RAISE EXCEPTION 'owner da nota diverge do cliente [fk_nota_saida_cliente_tenant]'
   USING ERRCODE='23503',CONSTRAINT='fk_nota_saida_cliente_tenant';
 END IF;
 IF NEW.condicao_pagamento_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM condicao_pagamento WHERE id=NEW.condicao_pagamento_id;
  IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   RAISE EXCEPTION 'owner da nota diverge da condicao [fk_nota_saida_condicao_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_nota_saida_condicao_tenant';
  END IF;
 END IF;
 IF NEW.local_estoque_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM local_estoque WHERE id=NEW.local_estoque_id;
  IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   RAISE EXCEPTION 'owner da nota diverge do local [fk_nota_saida_local_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_nota_saida_local_tenant';
  END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tg_nota_saida_owner_v56 BEFORE INSERT OR UPDATE OF
 organizacao_id,cliente_id,condicao_pagamento_id,local_estoque_id ON nota_saida
 FOR EACH ROW EXECUTE FUNCTION validar_nota_saida_owner_v56();

CREATE FUNCTION validar_item_saida_owner_v56() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
 SELECT organizacao_id INTO owner_ref FROM nota_saida WHERE id=NEW.nota_saida_id;
 IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
  RAISE EXCEPTION 'owner do item diverge da nota [fk_item_saida_nota_tenant]'
   USING ERRCODE='23503',CONSTRAINT='fk_item_saida_nota_tenant';
 END IF;
 SELECT organizacao_id INTO owner_ref FROM produto WHERE id=NEW.produto_id;
 IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
  IF NEW.organizacao_id IS NULL THEN
   RAISE EXCEPTION 'consumer global exige produto sem owner [fk_item_saida_produto_owner]'
    USING ERRCODE='23503',CONSTRAINT='fk_item_saida_produto_owner';
  END IF;
  RAISE EXCEPTION 'owner do item diverge do produto [fk_item_saida_produto_tenant]'
   USING ERRCODE='23503',CONSTRAINT='fk_item_saida_produto_tenant';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tg_item_saida_owner_v56 BEFORE INSERT OR UPDATE OF
 organizacao_id,nota_saida_id,produto_id ON item_nota_saida
 FOR EACH ROW EXECUTE FUNCTION validar_item_saida_owner_v56();

CREATE FUNCTION validar_conta_receber_owner_v56() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
 SELECT organizacao_id INTO owner_ref FROM cliente WHERE id=NEW.cliente_id;
 IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
  RAISE EXCEPTION 'owner da conta diverge do cliente [fk_conta_receber_cliente_tenant]'
   USING ERRCODE='23503',CONSTRAINT='fk_conta_receber_cliente_tenant';
 END IF;
 IF NEW.nota_saida_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM nota_saida WHERE id=NEW.nota_saida_id;
  IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   RAISE EXCEPTION 'owner da conta diverge da nota [fk_conta_receber_nota_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_conta_receber_nota_tenant';
  END IF;
 END IF;
 IF NEW.condicao_pagamento_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM condicao_pagamento WHERE id=NEW.condicao_pagamento_id;
  IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   RAISE EXCEPTION 'owner da conta diverge da condicao [fk_conta_receber_condicao_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_conta_receber_condicao_tenant';
  END IF;
 END IF;
 IF NEW.forma_pagamento_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM forma_pagamento WHERE id=NEW.forma_pagamento_id;
  IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   RAISE EXCEPTION 'owner da conta diverge da forma [fk_conta_receber_forma_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_conta_receber_forma_tenant';
  END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tg_conta_receber_owner_v56 BEFORE INSERT OR UPDATE OF
 organizacao_id,cliente_id,nota_saida_id,condicao_pagamento_id,forma_pagamento_id
 ON conta_receber FOR EACH ROW EXECUTE FUNCTION validar_conta_receber_owner_v56();

CREATE FUNCTION impedir_owner_nota_saida_v56() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
 RAISE EXCEPTION 'owner da nota e imutavel [ck_nota_saida_owner_imutavel]'
 USING ERRCODE='23514',CONSTRAINT='ck_nota_saida_owner_imutavel'; END IF; RETURN NEW; END; $$;
CREATE TRIGGER tg_00_nota_saida_owner BEFORE UPDATE OF organizacao_id ON nota_saida
 FOR EACH ROW EXECUTE FUNCTION impedir_owner_nota_saida_v56();
CREATE FUNCTION impedir_owner_conta_receber_v56() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
 RAISE EXCEPTION 'owner da conta e imutavel [ck_conta_receber_owner_imutavel]'
 USING ERRCODE='23514',CONSTRAINT='ck_conta_receber_owner_imutavel'; END IF; RETURN NEW; END; $$;
CREATE TRIGGER tg_00_conta_receber_owner BEFORE UPDATE OF organizacao_id ON conta_receber
 FOR EACH ROW EXECUTE FUNCTION impedir_owner_conta_receber_v56();
