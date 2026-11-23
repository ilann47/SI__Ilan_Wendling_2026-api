-- Mantém as restrições de positividade e de teto no título. Apenas permite
-- acumular as baixas com a mesma escala da V90, sem mudar valores anteriores.
SET LOCAL lock_timeout = '5s';
ALTER TABLE conta_pagar ALTER COLUMN valor_pago TYPE NUMERIC(19,6);
