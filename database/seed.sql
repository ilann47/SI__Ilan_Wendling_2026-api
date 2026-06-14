-- =====================================================================
-- Estacionamento Kaneko - SEED de dados de exemplo
-- Roda DEPOIS do schema.sql. Popula as 11 tabelas com massa coerente
-- para validacao/demonstracao. IDs explicitos; sequencias ajustadas no fim.
-- =====================================================================

-- ---------- GEOGRAFIA ----------
INSERT INTO pais (id, nome, sigla) VALUES
  (1, 'Brasil', 'BRA');

INSERT INTO estado (id, nome, uf, pais_id) VALUES
  (1, 'Parana',    'PR', 1),
  (2, 'Sao Paulo', 'SP', 1);

INSERT INTO cidade (id, nome, ddd, estado_id) VALUES
  (1, 'Missal',        '45', 1),
  (2, 'Foz do Iguacu', '45', 1),
  (3, 'Cascavel',      '45', 1),
  (4, 'Sao Paulo',     '11', 2);

-- ---------- RH ----------
INSERT INTO cargo (id, nome, descricao) VALUES
  (1, 'Gerente',           'Responsavel pela operacao do estacionamento'),
  (2, 'Operador de Patio', 'Registra entradas e saidas dos veiculos'),
  (3, 'Caixa',             'Recebe pagamentos');

INSERT INTO funcionario (id, nome, cpf, telefone, email, cidade_id, cargo_id, data_admissao, salario) VALUES
  (1, 'Carlos Pereira', '111.111.111-11', '45999990001', 'carlos@kaneko.com', 2, 1, DATE '2024-01-15', 5000.00),
  (2, 'Joao Mendes',    '222.222.222-22', '45999990002', 'joao@kaneko.com',   1, 2, DATE '2024-03-10', 2200.00),
  (3, 'Ana Souza',      '333.333.333-33', '45999990003', 'ana@kaneko.com',    2, 3, DATE '2025-02-01', 2000.00);

-- ---------- FORNECEDOR ----------
INSERT INTO fornecedor (id, nome, nome_fantasia, documento, telefone, email, cidade_id) VALUES
  (1, 'Bebidas Cataratas Ltda', 'Cataratas Bebidas', '11.111.111/0001-11', '4533330001', 'vendas@cataratas.com',    2),
  (2, 'Snacks do Oeste ME',     'Oeste Snacks',      '22.222.222/0001-22', '4533330002', 'contato@oestesnacks.com', 3);

-- ---------- TARIFA (a id=1 'Tarifa padrao' ja veio do schema.sql) ----------
INSERT INTO tarifa (id, descricao, valor_hora, valor_diaria, tolerancia_min, valor_mensalidade) VALUES
  (2, 'Tarifa Moto', 7.00, 35.00, 15, 180.00);

-- ---------- CLIENTE ----------
INSERT INTO cliente (id, nome, documento, telefone, email, endereco, numero, bairro, cep, cidade_id) VALUES
  (1, 'Maria Silva',        '444.444.444-44',     '45988880001', 'maria@email.com', 'Rua das Flores', '100',  'Centro',        '85890-000', 2),
  (2, 'Tech Solucoes Ltda', '33.333.333/0001-33', '45988880002', 'adm@techsol.com', 'Av. Brasil',     '2000', 'Centro',        '85851-000', 4),
  (3, 'Jose Santos',        '555.555.555-55',     '45988880003', 'jose@email.com',  'Rua XV',         '50',   'Sao Cristovao', '85870-000', 1);

-- ---------- VEICULO ----------
INSERT INTO veiculo (id, placa, modelo, cor, tipo, cliente_id) VALUES
  (1, 'ABC1D23', 'Onix',    'Prata',    'CARRO', 1),     -- Maria (mensalista)
  (2, 'DEF4G56', 'Corolla', 'Preto',    'CARRO', 2),     -- Tech  (mensalista)
  (3, 'GHI7J89', 'CG 160',  'Vermelha', 'MOTO',  3),     -- Jose
  (4, 'KLM0N12', 'Gol',     'Branco',   'CARRO', NULL),  -- avulso
  (5, 'OPQ3R45', 'HB20',    'Cinza',    'CARRO', NULL);  -- avulso

-- ---------- MENSALISTA ----------
INSERT INTO mensalista (id, cliente_id, veiculo_id, valor_mensal, data_inicio, status) VALUES
  (1, 1, 1, 250.00, DATE '2025-01-01', 'ATIVO'),
  (2, 2, 2, 300.00, DATE '2025-06-01', 'ATIVO');

-- ---------- MOVIMENTACAO ----------
-- Abertas (no patio agora):
INSERT INTO movimentacao (id, veiculo_id, tipo, mensalista_id, tarifa_id, data_entrada, status) VALUES
  (1, 1, 'MENSALISTA', 1,    NULL, now() - INTERVAL '3 hours',     'ABERTO'),
  (2, 4, 'AVULSO',     NULL, 1,    now() - INTERVAL '40 minutes',  'ABERTO');
-- Fechadas (ja sairam, com valor cobrado):
INSERT INTO movimentacao (id, veiculo_id, tipo, mensalista_id, tarifa_id, data_entrada, data_saida, valor_cobrado, status) VALUES
  (3, 5, 'AVULSO',     NULL, 1,    now() - INTERVAL '5 hours',                   now() - INTERVAL '2 hours', 30.00, 'FECHADO'), -- 3h x R$10
  (4, 3, 'AVULSO',     NULL, 1,    now() - INTERVAL '1 day' - INTERVAL '8 hours', now() - INTERVAL '1 day',  50.00, 'FECHADO'), -- bateu teto da diaria
  (5, 2, 'MENSALISTA', 2,    NULL, now() - INTERVAL '1 day',                     now() - INTERVAL '20 hours', 0.00, 'FECHADO'); -- mensalista nao paga
-- Cancelada (entrada digitada errada):
INSERT INTO movimentacao (id, veiculo_id, tipo, mensalista_id, tarifa_id, data_entrada, status) VALUES
  (6, 5, 'AVULSO', NULL, 1, now() - INTERVAL '6 hours', 'CANCELADO');

-- ---------- AJUSTE DAS SEQUENCIAS (IDENTITY) ----------
SELECT setval(pg_get_serial_sequence('pais','id'),         (SELECT max(id) FROM pais));
SELECT setval(pg_get_serial_sequence('estado','id'),       (SELECT max(id) FROM estado));
SELECT setval(pg_get_serial_sequence('cidade','id'),       (SELECT max(id) FROM cidade));
SELECT setval(pg_get_serial_sequence('cargo','id'),        (SELECT max(id) FROM cargo));
SELECT setval(pg_get_serial_sequence('funcionario','id'),  (SELECT max(id) FROM funcionario));
SELECT setval(pg_get_serial_sequence('fornecedor','id'),   (SELECT max(id) FROM fornecedor));
SELECT setval(pg_get_serial_sequence('tarifa','id'),       (SELECT max(id) FROM tarifa));
SELECT setval(pg_get_serial_sequence('cliente','id'),      (SELECT max(id) FROM cliente));
SELECT setval(pg_get_serial_sequence('veiculo','id'),      (SELECT max(id) FROM veiculo));
SELECT setval(pg_get_serial_sequence('mensalista','id'),   (SELECT max(id) FROM mensalista));
SELECT setval(pg_get_serial_sequence('movimentacao','id'), (SELECT max(id) FROM movimentacao));
