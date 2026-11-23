ALTER TABLE transportadora
    ADD COLUMN organizacao_id BIGINT;

ALTER TABLE transportadora
    ADD CONSTRAINT fk_transportadora_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_transportadora_organizacao_id
        UNIQUE (organizacao_id, id),
    ADD CONSTRAINT fk_transportadora_condicao_organizacao
        FOREIGN KEY (organizacao_id, condicao_pagamento_id)
        REFERENCES condicao_pagamento(organizacao_id, id) ON DELETE RESTRICT;

ALTER TABLE transportadora DROP CONSTRAINT uq_transportadora_documento;

CREATE UNIQUE INDEX uq_transportadora_organizacao_documento
    ON transportadora (organizacao_id, documento)
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_transportadora_legado_documento
    ON transportadora (documento)
    WHERE organizacao_id IS NULL;
CREATE INDEX ix_transportadora_organizacao_ativo
    ON transportadora (organizacao_id, ativo);

CREATE FUNCTION validar_owner_transportadora_condicao_pagamento()
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
            RAISE EXCEPTION 'owner da transportadora diverge da condicao [fk_transportadora_condicao_organizacao]'
                USING ERRCODE = '23503',
                      CONSTRAINT = 'fk_transportadora_condicao_organizacao';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_transportadora_condicao_pagamento_owner
BEFORE INSERT OR UPDATE OF organizacao_id, condicao_pagamento_id
ON transportadora
FOR EACH ROW EXECUTE FUNCTION validar_owner_transportadora_condicao_pagamento();

INSERT INTO permissao (codigo, nome) VALUES
    ('logistics:read', 'Consultar logistica'),
    ('logistics:manage', 'Administrar logistica');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel
CROSS JOIN permissao
WHERE papel.codigo = 'ADMIN_ORGANIZACAO'
  AND permissao.codigo IN ('logistics:read', 'logistics:manage');
