ALTER TABLE tentativa_acesso
    ADD COLUMN operacao VARCHAR(20);

UPDATE tentativa_acesso
SET operacao = CASE
    WHEN direcao = 'SAIDA' THEN 'CHECKOUT'
    ELSE 'CHECKIN'
END;

ALTER TABLE tentativa_acesso
    ALTER COLUMN operacao SET NOT NULL;

ALTER TABLE tentativa_acesso
    ADD CONSTRAINT ck_tentativa_operacao CHECK (
        (operacao = 'CHECKOUT' AND direcao = 'SAIDA')
        OR (operacao IN ('VALIDACAO', 'CHECKIN') AND direcao = 'ENTRADA')
    );

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
            AND (
                (operacao = 'VALIDACAO' AND ocupacao_resultante IS NULL)
                OR (operacao IN ('CHECKIN', 'CHECKOUT')
                    AND ocupacao_resultante IS NOT NULL
                    AND ocupacao_resultante >= 0)
            ))
    );

INSERT INTO permissao (codigo, nome)
VALUES ('access:validate', 'Validar credencial sem consumir o direito');

INSERT INTO papel_permissao (papel_id, permissao_id)
SELECT papel.id, permissao.id
FROM papel CROSS JOIN permissao
WHERE papel.codigo IN ('SUPERVISOR', 'OPERADOR', 'FISCAL_ACESSO')
  AND permissao.codigo = 'access:validate';
