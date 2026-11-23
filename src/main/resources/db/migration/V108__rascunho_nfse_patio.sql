-- Capture original do pagamento e identificação fiscal editável são distintas.
-- Nenhuma nota, conta, valor ou registro anterior é reprocessado.
ALTER TABLE patio_evento_estadia
    ADD COLUMN tomador_pagamento_documento VARCHAR(14),
    ADD COLUMN tomador_pagamento_nome VARCHAR(160),
    ADD CONSTRAINT ck_patio_estadia_tomador_pagamento CHECK (
        (tomador_pagamento_documento IS NULL) = (tomador_pagamento_nome IS NULL)
    );

ALTER TABLE patio_evento_fiscal
    ADD COLUMN tomador_documento VARCHAR(14),
    ADD COLUMN tomador_nome VARCHAR(160),
    ADD COLUMN versao BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_patio_fiscal_tomador CHECK (
        (tomador_documento IS NULL) = (tomador_nome IS NULL)
    );

CREATE INDEX ix_patio_fiscal_rascunho ON patio_evento_fiscal
    (organizacao_id, criado_em DESC, movimentacao_id DESC)
    WHERE status = 'PENDENTE_EMISSAO';
