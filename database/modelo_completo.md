# Estacionamento Kaneko — Modelo de dados COMPLETO (campos)

Especificação dos campos de todas as tabelas, para revisão **antes** de virar SQL.
Tipos no padrão PostgreSQL. Todas as tabelas têm, além do listado: `id` (PK, BIGINT
IDENTITY), `ativo` (BOOLEAN, quando é cadastro), `created_at`/`updated_at` (TIMESTAMPTZ).

## Premissas (me corrija se alguma estiver errada)
1. **Sem "pedido" de compra/venda** — seguindo o modelo, `compra` e `venda` já são os documentos.
2. **Nomenclatura**: `compra` = nota de entrada de mercadoria; `venda` = nota de saída; `nota_servico` = NFS-e do estacionamento.
3. **Financeiro incluído**: `compra` gera `conta_pagar`; `venda` e a mensalidade geram `conta_receber`.
4. Mantidos os cortes já decididos: sem `usuario`/login; sem multifilial.

---

## JÁ EXISTENTES (11 tabelas, prontas e validadas)
`pais`, `estado`, `cidade`, `cargo`, `funcionario`, `fornecedor`, `cliente`, `veiculo`,
`tarifa`, `mensalista`, `movimentacao`. (campos no `schema.sql`)

---

# NOVAS TABELAS (14)

## Bloco 1 — Cadastros de apoio

### categoria  (agrupa produtos)
| Campo | Tipo | Obrig. | Observação |
|---|---|---|---|
| nome | VARCHAR(60) | sim | único |
| descricao | VARCHAR(255) | não | |

### marca  (marca dos produtos)
| Campo | Tipo | Obrig. | Observação |
|---|---|---|---|
| nome | VARCHAR(60) | sim | único |

### unidade_medida
| Campo | Tipo | Obrig. | Observação |
|---|---|---|---|
| nome | VARCHAR(40) | sim | ex.: Unidade, Quilograma, Litro |
| sigla | VARCHAR(6) | sim | único — UN, KG, L, PCT |

### forma_pagamento
| Campo | Tipo | Obrig. | Observação |
|---|---|---|---|
| nome | VARCHAR(40) | sim | único — Dinheiro, Cartão, Pix, Boleto |

### condicao_pagamento
| Campo | Tipo | Obrig. | Observação |
|---|---|---|---|
| nome | VARCHAR(60) | sim | único — À vista, 30 dias, 30/60 |
| numero_parcelas | INTEGER | sim | default 1 |
| dias_entre_parcelas | INTEGER | sim | default 0 |

### servico  (tipos de serviço prestado)
| Campo | Tipo | Obrig. | Observação |
|---|---|---|---|
| nome | VARCHAR(80) | sim | único — Estacionamento Avulso, Mensalidade, Lavagem |
| descricao | VARCHAR(255) | não | |
| valor_padrao | NUMERIC(10,2) | não | preço sugerido |

## Bloco 2 — Produto (conveniência)

### produto
| Campo | Tipo | Obrig. | Observação |
|---|---|---|---|
| nome | VARCHAR(100) | sim | |
| descricao | VARCHAR(255) | não | |
| codigo_barras | VARCHAR(20) | não | único quando preenchido |
| categoria_id | FK → categoria | sim | |
| marca_id | FK → marca | não | |
| unidade_medida_id | FK → unidade_medida | sim | |
| preco_custo | NUMERIC(10,2) | sim | default 0; ≥ 0 |
| preco_venda | NUMERIC(10,2) | sim | ≥ 0 |
| estoque_atual | NUMERIC(12,3) | sim | default 0; ≥ 0 |
| estoque_minimo | NUMERIC(12,3) | sim | default 0; ≥ 0 |

## Bloco 3 — Compra (entrada de mercadoria)

### compra  (cabeçalho)
| Campo | Tipo | Obrig. | Observação |
|---|---|---|---|
| fornecedor_id | FK → fornecedor | sim | |
| condicao_pagamento_id | FK → condicao_pagamento | não | |
| numero_nota | VARCHAR(20) | não | nº da NF do fornecedor |
| serie | VARCHAR(5) | não | |
| data_emissao | DATE | sim | |
| data_entrada | DATE | sim | default hoje |
| valor_total | NUMERIC(12,2) | sim | default 0 |
| status | VARCHAR(20) | sim | ABERTA / CONFIRMADA / CANCELADA |
| observacao | VARCHAR(255) | não | |

> Único: (fornecedor_id, numero_nota, serie). Ao **CONFIRMAR** → soma itens no estoque e gera `conta_pagar`.

### item_compra
| Campo | Tipo | Obrig. | Observação |
|---|---|---|---|
| compra_id | FK → compra | sim | |
| produto_id | FK → produto | sim | |
| quantidade | NUMERIC(12,3) | sim | > 0 |
| valor_unitario | NUMERIC(10,2) | sim | ≥ 0 |
| valor_total | NUMERIC(12,2) | sim | qtd × unit |

## Bloco 4 — Venda (saída de mercadoria)

### venda  (cabeçalho)
| Campo | Tipo | Obrig. | Observação |
|---|---|---|---|
| cliente_id | FK → cliente | não | nulo = venda balcão |
| funcionario_id | FK → funcionario | não | quem vendeu |
| forma_pagamento_id | FK → forma_pagamento | não | |
| data_venda | TIMESTAMPTZ | sim | default agora |
| valor_total | NUMERIC(12,2) | sim | default 0 |
| status | VARCHAR(20) | sim | CONCLUIDA / CANCELADA |
| observacao | VARCHAR(255) | não | |

> Ao concluir → subtrai itens do estoque e gera `conta_receber`.

### item_venda
| Campo | Tipo | Obrig. | Observação |
|---|---|---|---|
| venda_id | FK → venda | sim | |
| produto_id | FK → produto | sim | |
| quantidade | NUMERIC(12,3) | sim | > 0 |
| valor_unitario | NUMERIC(10,2) | sim | ≥ 0 |
| valor_total | NUMERIC(12,2) | sim | qtd × unit |

## Bloco 5 — Nota de serviço (NFS-e do estacionamento)

### nota_servico
| Campo | Tipo | Obrig. | Observação |
|---|---|---|---|
| numero | VARCHAR(20) | não | |
| cliente_id | FK → cliente | não | |
| movimentacao_id | FK → movimentacao | não | o serviço avulso prestado |
| servico_id | FK → servico | não | |
| forma_pagamento_id | FK → forma_pagamento | não | |
| data_emissao | TIMESTAMPTZ | sim | default agora |
| valor | NUMERIC(10,2) | sim | |
| descricao | VARCHAR(255) | não | |
| status | VARCHAR(20) | sim | EMITIDA / CANCELADA |

## Bloco 6 — Financeiro

### conta_pagar  (origem: compra)
| Campo | Tipo | Obrig. | Observação |
|---|---|---|---|
| compra_id | FK → compra | não | origem |
| fornecedor_id | FK → fornecedor | sim | |
| descricao | VARCHAR(255) | não | |
| valor | NUMERIC(12,2) | sim | |
| data_vencimento | DATE | sim | |
| data_pagamento | DATE | não | nulo = em aberto |
| forma_pagamento_id | FK → forma_pagamento | não | |
| status | VARCHAR(20) | sim | ABERTA / PAGA / CANCELADA |

### conta_receber  (origem: venda OU mensalidade)
| Campo | Tipo | Obrig. | Observação |
|---|---|---|---|
| venda_id | FK → venda | não | origem (venda) |
| mensalista_id | FK → mensalista | não | origem (mensalidade) |
| cliente_id | FK → cliente | sim | |
| descricao | VARCHAR(255) | não | |
| valor | NUMERIC(12,2) | sim | |
| data_vencimento | DATE | sim | |
| data_recebimento | DATE | não | nulo = em aberto |
| forma_pagamento_id | FK → forma_pagamento | não | |
| status | VARCHAR(20) | sim | ABERTA / RECEBIDA / CANCELADA |

---

## Resumo
- **14 tabelas novas** → schema total: **25 tabelas**.
- Fluxo: `fornecedor → compra → (+estoque) produto → venda → cliente`; financeiro: `compra → conta_pagar`, `venda/mensalidade → conta_receber`; serviço: `movimentacao → nota_servico`.
