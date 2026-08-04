ALTER TABLE tarifa
    ADD COLUMN organizacao_id BIGINT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_tarifa_organizacao FOREIGN KEY(organizacao_id)
        REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_tarifa_organizacao_id UNIQUE(organizacao_id,id),
    ADD CONSTRAINT ck_tarifa_version CHECK(version>=0);
CREATE INDEX ix_tarifa_organizacao_ativo ON tarifa(organizacao_id,ativo,id DESC);

CREATE FUNCTION impedir_owner_tarifa_v59() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
 RAISE EXCEPTION 'owner da tarifa e imutavel [ck_tarifa_owner_imutavel]'
 USING ERRCODE='23514',CONSTRAINT='ck_tarifa_owner_imutavel'; END IF; RETURN NEW; END; $$;
CREATE TRIGGER tg_00_tarifa_owner BEFORE UPDATE OF organizacao_id ON tarifa
 FOR EACH ROW EXECUTE FUNCTION impedir_owner_tarifa_v59();

CREATE FUNCTION validar_tarifa_historica_movimentacao_v59() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
 IF NEW.tarifa_id IS NULL THEN RETURN NEW; END IF;
 SELECT organizacao_id INTO owner_ref FROM tarifa WHERE id=NEW.tarifa_id;
 IF NOT FOUND OR owner_ref IS NOT NULL THEN
  RAISE EXCEPTION 'movimentacao global exige tarifa sem owner [fk_movimentacao_tarifa_owner]'
   USING ERRCODE='23503',CONSTRAINT='fk_movimentacao_tarifa_owner';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tg_movimentacao_tarifa_owner BEFORE INSERT OR UPDATE OF tarifa_id
 ON movimentacao FOR EACH ROW EXECUTE FUNCTION validar_tarifa_historica_movimentacao_v59();
