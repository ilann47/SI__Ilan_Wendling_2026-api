ALTER TABLE evento
    ADD COLUMN politica_reentrada VARCHAR(30);

ALTER TABLE evento
    ADD CONSTRAINT ck_evento_politica_reentrada CHECK (
        politica_reentrada IS NULL
        OR politica_reentrada IN ('ENTRADA_UNICA', 'REENTRADA_PERMITIDA')
    );

ALTER TABLE evento
    ADD CONSTRAINT ck_evento_politica_apos_configuracao CHECK (
        status = 'RASCUNHO' OR politica_reentrada IS NOT NULL
    ) NOT VALID;
