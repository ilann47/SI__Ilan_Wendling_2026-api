ALTER TABLE representacao_qr
    ALTER COLUMN token_hash TYPE VARCHAR(64)
    USING trim(token_hash);
