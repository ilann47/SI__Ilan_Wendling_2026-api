ALTER TABLE cliente
    ADD COLUMN organizacao_id BIGINT;

ALTER TABLE cliente
    ADD CONSTRAINT fk_cliente_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_cliente_organizacao_id
        UNIQUE (organizacao_id, id),
    ADD CONSTRAINT fk_cliente_condicao_organizacao
        FOREIGN KEY (organizacao_id, condicao_pagamento_id)
        REFERENCES condicao_pagamento(organizacao_id, id) ON DELETE RESTRICT;

ALTER TABLE cliente DROP CONSTRAINT uq_cliente_documento;

CREATE UNIQUE INDEX uq_cliente_organizacao_documento
    ON cliente (organizacao_id, documento)
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_cliente_legado_documento
    ON cliente (documento)
    WHERE organizacao_id IS NULL;
CREATE INDEX ix_cliente_organizacao_ativo
    ON cliente (organizacao_id, ativo);

CREATE FUNCTION validar_owner_cliente_condicao_pagamento()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    owner_condicao BIGINT;
BEGIN
    IF NEW.condicao_pagamento_id IS NOT NULL THEN
        SELECT organizacao_id INTO owner_condicao
        FROM condicao_pagamento
        WHERE id = NEW.condicao_pagamento_id;

        IF NOT FOUND OR owner_condicao IS DISTINCT FROM NEW.organizacao_id THEN
            RAISE EXCEPTION 'owner do cliente diverge da condicao [fk_cliente_condicao_organizacao]'
                USING ERRCODE = '23503',
                      CONSTRAINT = 'fk_cliente_condicao_organizacao';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_cliente_condicao_pagamento_owner
BEFORE INSERT OR UPDATE OF organizacao_id, condicao_pagamento_id
ON cliente
FOR EACH ROW EXECUTE FUNCTION validar_owner_cliente_condicao_pagamento();

INSERT INTO permissao (codigo, nome) VALUES
    ('customers:read', 'Consultar clientes'),
    ('customers:manage', 'Administrar clientes');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel
CROSS JOIN permissao
WHERE papel.codigo = 'ADMIN_ORGANIZACAO'
  AND permissao.codigo IN ('customers:read', 'customers:manage');
