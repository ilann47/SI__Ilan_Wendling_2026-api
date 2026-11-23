-- Aditiva: não reprocessa estoque, custos, títulos ou documentos históricos.
CREATE FUNCTION normalizar_identificador_nota(valor TEXT) RETURNS TEXT
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT CASE WHEN btrim(valor) ~ '^[0-9]+$'
        THEN COALESCE(NULLIF(ltrim(btrim(valor), '0'), ''), '0')
        ELSE upper(btrim(valor)) END
$$;

-- Colisões semânticas preexistentes exigem reconciliação; nunca apagar uma nota.
CREATE UNIQUE INDEX uq_nota_entrada_identidade_normalizada
    ON nota_entrada (organizacao_id, fornecedor_id,
        normalizar_identificador_nota(numero), normalizar_identificador_nota(serie),
        normalizar_identificador_nota(modelo)) NULLS NOT DISTINCT;

ALTER TABLE nota_entrada
    ADD COLUMN transportadora_id BIGINT,
    ADD COLUMN observacao_interna VARCHAR(1000),
    ADD COLUMN cronograma_definido BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT fk_nota_entrada_transportadora_tenant
        FOREIGN KEY (organizacao_id, transportadora_id)
        REFERENCES transportadora(organizacao_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_nota_entrada_transportadora
        FOREIGN KEY (transportadora_id) REFERENCES transportadora(id) ON DELETE RESTRICT;

ALTER TABLE item_nota_entrada
    ADD COLUMN unidade_medida VARCHAR(10),
    ADD COLUMN codigo_produto_fornecedor VARCHAR(60),
    ADD COLUMN custo_total NUMERIC(15,2),
    ADD CONSTRAINT ck_item_nota_entrada_custo_total CHECK (custo_total IS NULL OR custo_total >= 0);
-- Snapshots novos são gravados ao criar/editar. Não inventar a unidade/código antigos.

CREATE TABLE parcela_nota_entrada (
    nota_entrada_id BIGINT NOT NULL REFERENCES nota_entrada(id) ON DELETE CASCADE,
    numero_parcela INTEGER NOT NULL CHECK (numero_parcela>0),
    valor NUMERIC(15,2) NOT NULL CHECK (valor>0),
    data_vencimento DATE NOT NULL,
    forma_pagamento_id BIGINT REFERENCES forma_pagamento(id) ON DELETE RESTRICT,
    forma_pagamento_nome VARCHAR(100),
    PRIMARY KEY (nota_entrada_id, numero_parcela)
);

CREATE FUNCTION validar_parcela_nota_owner_v78() RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_nota BIGINT; owner_forma BIGINT;
BEGIN
    IF NEW.forma_pagamento_id IS NOT NULL THEN
        SELECT organizacao_id INTO owner_nota FROM nota_entrada WHERE id=NEW.nota_entrada_id;
        SELECT organizacao_id INTO owner_forma FROM forma_pagamento WHERE id=NEW.forma_pagamento_id;
        IF owner_nota IS DISTINCT FROM owner_forma THEN
            RAISE EXCEPTION 'Forma de pagamento de outro tenant na parcela da nota'
                USING ERRCODE='23503', CONSTRAINT='fk_parcela_nota_forma_owner';
        END IF;
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER tg_parcela_nota_owner_v78 BEFORE INSERT OR UPDATE ON parcela_nota_entrada
    FOR EACH ROW EXECUTE FUNCTION validar_parcela_nota_owner_v78();

ALTER TABLE importacao_nota_entrada ADD COLUMN cronograma_definido BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE parcela_importacao_nota (
    importacao_id BIGINT NOT NULL REFERENCES importacao_nota_entrada(id) ON DELETE RESTRICT,
    numero_parcela INTEGER NOT NULL CHECK (numero_parcela>0),
    valor NUMERIC(15,2) NOT NULL CHECK (valor>0),
    data_vencimento DATE NOT NULL,
    forma_pagamento_id BIGINT REFERENCES forma_pagamento(id) ON DELETE RESTRICT,
    forma_pagamento_nome VARCHAR(100),
    PRIMARY KEY (importacao_id, numero_parcela)
);
CREATE FUNCTION validar_parcela_importacao_owner_v78() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_importacao BIGINT; owner_forma BIGINT;
BEGIN
    IF NEW.forma_pagamento_id IS NOT NULL THEN
        SELECT organizacao_id INTO owner_importacao FROM importacao_nota_entrada WHERE id=NEW.importacao_id;
        SELECT organizacao_id INTO owner_forma FROM forma_pagamento WHERE id=NEW.forma_pagamento_id;
        IF owner_importacao IS DISTINCT FROM owner_forma THEN
            RAISE EXCEPTION 'Forma de pagamento de outro tenant no cronograma da importacao'
                USING ERRCODE='23503', CONSTRAINT='fk_parcela_importacao_forma_owner';
        END IF;
    END IF;
    RETURN NEW;
END; $$;
CREATE TRIGGER tg_parcela_importacao_owner_v78 BEFORE INSERT OR UPDATE ON parcela_importacao_nota
    FOR EACH ROW EXECUTE FUNCTION validar_parcela_importacao_owner_v78();
