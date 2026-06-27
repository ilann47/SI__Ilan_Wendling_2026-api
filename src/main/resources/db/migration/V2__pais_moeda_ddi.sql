-- V2 — Pais: moeda corrente e DDI (codigo de discagem internacional).
-- Campos opcionais (nullable), para nao impactar dados ja existentes.
-- Primeira migracao incremental sobre o schema inicial (V1), por solicitacao
-- explicita de evolucao do cadastro de Pais.

ALTER TABLE pais ADD COLUMN moeda VARCHAR(30);  -- moeda corrente (nome ou codigo ISO 4217, ex.: Real, BRL)
ALTER TABLE pais ADD COLUMN ddi   VARCHAR(5);   -- codigo de discagem internacional (DDI), ex.: 55 (Brasil), 351 (Portugal)
