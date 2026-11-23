ALTER TABLE contas_pagar_avulsa
    ADD COLUMN organizacao_id BIGINT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_despesa_avulsa_organizacao FOREIGN KEY(organizacao_id)
        REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_despesa_avulsa_fornecedor_tenant FOREIGN KEY(organizacao_id,fornecedor_id)
        REFERENCES fornecedor(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_despesa_avulsa_forma_tenant FOREIGN KEY(organizacao_id,forma_pagamento_id)
        REFERENCES forma_pagamento(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_despesa_avulsa_organizacao_id UNIQUE(organizacao_id,id),
    ADD CONSTRAINT ck_despesa_avulsa_version CHECK(version>=0);
CREATE INDEX ix_despesa_avulsa_organizacao_situacao
    ON contas_pagar_avulsa(organizacao_id,situacao,data_vencimento);

CREATE FUNCTION impedir_owner_despesa_avulsa_v62() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
 RAISE EXCEPTION 'owner da despesa e imutavel [ck_despesa_avulsa_owner_imutavel]'
 USING ERRCODE='23514',CONSTRAINT='ck_despesa_avulsa_owner_imutavel'; END IF; RETURN NEW; END; $$;
CREATE TRIGGER tg_00_despesa_avulsa_owner BEFORE UPDATE OF organizacao_id ON contas_pagar_avulsa
 FOR EACH ROW EXECUTE FUNCTION impedir_owner_despesa_avulsa_v62();

CREATE FUNCTION validar_despesa_avulsa_owner_v62() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
 IF NEW.fornecedor_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM fornecedor WHERE id=NEW.fornecedor_id;
  IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   RAISE EXCEPTION 'owner diverge do fornecedor [fk_despesa_avulsa_fornecedor_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_despesa_avulsa_fornecedor_tenant'; END IF;
 END IF;
 IF NEW.forma_pagamento_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM forma_pagamento WHERE id=NEW.forma_pagamento_id;
  IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   RAISE EXCEPTION 'owner diverge da forma [fk_despesa_avulsa_forma_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_despesa_avulsa_forma_tenant'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tg_despesa_avulsa_owner_v62 BEFORE INSERT OR UPDATE OF
 organizacao_id,fornecedor_id,forma_pagamento_id ON contas_pagar_avulsa
 FOR EACH ROW EXECUTE FUNCTION validar_despesa_avulsa_owner_v62();
