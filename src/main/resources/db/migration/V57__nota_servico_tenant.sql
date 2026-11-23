ALTER TABLE nota_servico
    ADD COLUMN organizacao_id BIGINT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE conta_receber ADD COLUMN nota_servico_id BIGINT;

ALTER TABLE nota_servico DROP CONSTRAINT uq_nota_servico;
CREATE UNIQUE INDEX uq_nota_servico_tenant
    ON nota_servico(organizacao_id,numero,serie) WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_nota_servico_legado
    ON nota_servico(numero,serie) WHERE organizacao_id IS NULL;

ALTER TABLE nota_servico
    ADD CONSTRAINT fk_nota_servico_organizacao FOREIGN KEY(organizacao_id)
        REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_nota_servico_cliente_tenant FOREIGN KEY(organizacao_id,cliente_id)
        REFERENCES cliente(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_nota_servico_servico_tenant FOREIGN KEY(organizacao_id,servico_id)
        REFERENCES servico(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_nota_servico_condicao_tenant FOREIGN KEY(organizacao_id,condicao_pagamento_id)
        REFERENCES condicao_pagamento(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_nota_servico_forma_tenant FOREIGN KEY(organizacao_id,forma_pagamento_id)
        REFERENCES forma_pagamento(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_nota_servico_organizacao_id UNIQUE(organizacao_id,id),
    ADD CONSTRAINT ck_nota_servico_version CHECK(version>=0),
    ADD CONSTRAINT ck_nota_servico_origem_contextual CHECK(
        organizacao_id IS NULL OR (movimentacao_id IS NULL AND mensalista_id IS NULL));

ALTER TABLE conta_receber
    ADD CONSTRAINT fk_conta_receber_nota_servico FOREIGN KEY(nota_servico_id)
        REFERENCES nota_servico(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_conta_receber_nota_servico_tenant
        FOREIGN KEY(organizacao_id,nota_servico_id)
        REFERENCES nota_servico(organizacao_id,id) ON DELETE RESTRICT;
CREATE UNIQUE INDEX uq_conta_receber_nota_servico_parcela
    ON conta_receber(nota_servico_id,numero_parcela) WHERE nota_servico_id IS NOT NULL;
CREATE INDEX ix_conta_receber_nota_servico ON conta_receber(nota_servico_id);
CREATE INDEX ix_nota_servico_organizacao_situacao
    ON nota_servico(organizacao_id,situacao,data_emissao DESC);

DROP TRIGGER tg_nota_servico_owner ON nota_servico;

CREATE FUNCTION validar_nota_servico_owner_v57() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
 SELECT organizacao_id INTO owner_ref FROM cliente WHERE id=NEW.cliente_id;
 IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
  RAISE EXCEPTION 'owner da nota diverge do cliente [fk_nota_servico_cliente_tenant]'
   USING ERRCODE='23503',CONSTRAINT='fk_nota_servico_cliente_tenant';
 END IF;
 IF NEW.servico_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM servico WHERE id=NEW.servico_id;
  IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   IF NEW.organizacao_id IS NULL THEN
    RAISE EXCEPTION 'nota global exige servico sem owner [fk_nota_servico_servico_owner]'
     USING ERRCODE='23503',CONSTRAINT='fk_nota_servico_servico_owner';
   END IF;
   RAISE EXCEPTION 'owner da nota diverge do servico [fk_nota_servico_servico_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_nota_servico_servico_tenant';
  END IF;
 END IF;
 IF NEW.condicao_pagamento_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM condicao_pagamento WHERE id=NEW.condicao_pagamento_id;
  IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   RAISE EXCEPTION 'owner da nota diverge da condicao [fk_nota_servico_condicao_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_nota_servico_condicao_tenant';
  END IF;
 END IF;
 IF NEW.forma_pagamento_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM forma_pagamento WHERE id=NEW.forma_pagamento_id;
  IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   RAISE EXCEPTION 'owner da nota diverge da forma [fk_nota_servico_forma_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_nota_servico_forma_tenant';
  END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tg_nota_servico_owner_v57 BEFORE INSERT OR UPDATE OF
 organizacao_id,cliente_id,servico_id,condicao_pagamento_id,forma_pagamento_id
 ON nota_servico FOR EACH ROW EXECUTE FUNCTION validar_nota_servico_owner_v57();

CREATE FUNCTION validar_conta_receber_nota_servico_v57() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
 IF NEW.nota_servico_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM nota_servico WHERE id=NEW.nota_servico_id;
  IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   RAISE EXCEPTION 'owner da conta diverge da nota de servico [fk_conta_receber_nota_servico_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_conta_receber_nota_servico_tenant';
  END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tg_conta_receber_nota_servico_v57 BEFORE INSERT OR UPDATE OF
 organizacao_id,nota_servico_id ON conta_receber FOR EACH ROW
 EXECUTE FUNCTION validar_conta_receber_nota_servico_v57();

CREATE FUNCTION impedir_owner_nota_servico_v57() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
 RAISE EXCEPTION 'owner da nota e imutavel [ck_nota_servico_owner_imutavel]'
 USING ERRCODE='23514',CONSTRAINT='ck_nota_servico_owner_imutavel'; END IF; RETURN NEW; END; $$;
CREATE TRIGGER tg_00_nota_servico_owner BEFORE UPDATE OF organizacao_id ON nota_servico
 FOR EACH ROW EXECUTE FUNCTION impedir_owner_nota_servico_v57();
