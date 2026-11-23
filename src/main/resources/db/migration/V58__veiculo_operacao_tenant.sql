DO $$
BEGIN
 IF EXISTS(SELECT 1 FROM veiculo GROUP BY upper(btrim(placa)) HAVING count(*)>1) THEN
  RAISE EXCEPTION 'placas historicas colidem apos normalizacao [uq_veiculo_legado_placa]';
 END IF;
END; $$;

ALTER TABLE veiculo
    ADD COLUMN organizacao_id BIGINT,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE veiculo DROP CONSTRAINT uq_veiculo_placa;
CREATE UNIQUE INDEX uq_veiculo_tenant_placa
    ON veiculo(organizacao_id,upper(btrim(placa))) WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_veiculo_legado_placa
    ON veiculo(upper(btrim(placa))) WHERE organizacao_id IS NULL;

ALTER TABLE veiculo
    ADD CONSTRAINT fk_veiculo_organizacao FOREIGN KEY(organizacao_id)
        REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_veiculo_cliente_tenant FOREIGN KEY(organizacao_id,cliente_id)
        REFERENCES cliente(organizacao_id,id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_veiculo_organizacao_id UNIQUE(organizacao_id,id),
    ADD CONSTRAINT ck_veiculo_version CHECK(version>=0);
CREATE INDEX ix_veiculo_organizacao_ativo ON veiculo(organizacao_id,ativo,placa);

CREATE FUNCTION validar_veiculo_owner_v58() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT;
BEGIN
 IF NEW.cliente_id IS NOT NULL THEN
  SELECT organizacao_id INTO owner_ref FROM cliente WHERE id=NEW.cliente_id;
  IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
   RAISE EXCEPTION 'owner do veiculo diverge do cliente [fk_veiculo_cliente_tenant]'
    USING ERRCODE='23503',CONSTRAINT='fk_veiculo_cliente_tenant';
  END IF;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tg_veiculo_owner_v58 BEFORE INSERT OR UPDATE OF organizacao_id,cliente_id
 ON veiculo FOR EACH ROW EXECUTE FUNCTION validar_veiculo_owner_v58();

CREATE FUNCTION impedir_owner_veiculo_v58() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
 RAISE EXCEPTION 'owner do veiculo e imutavel [ck_veiculo_owner_imutavel]'
 USING ERRCODE='23514',CONSTRAINT='ck_veiculo_owner_imutavel'; END IF; RETURN NEW; END; $$;
CREATE TRIGGER tg_00_veiculo_owner BEFORE UPDATE OF organizacao_id ON veiculo
 FOR EACH ROW EXECUTE FUNCTION impedir_owner_veiculo_v58();

CREATE FUNCTION validar_veiculo_historico_consumer_v58() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT; constraint_name TEXT;
BEGIN
 SELECT organizacao_id INTO owner_ref FROM veiculo WHERE id=NEW.veiculo_id;
 constraint_name := CASE TG_TABLE_NAME
  WHEN 'mensalista' THEN 'fk_mensalista_veiculo_owner'
  ELSE 'fk_movimentacao_veiculo_owner' END;
 IF NOT FOUND OR owner_ref IS NOT NULL THEN
  RAISE EXCEPTION 'consumer global exige veiculo sem owner [%]',constraint_name
   USING ERRCODE='23503',CONSTRAINT=constraint_name;
 END IF;
 RETURN NEW;
END; $$;
CREATE TRIGGER tg_mensalista_veiculo_owner BEFORE INSERT OR UPDATE OF veiculo_id
 ON mensalista FOR EACH ROW EXECUTE FUNCTION validar_veiculo_historico_consumer_v58();
CREATE TRIGGER tg_movimentacao_veiculo_owner BEFORE INSERT OR UPDATE OF veiculo_id
 ON movimentacao FOR EACH ROW EXECUTE FUNCTION validar_veiculo_historico_consumer_v58();

INSERT INTO permissao(codigo,nome) VALUES
 ('operations:read','Consultar operacao de estacionamento'),
 ('operations:manage','Administrar operacao de estacionamento');
INSERT INTO papel_permissao(papel_id,permissao_id)
SELECT papel.id,permissao.id FROM papel CROSS JOIN permissao
WHERE papel.codigo='ADMIN_ORGANIZACAO'
  AND permissao.codigo IN('operations:read','operations:manage');
