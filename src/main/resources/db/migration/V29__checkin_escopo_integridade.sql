ALTER TABLE representacao_qr
    ADD CONSTRAINT uq_qr_id_credencial_tenant
        UNIQUE (id, credencial_id, organizacao_id);

ALTER TABLE tentativa_acesso
    ADD CONSTRAINT uq_tentativa_id_escopo_credencial
        UNIQUE (id, organizacao_id, evento_id, patio_id, credencial_id);

ALTER TABLE tentativa_acesso
    DROP CONSTRAINT fk_tentativa_qr;
ALTER TABLE tentativa_acesso
    ADD CONSTRAINT fk_tentativa_qr_credencial_tenant
        FOREIGN KEY (representacao_qr_id, credencial_id, organizacao_id)
        REFERENCES representacao_qr(id, credencial_id, organizacao_id)
        ON DELETE RESTRICT;

ALTER TABLE presenca
    ADD CONSTRAINT uq_presenca_id_escopo_credencial
        UNIQUE (id, organizacao_id, evento_id, patio_id, credencial_id);

ALTER TABLE presenca
    DROP CONSTRAINT presenca_tentativa_checkin_id_fkey;
ALTER TABLE presenca
    ADD CONSTRAINT fk_presenca_tentativa_escopo
        FOREIGN KEY (
            tentativa_checkin_id, organizacao_id, evento_id, patio_id, credencial_id)
        REFERENCES tentativa_acesso(
            id, organizacao_id, evento_id, patio_id, credencial_id)
        ON DELETE RESTRICT;

ALTER TABLE sessao_estacionamento
    DROP CONSTRAINT fk_sessao_presenca_tenant;
ALTER TABLE sessao_estacionamento
    ADD CONSTRAINT fk_sessao_presenca_escopo
        FOREIGN KEY (
            presenca_id, organizacao_id, evento_id, patio_id, credencial_id)
        REFERENCES presenca(
            id, organizacao_id, evento_id, patio_id, credencial_id)
        ON DELETE RESTRICT;

ALTER TABLE sessao_estacionamento
    DROP CONSTRAINT fk_sessao_tentativa;
ALTER TABLE sessao_estacionamento
    ADD CONSTRAINT fk_sessao_tentativa_escopo
        FOREIGN KEY (
            tentativa_entrada_id, organizacao_id, evento_id, patio_id, credencial_id)
        REFERENCES tentativa_acesso(
            id, organizacao_id, evento_id, patio_id, credencial_id)
        ON DELETE RESTRICT;
