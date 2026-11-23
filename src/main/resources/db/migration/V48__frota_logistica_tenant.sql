DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM veiculo_frota
        GROUP BY upper(btrim(placa))
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'placas historicas de veiculo de frota colidem apos normalizacao [uq_veiculo_frota_legado_placa]'
            USING ERRCODE = '23505', CONSTRAINT = 'uq_veiculo_frota_legado_placa';
    END IF;
END;
$$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM transportadora_veiculo_frota vinculo
        JOIN transportadora transportadora ON transportadora.id = vinculo.transportadora_id
        WHERE transportadora.organizacao_id IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'frota historica referencia transportadora tenant [fk_tvf_transportadora_organizacao]'
            USING ERRCODE = '23503', CONSTRAINT = 'fk_tvf_transportadora_organizacao';
    END IF;
END;
$$;

ALTER TABLE veiculo_frota ADD COLUMN organizacao_id BIGINT;
ALTER TABLE transportadora_veiculo_frota ADD COLUMN organizacao_id BIGINT;

ALTER TABLE veiculo_frota
    ADD CONSTRAINT fk_veiculo_frota_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_veiculo_frota_organizacao_id
        UNIQUE (organizacao_id, id);

ALTER TABLE veiculo_frota DROP CONSTRAINT uq_veiculo_frota_placa;
CREATE UNIQUE INDEX uq_veiculo_frota_organizacao_placa
    ON veiculo_frota (organizacao_id, upper(btrim(placa)))
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_veiculo_frota_legado_placa
    ON veiculo_frota (upper(btrim(placa)))
    WHERE organizacao_id IS NULL;
CREATE INDEX ix_veiculo_frota_organizacao_ativo
    ON veiculo_frota (organizacao_id, ativo);

ALTER TABLE transportadora_veiculo_frota
    ADD CONSTRAINT fk_tvf_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_tvf_transportadora_organizacao
        FOREIGN KEY (organizacao_id, transportadora_id)
        REFERENCES transportadora(organizacao_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_tvf_veiculo_organizacao
        FOREIGN KEY (organizacao_id, veiculo_frota_id)
        REFERENCES veiculo_frota(organizacao_id, id) ON DELETE RESTRICT;

ALTER TABLE transportadora_veiculo_frota DROP CONSTRAINT uq_transp_veiculo_frota;
CREATE UNIQUE INDEX uq_tvf_organizacao_par
    ON transportadora_veiculo_frota
        (organizacao_id, transportadora_id, veiculo_frota_id)
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_tvf_legado_par
    ON transportadora_veiculo_frota (transportadora_id, veiculo_frota_id)
    WHERE organizacao_id IS NULL;
CREATE INDEX ix_tvf_organizacao_ativo
    ON transportadora_veiculo_frota (organizacao_id, ativo);
CREATE INDEX ix_tvf_organizacao_transportadora
    ON transportadora_veiculo_frota (organizacao_id, transportadora_id);
CREATE INDEX ix_tvf_organizacao_veiculo
    ON transportadora_veiculo_frota (organizacao_id, veiculo_frota_id);

CREATE FUNCTION validar_owner_transportadora_veiculo_frota()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    owner_transportadora BIGINT;
    owner_veiculo BIGINT;
BEGIN
    SELECT organizacao_id INTO owner_transportadora
    FROM transportadora WHERE id = NEW.transportadora_id;
    IF NOT FOUND OR owner_transportadora IS DISTINCT FROM NEW.organizacao_id THEN
        RAISE EXCEPTION 'owner do vinculo diverge da transportadora [fk_tvf_transportadora_organizacao]'
            USING ERRCODE = '23503', CONSTRAINT = 'fk_tvf_transportadora_organizacao';
    END IF;

    SELECT organizacao_id INTO owner_veiculo
    FROM veiculo_frota WHERE id = NEW.veiculo_frota_id;
    IF NOT FOUND OR owner_veiculo IS DISTINCT FROM NEW.organizacao_id THEN
        RAISE EXCEPTION 'owner do vinculo diverge do veiculo [fk_tvf_veiculo_organizacao]'
            USING ERRCODE = '23503', CONSTRAINT = 'fk_tvf_veiculo_organizacao';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_tvf_referencias_owner
BEFORE INSERT OR UPDATE OF organizacao_id, transportadora_id, veiculo_frota_id
ON transportadora_veiculo_frota
FOR EACH ROW EXECUTE FUNCTION validar_owner_transportadora_veiculo_frota();

CREATE FUNCTION impedir_alteracao_owner_veiculo_frota()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
        RAISE EXCEPTION 'owner de veiculo de frota e imutavel; backfill exige UC732 [ck_veiculo_frota_owner_imutavel]'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_veiculo_frota_owner_imutavel';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_00_veiculo_frota_owner_imutavel
BEFORE UPDATE OF organizacao_id ON veiculo_frota
FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_owner_veiculo_frota();

CREATE FUNCTION impedir_alteracao_owner_tvf()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id THEN
        RAISE EXCEPTION 'owner do vinculo de frota e imutavel; backfill exige UC732 [ck_tvf_owner_imutavel]'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_tvf_owner_imutavel';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_00_tvf_owner_imutavel
BEFORE UPDATE OF organizacao_id ON transportadora_veiculo_frota
FOR EACH ROW EXECUTE FUNCTION impedir_alteracao_owner_tvf();

CREATE FUNCTION impedir_backfill_transportadora_com_frota()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.organizacao_id IS DISTINCT FROM OLD.organizacao_id
       AND EXISTS (
           SELECT 1 FROM transportadora_veiculo_frota
           WHERE transportadora_id = OLD.id
       ) THEN
        RAISE EXCEPTION 'backfill de transportadora exige migracao atomica da frota [ck_transportadora_backfill_frota]'
            USING ERRCODE = '23514', CONSTRAINT = 'ck_transportadora_backfill_frota';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER tg_transportadora_backfill_frota
BEFORE UPDATE OF organizacao_id ON transportadora
FOR EACH ROW EXECUTE FUNCTION impedir_backfill_transportadora_com_frota();
