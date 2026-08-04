ALTER TABLE movimentacao
    ADD COLUMN organizacao_id BIGINT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_movimentacao_organizacao FOREIGN KEY(organizacao_id)
        REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_movimentacao_veiculo_tenant FOREIGN KEY(organizacao_id,veiculo_id)
        REFERENCES veiculo(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_movimentacao_mensalista_tenant FOREIGN KEY(organizacao_id,mensalista_id)
        REFERENCES mensalista(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_movimentacao_tarifa_tenant FOREIGN KEY(organizacao_id,tarifa_id)
        REFERENCES tarifa(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_movimentacao_patio_tenant FOREIGN KEY(patio_id,organizacao_id)
        REFERENCES patio(id,organizacao_id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_movimentacao_version CHECK(version>=0),
    ADD CONSTRAINT ck_movimentacao_patio_contexto
        CHECK(organizacao_id IS NULL OR patio_id IS NOT NULL);
CREATE INDEX ix_movimentacao_organizacao_status
    ON movimentacao(organizacao_id,status,data_entrada DESC);

DROP TRIGGER tg_movimentacao_veiculo_owner ON movimentacao;
DROP TRIGGER tg_movimentacao_tarifa_owner ON movimentacao;
DROP TRIGGER tg_movimentacao_mensalista_owner ON movimentacao;

CREATE FUNCTION impedir_owner_movimentacao_v61() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
 RAISE EXCEPTION 'owner da movimentacao e imutavel [ck_movimentacao_owner_imutavel]'
 USING ERRCODE='23514',CONSTRAINT='ck_movimentacao_owner_imutavel'; END IF; RETURN NEW; END; $$;
CREATE TRIGGER tg_00_movimentacao_owner BEFORE UPDATE OF organizacao_id ON movimentacao
 FOR EACH ROW EXECUTE FUNCTION impedir_owner_movimentacao_v61();

CREATE FUNCTION validar_movimentacao_owner_v61() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
 SELECT organizacao_id INTO owner_ref FROM veiculo WHERE id=NEW.veiculo_id;
 IF owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
  RAISE EXCEPTION 'owner diverge do veiculo [fk_movimentacao_veiculo_tenant]'
   USING ERRCODE='23503',CONSTRAINT='fk_movimentacao_veiculo_tenant'; END IF;
 IF NEW.mensalista_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM mensalista WHERE id=NEW.mensalista_id;
  IF owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   RAISE EXCEPTION 'owner diverge do mensalista [fk_movimentacao_mensalista_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_movimentacao_mensalista_tenant'; END IF;
 END IF;
 IF NEW.tarifa_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM tarifa WHERE id=NEW.tarifa_id;
  IF owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   RAISE EXCEPTION 'owner diverge da tarifa [fk_movimentacao_tarifa_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_movimentacao_tarifa_tenant'; END IF;
 END IF;
 IF NEW.patio_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM patio WHERE id=NEW.patio_id;
  IF owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   RAISE EXCEPTION 'owner diverge do patio [fk_movimentacao_patio_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_movimentacao_patio_tenant'; END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tg_movimentacao_owner_v61 BEFORE INSERT OR UPDATE OF
 organizacao_id,veiculo_id,mensalista_id,tarifa_id,patio_id ON movimentacao
 FOR EACH ROW EXECUTE FUNCTION validar_movimentacao_owner_v61();
