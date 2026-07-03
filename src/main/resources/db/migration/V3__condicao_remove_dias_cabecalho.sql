-- V3 — Condicao de Pagamento: remove os campos "dias" do cabecalho, que nao
-- eram usados em lugar nenhum. O vencimento de cada parcela vem de
-- parcela_condicao_pagamento.dias (dias a partir da emissao).
-- O CHECK ck_condicao_parcelas referenciava essas colunas; recriamos a regra
-- mantendo apenas a validacao de numero_parcelas.

ALTER TABLE condicao_pagamento DROP CONSTRAINT ck_condicao_parcelas;
ALTER TABLE condicao_pagamento DROP COLUMN dias_primeira_parcela;
ALTER TABLE condicao_pagamento DROP COLUMN dias_entre_parcelas;
ALTER TABLE condicao_pagamento ADD CONSTRAINT ck_condicao_parcelas CHECK (numero_parcelas >= 1);
