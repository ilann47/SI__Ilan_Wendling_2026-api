-- =====================================================================
-- Estacionamento Kaneko - SEED de dados de exemplo (18 tabelas)
-- Roda DEPOIS do schema.sql. IDs explicitos; sequencias ajustadas no fim.
-- =====================================================================

-- ---------- GEOGRAFIA ----------
INSERT INTO pais (id, nome, sigla, codigo, nacionalidade) VALUES (1,'Brasil','BRA','55','Brasileiro');
INSERT INTO estado (id, nome, uf, pais_id) VALUES (1,'Parana','PR',1),(2,'Sao Paulo','SP',1);
INSERT INTO cidade (id, nome, ddd, codigo_ibge, estado_id) VALUES
  (1,'Missal','45','4115358',1),(2,'Foz do Iguacu','45','4108304',1),(3,'Cascavel','45','4104808',1),(4,'Sao Paulo','11','3550308',2);

-- ---------- PAGAMENTO ----------
INSERT INTO forma_pagamento (id, nome, descricao) VALUES
  (1,'Dinheiro','Pagamento em especie'),
  (2,'Pix','Transferencia instantanea'),
  (3,'Cartao de Credito','Cartao de credito'),
  (4,'Boleto','Boleto bancario');
INSERT INTO condicao_pagamento (id, nome, numero_parcelas, percentual_juros, percentual_multa, percentual_desconto) VALUES
  (1,'A vista',1,0.00,0.00,5.00),
  (2,'30 dias',1,0.00,2.00,0.00),
  (3,'30/60',2,1.00,2.00,0.00);
-- parcelas (somam 100% por condicao)
INSERT INTO parcela_condicao_pagamento (condicao_pagamento_id, numero, dias, percentual, forma_pagamento_id) VALUES
  (1,1,0,100.00,1),
  (2,1,30,100.00,4),
  (3,1,30,50.00,4),
  (3,2,60,50.00,4);

-- ---------- RH ----------
INSERT INTO cargo (id, nome, descricao, salario_base, carga_horaria, requer_cnh) VALUES
  (1,'Gerente','Responsavel pela operacao',5000.00,220,FALSE),
  (2,'Operador de Patio','Registra entradas e saidas',2000.00,220,TRUE),
  (3,'Caixa','Recebe pagamentos',1800.00,220,FALSE);
INSERT INTO funcionario (id, nome, cpf, telefone, email, cidade_id, cargo_id, data_nascimento, sexo, data_admissao, salario) VALUES
  (1,'Carlos Pereira','111.111.111-11','45999990001','carlos@kaneko.com',2,1,DATE '1985-04-12','MASCULINO',DATE '2024-01-15',5000.00),
  (2,'Joao Mendes','222.222.222-22','45999990002','joao@kaneko.com',1,2,DATE '1990-08-20','MASCULINO',DATE '2024-03-10',2200.00),
  (3,'Ana Souza','333.333.333-33','45999990003','ana@kaneko.com',2,3,DATE '1995-11-03','FEMININO',DATE '2025-02-01',2000.00);

-- ---------- FORNECEDOR (com condicao + limite) ----------
INSERT INTO fornecedor (id, nome, nome_fantasia, documento, cidade_id, condicao_pagamento_id, limite_credito) VALUES
  (1,'Bebidas Cataratas Ltda','Cataratas Bebidas','11.111.111/0001-11',2,2,5000.00),
  (2,'Snacks do Oeste ME','Oeste Snacks','22.222.222/0001-22',3,3,3000.00);

-- ---------- CLIENTE (com condicao + limite) ----------
INSERT INTO cliente (id, nome, tipo, documento, telefone, email, endereco, numero, bairro, cep, cidade_id, condicao_pagamento_id, data_nascimento, sexo, limite_credito) VALUES
  (1,'Maria Silva','FISICA','444.444.444-44','45988880001','maria@email.com','Rua das Flores','100','Centro','85890-000',2,1,DATE '1988-06-15','FEMININO',1000.00),
  (2,'Tech Solucoes Ltda','JURIDICA','33.333.333/0001-33','45988880002','adm@techsol.com','Av. Brasil','2000','Centro','85851-000',4,3,NULL,NULL,5000.00),
  (3,'Jose Santos','FISICA','555.555.555-55','45988880003','jose@email.com','Rua XV','50','Sao Cristovao','85870-000',1,1,DATE '1979-02-28','MASCULINO',500.00);

-- ---------- CADASTROS DE APOIO (produto/servico) ----------
INSERT INTO categoria (id, nome, descricao) VALUES (1,'Bebidas','Bebidas em geral'),(2,'Snacks','Salgadinhos e doces'),(3,'Limpeza','Produtos de limpeza');
INSERT INTO marca (id, nome) VALUES (1,'Coca-Cola'),(2,'Pepsico'),(3,'Generica');
INSERT INTO unidade_medida (id, nome, sigla) VALUES (1,'Unidade','UN'),(2,'Litro','L'),(3,'Quilograma','KG'),(4,'Pacote','PCT');
INSERT INTO servico (id, nome, descricao, valor_padrao) VALUES
  (1,'Estacionamento Avulso','Cobranca por hora/diaria',10.00),
  (2,'Mensalidade','Plano mensal de estacionamento',250.00),
  (3,'Lavagem Simples','Lavagem externa do veiculo',30.00);

-- ---------- PRODUTO (conveniencia) ----------
INSERT INTO produto (id, nome, codigo_barras, marca_id, unidade_medida_id, categoria_id, valor_compra, custo, valor_venda, percentual_lucro, quantidade, quantidade_minima) VALUES
  (1,'Coca-Cola Lata 350ml','7894900011517',1,1,1,2.50,2.50,5.00,100.00,120,24),
  (2,'Agua Mineral 500ml','7891000051234',3,1,1,1.00,1.00,2.50,150.00,200,48),
  (3,'Salgadinho Pacote 100g','7892840812345',2,4,2,3.00,3.00,6.00,100.00,60,12),
  (4,'Detergente 500ml','7891234567890',3,2,3,2.00,2.00,4.50,125.00,30,6);

-- ---------- TARIFA (id=1 'Tarifa padrao' ja veio do schema) ----------
INSERT INTO tarifa (id, descricao, valor_hora, valor_diaria, tolerancia_min, valor_mensalidade) VALUES (2,'Tarifa Moto',7.00,35.00,15,180.00);

-- ---------- VEICULO ----------
INSERT INTO veiculo (id, placa, modelo, cor, tipo, cliente_id) VALUES
  (1,'ABC1D23','Onix','Prata','CARRO',1),
  (2,'DEF4G56','Corolla','Preto','CARRO',2),
  (3,'GHI7J89','CG 160','Vermelha','MOTO',3),
  (4,'KLM0N12','Gol','Branco','CARRO',NULL),
  (5,'OPQ3R45','HB20','Cinza','CARRO',NULL);

-- ---------- MENSALISTA ----------
INSERT INTO mensalista (id, cliente_id, veiculo_id, valor_mensal, data_inicio, status) VALUES
  (1,1,1,250.00,DATE '2025-01-01','ATIVO'),
  (2,2,2,300.00,DATE '2025-06-01','ATIVO');

-- ---------- MOVIMENTACAO ----------
INSERT INTO movimentacao (id, veiculo_id, tipo, mensalista_id, tarifa_id, data_entrada, status) VALUES
  (1,1,'MENSALISTA',1,NULL,now() - INTERVAL '3 hours','ABERTO'),
  (2,4,'AVULSO',NULL,1,now() - INTERVAL '40 minutes','ABERTO');
INSERT INTO movimentacao (id, veiculo_id, tipo, mensalista_id, tarifa_id, data_entrada, data_saida, valor_cobrado, status) VALUES
  (3,5,'AVULSO',NULL,1,now() - INTERVAL '5 hours',now() - INTERVAL '2 hours',30.00,'FECHADO'),
  (4,3,'AVULSO',NULL,1,now() - INTERVAL '1 day' - INTERVAL '8 hours',now() - INTERVAL '1 day',50.00,'FECHADO'),
  (5,2,'MENSALISTA',2,NULL,now() - INTERVAL '1 day',now() - INTERVAL '20 hours',0.00,'FECHADO');
INSERT INTO movimentacao (id, veiculo_id, tipo, mensalista_id, tarifa_id, data_entrada, status) VALUES
  (6,5,'AVULSO',NULL,1,now() - INTERVAL '6 hours','CANCELADO');

-- ---------- NOTA DE ENTRADA (compra do fornecedor, confirmada) ----------
INSERT INTO nota_entrada (id, numero, serie, fornecedor_id, condicao_pagamento_id, data_emissao, data_chegada, valor_produtos, valor_total, situacao) VALUES
  (1,'1001','1',1,2,DATE '2026-06-01',DATE '2026-06-03',500.00,500.00,'CONFIRMADA');
INSERT INTO item_nota_entrada (nota_entrada_id, produto_id, sequencia, quantidade, valor_unitario, valor_total) VALUES
  (1,1,1,120,2.50,300.00),
  (1,2,2,200,1.00,200.00);

-- ---------- NOTA DE SAIDA (venda na conveniencia, confirmada) ----------
INSERT INTO nota_saida (id, numero, serie, cliente_id, condicao_pagamento_id, data_emissao, data_saida, valor_produtos, valor_total, situacao) VALUES
  (1,'5001','1',1,1,DATE '2026-06-10',DATE '2026-06-10',16.00,16.00,'CONFIRMADA');
INSERT INTO item_nota_saida (nota_saida_id, produto_id, sequencia, quantidade, valor_unitario, valor_total) VALUES
  (1,1,1,2,5.00,10.00),
  (1,3,2,1,6.00,6.00);

-- ---------- FINANCEIRO (contas geradas pelas notas / mensalidade) ----------
-- A pagar: da nota de entrada (compra a prazo 30 dias)
INSERT INTO conta_pagar (id, fornecedor_id, nota_entrada_id, condicao_pagamento_id, numero_parcela, total_parcelas, valor_original, valor_total, data_emissao, data_vencimento, situacao) VALUES
  (1,1,1,2,1,1,500.00,500.00,DATE '2026-06-01',DATE '2026-07-01','PENDENTE');
-- A receber: da venda na conveniencia (a vista, ja recebida)
INSERT INTO conta_receber (id, cliente_id, nota_saida_id, condicao_pagamento_id, forma_pagamento_id, numero_parcela, total_parcelas, valor_original, valor_recebido, valor_total, data_emissao, data_vencimento, data_recebimento, situacao) VALUES
  (1,1,1,1,1,1,1,16.00,16.00,16.00,DATE '2026-06-10',DATE '2026-06-10',DATE '2026-06-10','RECEBIDA');
-- A receber: da mensalidade (mensalista, pendente)
INSERT INTO conta_receber (id, cliente_id, mensalista_id, numero_parcela, total_parcelas, valor_original, valor_total, data_emissao, data_vencimento, situacao) VALUES
  (2,1,1,1,1,250.00,250.00,DATE '2026-06-01',DATE '2026-06-10','PENDENTE');

-- ---------- NOTA DE SERVICO (NFS-e: servico de estacionamento) ----------
-- NFS-e da diaria avulsa do Jose (origem = movimentacao)
INSERT INTO nota_servico (id, numero, serie, cliente_id, servico_id, movimentacao_id, forma_pagamento_id, data_emissao, valor_servico, aliquota_iss, valor_iss, valor_total, situacao) VALUES
  (1,'9001','1',3,1,4,1,DATE '2026-06-13',50.00,5.00,2.50,50.00,'EMITIDA');
-- NFS-e da mensalidade da Maria (origem = mensalista)
INSERT INTO nota_servico (id, numero, serie, cliente_id, servico_id, mensalista_id, condicao_pagamento_id, data_emissao, valor_servico, aliquota_iss, valor_iss, valor_total, situacao) VALUES
  (2,'9002','1',1,2,1,1,DATE '2026-06-01',250.00,5.00,12.50,250.00,'EMITIDA');

-- ---------- COMPLEMENTOS (grupo B) ----------
-- Usuarios (ligados a funcionarios); senha = hash de exemplo
INSERT INTO usuario (id, nome, login, senha, email, funcionario_id, perfil) VALUES
  (1,'Carlos Pereira','admin','$2a$10$exemploHashAdmin0000000000000000000000','admin@kaneko.com',1,'ADMIN'),
  (2,'Joao Mendes','joao','$2a$10$exemploHashJoao00000000000000000000000','joao@kaneko.com',2,'OPERADOR');
-- Despesas avulsas (sem nota fiscal)
INSERT INTO contas_pagar_avulsa (id, descricao, forma_pagamento_id, valor_original, valor_total, data_emissao, data_vencimento, situacao) VALUES
  (1,'Aluguel do imovel - junho',4,3000.00,3000.00,DATE '2026-06-01',DATE '2026-06-10','PENDENTE'),
  (2,'Energia eletrica - maio',1,850.00,850.00,DATE '2026-06-01',DATE '2026-06-15','PENDENTE');
-- Vinculo produto x fornecedor (de qual fornecedor compramos cada produto)
INSERT INTO produto_fornecedor (produto_id, fornecedor_id, codigo_prod, custo) VALUES
  (1,1,'CC350',2.50),(2,1,'AG500',1.00),(3,2,'SLG100',3.00);
-- Contatos do fornecedor 1 (Cataratas)
INSERT INTO fornecedor_email (fornecedor_id, email, tipo, principal) VALUES
  (1,'vendas@cataratas.com','COMERCIAL',TRUE),(1,'financeiro@cataratas.com','FINANCEIRO',FALSE),
  (2,'contato@oestesnacks.com','COMERCIAL',TRUE);
INSERT INTO fornecedor_telefone (fornecedor_id, telefone, tipo, principal) VALUES
  (1,'4533330001','COMERCIAL',TRUE),(1,'45999998888','CELULAR',FALSE),
  (2,'4533330002','COMERCIAL',TRUE);

-- ---------- AJUSTE DAS SEQUENCIAS (IDENTITY) ----------
SELECT setval(pg_get_serial_sequence('pais','id'),               (SELECT max(id) FROM pais));
SELECT setval(pg_get_serial_sequence('estado','id'),             (SELECT max(id) FROM estado));
SELECT setval(pg_get_serial_sequence('cidade','id'),             (SELECT max(id) FROM cidade));
SELECT setval(pg_get_serial_sequence('forma_pagamento','id'),    (SELECT max(id) FROM forma_pagamento));
SELECT setval(pg_get_serial_sequence('condicao_pagamento','id'), (SELECT max(id) FROM condicao_pagamento));
SELECT setval(pg_get_serial_sequence('parcela_condicao_pagamento','id'), (SELECT max(id) FROM parcela_condicao_pagamento));
SELECT setval(pg_get_serial_sequence('cargo','id'),              (SELECT max(id) FROM cargo));
SELECT setval(pg_get_serial_sequence('funcionario','id'),        (SELECT max(id) FROM funcionario));
SELECT setval(pg_get_serial_sequence('fornecedor','id'),         (SELECT max(id) FROM fornecedor));
SELECT setval(pg_get_serial_sequence('cliente','id'),            (SELECT max(id) FROM cliente));
SELECT setval(pg_get_serial_sequence('categoria','id'),          (SELECT max(id) FROM categoria));
SELECT setval(pg_get_serial_sequence('marca','id'),              (SELECT max(id) FROM marca));
SELECT setval(pg_get_serial_sequence('unidade_medida','id'),     (SELECT max(id) FROM unidade_medida));
SELECT setval(pg_get_serial_sequence('servico','id'),            (SELECT max(id) FROM servico));
SELECT setval(pg_get_serial_sequence('produto','id'),            (SELECT max(id) FROM produto));
SELECT setval(pg_get_serial_sequence('tarifa','id'),             (SELECT max(id) FROM tarifa));
SELECT setval(pg_get_serial_sequence('veiculo','id'),            (SELECT max(id) FROM veiculo));
SELECT setval(pg_get_serial_sequence('mensalista','id'),         (SELECT max(id) FROM mensalista));
SELECT setval(pg_get_serial_sequence('movimentacao','id'),       (SELECT max(id) FROM movimentacao));
SELECT setval(pg_get_serial_sequence('nota_entrada','id'),       (SELECT max(id) FROM nota_entrada));
SELECT setval(pg_get_serial_sequence('nota_saida','id'),         (SELECT max(id) FROM nota_saida));
SELECT setval(pg_get_serial_sequence('conta_pagar','id'),        (SELECT max(id) FROM conta_pagar));
SELECT setval(pg_get_serial_sequence('conta_receber','id'),      (SELECT max(id) FROM conta_receber));
SELECT setval(pg_get_serial_sequence('nota_servico','id'),       (SELECT max(id) FROM nota_servico));
SELECT setval(pg_get_serial_sequence('usuario','id'),            (SELECT max(id) FROM usuario));
SELECT setval(pg_get_serial_sequence('contas_pagar_avulsa','id'),(SELECT max(id) FROM contas_pagar_avulsa));
