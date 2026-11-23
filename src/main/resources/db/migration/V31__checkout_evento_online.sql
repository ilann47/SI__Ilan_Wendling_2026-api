ALTER TABLE tentativa_acesso
    DROP CONSTRAINT ck_tentativa_direcao;
ALTER TABLE tentativa_acesso
    ADD CONSTRAINT ck_tentativa_direcao CHECK (direcao IN ('ENTRADA', 'SAIDA'));

ALTER TABLE tentativa_acesso
    DROP CONSTRAINT ck_tentativa_decisao;
ALTER TABLE tentativa_acesso
    ADD CONSTRAINT ck_tentativa_decisao CHECK (
        (status IN ('RECEBIDA', 'AVALIANDO')
            AND motivo IS NULL AND decidido_em IS NULL)
        OR (status = 'RECUSADA'
            AND motivo IS NOT NULL AND decidido_em IS NOT NULL
            AND ocupacao_resultante IS NULL)
        OR (status = 'AUTORIZADA'
            AND motivo = 'AUTORIZADA' AND decidido_em IS NOT NULL
            AND credencial_id IS NOT NULL AND representacao_qr_id IS NOT NULL
            AND ocupacao_resultante IS NOT NULL AND ocupacao_resultante >= 0)
    );

ALTER TABLE tentativa_acesso
    DROP CONSTRAINT ck_tentativa_motivo;
ALTER TABLE tentativa_acesso
    ADD CONSTRAINT ck_tentativa_motivo CHECK (motivo IS NULL OR motivo IN (
        'AUTORIZADA', 'CREDENCIAL_INVALIDA', 'CREDENCIAL_INATIVA',
        'FORA_DA_JANELA', 'EVENTO_FORA_DE_OPERACAO', 'PATIO_INCORRETO',
        'ENTRADA_DUPLICADA', 'PATIO_LOTADO', 'PRESENCA_INEXISTENTE',
        'SAIDA_DUPLICADA'
    ));

ALTER TABLE presenca
    ADD COLUMN tentativa_checkout_id BIGINT UNIQUE;
ALTER TABLE presenca
    ADD CONSTRAINT fk_presenca_tentativa_checkout_escopo
        FOREIGN KEY (
            tentativa_checkout_id, organizacao_id, evento_id, patio_id, credencial_id)
        REFERENCES tentativa_acesso(
            id, organizacao_id, evento_id, patio_id, credencial_id)
        ON DELETE RESTRICT;

ALTER TABLE sessao_estacionamento
    ADD COLUMN tentativa_saida_id BIGINT UNIQUE;
ALTER TABLE sessao_estacionamento
    ADD CONSTRAINT fk_sessao_tentativa_saida_escopo
        FOREIGN KEY (
            tentativa_saida_id, organizacao_id, evento_id, patio_id, credencial_id)
        REFERENCES tentativa_acesso(
            id, organizacao_id, evento_id, patio_id, credencial_id)
        ON DELETE RESTRICT;

INSERT INTO permissao (codigo, nome)
VALUES ('access:checkout', 'Realizar check-out online');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel CROSS JOIN permissao
WHERE papel.codigo IN ('SUPERVISOR', 'OPERADOR')
  AND permissao.codigo = 'access:checkout';
