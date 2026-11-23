-- Corte explícito e restrito ao PostgreSQL local de teste. Flyway aplica a
-- migração em uma transação: sem CASCADE, sem UPDATE e sem reprocessar documentos.
-- A fonte é o inteiro já gravado, ou a conversão exata do valor histórico salvo.
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

DO $$
BEGIN
    IF current_database() <> 'estacionamento_kaneko_fiscal_20261105_test'
       OR inet_server_addr() IS DISTINCT FROM inet '127.0.0.1' THEN
        RAISE EXCEPTION 'Corte V96 permitido somente no PostgreSQL de teste local (127.0.0.1).';
    END IF;
END;
$$;

-- O gatilho permanece ativo; remove-se apenas a lista UPDATE OF durante a
-- troca de representação, para não bloquear a alteração de tipo da coluna.
CREATE OR REPLACE TRIGGER tg_estoque_produto_tenant_sem_saldo_legado
BEFORE INSERT OR UPDATE ON produto
FOR EACH ROW EXECUTE FUNCTION impedir_saldo_legado_em_produto_tenant();

-- O teto percentual passa de 100 para 100000000 na representação inteira.
ALTER TABLE item_nota_entrada DROP CONSTRAINT ck_item_entrada_valores;

ALTER TABLE nota_entrada
    ALTER COLUMN valor_produtos TYPE NUMERIC(19,0) USING coalesce(valor_produtos_micros, kaneko_micros(valor_produtos)),
    ALTER COLUMN valor_frete TYPE NUMERIC(19,0) USING coalesce(valor_frete_micros, kaneko_micros(valor_frete)),
    ALTER COLUMN valor_seguro TYPE NUMERIC(19,0) USING coalesce(valor_seguro_micros, kaneko_micros(valor_seguro)),
    ALTER COLUMN outras_despesas TYPE NUMERIC(19,0) USING coalesce(outras_despesas_micros, kaneko_micros(outras_despesas)),
    ALTER COLUMN valor_desconto TYPE NUMERIC(19,0) USING coalesce(valor_desconto_micros, kaneko_micros(valor_desconto)),
    ALTER COLUMN valor_total TYPE NUMERIC(19,0) USING coalesce(valor_total_micros, kaneko_micros(valor_total));
ALTER TABLE nota_entrada
    DROP COLUMN valor_produtos_micros,
    DROP COLUMN valor_frete_micros,
    DROP COLUMN valor_seguro_micros,
    DROP COLUMN outras_despesas_micros,
    DROP COLUMN valor_desconto_micros,
    DROP COLUMN valor_total_micros;
ALTER TABLE nota_entrada RENAME COLUMN valor_produtos TO valor_produtos_micros;
ALTER TABLE nota_entrada RENAME COLUMN valor_frete TO valor_frete_micros;
ALTER TABLE nota_entrada RENAME COLUMN valor_seguro TO valor_seguro_micros;
ALTER TABLE nota_entrada RENAME COLUMN outras_despesas TO outras_despesas_micros;
ALTER TABLE nota_entrada RENAME COLUMN valor_desconto TO valor_desconto_micros;
ALTER TABLE nota_entrada RENAME COLUMN valor_total TO valor_total_micros;

ALTER TABLE item_nota_entrada
    ALTER COLUMN quantidade TYPE NUMERIC(18,0) USING coalesce(quantidade_micros, kaneko_micros(quantidade)),
    ALTER COLUMN valor_unitario TYPE NUMERIC(19,0) USING coalesce(valor_unitario_micros, kaneko_micros(valor_unitario)),
    ALTER COLUMN valor_desconto TYPE NUMERIC(19,0) USING coalesce(valor_desconto_micros, kaneko_micros(valor_desconto)),
    ALTER COLUMN percentual_desconto TYPE NUMERIC(9,0) USING coalesce(percentual_desconto_micros, kaneko_micros(percentual_desconto)),
    ALTER COLUMN valor_total TYPE NUMERIC(19,0) USING coalesce(valor_total_micros, kaneko_micros(valor_total)),
    ALTER COLUMN rateio_frete TYPE NUMERIC(19,0) USING coalesce(rateio_frete_micros, kaneko_micros(rateio_frete)),
    ALTER COLUMN rateio_seguro TYPE NUMERIC(19,0) USING coalesce(rateio_seguro_micros, kaneko_micros(rateio_seguro)),
    ALTER COLUMN rateio_outras TYPE NUMERIC(19,0) USING coalesce(rateio_outras_micros, kaneko_micros(rateio_outras)),
    ALTER COLUMN custo_total TYPE NUMERIC(19,0) USING coalesce(custo_total_micros, kaneko_micros(custo_total)),
    ALTER COLUMN custo_preco_final TYPE NUMERIC(19,0) USING coalesce(custo_preco_final_micros, kaneko_micros(custo_preco_final)),
    ALTER COLUMN saldo_estoque_anterior TYPE NUMERIC(18,0) USING coalesce(saldo_estoque_anterior_micros, kaneko_micros(saldo_estoque_anterior)),
    ALTER COLUMN custo_medio_anterior TYPE NUMERIC(19,0) USING coalesce(custo_medio_anterior_micros, kaneko_micros(custo_medio_anterior)),
    ALTER COLUMN custo_medio_posterior TYPE NUMERIC(19,0) USING coalesce(custo_medio_posterior_micros, kaneko_micros(custo_medio_posterior));
ALTER TABLE item_nota_entrada
    DROP COLUMN quantidade_micros,
    DROP COLUMN valor_unitario_micros,
    DROP COLUMN valor_desconto_micros,
    DROP COLUMN percentual_desconto_micros,
    DROP COLUMN valor_total_micros,
    DROP COLUMN rateio_frete_micros,
    DROP COLUMN rateio_seguro_micros,
    DROP COLUMN rateio_outras_micros,
    DROP COLUMN custo_total_micros,
    DROP COLUMN custo_preco_final_micros,
    DROP COLUMN saldo_estoque_anterior_micros,
    DROP COLUMN custo_medio_anterior_micros,
    DROP COLUMN custo_medio_posterior_micros;
ALTER TABLE item_nota_entrada RENAME COLUMN quantidade TO quantidade_micros;
ALTER TABLE item_nota_entrada RENAME COLUMN valor_unitario TO valor_unitario_micros;
ALTER TABLE item_nota_entrada RENAME COLUMN valor_desconto TO valor_desconto_micros;
ALTER TABLE item_nota_entrada RENAME COLUMN percentual_desconto TO percentual_desconto_micros;
ALTER TABLE item_nota_entrada RENAME COLUMN valor_total TO valor_total_micros;
ALTER TABLE item_nota_entrada RENAME COLUMN rateio_frete TO rateio_frete_micros;
ALTER TABLE item_nota_entrada RENAME COLUMN rateio_seguro TO rateio_seguro_micros;
ALTER TABLE item_nota_entrada RENAME COLUMN rateio_outras TO rateio_outras_micros;
ALTER TABLE item_nota_entrada RENAME COLUMN custo_total TO custo_total_micros;
ALTER TABLE item_nota_entrada RENAME COLUMN custo_preco_final TO custo_preco_final_micros;
ALTER TABLE item_nota_entrada RENAME COLUMN saldo_estoque_anterior TO saldo_estoque_anterior_micros;
ALTER TABLE item_nota_entrada RENAME COLUMN custo_medio_anterior TO custo_medio_anterior_micros;
ALTER TABLE item_nota_entrada RENAME COLUMN custo_medio_posterior TO custo_medio_posterior_micros;

ALTER TABLE parcela_nota_entrada
    ALTER COLUMN valor TYPE NUMERIC(19,0) USING coalesce(valor_micros, kaneko_micros(valor));
ALTER TABLE parcela_nota_entrada
    DROP COLUMN valor_micros;
ALTER TABLE parcela_nota_entrada RENAME COLUMN valor TO valor_micros;

ALTER TABLE saldo_estoque
    ALTER COLUMN quantidade TYPE NUMERIC(18,0) USING coalesce(quantidade_micros, kaneko_micros(quantidade));
ALTER TABLE saldo_estoque
    DROP COLUMN quantidade_micros;
ALTER TABLE saldo_estoque RENAME COLUMN quantidade TO quantidade_micros;

ALTER TABLE movimento_estoque
    ALTER COLUMN delta TYPE NUMERIC(18,0) USING coalesce(delta_micros, kaneko_micros(delta)),
    ALTER COLUMN saldo_anterior TYPE NUMERIC(18,0) USING coalesce(saldo_anterior_micros, kaneko_micros(saldo_anterior)),
    ALTER COLUMN saldo_posterior TYPE NUMERIC(18,0) USING coalesce(saldo_posterior_micros, kaneko_micros(saldo_posterior)),
    ALTER COLUMN custo_unitario TYPE NUMERIC(19,0) USING coalesce(custo_unitario_micros, kaneko_micros(custo_unitario)),
    ALTER COLUMN saldo_total_anterior TYPE NUMERIC(18,0) USING coalesce(saldo_total_anterior_micros, kaneko_micros(saldo_total_anterior)),
    ALTER COLUMN saldo_total_posterior TYPE NUMERIC(18,0) USING coalesce(saldo_total_posterior_micros, kaneko_micros(saldo_total_posterior)),
    ALTER COLUMN custo_medio_anterior TYPE NUMERIC(19,0) USING coalesce(custo_medio_anterior_micros, kaneko_micros(custo_medio_anterior)),
    ALTER COLUMN custo_medio_posterior TYPE NUMERIC(19,0) USING coalesce(custo_medio_posterior_micros, kaneko_micros(custo_medio_posterior));
ALTER TABLE movimento_estoque
    DROP COLUMN delta_micros,
    DROP COLUMN saldo_anterior_micros,
    DROP COLUMN saldo_posterior_micros,
    DROP COLUMN custo_unitario_micros,
    DROP COLUMN saldo_total_anterior_micros,
    DROP COLUMN saldo_total_posterior_micros,
    DROP COLUMN custo_medio_anterior_micros,
    DROP COLUMN custo_medio_posterior_micros;
ALTER TABLE movimento_estoque RENAME COLUMN delta TO delta_micros;
ALTER TABLE movimento_estoque RENAME COLUMN saldo_anterior TO saldo_anterior_micros;
ALTER TABLE movimento_estoque RENAME COLUMN saldo_posterior TO saldo_posterior_micros;
ALTER TABLE movimento_estoque RENAME COLUMN custo_unitario TO custo_unitario_micros;
ALTER TABLE movimento_estoque RENAME COLUMN saldo_total_anterior TO saldo_total_anterior_micros;
ALTER TABLE movimento_estoque RENAME COLUMN saldo_total_posterior TO saldo_total_posterior_micros;
ALTER TABLE movimento_estoque RENAME COLUMN custo_medio_anterior TO custo_medio_anterior_micros;
ALTER TABLE movimento_estoque RENAME COLUMN custo_medio_posterior TO custo_medio_posterior_micros;

ALTER TABLE produto
    ALTER COLUMN quantidade TYPE NUMERIC(18,0) USING coalesce(quantidade_micros, kaneko_micros(quantidade)),
    ALTER COLUMN quantidade_minima TYPE NUMERIC(18,0) USING coalesce(quantidade_minima_micros, kaneko_micros(quantidade_minima)),
    ALTER COLUMN valor_compra TYPE NUMERIC(19,0) USING coalesce(valor_compra_micros, kaneko_micros(valor_compra)),
    ALTER COLUMN custo TYPE NUMERIC(19,0) USING coalesce(custo_micros, kaneko_micros(custo));
ALTER TABLE produto
    DROP COLUMN quantidade_micros,
    DROP COLUMN quantidade_minima_micros,
    DROP COLUMN valor_compra_micros,
    DROP COLUMN custo_micros;
ALTER TABLE produto RENAME COLUMN quantidade TO quantidade_micros;
ALTER TABLE produto RENAME COLUMN quantidade_minima TO quantidade_minima_micros;
ALTER TABLE produto RENAME COLUMN valor_compra TO valor_compra_micros;
ALTER TABLE produto RENAME COLUMN custo TO custo_micros;

ALTER TABLE conta_pagar
    ALTER COLUMN valor_original TYPE NUMERIC(19,0) USING coalesce(valor_original_micros, kaneko_micros(valor_original)),
    ALTER COLUMN valor_pago TYPE NUMERIC(19,0) USING coalesce(valor_pago_micros, kaneko_micros(valor_pago)),
    ALTER COLUMN valor_desconto TYPE NUMERIC(19,0) USING coalesce(valor_desconto_micros, kaneko_micros(valor_desconto)),
    ALTER COLUMN valor_juros TYPE NUMERIC(19,0) USING coalesce(valor_juros_micros, kaneko_micros(valor_juros)),
    ALTER COLUMN valor_multa TYPE NUMERIC(19,0) USING coalesce(valor_multa_micros, kaneko_micros(valor_multa)),
    ALTER COLUMN valor_total TYPE NUMERIC(19,0) USING coalesce(valor_total_micros, kaneko_micros(valor_total));
ALTER TABLE conta_pagar
    DROP COLUMN valor_original_micros,
    DROP COLUMN valor_pago_micros,
    DROP COLUMN valor_desconto_micros,
    DROP COLUMN valor_juros_micros,
    DROP COLUMN valor_multa_micros,
    DROP COLUMN valor_total_micros;
ALTER TABLE conta_pagar RENAME COLUMN valor_original TO valor_original_micros;
ALTER TABLE conta_pagar RENAME COLUMN valor_pago TO valor_pago_micros;
ALTER TABLE conta_pagar RENAME COLUMN valor_desconto TO valor_desconto_micros;
ALTER TABLE conta_pagar RENAME COLUMN valor_juros TO valor_juros_micros;
ALTER TABLE conta_pagar RENAME COLUMN valor_multa TO valor_multa_micros;
ALTER TABLE conta_pagar RENAME COLUMN valor_total TO valor_total_micros;

ALTER TABLE baixa_financeira
    ALTER COLUMN valor TYPE NUMERIC(19,0) USING coalesce(valor_micros, kaneko_micros(valor));
ALTER TABLE baixa_financeira
    DROP COLUMN valor_micros;
ALTER TABLE baixa_financeira RENAME COLUMN valor TO valor_micros;

ALTER TABLE importacao_nota_entrada
    ALTER COLUMN total TYPE NUMERIC(19,0) USING coalesce(total_micros, kaneko_micros(total));
ALTER TABLE importacao_nota_entrada
    DROP COLUMN total_micros;
ALTER TABLE importacao_nota_entrada RENAME COLUMN total TO total_micros;

ALTER TABLE item_importacao_nota_entrada
    ALTER COLUMN fator_conversao TYPE NUMERIC(18,0) USING coalesce(fator_conversao_micros, kaneko_micros(fator_conversao)),
    ALTER COLUMN quantidade TYPE NUMERIC(18,0) USING coalesce(quantidade_micros, kaneko_micros(quantidade)),
    ALTER COLUMN valor_unitario TYPE NUMERIC(19,0) USING coalesce(valor_unitario_micros, kaneko_micros(valor_unitario)),
    ALTER COLUMN quantidade_conferida TYPE NUMERIC(18,0) USING coalesce(quantidade_conferida_micros, kaneko_micros(quantidade_conferida));
ALTER TABLE item_importacao_nota_entrada
    DROP COLUMN fator_conversao_micros,
    DROP COLUMN quantidade_micros,
    DROP COLUMN valor_unitario_micros,
    DROP COLUMN quantidade_conferida_micros;
ALTER TABLE item_importacao_nota_entrada RENAME COLUMN fator_conversao TO fator_conversao_micros;
ALTER TABLE item_importacao_nota_entrada RENAME COLUMN quantidade TO quantidade_micros;
ALTER TABLE item_importacao_nota_entrada RENAME COLUMN valor_unitario TO valor_unitario_micros;
ALTER TABLE item_importacao_nota_entrada RENAME COLUMN quantidade_conferida TO quantidade_conferida_micros;

ALTER TABLE parcela_importacao_nota
    ALTER COLUMN valor TYPE NUMERIC(19,0) USING coalesce(valor_micros, kaneko_micros(valor));
ALTER TABLE parcela_importacao_nota
    DROP COLUMN valor_micros;
ALTER TABLE parcela_importacao_nota RENAME COLUMN valor TO valor_micros;

ALTER TABLE produto ALTER COLUMN quantidade_minima_micros SET DEFAULT 1000000;

ALTER TABLE item_nota_entrada
    ADD CONSTRAINT ck_item_entrada_valores CHECK (
        valor_unitario_micros >= 0 AND valor_desconto_micros >= 0 AND valor_total_micros >= 0
        AND percentual_desconto_micros BETWEEN 0 AND 100000000
    ),
    ADD CONSTRAINT ck_item_entrada_composicao_micros CHECK (
        rateio_frete_micros >= 0 AND rateio_seguro_micros >= 0 AND rateio_outras_micros >= 0
        AND custo_preco_final_micros >= 0 AND saldo_estoque_anterior_micros >= 0
        AND custo_medio_anterior_micros >= 0 AND custo_medio_posterior_micros >= 0
    );
ALTER TABLE movimento_estoque
    ADD CONSTRAINT ck_movimento_estoque_custo_total_micros CHECK (
        saldo_total_anterior_micros >= 0 AND saldo_total_posterior_micros >= 0
        AND custo_medio_anterior_micros >= 0 AND custo_medio_posterior_micros >= 0
    );

CREATE OR REPLACE FUNCTION validar_compensacao_movimento_estoque()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE
    original movimento_estoque%ROWTYPE;
BEGIN
    IF NEW.tipo <> 'COMPENSACAO' THEN
        RETURN NEW;
    END IF;
    IF NEW.id IS NOT NULL AND NEW.compensado_id = NEW.id THEN
        RAISE EXCEPTION 'movimento nao pode compensar a si proprio [ck_movimento_estoque_compensacao_valida]'
            USING ERRCODE = '23514',
                  CONSTRAINT = 'ck_movimento_estoque_compensacao_valida';
    END IF;
    SELECT * INTO original FROM movimento_estoque WHERE id = NEW.compensado_id;
    IF NOT FOUND THEN
        RETURN NEW;
    END IF;
    IF original.tipo = 'COMPENSACAO'
       OR NEW.delta_micros <> -original.delta_micros
       OR NEW.organizacao_id <> original.organizacao_id
       OR NEW.produto_id <> original.produto_id
       OR NEW.local_estoque_id <> original.local_estoque_id
       OR NEW.saldo_estoque_id <> original.saldo_estoque_id THEN
        RAISE EXCEPTION 'compensacao deve inverter movimento original nao compensatorio e manter as mesmas pontas [ck_movimento_estoque_compensacao_valida]'
            USING ERRCODE = '23514',
                  CONSTRAINT = 'ck_movimento_estoque_compensacao_valida';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION validar_integridade_saldo_razao_estoque()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
DECLARE
    saldo_id BIGINT;
    quantidade_atual saldo_estoque.quantidade_micros%TYPE;
    total_movimentos BIGINT;
    cadeia_valida BOOLEAN;
    ultimo_saldo saldo_estoque.quantidade_micros%TYPE;
BEGIN
    IF TG_TABLE_NAME = 'saldo_estoque' THEN
        saldo_id := NEW.id;
    ELSE
        saldo_id := NEW.saldo_estoque_id;
    END IF;

    SELECT quantidade_micros INTO quantidade_atual
    FROM saldo_estoque WHERE id = saldo_id;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT count(*),
           COALESCE(bool_and(
               (ordem = 1 AND saldo_anterior_micros = 0)
               OR (ordem > 1 AND saldo_anterior_micros = saldo_posterior_anterior)
           ), true),
           max(saldo_posterior_micros) FILTER (WHERE ordem_inversa = 1)
      INTO total_movimentos, cadeia_valida, ultimo_saldo
    FROM (
        SELECT saldo_anterior_micros, saldo_posterior_micros,
               row_number() OVER (ORDER BY id) AS ordem,
               row_number() OVER (ORDER BY id DESC) AS ordem_inversa,
               lag(saldo_posterior_micros) OVER (ORDER BY id) AS saldo_posterior_anterior
        FROM movimento_estoque
        WHERE saldo_estoque_id = saldo_id
    ) cadeia;

    IF NOT cadeia_valida
       OR (total_movimentos = 0 AND quantidade_atual <> 0)
       OR (total_movimentos > 0 AND quantidade_atual <> ultimo_saldo) THEN
        RAISE EXCEPTION 'saldo e razao de estoque estao divergentes [ck_saldo_estoque_razao_consistente]'
            USING ERRCODE = '23514',
                  CONSTRAINT = 'ck_saldo_estoque_razao_consistente';
    END IF;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION impedir_saldo_legado_em_produto_tenant()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF NEW.organizacao_id IS NOT NULL AND NEW.quantidade_micros <> 0 THEN
        RAISE EXCEPTION 'produto tenant usa razao de estoque [ck_estoque_produto_tenant_sem_saldo_legado]'
            USING ERRCODE = '23514',
                  CONSTRAINT = 'ck_estoque_produto_tenant_sem_saldo_legado';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION impedir_inativacao_produto_com_saldo_estoque()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF OLD.ativo = true AND NEW.ativo = false AND NEW.organizacao_id IS NOT NULL
       AND EXISTS (
           SELECT 1 FROM saldo_estoque
           WHERE organizacao_id = NEW.organizacao_id
             AND produto_id = NEW.id
             AND quantidade_micros <> 0
       ) THEN
        RAISE EXCEPTION 'produto com saldo de estoque nao pode ser inativado [ck_estoque_produto_inativo_com_saldo]'
            USING ERRCODE = '23514',
                  CONSTRAINT = 'ck_estoque_produto_inativo_com_saldo';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER tg_estoque_produto_tenant_sem_saldo_legado
BEFORE INSERT OR UPDATE OF organizacao_id, quantidade_micros ON produto
FOR EACH ROW EXECUTE FUNCTION impedir_saldo_legado_em_produto_tenant();
