-- Notas existentes conservam seus valores e efeitos; rascunhos devem ser salvos
-- novamente para gerar o cronograma. Não apaga nem reconcilia colisões.
CREATE UNIQUE INDEX uq_nota_saida_identidade_normalizada
    ON nota_saida (organizacao_id,
        normalizar_identificador_nota(numero), normalizar_identificador_nota(serie),
        normalizar_identificador_nota(modelo))
    WHERE organizacao_id IS NOT NULL;
DROP INDEX uq_nota_saida_tenant;

ALTER TABLE nota_saida ADD COLUMN cronograma_definido BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE parcela_nota_saida (
    nota_saida_id BIGINT NOT NULL REFERENCES nota_saida(id) ON DELETE CASCADE,
    numero_parcela INTEGER NOT NULL CHECK (numero_parcela > 0),
    valor NUMERIC(15,2) NOT NULL CHECK (valor > 0),
    data_vencimento DATE NOT NULL,
    forma_pagamento_id BIGINT REFERENCES forma_pagamento(id) ON DELETE RESTRICT,
    forma_pagamento_nome VARCHAR(100),
    PRIMARY KEY (nota_saida_id, numero_parcela)
);

CREATE FUNCTION validar_parcela_saida_owner_v80() RETURNS trigger
LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE owner_nota BIGINT; owner_forma BIGINT;
BEGIN
    IF NEW.forma_pagamento_id IS NOT NULL THEN
        SELECT organizacao_id INTO owner_nota FROM nota_saida WHERE id=NEW.nota_saida_id;
        SELECT organizacao_id INTO owner_forma FROM forma_pagamento WHERE id=NEW.forma_pagamento_id;
        IF owner_nota IS DISTINCT FROM owner_forma THEN
            RAISE EXCEPTION 'Forma de pagamento de outro tenant na parcela de saída'
                USING ERRCODE='23503', CONSTRAINT='fk_parcela_saida_forma_owner';
        END IF;
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validar_parcela_saida_owner
    BEFORE INSERT OR UPDATE ON parcela_nota_saida
    FOR EACH ROW EXECUTE FUNCTION validar_parcela_saida_owner_v80();
