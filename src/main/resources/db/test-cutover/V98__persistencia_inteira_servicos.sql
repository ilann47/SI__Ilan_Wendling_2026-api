-- Preserva valores, identidade, situação e revisão; não recalcula notas ou ordens.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';
DO $$
BEGIN
    IF current_database() <> 'estacionamento_kaneko_fiscal_20261105_test'
        OR inet_server_addr() IS DISTINCT FROM inet '127.0.0.1' THEN
        RAISE EXCEPTION 'V98 restrita ao banco fiscal local de teste';
    END IF;
END $$;

-- Reinstala as regras cujos operandos mudam de unidade.
DROP TRIGGER tg_nota_ordem_servico_v65 ON nota_servico;
ALTER TABLE item_ordem_servico DROP CONSTRAINT ck_item_ordem_servico_valores;
ALTER TABLE nota_servico DROP CONSTRAINT ck_nota_servico_aliquota,
    DROP COLUMN valor_servico_micros, DROP COLUMN aliquota_iss_micros,
    DROP COLUMN valor_iss_micros, DROP COLUMN valor_desconto_micros, DROP COLUMN valor_total_micros;

ALTER TABLE ordem_servico
    ALTER COLUMN subtotal TYPE NUMERIC(19,0) USING kaneko_micros(subtotal),
    ALTER COLUMN valor_desconto TYPE NUMERIC(19,0) USING kaneko_micros(valor_desconto),
    ALTER COLUMN valor_total TYPE NUMERIC(19,0) USING kaneko_micros(valor_total);
ALTER TABLE ordem_servico RENAME COLUMN subtotal TO subtotal_micros;
ALTER TABLE ordem_servico RENAME COLUMN valor_desconto TO valor_desconto_micros;
ALTER TABLE ordem_servico RENAME COLUMN valor_total TO valor_total_micros;

ALTER TABLE item_ordem_servico
    ALTER COLUMN quantidade TYPE NUMERIC(18,0) USING kaneko_micros(quantidade),
    ALTER COLUMN valor_unitario TYPE NUMERIC(19,0) USING kaneko_micros(valor_unitario),
    ALTER COLUMN valor_desconto TYPE NUMERIC(19,0) USING kaneko_micros(valor_desconto),
    ALTER COLUMN valor_total TYPE NUMERIC(19,0) USING kaneko_micros(valor_total);
ALTER TABLE item_ordem_servico RENAME COLUMN quantidade TO quantidade_micros;
ALTER TABLE item_ordem_servico RENAME COLUMN valor_unitario TO valor_unitario_micros;
ALTER TABLE item_ordem_servico RENAME COLUMN valor_desconto TO valor_desconto_micros;
ALTER TABLE item_ordem_servico RENAME COLUMN valor_total TO valor_total_micros;

ALTER TABLE nota_servico
    ALTER COLUMN valor_servico TYPE NUMERIC(19,0) USING kaneko_micros(valor_servico),
    ALTER COLUMN aliquota_iss TYPE NUMERIC(9,0) USING kaneko_micros(aliquota_iss),
    ALTER COLUMN valor_iss TYPE NUMERIC(19,0) USING kaneko_micros(valor_iss),
    ALTER COLUMN valor_desconto TYPE NUMERIC(19,0) USING kaneko_micros(valor_desconto),
    ALTER COLUMN valor_total TYPE NUMERIC(19,0) USING kaneko_micros(valor_total);
ALTER TABLE nota_servico RENAME COLUMN valor_servico TO valor_servico_micros;
ALTER TABLE nota_servico RENAME COLUMN aliquota_iss TO aliquota_iss_micros;
ALTER TABLE nota_servico RENAME COLUMN valor_iss TO valor_iss_micros;
ALTER TABLE nota_servico RENAME COLUMN valor_desconto TO valor_desconto_micros;
ALTER TABLE nota_servico RENAME COLUMN valor_total TO valor_total_micros;

ALTER TABLE servico
    ALTER COLUMN valor_padrao TYPE NUMERIC(14,0) USING kaneko_micros(valor_padrao);
ALTER TABLE servico RENAME COLUMN valor_padrao TO valor_padrao_micros;

ALTER TABLE item_ordem_servico ADD CONSTRAINT ck_item_ordem_servico_valores CHECK (
    valor_unitario_micros >= 0 AND valor_desconto_micros >= 0
    AND valor_desconto_micros <= round(quantidade_micros * valor_unitario_micros * 0.000001)
    AND valor_total_micros = round(quantidade_micros * valor_unitario_micros * 0.000001) - valor_desconto_micros
);
ALTER TABLE nota_servico ADD CONSTRAINT ck_nota_servico_aliquota
    CHECK (aliquota_iss_micros BETWEEN 0 AND 100000000);

CREATE OR REPLACE FUNCTION validar_nota_ordem_servico_v65() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_ref BIGINT; cliente_ref BIGINT; status_ref VARCHAR(30); total_ref NUMERIC(19,0);
BEGIN
    IF NEW.ordem_servico_id IS NULL THEN RETURN NEW; END IF;
    SELECT organizacao_id, cliente_id, status, valor_total_micros
      INTO owner_ref, cliente_ref, status_ref, total_ref
      FROM ordem_servico WHERE id=NEW.ordem_servico_id;
    IF NOT FOUND OR owner_ref IS DISTINCT FROM NEW.organizacao_id THEN
        RAISE EXCEPTION 'owner da nota diverge da ordem [fk_nota_servico_ordem_tenant]'
            USING ERRCODE='23503', CONSTRAINT='fk_nota_servico_ordem_tenant';
    END IF;
    IF cliente_ref IS DISTINCT FROM NEW.cliente_id THEN
        RAISE EXCEPTION 'cliente da nota diverge da ordem [ck_nota_ordem_servico_cliente]'
            USING ERRCODE='23514', CONSTRAINT='ck_nota_ordem_servico_cliente';
    END IF;
    IF status_ref <> 'CONCLUIDA' THEN
        RAISE EXCEPTION 'ordem precisa estar concluida [ck_nota_ordem_servico_concluida]'
            USING ERRCODE='23514', CONSTRAINT='ck_nota_ordem_servico_concluida';
    END IF;
    IF total_ref IS DISTINCT FROM NEW.valor_total_micros THEN
        RAISE EXCEPTION 'valor da nota diverge da ordem [ck_nota_ordem_servico_valor]'
            USING ERRCODE='23514', CONSTRAINT='ck_nota_ordem_servico_valor';
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER tg_nota_ordem_servico_v65
BEFORE INSERT OR UPDATE OF organizacao_id, ordem_servico_id, cliente_id, valor_total_micros
ON nota_servico FOR EACH ROW EXECUTE FUNCTION validar_nota_ordem_servico_v65();

-- Converte apenas os números já congelados e mantém ordem e metadados de cada registro.
CREATE FUNCTION escalar_snapshot_servico_v98(snapshot jsonb, VARIADIC chaves text[]) RETURNS jsonb
LANGUAGE plpgsql IMMUTABLE STRICT SET search_path FROM CURRENT AS $$
DECLARE registro jsonb; chave text; resultado jsonb := '[]';
BEGIN
    FOR registro IN SELECT value FROM jsonb_array_elements(snapshot) LOOP
        FOREACH chave IN ARRAY chaves LOOP
            IF jsonb_typeof(registro -> chave) = 'number' THEN
                registro := jsonb_set(registro, ARRAY[chave], to_jsonb(kaneko_micros((registro ->> chave)::numeric)));
            ELSIF registro ? chave AND registro -> chave <> 'null'::jsonb THEN
                RAISE EXCEPTION 'Snapshot de Nota de Serviço inválido no campo %', chave;
            END IF;
        END LOOP;
        resultado := resultado || jsonb_build_array(registro);
    END LOOP;
    RETURN resultado;
END $$;
UPDATE nota_servico
SET itens_conferidos = escalar_snapshot_servico_v98(itens_conferidos, 'quantidade', 'valorUnitario', 'valorDesconto', 'valorTotal'),
    parcelas_conferidas = escalar_snapshot_servico_v98(parcelas_conferidas, 'valor')
WHERE itens_conferidos <> '[]'::jsonb OR parcelas_conferidas <> '[]'::jsonb;
DROP FUNCTION escalar_snapshot_servico_v98(jsonb, text[]);
