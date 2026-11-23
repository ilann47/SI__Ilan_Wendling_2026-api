-- O Pedido de Venda foi substituído pela Nota de Saída avulsa.
-- A transação da migration bloqueia escritas entre a inspeção e a remoção.
LOCK TABLE venda_administrativa, item_venda_administrativa,
    nota_saida, conta_receber, movimento_estoque IN ACCESS EXCLUSIVE MODE;
LOCK TABLE papel, permissao, papel_permissao, atribuicao_acesso IN SHARE ROW EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM nota_saida WHERE venda_administrativa_id IS NOT NULL)
        OR EXISTS (SELECT 1 FROM conta_receber WHERE venda_administrativa_id IS NOT NULL)
        OR EXISTS (SELECT 1 FROM movimento_estoque WHERE origem_tipo = 'VENDA_ADMINISTRATIVA') THEN
        RAISE EXCEPTION 'V82: Pedido de Venda ainda possui efeitos em nota, financeiro ou estoque; trate os vínculos antes da remoção.'
            USING ERRCODE = '23514';
    END IF;

    -- O papel criado pela V64 só pode ser retirado se não tiver sido reutilizado.
    IF EXISTS (
        SELECT 1 FROM papel p
        JOIN papel_permissao pp ON pp.papel_id = p.id
        JOIN permissao pe ON pe.id = pp.permissao_id
        WHERE p.codigo = 'COMERCIAL' AND pe.codigo NOT IN ('sales:read', 'sales:manage')
    ) OR EXISTS (
        SELECT 1 FROM atribuicao_acesso a
        JOIN papel p ON p.id = a.papel_id WHERE p.codigo = 'COMERCIAL'
    ) THEN
        RAISE EXCEPTION 'V82: papel COMERCIAL tem permissões adicionais ou atribuições; trate os acessos antes da remoção.'
            USING ERRCODE = '23514';
    END IF;
END $$;

DROP TRIGGER tg_nota_venda_administrativa_v64 ON nota_saida;
DROP FUNCTION validar_nota_venda_administrativa_v64() RESTRICT;

-- O CHECK da V81 continua exigindo reversão somente em nota contextual cancelada.
ALTER TABLE nota_saida DROP CONSTRAINT ck_nota_saida_reversao;
ALTER TABLE nota_saida DROP CONSTRAINT fk_nota_saida_venda_tenant;
DROP INDEX uq_nota_saida_venda_administrativa;
ALTER TABLE nota_saida DROP COLUMN venda_administrativa_id RESTRICT;
ALTER TABLE nota_saida ADD CONSTRAINT ck_nota_saida_reversao CHECK (
    (revertida_em IS NULL AND motivo_reversao IS NULL)
    OR (organizacao_id IS NOT NULL AND situacao = 'CANCELADA'
        AND revertida_em IS NOT NULL AND motivo_reversao IS NOT NULL
        AND length(btrim(motivo_reversao)) > 0)
);

ALTER TABLE conta_receber DROP CONSTRAINT fk_conta_receber_venda_tenant;
DROP INDEX ix_conta_receber_venda;
ALTER TABLE conta_receber DROP COLUMN venda_administrativa_id RESTRICT;

DROP TABLE item_venda_administrativa RESTRICT;
DROP TABLE venda_administrativa RESTRICT;
DROP FUNCTION impedir_owner_venda_administrativa_v64() RESTRICT;

DELETE FROM papel_permissao pp USING permissao p
    WHERE pp.permissao_id = p.id AND p.codigo IN ('sales:read', 'sales:manage');
DELETE FROM permissao WHERE codigo IN ('sales:read', 'sales:manage');
DELETE FROM papel WHERE codigo = 'COMERCIAL';

-- Remover apenas metadados operacionais exclusivos; auditoria append-only intacta.
DELETE FROM requisicao_idempotente
    WHERE recurso_tipo = 'VENDA_ADMINISTRATIVA' OR operacao = 'GERAR_NOTA_VENDA';
DELETE FROM evento_outbox WHERE aggregate_type = 'VENDA_ADMINISTRATIVA';
