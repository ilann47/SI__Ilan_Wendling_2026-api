ALTER TABLE forma_pagamento
    ADD COLUMN organizacao_id BIGINT;
ALTER TABLE condicao_pagamento
    ADD COLUMN organizacao_id BIGINT;
ALTER TABLE parcela_condicao_pagamento
    ADD COLUMN organizacao_id BIGINT;

ALTER TABLE forma_pagamento
    ADD CONSTRAINT fk_forma_pagamento_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_forma_pagamento_organizacao_id
        UNIQUE (organizacao_id, id);

ALTER TABLE condicao_pagamento
    ADD CONSTRAINT fk_condicao_pagamento_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_condicao_pagamento_organizacao_id
        UNIQUE (organizacao_id, id);

ALTER TABLE parcela_condicao_pagamento
    ADD CONSTRAINT fk_parcela_pagamento_organizacao
        FOREIGN KEY (organizacao_id) REFERENCES organizacao(id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_parcela_condicao_organizacao
        FOREIGN KEY (organizacao_id, condicao_pagamento_id)
        REFERENCES condicao_pagamento(organizacao_id, id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_parcela_forma_organizacao
        FOREIGN KEY (organizacao_id, forma_pagamento_id)
        REFERENCES forma_pagamento(organizacao_id, id) ON DELETE RESTRICT;

ALTER TABLE forma_pagamento DROP CONSTRAINT uq_forma_pagamento_nome;
ALTER TABLE condicao_pagamento DROP CONSTRAINT uq_condicao_pagamento_nome;

CREATE UNIQUE INDEX uq_forma_pagamento_organizacao_nome
    ON forma_pagamento (organizacao_id, lower(btrim(nome)))
    WHERE organizacao_id IS NOT NULL;
CREATE INDEX ix_forma_pagamento_organizacao_ativo
    ON forma_pagamento (organizacao_id, ativo);

CREATE UNIQUE INDEX uq_condicao_pagamento_organizacao_nome
    ON condicao_pagamento (organizacao_id, lower(btrim(nome)))
    WHERE organizacao_id IS NOT NULL;
CREATE INDEX ix_condicao_pagamento_organizacao_ativo
    ON condicao_pagamento (organizacao_id, ativo);
CREATE INDEX ix_parcela_pagamento_organizacao
    ON parcela_condicao_pagamento (organizacao_id);

INSERT INTO permissao (codigo, nome) VALUES
    ('payments:read', 'Consultar formas e condicoes de pagamento'),
    ('payments:manage', 'Administrar formas e condicoes de pagamento');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel
CROSS JOIN permissao
WHERE papel.codigo = 'ADMIN_ORGANIZACAO'
  AND permissao.codigo IN ('payments:read', 'payments:manage');
