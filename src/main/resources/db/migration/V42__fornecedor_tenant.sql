ALTER TABLE fornecedor
    ADD COLUMN organizacao_id BIGINT;

ALTER TABLE fornecedor
    ADD CONSTRAINT fk_fornecedor_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_fornecedor_organizacao_id
        UNIQUE (organizacao_id, id),
    ADD CONSTRAINT fk_fornecedor_condicao_organizacao
        FOREIGN KEY (organizacao_id, condicao_pagamento_id)
        REFERENCES condicao_pagamento(organizacao_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_fornecedor_transportadora_organizacao
        FOREIGN KEY (organizacao_id, transportadora_id)
        REFERENCES transportadora(organizacao_id, id) ON DELETE RESTRICT;

ALTER TABLE fornecedor DROP CONSTRAINT uq_fornecedor_documento;

CREATE UNIQUE INDEX uq_fornecedor_organizacao_documento
    ON fornecedor (organizacao_id, documento)
    WHERE organizacao_id IS NOT NULL;
CREATE UNIQUE INDEX uq_fornecedor_legado_documento
    ON fornecedor (documento)
    WHERE organizacao_id IS NULL;
CREATE INDEX ix_fornecedor_organizacao_ativo
    ON fornecedor (organizacao_id, ativo);

CREATE FUNCTION validar_owner_fornecedor_referencias()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    owner_condicao BIGINT;
    owner_transportadora BIGINT;
BEGIN
    IF NEW.condicao_pagamento_id IS NOT NULL THEN
        SELECT organizacao_id INTO owner_condicao
        FROM condicao_pagamento
        WHERE id = NEW.condicao_pagamento_id;
        IF NOT FOUND OR owner_condicao IS DISTINCT FROM NEW.organizacao_id THEN
            RAISE EXCEPTION 'owner do fornecedor diverge da condicao [fk_fornecedor_condicao_organizacao]'
                USING ERRCODE = '23503', CONSTRAINT = 'fk_fornecedor_condicao_organizacao';
        END IF;
    END IF;

    IF NEW.transportadora_id IS NOT NULL THEN
        SELECT organizacao_id INTO owner_transportadora
        FROM transportadora
        WHERE id = NEW.transportadora_id;
        IF NOT FOUND OR owner_transportadora IS DISTINCT FROM NEW.organizacao_id THEN
            RAISE EXCEPTION 'owner do fornecedor diverge da transportadora [fk_fornecedor_transportadora_organizacao]'
                USING ERRCODE = '23503', CONSTRAINT = 'fk_fornecedor_transportadora_organizacao';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER tg_fornecedor_referencias_owner
BEFORE INSERT OR UPDATE OF organizacao_id, condicao_pagamento_id, transportadora_id
ON fornecedor
FOR EACH ROW EXECUTE FUNCTION validar_owner_fornecedor_referencias();

INSERT INTO permissao (codigo, nome) VALUES
    ('suppliers:read', 'Consultar fornecedores'),
    ('suppliers:manage', 'Administrar fornecedores');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel
CROSS JOIN permissao
WHERE papel.codigo = 'ADMIN_ORGANIZACAO'
  AND permissao.codigo IN ('suppliers:read', 'suppliers:manage');
