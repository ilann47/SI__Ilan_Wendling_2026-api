-- Remove a nacionalidade (FK pais) de cliente, funcionario e fornecedor.
-- O pais ja e alcancavel por cidade -> estado -> pais, tornando o campo
-- redundante. O DROP COLUMN remove junto a FK e o indice associados.
ALTER TABLE cliente     DROP COLUMN nacionalidade_id;
ALTER TABLE funcionario DROP COLUMN nacionalidade_id;
ALTER TABLE fornecedor  DROP COLUMN nacionalidade_id;
