-- Seed local abrangente. Executado somente com APP_DEMO_DATA_ENABLED=true.
-- Todos os marcadores usam valores "Demo" e as insercoes sao idempotentes.

insert into usuario (id, nome, login, senha, email, perfil, ativo)
select nextval('usuario_id_seq'), 'Operador Demo',
       current_setting('app.demo_operator_login'), admin.senha,
       'operador.demo@kaneko.local', 'OPERADOR', true
from usuario admin
where lower(admin.login) = lower(current_setting('app.demo_admin_login'))
  and not exists (
      select 1 from usuario u
      where lower(u.login) = lower(current_setting('app.demo_operator_login'))
  );

drop table if exists demo_seed_context;
create temporary table demo_seed_context on commit drop as
select o.id organizacao_id,
       admin.id admin_id,
       operador.id operador_id,
       c.id cidade_id,
       p.id patio_id,
       e.id evento_id,
       a.id alocacao_id,
       pe.id produto_estacionamento_id,
       lp.id lote_preco_id
from organizacao o
join usuario admin
  on lower(admin.login) = lower(current_setting('app.demo_admin_login'))
join usuario operador
  on lower(operador.login) = lower(current_setting('app.demo_operator_login'))
join cidade c on lower(c.nome) = 'cascavel'
join patio p on p.organizacao_id = o.id and p.nome = 'Patio Principal Demo'
join evento e on e.organizacao_id = o.id and e.nome = 'Festival Kaneko Demo'
join alocacao_patio_evento a
  on a.organizacao_id = o.id and a.evento_id = e.id and a.patio_id = p.id
join produto_estacionamento pe
  on pe.organizacao_id = o.id and pe.evento_id = e.id
  and pe.nome = 'Estacionamento Comum Demo'
join lote_preco lp
  on lp.organizacao_id = o.id and lp.produto_id = pe.id
  and lp.nome = 'Lote Demonstracao'
where o.documento = '11222333000181';

insert into membro_organizacao
    (id, organizacao_id, usuario_id, status, origem, entrou_em, version)
select nextval('membro_organizacao_id_seq'), c.organizacao_id, c.operador_id,
       'ATIVO', 'ADMINISTRATIVA', now(), 0
from demo_seed_context c
where not exists (
    select 1 from membro_organizacao m
    where m.organizacao_id = c.organizacao_id
      and m.usuario_id = c.operador_id
      and m.status <> 'ENCERRADO'
);

insert into atribuicao_acesso
    (id, membro_organizacao_id, papel_id, organizacao_id, escopo_tipo,
     escopo_id, concedido_em, version, vigente_de)
select nextval('atribuicao_acesso_id_seq'), m.id, p.id, c.organizacao_id,
       'ORGANIZACAO', c.organizacao_id, now(), 0, now()
from demo_seed_context c
join membro_organizacao m
  on m.organizacao_id = c.organizacao_id
 and m.usuario_id = c.operador_id and m.status = 'ATIVO'
join papel p on p.codigo = 'OPERADOR'
where not exists (
    select 1 from atribuicao_acesso a
    where a.membro_organizacao_id = m.id and a.papel_id = p.id
      and a.revogado_em is null
);

insert into forma_pagamento
    (id, nome, descricao, ativo, organizacao_id)
select nextval('forma_pagamento_id_seq'), v.nome, v.descricao, true, c.organizacao_id
from demo_seed_context c
cross join (values
    ('Dinheiro Demo', 'Recebimento em especie'),
    ('PIX Demo', 'Transferencia instantanea'),
    ('Cartao Demo', 'Credito ou debito')
) v(nome, descricao)
where not exists (
    select 1 from forma_pagamento f
    where f.organizacao_id = c.organizacao_id and f.nome = v.nome
);

insert into condicao_pagamento
    (id, nome, numero_parcelas, percentual_juros, percentual_multa,
     percentual_desconto, ativo, organizacao_id)
select nextval('condicao_pagamento_id_seq'), v.nome, v.parcelas, 0, 2, v.desconto,
       true, c.organizacao_id
from demo_seed_context c
cross join (values
    ('A vista Demo', 1, 5::numeric),
    ('30/60 dias Demo', 2, 0::numeric)
) v(nome, parcelas, desconto)
where not exists (
    select 1 from condicao_pagamento cp
    where cp.organizacao_id = c.organizacao_id and cp.nome = v.nome
);

insert into parcela_condicao_pagamento
    (id, condicao_pagamento_id, numero, dias, percentual,
     forma_pagamento_id, organizacao_id)
select nextval('parcela_condicao_pagamento_id_seq'), cp.id, v.numero, v.dias,
       v.percentual, fp.id, c.organizacao_id
from demo_seed_context c
join condicao_pagamento cp
  on cp.organizacao_id = c.organizacao_id and cp.nome = '30/60 dias Demo'
join forma_pagamento fp
  on fp.organizacao_id = c.organizacao_id and fp.nome = 'PIX Demo'
cross join (values (1, 30, 50::numeric), (2, 60, 50::numeric))
    v(numero, dias, percentual)
where not exists (
    select 1 from parcela_condicao_pagamento p
    where p.condicao_pagamento_id = cp.id and p.numero = v.numero
);

insert into cargo
    (id, nome, descricao, salario_base, carga_horaria, requer_cnh, ativo, organizacao_id)
select nextval('cargo_id_seq'), v.nome, v.descricao, v.salario, 44, false, true,
       c.organizacao_id
from demo_seed_context c
cross join (values
    ('Administrador Demo', 'Gestao administrativa do tenant', 8500::numeric),
    ('Operador de Patio Demo', 'Operacao de entrada e saida', 2800::numeric)
) v(nome, descricao, salario)
where not exists (
    select 1 from cargo ca
    where ca.organizacao_id = c.organizacao_id and ca.nome = v.nome
);

insert into funcionario
    (id, nome, apelido, tipo, cpf, data_nascimento, sexo, estado_civil,
     telefone, email, endereco, numero, bairro, cep, cidade_id, cargo_id,
     data_admissao, salario, observacao, ativo, organizacao_id)
select nextval('funcionario_id_seq'), 'Operador Kaneko Demo', 'Operador Demo',
       'FISICA', '98765432100', date '1992-05-12', 'MASCULINO', 'SOLTEIRO',
       '(45) 99999-2000', 'operador.demo@kaneko.local', 'Rua Demo', '200',
       'Centro', '85810-000', c.cidade_id, ca.id, current_date - 365,
       2800, 'Funcionario criado pelo seed local', true, c.organizacao_id
from demo_seed_context c
join cargo ca
  on ca.organizacao_id = c.organizacao_id and ca.nome = 'Operador de Patio Demo'
where not exists (
    select 1 from funcionario f
    where f.organizacao_id = c.organizacao_id and f.cpf = '98765432100'
);

insert into cliente
    (id, nome, apelido, tipo, documento, data_nascimento, sexo, estado_civil,
     telefone, email, endereco, numero, bairro, cep, cidade_id,
     condicao_pagamento_id, limite_credito, observacao, ativo, organizacao_id)
select nextval('cliente_id_seq'), v.nome, v.apelido, v.tipo, v.documento,
       v.nascimento, v.sexo, v.civil, v.telefone, v.email, 'Avenida Demo',
       v.numero, 'Centro', '85810-000', c.cidade_id, cp.id, v.limite,
       'Cliente demonstrativo', true, c.organizacao_id
from demo_seed_context c
join condicao_pagamento cp
  on cp.organizacao_id = c.organizacao_id and cp.nome = '30/60 dias Demo'
cross join (values
    ('Mariana Souza Demo', 'Mariana', 'FISICA', '12345678909', date '1988-03-20',
     'FEMININO', 'CASADO', '(45) 99999-1001', 'mariana.demo@kaneko.local', '101', 5000::numeric),
    ('Empresa Cliente Demo Ltda', 'Cliente Empresa', 'JURIDICA', '44555666000177', null::date,
     null::varchar, null::varchar, '(45) 3333-1000', 'financeiro.cliente@kaneko.local', '102', 20000::numeric)
) v(nome, apelido, tipo, documento, nascimento, sexo, civil, telefone, email, numero, limite)
where not exists (
    select 1 from cliente cl
    where cl.organizacao_id = c.organizacao_id and cl.documento = v.documento
);

insert into transportadora
    (id, nome, nome_fantasia, tipo, documento, telefone, email, endereco,
     numero, bairro, cep, cidade_id, condicao_pagamento_id, observacao, ativo,
     organizacao_id)
select nextval('transportadora_id_seq'), 'Transportes Kaneko Demo Ltda',
       'Kaneko Log Demo', 'JURIDICA', '55666777000188', '(45) 3333-2200',
       'logistica.demo@kaneko.local', 'Rodovia BR-277', '5000', 'Industrial',
       '85818-000', c.cidade_id, cp.id, 'Transportadora demonstrativa', true,
       c.organizacao_id
from demo_seed_context c
join condicao_pagamento cp
  on cp.organizacao_id = c.organizacao_id and cp.nome = '30/60 dias Demo'
where not exists (
    select 1 from transportadora t
    where t.organizacao_id = c.organizacao_id and t.documento = '55666777000188'
);

insert into veiculo_frota
    (id, placa, modelo, marca, ano, capacidade, observacao, ativo, organizacao_id)
select nextval('veiculo_frota_id_seq'), 'LOG2D26', 'Delivery 11.180',
       'Volkswagen', 2024, 8500, 'Caminhao demo', true, c.organizacao_id
from demo_seed_context c
where not exists (
    select 1 from veiculo_frota v
    where v.organizacao_id = c.organizacao_id and v.placa = 'LOG2D26'
);

insert into transportadora_veiculo_frota
    (id, transportadora_id, veiculo_frota_id, ativo, organizacao_id)
select nextval('transportadora_veiculo_frota_id_seq'), t.id, v.id, true,
       c.organizacao_id
from demo_seed_context c
join transportadora t
  on t.organizacao_id = c.organizacao_id and t.documento = '55666777000188'
join veiculo_frota v
  on v.organizacao_id = c.organizacao_id and v.placa = 'LOG2D26'
where not exists (
    select 1 from transportadora_veiculo_frota tv
    where tv.transportadora_id = t.id and tv.veiculo_frota_id = v.id
);

insert into fornecedor
    (id, nome, nome_fantasia, tipo, documento, endereco, numero, bairro, cep,
     cidade_id, condicao_pagamento_id, limite_credito, observacao, ativo,
     transportadora_id, organizacao_id)
select nextval('fornecedor_id_seq'), 'Distribuidora Conveniencia Demo Ltda',
       'Distribuidora Demo', 'JURIDICA', '66777888000199', 'Rua das Industrias',
       '900', 'Industrial', '85818-100', c.cidade_id, cp.id, 30000,
       'Fornecedor demonstrativo', true, t.id, c.organizacao_id
from demo_seed_context c
join condicao_pagamento cp
  on cp.organizacao_id = c.organizacao_id and cp.nome = '30/60 dias Demo'
join transportadora t
  on t.organizacao_id = c.organizacao_id and t.documento = '55666777000188'
where not exists (
    select 1 from fornecedor f
    where f.organizacao_id = c.organizacao_id and f.documento = '66777888000199'
);

insert into fornecedor_email
    (id, fornecedor_id, email, tipo, principal, ativo)
select nextval('fornecedor_email_id_seq'), f.id, 'vendas.demo@fornecedor.local',
       'VENDAS', true, true
from demo_seed_context c
join fornecedor f
  on f.organizacao_id = c.organizacao_id and f.documento = '66777888000199'
where not exists (
    select 1 from fornecedor_email e
    where e.fornecedor_id = f.id and e.email = 'vendas.demo@fornecedor.local'
);

insert into fornecedor_telefone
    (id, fornecedor_id, telefone, tipo, principal, ativo)
select nextval('fornecedor_telefone_id_seq'), f.id, '(45) 99999-3000',
       'WHATSAPP', true, true
from demo_seed_context c
join fornecedor f
  on f.organizacao_id = c.organizacao_id and f.documento = '66777888000199'
where not exists (
    select 1 from fornecedor_telefone t
    where t.fornecedor_id = f.id and t.telefone = '(45) 99999-3000'
);

insert into categoria (id, nome, descricao, ativo, organizacao_id, version)
select nextval('categoria_id_seq'), v.nome, v.descricao, true, c.organizacao_id, 0
from demo_seed_context c
cross join (values
    ('Bebidas Demo', 'Bebidas da conveniencia'),
    ('Alimentos Demo', 'Alimentos e snacks')
) v(nome, descricao)
where not exists (
    select 1 from categoria ca
    where ca.organizacao_id = c.organizacao_id and ca.nome = v.nome
);

insert into marca (id, nome, ativo, organizacao_id, version)
select nextval('marca_id_seq'), v.nome, true, c.organizacao_id, 0
from demo_seed_context c
cross join (values ('Kaneko Demo'), ('Parceira Demo')) v(nome)
where not exists (
    select 1 from marca m
    where m.organizacao_id = c.organizacao_id and m.nome = v.nome
);

insert into unidade_medida (id, nome, sigla, ativo, organizacao_id, version)
select nextval('unidade_medida_id_seq'), v.nome, v.sigla, true, c.organizacao_id, 0
from demo_seed_context c
cross join (values ('Unidade Demo', 'UN'), ('Litro Demo', 'L')) v(nome, sigla)
where not exists (
    select 1 from unidade_medida u
    where u.organizacao_id = c.organizacao_id and u.sigla = v.sigla
);

insert into produto
    (id, nome, codigo_barras, referencia, marca_id, unidade_medida_id,
     categoria_id, valor_compra, valor_venda, percentual_lucro, quantidade,
     quantidade_minima, descricao, observacao, ativo, custo, organizacao_id, version)
select nextval('produto_id_seq'), v.nome, v.codigo, v.referencia, m.id, u.id,
       ca.id, v.compra, v.venda, v.lucro, v.quantidade, v.minimo,
       v.descricao, 'Produto demonstrativo', true, v.compra, c.organizacao_id, 0
from demo_seed_context c
join marca m on m.organizacao_id = c.organizacao_id and m.nome = 'Kaneko Demo'
join unidade_medida u on u.organizacao_id = c.organizacao_id and u.sigla = 'UN'
join categoria ca on ca.organizacao_id = c.organizacao_id and ca.nome = 'Bebidas Demo'
cross join (values
    ('Agua Mineral Demo 500ml', '7890000000001', 'AGUA-DEMO', 2.00::numeric, 5.00::numeric, 150::numeric, 0::numeric, 10::numeric, 'Agua sem gas'),
    ('Refrigerante Demo Lata', '7890000000002', 'REFRI-DEMO', 3.50::numeric, 8.00::numeric, 128.57::numeric, 0::numeric, 6::numeric, 'Refrigerante 350ml')
) v(nome, codigo, referencia, compra, venda, lucro, quantidade, minimo, descricao)
where not exists (
    select 1 from produto p
    where p.organizacao_id = c.organizacao_id and p.referencia = v.referencia
);

insert into servico
    (id, nome, descricao, valor_padrao, ativo, organizacao_id, version)
select nextval('servico_id_seq'), v.nome, v.descricao, v.valor, true,
       c.organizacao_id, 0
from demo_seed_context c
cross join (values
    ('Lavagem Expressa Demo', 'Lavagem externa durante o estacionamento', 35::numeric),
    ('Manobrista Demo', 'Servico de manobrista para eventos', 25::numeric)
) v(nome, descricao, valor)
where not exists (
    select 1 from servico s
    where s.organizacao_id = c.organizacao_id and s.nome = v.nome
);

insert into produto_fornecedor
    (id, produto_id, fornecedor_id, codigo_prod, custo, ativo, organizacao_id, version)
select nextval('produto_fornecedor_id_seq'), p.id, f.id,
       'FORN-' || p.referencia, p.valor_compra, true, c.organizacao_id, 0
from demo_seed_context c
join fornecedor f
  on f.organizacao_id = c.organizacao_id and f.documento = '66777888000199'
join produto p
  on p.organizacao_id = c.organizacao_id and p.referencia in ('AGUA-DEMO', 'REFRI-DEMO')
where not exists (
    select 1 from produto_fornecedor pf
    where pf.produto_id = p.id and pf.fornecedor_id = f.id
);

insert into local_estoque
    (id, organizacao_id, nome, ativo, version)
select nextval('local_estoque_id_seq'), c.organizacao_id,
       'Deposito Conveniencia Demo', true, 0
from demo_seed_context c
where not exists (
    select 1 from local_estoque l
    where l.organizacao_id = c.organizacao_id and l.nome = 'Deposito Conveniencia Demo'
);

insert into saldo_estoque
    (id, organizacao_id, produto_id, local_estoque_id, quantidade, version)
select nextval('saldo_estoque_id_seq'), c.organizacao_id, p.id, l.id,
       case p.referencia when 'AGUA-DEMO' then 16 else 8 end, 0
from demo_seed_context c
join local_estoque l
  on l.organizacao_id = c.organizacao_id and l.nome = 'Deposito Conveniencia Demo'
join produto p
  on p.organizacao_id = c.organizacao_id and p.referencia in ('AGUA-DEMO', 'REFRI-DEMO')
where not exists (
    select 1 from saldo_estoque s
    where s.organizacao_id = c.organizacao_id
      and s.produto_id = p.id and s.local_estoque_id = l.id
);

insert into movimento_estoque
    (id, organizacao_id, produto_id, local_estoque_id, saldo_estoque_id,
     tipo, delta, saldo_anterior, saldo_posterior, custo_unitario,
     origem_tipo, origem_chave, ator_id, motivo, ocorrido_em)
select nextval('movimento_estoque_id_seq'), c.organizacao_id, p.id, l.id, s.id,
       v.tipo, v.delta, v.anterior, v.posterior, p.valor_compra,
       v.origem_tipo, v.origem_chave, c.admin_id, v.motivo, now() + v.intervalo
from demo_seed_context c
join local_estoque l
  on l.organizacao_id = c.organizacao_id and l.nome = 'Deposito Conveniencia Demo'
join produto p
  on p.organizacao_id = c.organizacao_id and p.referencia = 'AGUA-DEMO'
join saldo_estoque s
  on s.organizacao_id = c.organizacao_id and s.produto_id = p.id and s.local_estoque_id = l.id
cross join (values
    ('RECEBIMENTO', 20::numeric, 0::numeric, 20::numeric, 'NOTA_ENTRADA', 'NE-DEMO-001', 'Recebimento da compra demo', interval '-7 days'),
    ('BAIXA', -4::numeric, 20::numeric, 16::numeric, 'NOTA_SAIDA', 'NS-DEMO-001', 'Venda na conveniencia demo', interval '-2 days')
) v(tipo, delta, anterior, posterior, origem_tipo, origem_chave, motivo, intervalo)
where not exists (
    select 1 from movimento_estoque me
    where me.organizacao_id = c.organizacao_id
      and me.origem_tipo = v.origem_tipo and me.origem_chave = v.origem_chave
      and me.produto_id = p.id and me.local_estoque_id = l.id and me.tipo = v.tipo
);

insert into movimento_estoque
    (id, organizacao_id, produto_id, local_estoque_id, saldo_estoque_id,
     tipo, delta, saldo_anterior, saldo_posterior, custo_unitario,
     origem_tipo, origem_chave, ator_id, motivo)
select nextval('movimento_estoque_id_seq'), c.organizacao_id, p.id, l.id, s.id,
       'AJUSTE', 8, 0, 8, p.valor_compra, 'AJUSTE_INICIAL', 'AJ-DEMO-REFRI',
       c.admin_id, 'Inventario inicial demonstrativo'
from demo_seed_context c
join local_estoque l
  on l.organizacao_id = c.organizacao_id and l.nome = 'Deposito Conveniencia Demo'
join produto p
  on p.organizacao_id = c.organizacao_id and p.referencia = 'REFRI-DEMO'
join saldo_estoque s
  on s.organizacao_id = c.organizacao_id and s.produto_id = p.id and s.local_estoque_id = l.id
where not exists (
    select 1 from movimento_estoque me
    where me.organizacao_id = c.organizacao_id and me.origem_tipo = 'AJUSTE_INICIAL'
      and me.origem_chave = 'AJ-DEMO-REFRI' and me.produto_id = p.id
);

insert into nota_entrada
    (id, numero, modelo, serie, fornecedor_id, condicao_pagamento_id,
     data_emissao, data_chegada, tipo_frete, valor_produtos, valor_frete,
     valor_seguro, outras_despesas, valor_desconto, valor_total, situacao,
     observacao, organizacao_id, local_estoque_id, version)
select nextval('nota_entrada_id_seq'), 'NE-DEMO-001', '55', 'D1', f.id, cp.id,
       current_date - 7, current_date - 7, 'CIF', 40, 0, 0, 0, 0, 40,
       'CONFIRMADA', 'Compra recebida para demonstracao', c.organizacao_id, l.id, 1
from demo_seed_context c
join fornecedor f
  on f.organizacao_id = c.organizacao_id and f.documento = '66777888000199'
join condicao_pagamento cp
  on cp.organizacao_id = c.organizacao_id and cp.nome = '30/60 dias Demo'
join local_estoque l
  on l.organizacao_id = c.organizacao_id and l.nome = 'Deposito Conveniencia Demo'
where not exists (
    select 1 from nota_entrada n
    where n.organizacao_id = c.organizacao_id and n.numero = 'NE-DEMO-001'
      and n.serie = 'D1'
);

insert into item_nota_entrada
    (id, nota_entrada_id, produto_id, sequencia, quantidade, valor_unitario,
     valor_desconto, percentual_desconto, valor_total, rateio_frete,
     rateio_seguro, rateio_outras, custo_preco_final, organizacao_id)
select nextval('item_nota_entrada_id_seq'), n.id, p.id, 1, 20, 2, 0, 0,
       40, 0, 0, 0, 2, c.organizacao_id
from demo_seed_context c
join nota_entrada n
  on n.organizacao_id = c.organizacao_id and n.numero = 'NE-DEMO-001' and n.serie = 'D1'
join produto p
  on p.organizacao_id = c.organizacao_id and p.referencia = 'AGUA-DEMO'
where not exists (
    select 1 from item_nota_entrada i
    where i.nota_entrada_id = n.id and i.sequencia = 1
);

insert into conta_pagar
    (id, fornecedor_id, nota_entrada_id, condicao_pagamento_id,
     forma_pagamento_id, numero_parcela, total_parcelas, valor_original,
     valor_pago, valor_desconto, valor_juros, valor_multa, valor_total,
     data_emissao, data_vencimento, situacao, observacao, organizacao_id, version)
select nextval('conta_pagar_id_seq'), f.id, n.id, cp.id, fp.id, 1, 1, 40,
       0, 0, 0, 0, 40, current_date - 7, current_date + 23, 'PENDENTE',
       'Parcela gerada pela nota de entrada demo', c.organizacao_id, 0
from demo_seed_context c
join fornecedor f
  on f.organizacao_id = c.organizacao_id and f.documento = '66777888000199'
join nota_entrada n
  on n.organizacao_id = c.organizacao_id and n.numero = 'NE-DEMO-001' and n.serie = 'D1'
join condicao_pagamento cp
  on cp.organizacao_id = c.organizacao_id and cp.nome = '30/60 dias Demo'
join forma_pagamento fp
  on fp.organizacao_id = c.organizacao_id and fp.nome = 'PIX Demo'
where not exists (
    select 1 from conta_pagar cp2
    where cp2.organizacao_id = c.organizacao_id and cp2.nota_entrada_id = n.id
      and cp2.numero_parcela = 1
);

insert into nota_saida
    (id, numero, modelo, serie, cliente_id, condicao_pagamento_id,
     data_emissao, data_saida, tipo_frete, valor_produtos, valor_frete,
     valor_seguro, outras_despesas, valor_desconto, valor_total, situacao,
     observacao, organizacao_id, local_estoque_id, version)
select nextval('nota_saida_id_seq'), 'NS-DEMO-001', '55', 'D1', cl.id, cp.id,
       current_date - 2, current_date - 2, 'SEM', 20, 0, 0, 0, 0, 20,
       'CONFIRMADA', 'Venda da conveniencia demonstrativa', c.organizacao_id, l.id, 1
from demo_seed_context c
join cliente cl
  on cl.organizacao_id = c.organizacao_id and cl.documento = '12345678909'
join condicao_pagamento cp
  on cp.organizacao_id = c.organizacao_id and cp.nome = 'A vista Demo'
join local_estoque l
  on l.organizacao_id = c.organizacao_id and l.nome = 'Deposito Conveniencia Demo'
where not exists (
    select 1 from nota_saida n
    where n.organizacao_id = c.organizacao_id and n.numero = 'NS-DEMO-001'
      and n.serie = 'D1'
);

insert into item_nota_saida
    (id, nota_saida_id, produto_id, sequencia, quantidade, valor_unitario,
     valor_desconto, percentual_desconto, valor_total, rateio_frete,
     rateio_seguro, rateio_outras, custo_preco_final, organizacao_id)
select nextval('item_nota_saida_id_seq'), n.id, p.id, 1, 4, 5, 0, 0,
       20, 0, 0, 0, 2, c.organizacao_id
from demo_seed_context c
join nota_saida n
  on n.organizacao_id = c.organizacao_id and n.numero = 'NS-DEMO-001' and n.serie = 'D1'
join produto p
  on p.organizacao_id = c.organizacao_id and p.referencia = 'AGUA-DEMO'
where not exists (
    select 1 from item_nota_saida i
    where i.nota_saida_id = n.id and i.sequencia = 1
);

insert into conta_receber
    (id, cliente_id, nota_saida_id, condicao_pagamento_id, forma_pagamento_id,
     numero_parcela, total_parcelas, valor_original, valor_recebido,
     valor_desconto, valor_juros, valor_multa, valor_total, data_emissao,
     data_vencimento, data_recebimento, situacao, observacao, organizacao_id, version)
select nextval('conta_receber_id_seq'), cl.id, n.id, cp.id, fp.id, 1, 1, 20,
       20, 0, 0, 0, 20, current_date - 2, current_date - 2, current_date - 2,
       'RECEBIDA', 'Recebimento da venda demo', c.organizacao_id, 1
from demo_seed_context c
join cliente cl
  on cl.organizacao_id = c.organizacao_id and cl.documento = '12345678909'
join nota_saida n
  on n.organizacao_id = c.organizacao_id and n.numero = 'NS-DEMO-001' and n.serie = 'D1'
join condicao_pagamento cp
  on cp.organizacao_id = c.organizacao_id and cp.nome = 'A vista Demo'
join forma_pagamento fp
  on fp.organizacao_id = c.organizacao_id and fp.nome = 'PIX Demo'
where not exists (
    select 1 from conta_receber cr
    where cr.organizacao_id = c.organizacao_id and cr.nota_saida_id = n.id
      and cr.numero_parcela = 1
);

insert into nota_servico
    (id, numero, modelo, serie, cliente_id, servico_id, condicao_pagamento_id,
     forma_pagamento_id, data_emissao, valor_servico, aliquota_iss, valor_iss,
     valor_desconto, valor_total, situacao, observacao, organizacao_id, version)
select nextval('nota_servico_id_seq'), 'NFS-DEMO-001', 'NFS-e', 'D1', cl.id,
       s.id, cp.id, fp.id, current_date - 1, 35, 5, 1.75, 0, 35,
       'EMITIDA', 'Lavagem expressa demonstrativa', c.organizacao_id, 1
from demo_seed_context c
join cliente cl
  on cl.organizacao_id = c.organizacao_id and cl.documento = '12345678909'
join servico s
  on s.organizacao_id = c.organizacao_id and s.nome = 'Lavagem Expressa Demo'
join condicao_pagamento cp
  on cp.organizacao_id = c.organizacao_id and cp.nome = 'A vista Demo'
join forma_pagamento fp
  on fp.organizacao_id = c.organizacao_id and fp.nome = 'Cartao Demo'
where not exists (
    select 1 from nota_servico n
    where n.organizacao_id = c.organizacao_id and n.numero = 'NFS-DEMO-001'
      and n.serie = 'D1'
);

insert into conta_receber
    (id, cliente_id, nota_servico_id, condicao_pagamento_id, forma_pagamento_id,
     numero_parcela, total_parcelas, valor_original, valor_recebido,
     valor_desconto, valor_juros, valor_multa, valor_total, data_emissao,
     data_vencimento, situacao, observacao, organizacao_id, version)
select nextval('conta_receber_id_seq'), cl.id, ns.id, cp.id, fp.id, 1, 1, 35,
       0, 0, 0, 0, 35, current_date - 1, current_date + 14, 'PENDENTE',
       'Faturamento do servico demo', c.organizacao_id, 0
from demo_seed_context c
join cliente cl
  on cl.organizacao_id = c.organizacao_id and cl.documento = '12345678909'
join nota_servico ns
  on ns.organizacao_id = c.organizacao_id and ns.numero = 'NFS-DEMO-001' and ns.serie = 'D1'
join condicao_pagamento cp
  on cp.organizacao_id = c.organizacao_id and cp.nome = 'A vista Demo'
join forma_pagamento fp
  on fp.organizacao_id = c.organizacao_id and fp.nome = 'Cartao Demo'
where not exists (
    select 1 from conta_receber cr
    where cr.organizacao_id = c.organizacao_id and cr.nota_servico_id = ns.id
      and cr.numero_parcela = 1
);

insert into contas_pagar_avulsa
    (id, descricao, fornecedor_id, forma_pagamento_id, numero_nota, modelo,
     serie, numero_parcela, total_parcelas, valor_original, valor_pago,
     valor_desconto, valor_juros, valor_multa, valor_total, data_emissao,
     data_vencimento, situacao, observacao, organizacao_id, version)
select nextval('contas_pagar_avulsa_id_seq'), 'Energia eletrica Demo', null, fp.id,
       'DESP-DEMO-001', 'FAT', 'D1', 1, 1, 480, 0, 0, 0, 0, 480,
       current_date, current_date + 10, 'PENDENTE',
       'Despesa operacional demonstrativa', c.organizacao_id, 0
from demo_seed_context c
join forma_pagamento fp
  on fp.organizacao_id = c.organizacao_id and fp.nome = 'PIX Demo'
where not exists (
    select 1 from contas_pagar_avulsa d
    where d.organizacao_id = c.organizacao_id and d.numero_nota = 'DESP-DEMO-001'
);

insert into tarifa
    (id, descricao, valor_hora, valor_diaria, tolerancia_min,
     valor_mensalidade, ativo, organizacao_id, version)
select nextval('tarifa_id_seq'), v.descricao, v.hora, v.diaria, v.tolerancia,
       v.mensalidade, true, c.organizacao_id, 0
from demo_seed_context c
cross join (values
    ('Tarifa Rotativa Demo', 8::numeric, 45::numeric, 15, 0::numeric),
    ('Tarifa Mensal Demo', 0::numeric, 0::numeric, 15, 220::numeric)
) v(descricao, hora, diaria, tolerancia, mensalidade)
where not exists (
    select 1 from tarifa t
    where t.organizacao_id = c.organizacao_id and t.descricao = v.descricao
);

insert into veiculo
    (id, placa, modelo, cor, tipo, cliente_id, observacao, ativo,
     organizacao_id, version)
select nextval('veiculo_id_seq'), v.placa, v.modelo, v.cor, v.tipo, cl.id,
       'Veiculo demonstrativo', true, c.organizacao_id, 0
from demo_seed_context c
cross join (values
    ('ABC1D23', 'Corolla', 'Prata', 'CARRO', '12345678909'),
    ('XYZ9K87', 'Onix', 'Branco', 'CARRO', '44555666000177'),
    ('MOT2A34', 'CG 160', 'Vermelha', 'MOTO', '12345678909')
) v(placa, modelo, cor, tipo, documento)
join cliente cl
  on cl.organizacao_id = c.organizacao_id and cl.documento = v.documento
where not exists (
    select 1 from veiculo ve
    where ve.organizacao_id = c.organizacao_id and ve.placa = v.placa
);

insert into mensalista
    (id, cliente_id, veiculo_id, valor_mensal, data_inicio, status,
     organizacao_id, version)
select nextval('mensalista_id_seq'), cl.id, v.id, 220, current_date - 90,
       'ATIVO', c.organizacao_id, 0
from demo_seed_context c
join cliente cl
  on cl.organizacao_id = c.organizacao_id and cl.documento = '12345678909'
join veiculo v
  on v.organizacao_id = c.organizacao_id and v.placa = 'ABC1D23'
where not exists (
    select 1 from mensalista m
    where m.organizacao_id = c.organizacao_id and m.veiculo_id = v.id
      and m.status = 'ATIVO'
);

insert into conta_receber
    (id, cliente_id, mensalista_id, forma_pagamento_id, numero_parcela,
     total_parcelas, valor_original, valor_recebido, valor_desconto,
     valor_juros, valor_multa, valor_total, data_emissao, data_vencimento,
     situacao, observacao, organizacao_id, version)
select nextval('conta_receber_id_seq'), cl.id, m.id, fp.id, 1, 1, 220,
       0, 0, 0, 0, 220, current_date, current_date + 5, 'PENDENTE',
       'Mensalidade demonstrativa', c.organizacao_id, 0
from demo_seed_context c
join cliente cl
  on cl.organizacao_id = c.organizacao_id and cl.documento = '12345678909'
join mensalista m
  on m.organizacao_id = c.organizacao_id and m.cliente_id = cl.id and m.status = 'ATIVO'
join forma_pagamento fp
  on fp.organizacao_id = c.organizacao_id and fp.nome = 'PIX Demo'
where not exists (
    select 1 from conta_receber cr
    where cr.organizacao_id = c.organizacao_id and cr.mensalista_id = m.id
      and cr.data_emissao = current_date
);

insert into movimentacao
    (id, veiculo_id, mensalista_id, tarifa_id, tipo, data_entrada,
     data_saida, valor_cobrado, status, patio_id, organizacao_id, version)
select nextval('movimentacao_id_seq'), v.id, null, t.id, 'AVULSO',
       now() - interval '5 hours', now() - interval '2 hours', 24,
       'FECHADO', c.patio_id, c.organizacao_id, 1
from demo_seed_context c
join veiculo v on v.organizacao_id = c.organizacao_id and v.placa = 'XYZ9K87'
join tarifa t on t.organizacao_id = c.organizacao_id and t.descricao = 'Tarifa Rotativa Demo'
where not exists (
    select 1 from movimentacao m
    where m.organizacao_id = c.organizacao_id and m.veiculo_id = v.id
      and m.status = 'FECHADO'
);

insert into movimentacao
    (id, veiculo_id, mensalista_id, tarifa_id, tipo, data_entrada,
     status, patio_id, organizacao_id, version)
select nextval('movimentacao_id_seq'), v.id, m.id, t.id, 'MENSALISTA',
       now() - interval '45 minutes', 'ABERTO', c.patio_id, c.organizacao_id, 0
from demo_seed_context c
join veiculo v on v.organizacao_id = c.organizacao_id and v.placa = 'ABC1D23'
join mensalista m
  on m.organizacao_id = c.organizacao_id and m.veiculo_id = v.id and m.status = 'ATIVO'
join tarifa t on t.organizacao_id = c.organizacao_id and t.descricao = 'Tarifa Mensal Demo'
where not exists (
    select 1 from movimentacao mv
    where mv.organizacao_id = c.organizacao_id and mv.veiculo_id = v.id
      and mv.status = 'ABERTO'
);

insert into reserva_inventario
    (id, organizacao_id, evento_id, produto_id, ator_id, quantidade, canal,
     referencia_comprador, chave_idempotencia, lote_preco_id, preco_unitario,
     moeda, status, expira_em, finalizada_em, motivo_finalizacao, version)
select nextval('reserva_inventario_id_seq'), c.organizacao_id, c.evento_id,
       c.produto_estacionamento_id, c.admin_id, 1, 'WEB',
       'mariana.demo@kaneko.local', 'demo-reserva-confirmada-v1', c.lote_preco_id,
       lp.preco, lp.moeda, 'CONFIRMADA', now() + interval '30 minutes', now(),
       'PEDIDO_CONFIRMADO', 1
from demo_seed_context c
join lote_preco lp on lp.id = c.lote_preco_id
where not exists (
    select 1 from reserva_inventario r
    where r.organizacao_id = c.organizacao_id
      and r.chave_idempotencia = 'demo-reserva-confirmada-v1'
);

insert into pedido
    (id, organizacao_id, evento_id, reserva_inventario_id, ator_id, numero,
     canal, placa_veiculo, status, subtotal, desconto, taxa, total, moeda,
     expira_em, version)
select nextval('pedido_id_seq'), c.organizacao_id, c.evento_id, r.id, c.admin_id,
       'PED-00000000000000000000000000000001', 'WEB', 'ABC1D23', 'CONFIRMADO',
       r.preco_unitario, 0, 0, r.preco_unitario, r.moeda,
       now() + interval '30 minutes', 1
from demo_seed_context c
join reserva_inventario r
  on r.organizacao_id = c.organizacao_id
 and r.chave_idempotencia = 'demo-reserva-confirmada-v1'
where not exists (
    select 1 from pedido p
    where p.numero = 'PED-00000000000000000000000000000001'
);

insert into comprador_pedido (pedido_id, nome, email, documento)
select p.id, 'Mariana Souza Demo', 'mariana.demo@kaneko.local', '12345678909'
from demo_seed_context c
join pedido p
  on p.organizacao_id = c.organizacao_id
 and p.numero = 'PED-00000000000000000000000000000001'
where not exists (select 1 from comprador_pedido cp where cp.pedido_id = p.id);

insert into item_pedido
    (id, pedido_id, produto_id, alocacao_id, lote_preco_id, nome_produto,
     categoria, direito, inicio_acesso, fim_acesso, quantidade, preco_unitario,
     moeda, total, patio_id)
select nextval('item_pedido_id_seq'), p.id, pe.id, a.id, lp.id, pe.nome,
       pe.categoria, pe.direito, pe.inicio_acesso, pe.fim_acesso, 1, lp.preco,
       lp.moeda, lp.preco, a.patio_id
from demo_seed_context c
join pedido p
  on p.organizacao_id = c.organizacao_id
 and p.numero = 'PED-00000000000000000000000000000001'
join produto_estacionamento pe on pe.id = c.produto_estacionamento_id
join alocacao_patio_evento a on a.id = c.alocacao_id
join lote_preco lp on lp.id = c.lote_preco_id
where not exists (
    select 1 from item_pedido i where i.pedido_id = p.id and i.produto_id = pe.id
);

insert into confirmacao_manual_pedido
    (id, organizacao_id, pedido_id, ator_id, meio_pagamento,
     referencia_evidencia, motivo, status_conciliacao, version)
select nextval('confirmacao_manual_pedido_id_seq'), c.organizacao_id, p.id,
       c.admin_id, 'PIX', 'DEMO-PIX-0001', 'Pagamento demonstrativo confirmado',
       'CONCILIADA', 0
from demo_seed_context c
join pedido p
  on p.organizacao_id = c.organizacao_id
 and p.numero = 'PED-00000000000000000000000000000001'
where not exists (
    select 1 from confirmacao_manual_pedido cm where cm.pedido_id = p.id
);

insert into credencial_acesso
    (id, organizacao_id, evento_id, patio_id, pedido_id, item_pedido_id,
     ator_id, codigo_publico, numero_unidade, origem, categoria, direito,
     inicio_validade, fim_validade, meio_preferido, placa_veiculo, status,
     version, qr_versao)
select nextval('credencial_acesso_id_seq'), c.organizacao_id, c.evento_id,
       c.patio_id, p.id, i.id, c.admin_id,
       'CRD-00000000000000000000000000000001', 1, 'PEDIDO', i.categoria,
       i.direito, i.inicio_acesso, i.fim_acesso, 'QR', p.placa_veiculo,
       'ATIVA', 0, 1
from demo_seed_context c
join pedido p
  on p.organizacao_id = c.organizacao_id
 and p.numero = 'PED-00000000000000000000000000000001'
join item_pedido i on i.pedido_id = p.id
where not exists (
    select 1 from credencial_acesso ca
    where ca.codigo_publico = 'CRD-00000000000000000000000000000001'
);

insert into representacao_qr
    (id, organizacao_id, credencial_id, numero_versao, token_hash,
     emitido_em, expira_em, version)
select nextval('representacao_qr_id_seq'), c.organizacao_id, ca.id, 1,
       '0000000000000000000000000000000000000000000000000000000000000001',
       now() - interval '10 minutes', ca.fim_validade, 0
from demo_seed_context c
join credencial_acesso ca
  on ca.organizacao_id = c.organizacao_id
 and ca.codigo_publico = 'CRD-00000000000000000000000000000001'
where not exists (
    select 1 from representacao_qr qr where qr.credencial_id = ca.id and qr.numero_versao = 1
);

insert into tentativa_acesso
    (id, organizacao_id, evento_id, patio_id, ator_id, credencial_id,
     representacao_qr_id, direcao, origem_leitura, faixa, status, motivo,
     ocupacao_resultante, recebido_em, decidido_em, version, operacao)
select nextval('tentativa_acesso_id_seq'), c.organizacao_id, c.evento_id,
       c.patio_id, c.operador_id, ca.id, qr.id, 'ENTRADA', 'QR', 'FAIXA-DEMO-01',
       'AUTORIZADA', 'AUTORIZADA', 1, now() - interval '20 minutes',
       now() - interval '20 minutes', 0, 'CHECKIN'
from demo_seed_context c
join credencial_acesso ca
  on ca.organizacao_id = c.organizacao_id
 and ca.codigo_publico = 'CRD-00000000000000000000000000000001'
join representacao_qr qr on qr.credencial_id = ca.id and qr.numero_versao = 1
where not exists (
    select 1 from tentativa_acesso ta
    where ta.organizacao_id = c.organizacao_id and ta.faixa = 'FAIXA-DEMO-01'
      and ta.operacao = 'CHECKIN' and ta.credencial_id = ca.id
);

insert into presenca
    (id, organizacao_id, evento_id, patio_id, credencial_id,
     tentativa_checkin_id, checkin_em, status, version)
select nextval('presenca_id_seq'), c.organizacao_id, c.evento_id, c.patio_id,
       ca.id, ta.id, ta.decidido_em, 'DENTRO', 0
from demo_seed_context c
join credencial_acesso ca
  on ca.organizacao_id = c.organizacao_id
 and ca.codigo_publico = 'CRD-00000000000000000000000000000001'
join tentativa_acesso ta
  on ta.organizacao_id = c.organizacao_id and ta.credencial_id = ca.id
 and ta.operacao = 'CHECKIN' and ta.status = 'AUTORIZADA'
where not exists (
    select 1 from presenca pr where pr.credencial_id = ca.id and pr.status = 'DENTRO'
);

insert into sessao_estacionamento
    (id, organizacao_id, evento_id, patio_id, credencial_id, pedido_id,
     item_pedido_id, presenca_id, tentativa_entrada_id, origem_direito,
     placa_veiculo, entrada_em, status, version)
select nextval('sessao_estacionamento_id_seq'), c.organizacao_id, c.evento_id,
       c.patio_id, ca.id, ca.pedido_id, ca.item_pedido_id, pr.id,
       pr.tentativa_checkin_id, 'PEDIDO_EVENTO', ca.placa_veiculo,
       pr.checkin_em, 'ABERTA', 0
from demo_seed_context c
join credencial_acesso ca
  on ca.organizacao_id = c.organizacao_id
 and ca.codigo_publico = 'CRD-00000000000000000000000000000001'
join presenca pr on pr.credencial_id = ca.id and pr.status = 'DENTRO'
where not exists (
    select 1 from sessao_estacionamento se where se.presenca_id = pr.id
);

update credencial_acesso ca
set status = 'DENTRO', updated_at = now(), version = ca.version + 1
from demo_seed_context c
where ca.organizacao_id = c.organizacao_id
  and ca.codigo_publico = 'CRD-00000000000000000000000000000001'
  and ca.status = 'ATIVA';

update produto_estacionamento pe
set quantidade_vendida = greatest(pe.quantidade_vendida, 1), updated_at = now()
from demo_seed_context c
where pe.id = c.produto_estacionamento_id and pe.quantidade_vendida < 1;

update alocacao_patio_evento a
set ocupacao_atual = greatest(a.ocupacao_atual, 1), updated_at = now()
from demo_seed_context c
where a.id = c.alocacao_id and a.ocupacao_atual < 1;

insert into tentativa_acesso
    (id, organizacao_id, evento_id, patio_id, ator_id, direcao,
     origem_leitura, faixa, status, motivo, recebido_em, decidido_em,
     version, operacao)
select nextval('tentativa_acesso_id_seq'), c.organizacao_id, c.evento_id,
       c.patio_id, c.operador_id, 'ENTRADA', 'QR', 'FAIXA-DEMO-02',
       'RECUSADA', 'CREDENCIAL_INVALIDA', now() - interval '5 minutes',
       now() - interval '5 minutes', 0, 'VALIDACAO'
from demo_seed_context c
where not exists (
    select 1 from tentativa_acesso ta
    where ta.organizacao_id = c.organizacao_id and ta.faixa = 'FAIXA-DEMO-02'
      and ta.status = 'RECUSADA' and ta.motivo = 'CREDENCIAL_INVALIDA'
);

insert into ordem_compra
    (id, organizacao_id, fornecedor_id, numero, status, data_emissao,
     previsao_entrega, moeda, subtotal, valor_frete, valor_desconto,
     valor_total, observacao, version)
select nextval('ordem_compra_id_seq'), c.organizacao_id, f.id,
       'OC-DEMO-001', 'APROVADA', current_date - 2, current_date + 5,
       'BRL', 40::numeric, 5::numeric, 0::numeric, 45::numeric,
       'Ordem pronta para demonstrar recebimento parcial ou total', 0
from demo_seed_context c
join fornecedor f
  on f.organizacao_id = c.organizacao_id
 and f.nome = 'Distribuidora Conveniencia Demo Ltda'
where not exists (
    select 1 from ordem_compra o
    where o.organizacao_id = c.organizacao_id and o.numero = 'OC-DEMO-001'
);

insert into item_ordem_compra
    (id, organizacao_id, ordem_compra_id, produto_id, sequencia,
     quantidade_pedida, quantidade_recebida, valor_unitario,
     valor_desconto, valor_total)
select nextval('item_ordem_compra_id_seq'), c.organizacao_id, o.id, p.id, 1,
       20::numeric, 0::numeric, 2::numeric, 0::numeric, 40::numeric
from demo_seed_context c
join ordem_compra o
  on o.organizacao_id = c.organizacao_id and o.numero = 'OC-DEMO-001'
join produto p
  on p.organizacao_id = c.organizacao_id and p.nome = 'Agua Mineral Demo 500ml'
where not exists (
    select 1 from item_ordem_compra i
    where i.ordem_compra_id = o.id and i.sequencia = 1
);
