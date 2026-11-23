-- Permite identidade global neutra criada por autocadastro, sem conceder os
-- privilegios legados ADMIN ou OPERADOR.
ALTER TABLE usuario DROP CONSTRAINT ck_usuario_perfil;
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_perfil
    CHECK (perfil IN ('ADMIN', 'OPERADOR', 'USUARIO'));
