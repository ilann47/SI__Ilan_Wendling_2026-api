-- Alarga a capacidade sem descartar casas antigas, sem atualizar lançamentos
-- e sem retirar NOT NULL, CHECK positivo, FKs ou o histórico append-only.
-- A parte inteira continua com 13 dígitos: (15,2) → (19,6).
SET LOCAL lock_timeout = '5s';
ALTER TABLE baixa_financeira ALTER COLUMN valor TYPE NUMERIC(19,6);
