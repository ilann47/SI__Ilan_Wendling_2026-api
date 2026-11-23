ALTER TABLE mensalista
    ADD COLUMN organizacao_id BIGINT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_mensalista_organizacao FOREIGN KEY(organizacao_id)
        REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_mensalista_cliente_tenant FOREIGN KEY(organizacao_id,cliente_id)
        REFERENCES cliente(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_mensalista_veiculo_tenant FOREIGN KEY(organizacao_id,veiculo_id)
        REFERENCES veiculo(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_mensalista_organizacao_id UNIQUE(organizacao_id,id),
    ADD CONSTRAINT ck_mensalista_version CHECK(version>=0);
CREATE INDEX ix_mensalista_organizacao_status
    ON mensalista(organizacao_id,status,data_inicio);

DROP TRIGGER tg_mensalista_veiculo_owner ON mensalista;

ALTER TABLE conta_receber DROP CONSTRAINT ck_conta_receber_mensalista_contextual;
ALTER TABLE conta_receber ADD CONSTRAINT fk_conta_receber_mensalista_tenant
    FOREIGN KEY(organizacao_id,mensalista_id)
    REFERENCES mensalista(organizacao_id,id) ON DELETE RESTRICT;

CREATE FUNCTION validar_mensalista_owner_v60() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_cliente BIGINT; owner_veiculo BIGINT;
BEGIN
 SELECT organizacao_id INTO owner_cliente FROM cliente WHERE id=NEW.cliente_id;
 SELECT organizacao_id INTO owner_veiculo FROM veiculo WHERE id=NEW.veiculo_id;
 IF owner_cliente IS DISTINCT FROM NEW.organizacao_id THEN
  RAISE EXCEPTION 'owner do mensalista diverge do cliente [fk_mensalista_cliente_tenant]'
   USING ERRCODE='23503',CONSTRAINT='fk_mensalista_cliente_tenant';
 END IF;
 IF owner_veiculo IS DISTINCT FROM NEW.organizacao_id THEN
  RAISE EXCEPTION 'owner do mensalista diverge do veiculo [fk_mensalista_veiculo_tenant]'
   USING ERRCODE='23503',CONSTRAINT='fk_mensalista_veiculo_tenant';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tg_mensalista_owner_v60 BEFORE INSERT OR UPDATE OF
 organizacao_id,cliente_id,veiculo_id ON mensalista
 FOR EACH ROW EXECUTE FUNCTION validar_mensalista_owner_v60();

CREATE FUNCTION impedir_owner_mensalista_v60() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
 RAISE EXCEPTION 'owner do mensalista e imutavel [ck_mensalista_owner_imutavel]'
 USING ERRCODE='23514',CONSTRAINT='ck_mensalista_owner_imutavel'; END IF; RETURN NEW; END; $$;
CREATE TRIGGER tg_00_mensalista_owner BEFORE UPDATE OF organizacao_id ON mensalista
 FOR EACH ROW EXECUTE FUNCTION impedir_owner_mensalista_v60();

CREATE FUNCTION validar_conta_mensalista_owner_v60() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
 IF NEW.mensalista_id IS NULL THEN RETURN NEW; END IF;
 SELECT organizacao_id INTO owner_ref FROM mensalista WHERE id=NEW.mensalista_id;
 IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
  RAISE EXCEPTION 'owner da conta diverge do mensalista [fk_conta_receber_mensalista_tenant]'
   USING ERRCODE='23503',CONSTRAINT='fk_conta_receber_mensalista_tenant';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tg_conta_mensalista_owner_v60 BEFORE INSERT OR UPDATE OF
 organizacao_id,mensalista_id ON conta_receber
 FOR EACH ROW EXECUTE FUNCTION validar_conta_mensalista_owner_v60();

CREATE FUNCTION validar_mensalista_historico_mov_v60() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
 IF NEW.mensalista_id IS NULL THEN RETURN NEW; END IF;
 SELECT organizacao_id INTO owner_ref FROM mensalista WHERE id=NEW.mensalista_id;
 IF NOT FOUND OR owner_ref IS NOT NULL THEN
  RAISE EXCEPTION 'movimentacao global exige mensalista sem owner [fk_movimentacao_mensalista_owner]'
   USING ERRCODE='23503',CONSTRAINT='fk_movimentacao_mensalista_owner';
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tg_movimentacao_mensalista_owner BEFORE INSERT OR UPDATE OF mensalista_id
 ON movimentacao FOR EACH ROW EXECUTE FUNCTION validar_mensalista_historico_mov_v60();
