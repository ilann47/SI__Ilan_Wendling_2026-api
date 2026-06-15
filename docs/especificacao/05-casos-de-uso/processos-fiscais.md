> Links: [[README]] · [[04-requisitos-funcionais]]

# Seção 5 — Processos Fiscais e de Movimentação de Mercadoria

Este documento especifica os **processos fiscais** do sistema Estacionamento Kaneko: o registro de compras de mercadoria para a conveniência (nota de entrada), o registro de vendas dessas mercadorias (nota de saída) e a emissão de nota fiscal de serviço (NFS-e) referente ao estacionamento e demais serviços prestados. Cada processo é descrito no formato estendido de caso de uso, com regras de negócio aterradas no comportamento real esperado das camadas de serviço (services) e nas restrições efetivas do esquema relacional (`database/schema.sql`), incluindo a atualização de estoque, a geração de contas a pagar/receber com suas parcelas, o cálculo do ISS e as transições de situação documental.

Documentos relacionados: [[03-der]] (modelo entidade-relacionamento), [[07-dicionario-de-dados]] (definição de cada campo e domínio), [[04-requisitos-funcionais]] (requisitos funcionais correspondentes) e [[10-requisitos-nao-funcionais]] (transação, integridade e desempenho).

---

## Convenções aplicáveis a todos os processos

As convenções abaixo valem para os três processos e não são repetidas em cada um:

- **Atomicidade transacional.** Toda confirmação ou emissão que produza efeitos colaterais (baixa/entrada de estoque, geração de contas e parcelas, persistência da nota) é executada dentro de uma **única transação de banco**. Em caso de falha em qualquer passo, a transação inteira sofre *rollback* e nenhum efeito é persistido, preservando a consistência entre nota, estoque e financeiro.
- **Imutabilidade após o efeito fiscal.** Uma nota nas situações `CONFIRMADA` (entrada/saída) ou `EMITIDA` (serviço) é considerada documento fiscal consolidado: seus itens e valores não podem ser editados. Alterações exigem cancelamento e nova emissão.
- **Numeração.** A combinação que identifica a nota é única no banco: `(numero, serie, fornecedor_id)` para entrada, `(numero, serie, cliente_id)` para saída e `(numero, serie)` para serviço. Tentativas de duplicar essa combinação são rejeitadas.
- **Valores monetários** seguem `NUMERIC(15,2)`; quantidades de produto seguem `NUMERIC(15,3)`, admitindo fração para itens vendidos por peso/volume (KG, L).
- **Domínio de situação.** Entrada/saída: `PENDENTE`, `CONFIRMADA`, `CANCELADA`. Serviço: `PENDENTE`, `EMITIDA`, `CANCELADA`. Contas (pagar/receber): `PENDENTE`, `PARCIAL`, `PAGA`/`RECEBIDA`, `CANCELADA`.

---

## UC401 — Processo 1 — Registrar Compra (Nota de Entrada)

## Objetivo

Registrar a compra de mercadorias para a loja de conveniência junto a um fornecedor, controlando o documento fiscal de entrada (`nota_entrada`) e seus itens (`item_nota_entrada`). Ao **confirmar** a nota, o sistema deve incorporar as quantidades adquiridas ao estoque dos produtos e provisionar o passivo financeiro correspondente, gerando as parcelas de contas a pagar (`conta_pagar`) conforme a condição de pagamento acordada. O processo garante que o estoque físico e o contas a pagar reflitam fielmente a entrada de mercadoria.

## Regras de Negócio

- **RN101 — Cabeçalho obrigatório.** Toda nota de entrada exige `numero`, `serie`, `modelo` e um `fornecedor` previamente cadastrado e ativo. `modelo` e `serie` assumem `'55'` e `'1'` por padrão quando não informados.
- **RN102 — Unicidade do documento.** Não é permitido registrar duas notas com o mesmo trio `(numero, serie, fornecedor_id)`. A tentativa é rejeitada pela restrição de unicidade `uq_nota_entrada`.
- **RN103 — Nota não vazia para confirmação.** Uma nota só pode ser confirmada se possuir ao menos um item. A persistência de cada item exige `quantidade > 0` e `valor_unitario >= 0`.
- **RN104 — Sequência do item.** A `sequencia` de cada item é única dentro da nota (`uq_item_entrada_seq`), atribuída de forma incremental (1, 2, 3, …) na ordem de inclusão.
- **RN105 — Cálculo do total do item.** Para cada item, `valor_total = (quantidade × valor_unitario) − valor_desconto`. Quando informado `percentual_desconto`, o `valor_desconto` é derivado dele sobre o bruto. O `percentual_desconto` é limitado ao intervalo de 0 a 100.
- **RN106 — Rateio de despesas acessórias.** Os valores de `valor_frete`, `valor_seguro` e `outras_despesas` do cabeçalho são rateados entre os itens proporcionalmente ao `valor_total` de cada um, gravando `rateio_frete`, `rateio_seguro` e `rateio_outras` em cada item.
- **RN107 — Custo final do item.** O `custo_preco_final` de cada item é o custo unitário real após o rateio: `custo_preco_final = (valor_total + rateio_frete + rateio_seguro + rateio_outras) ÷ quantidade`. Este custo é o usado para atualizar o `valor_compra` do produto.
- **RN108 — Total da nota.** `valor_total = valor_produtos + valor_frete + valor_seguro + outras_despesas − valor_desconto`, onde `valor_produtos` é o somatório dos `valor_total` dos itens. Todos os valores são restringidos a não-negativos.
- **RN109 — Efeito de estoque somente na confirmação.** A entrada de estoque ocorre **exclusivamente** na transição `PENDENTE → CONFIRMADA`. Enquanto `PENDENTE`, a nota não altera estoque nem gera financeiro.
- **RN110 — Soma ao estoque.** Ao confirmar, para cada item, `produto.quantidade` é incrementado em `item.quantidade`. O produto também tem seu `valor_compra` atualizado pelo `custo_preco_final` do item, e o `valor_venda`/`percentual_lucro` recalculados conforme a margem-alvo cadastrada (quando aplicável).
- **RN111 — Geração de contas a pagar.** Ao confirmar, é gerado o passivo financeiro a partir do `valor_total` da nota e da `condicao_pagamento`. Quando a condição define múltiplas parcelas, são criados N registros em `conta_pagar`, um por parcela, com `numero_parcela`/`total_parcelas` preenchidos. O par `(nota_entrada_id, numero_parcela)` é único (`uq_conta_pagar_parcela`).
- **RN112 — Datas e valores das parcelas.** Cada parcela nasce com `data_emissao` igual à data de geração (`CURRENT_DATE`, padrão da coluna) e `data_vencimento = data_emissao + dias` (a partir de `parcela_condicao_pagamento.dias` ou de `dias_primeira_parcela`/`dias_entre_parcelas`, com `dias >= 0`), satisfazendo `ck_conta_pagar_vencimento`; à vista (`dias = 0`), `data_vencimento = data_emissao`. O `valor_total` da parcela — coluna `NOT NULL` sem default — é definido obrigatoriamente como o rateio do `valor_total` da nota pelo `percentual` da parcela na condição de pagamento; `valor_original` recebe o mesmo rateio. O somatório dos `valor_total` das parcelas é igual ao `valor_total` da nota; eventual diferença de centavos por arredondamento é ajustada na última parcela. Cada parcela nasce com `situacao = 'PENDENTE'`, `valor_pago = 0` e `data_pagamento` nula — o que satisfaz `ck_conta_pagar_pago_teto` (`valor_pago <= valor_total`).
- **RN113 — Condição ausente.** Se a nota não tiver `condicao_pagamento`, gera-se uma única parcela à vista (`total_parcelas = 1`), com vencimento igual à `data_emissao`.
- **RN114 — Cancelamento.** Uma nota `PENDENTE` pode ser cancelada sem efeitos colaterais. O cancelamento de nota já `CONFIRMADA` é estorno: reverte o estoque (subtrai as quantidades) e cancela as parcelas de `conta_pagar` ainda não pagas; só é permitido se nenhuma parcela vinculada estiver `PAGA` ou `PARCIAL`. *(Regra de aplicação, garantida na camada de serviço; sem constraint correspondente no banco.)*
- **RN115 — Coerência data de chegada.** Quando informada, `data_chegada` não pode ser anterior à `data_emissao` (`ck_nota_entrada_chegada`).

## Atores

- **Ator principal:** Operador de Estoque/Compras (perfil `OPERADOR` ou `ADMIN`), responsável por lançar a nota recebida do fornecedor.
- **Atores de apoio (entidades):** Fornecedor, Produto, Condição de Pagamento, Forma de Pagamento.
- **Sistemas impactados:** módulo de Estoque (`produto.quantidade`) e módulo Financeiro (`conta_pagar`).

## Pré-Condição

1. Existe ao menos um **fornecedor** ativo cadastrado.
2. Todos os **produtos** dos itens estão previamente cadastrados e ativos.
3. A **condição de pagamento** (quando utilizada) está cadastrada e, se parcelada, possui suas parcelas definidas em `parcela_condicao_pagamento`.
4. O operador está autenticado e autorizado.

## Pós-Condição

**Em caso de sucesso (confirmação):**
1. A `nota_entrada` está persistida com `situacao = 'CONFIRMADA'` e `valor_total` calculado.
2. A `quantidade` em estoque de cada produto da nota foi **incrementada**.
3. Foram criados os registros em `conta_pagar` (uma ou mais parcelas), todos com `situacao = 'PENDENTE'`, cuja soma de `valor_original` equivale ao `valor_total` da nota.

**Em caso de falha:** nenhuma alteração é persistida (rollback total); estoque e financeiro permanecem inalterados.

## Ativação

O processo é ativado quando o operador seleciona "Registrar Compra / Nota de Entrada" e, após preencher cabeçalho e itens, aciona a ação **Confirmar**. A simples gravação como rascunho mantém a nota em `PENDENTE` sem efeitos.

## Fluxo Principal

1. O operador abre o formulário de nota de entrada e informa o **fornecedor**, o `numero`, a `serie`, o `modelo`, a `data_emissao` (padrão: data atual), o `tipo_frete` (`CIF`/`FOB`/`SEM`) e, opcionalmente, a `condicao_pagamento` e a `data_chegada`.
2. O sistema valida a unicidade do trio `(numero, serie, fornecedor_id)`. Se já existir, dispara **E1**.
3. O operador adiciona os **itens**: para cada um, seleciona o `produto`, informa `quantidade` e `valor_unitario` e, opcionalmente, `valor_desconto`/`percentual_desconto`. O sistema atribui a `sequencia`, valida `quantidade > 0` (senão **E3**) e calcula `valor_total` do item (RN105).
4. O operador informa, no cabeçalho, `valor_frete`, `valor_seguro`, `outras_despesas` e `valor_desconto` da nota (todos opcionais, padrão zero).
5. O sistema consolida `valor_produtos` (somatório dos itens) e calcula `valor_total` da nota (RN108).
6. O operador aciona **Confirmar**. O sistema abre uma transação e revalida que a nota possui ao menos um item (senão **E2**) e que a situação atual é `PENDENTE` (senão **E5**).
7. O sistema executa o **rateio** das despesas acessórias entre os itens (RN106) e calcula o `custo_preco_final` de cada item (RN107).
8. Para cada item, o sistema **incrementa** `produto.quantidade` em `item.quantidade` e atualiza o `valor_compra` do produto pelo `custo_preco_final`, recalculando `valor_venda`/`percentual_lucro` conforme a margem cadastrada.
9. O sistema **gera o contas a pagar** (RN111–RN113): resolve o número de parcelas pela `condicao_pagamento`; para cada parcela, calcula `data_vencimento` e `valor_original`, ajustando centavos residuais na última parcela; insere os registros em `conta_pagar` com `situacao = 'PENDENTE'`, vinculados à nota e ao fornecedor.
10. O sistema atualiza a `nota_entrada` para `situacao = 'CONFIRMADA'` e **confirma a transação** (commit).
11. O sistema exibe a mensagem **"Nota de entrada confirmada. Estoque atualizado e {N} parcela(s) a pagar gerada(s)."** e retorna o identificador da nota.

## Fluxos de Exceção

- **E1 — Nota duplicada.** No passo 2, já existe nota com o mesmo `(numero, serie, fornecedor_id)`. Mensagem: **"Já existe uma nota de entrada com este número, série e fornecedor."** O processo é interrompido.
- **E2 — Nota sem itens.** No passo 6, a nota não possui itens. Mensagem: **"Inclua ao menos um item antes de confirmar a nota de entrada."** A confirmação é abortada (rollback).
- **E3 — Quantidade inválida.** No passo 3, `quantidade` é menor ou igual a zero. Mensagem: **"A quantidade do item deve ser maior que zero."** O item não é gravado.
- **E4 — Fornecedor inexistente ou inativo.** No passo 1/6, o fornecedor informado não existe ou está inativo. Mensagem: **"Fornecedor inválido ou inativo. Selecione um fornecedor cadastrado."** O processo é interrompido.
- **E5 — Situação não permite confirmação.** No passo 6, a nota já está `CONFIRMADA` ou `CANCELADA`. Mensagem: **"Esta nota já foi confirmada ou cancelada e não pode ser confirmada novamente."** Nenhuma alteração ocorre.
- **E6 — Falha na geração financeira.** No passo 9, a soma das parcelas não fecha com o `valor_total` ou a condição de pagamento é inconsistente. Mensagem: **"Não foi possível gerar as parcelas: a soma das parcelas não confere com o total da nota."** A transação inteira sofre rollback (estoque não é alterado).
- **E7 — Produto inexistente.** No passo 3/8, o produto referenciado não existe. Mensagem: **"Produto não encontrado para o item informado."** O processo é interrompido (rollback).

---

## UC402 — Processo 2 — Registrar Venda (Nota de Saída)

## Objetivo

Registrar a venda de mercadorias da conveniência a um cliente, controlando o documento fiscal de saída (`nota_saida`) e seus itens (`item_nota_saida`). Ao **confirmar** a nota, o sistema deve **baixar** as quantidades vendidas do estoque dos produtos e provisionar o ativo financeiro correspondente, gerando as parcelas de contas a receber (`conta_receber`) conforme a condição de pagamento. O processo impede a venda de mercadoria sem saldo de estoque suficiente e mantém o financeiro a receber sincronizado com a saída de mercadoria.

## Regras de Negócio

- **RN201 — Cabeçalho obrigatório.** Toda nota de saída exige `numero`, `serie`, `modelo` e um `cliente` previamente cadastrado e ativo. `modelo` e `serie` assumem `'55'` e `'1'` por padrão.
- **RN202 — Unicidade do documento.** Não é permitido registrar duas notas com o mesmo trio `(numero, serie, cliente_id)` (`uq_nota_saida`).
- **RN203 — Nota não vazia para confirmação.** A confirmação exige ao menos um item, cada um com `quantidade > 0` e `valor_unitario >= 0`.
- **RN204 — Sequência do item.** A `sequencia` é única na nota (`uq_item_saida_seq`), atribuída incrementalmente.
- **RN205 — Cálculo do total do item.** `valor_total = (quantidade × valor_unitario) − valor_desconto`; `percentual_desconto` limitado a 0–100.
- **RN206 — Rateio e total da nota.** `valor_frete`, `valor_seguro` e `outras_despesas` são rateados entre os itens; `valor_produtos` é o somatório dos itens; `valor_total = valor_produtos + valor_frete + valor_seguro + outras_despesas − valor_desconto`.
- **RN207 — Saldo de estoque obrigatório.** Na confirmação, para cada item, `produto.quantidade` deve ser **maior ou igual** à `item.quantidade`. Responsabilidades separadas: o **bloqueio** efetivo da venda por estoque insuficiente é uma validação *fail-fast* do serviço, que confere o saldo de cada item **antes** de qualquer baixa e aborta a transação ao detectar saldo inferior ao solicitado (ver **E4**). A restrição `ck_produto_valores` (que inclui `quantidade >= 0`) é apenas uma **guarda de integridade** do banco: garante que o estoque jamais fique negativo, servindo de última linha de defesa caso a validação de serviço falhe — não é o mecanismo que valida o saldo da venda.
- **RN208 — Efeito de estoque somente na confirmação.** A baixa de estoque ocorre **exclusivamente** na transição `PENDENTE → CONFIRMADA`. Enquanto `PENDENTE`, a nota não altera estoque nem gera financeiro.
- **RN209 — Baixa do estoque.** Ao confirmar, para cada item, `produto.quantidade` é **decrementado** em `item.quantidade`.
- **RN210 — Geração de contas a receber.** Ao confirmar, é gerado o ativo financeiro a partir do `valor_total` e da `condicao_pagamento`, criando N registros em `conta_receber` (um por parcela), com `numero_parcela`/`total_parcelas`. O par `(nota_saida_id, numero_parcela)` é único (`uq_conta_receber_parcela`).
- **RN211 — Datas e valores das parcelas.** Cada parcela nasce com `data_emissao` igual à data de geração (`CURRENT_DATE`, padrão da coluna) e `data_vencimento = data_emissao + dias` (`dias >= 0`), satisfazendo `ck_conta_receber_vencimento`; à vista (`dias = 0`), `data_vencimento = data_emissao`. O `valor_total` da parcela — coluna `NOT NULL` sem default — é definido obrigatoriamente como o rateio do `valor_total` da nota pelo `percentual` da parcela na condição de pagamento; `valor_original` recebe o mesmo rateio. A soma dos `valor_total` das parcelas equivale ao `valor_total` da nota, com ajuste de centavos na última parcela. Cada parcela nasce `PENDENTE`, `valor_recebido = 0`, `data_recebimento` nula — o que satisfaz `ck_conta_receber_recebido_teto` (`valor_recebido <= valor_total`).
- **RN212 — Condição ausente.** Sem `condicao_pagamento`, gera-se parcela única à vista (`total_parcelas = 1`), vencimento igual à `data_emissao`.
- **RN213 — Cancelamento.** Nota `PENDENTE` é cancelada sem efeito. Cancelamento de nota `CONFIRMADA` é estorno: devolve as quantidades ao estoque e cancela as parcelas de `conta_receber` ainda não recebidas; permitido apenas se nenhuma parcela estiver `RECEBIDA` ou `PARCIAL`. *(Regra de aplicação, garantida na camada de serviço; sem constraint correspondente no banco.)*
- **RN214 — Coerência data de saída.** Quando informada, `data_saida` não pode ser anterior à `data_emissao` (`ck_nota_saida_data`).

## Atores

- **Ator principal:** Operador de Caixa/Vendas (perfil `OPERADOR` ou `ADMIN`).
- **Atores de apoio (entidades):** Cliente, Produto, Condição de Pagamento, Forma de Pagamento.
- **Sistemas impactados:** módulo de Estoque (`produto.quantidade`) e módulo Financeiro (`conta_receber`).

## Pré-Condição

1. Existe ao menos um **cliente** ativo cadastrado.
2. Os **produtos** dos itens estão cadastrados, ativos e com **saldo de estoque suficiente** para a venda.
3. A **condição de pagamento** (quando utilizada) está cadastrada e suas parcelas definidas.
4. O operador está autenticado e autorizado.

## Pós-Condição

**Em caso de sucesso (confirmação):**
1. A `nota_saida` está persistida com `situacao = 'CONFIRMADA'` e `valor_total` calculado.
2. A `quantidade` em estoque de cada produto foi **decrementada**.
3. Foram criados os registros em `conta_receber` (uma ou mais parcelas), todos `PENDENTE`, cuja soma de `valor_original` equivale ao `valor_total` da nota.

**Em caso de falha:** rollback total; estoque e financeiro permanecem inalterados.

## Ativação

O processo é ativado quando o operador seleciona "Registrar Venda / Nota de Saída" e, após preencher cabeçalho e itens, aciona **Confirmar**. A gravação como rascunho mantém a nota em `PENDENTE`.

## Fluxo Principal

1. O operador abre o formulário de nota de saída e informa o **cliente**, o `numero`, a `serie`, o `modelo`, a `data_emissao` (padrão: atual), o `tipo_frete` e, opcionalmente, a `condicao_pagamento`.
2. O sistema valida a unicidade do trio `(numero, serie, cliente_id)`. Se já existir, dispara **E1**.
3. O operador adiciona os **itens**: para cada um, seleciona o `produto`, informa `quantidade` e `valor_unitario` e, opcionalmente, desconto. O sistema atribui a `sequencia`, valida `quantidade > 0` (senão **E3**) e calcula o `valor_total` do item (RN205).
4. O operador informa os valores acessórios do cabeçalho (frete, seguro, outras despesas, desconto).
5. O sistema consolida `valor_produtos` e calcula `valor_total` da nota (RN206).
6. O operador aciona **Confirmar**. O sistema abre uma transação e revalida a existência de itens (senão **E2**) e que a situação é `PENDENTE` (senão **E5**).
7. O sistema **verifica o saldo de estoque** de cada item: se `produto.quantidade < item.quantidade` para qualquer item, dispara **E4** e aborta antes de qualquer baixa (RN207).
8. Com saldo confirmado, o sistema **decrementa** `produto.quantidade` em `item.quantidade` para cada item (RN209).
9. O sistema **gera o contas a receber** (RN210–RN212): resolve as parcelas pela `condicao_pagamento`; para cada parcela calcula `data_vencimento` e `valor_original` (ajuste de centavos na última); insere os registros em `conta_receber` com `situacao = 'PENDENTE'`, vinculados à nota e ao cliente.
10. O sistema atualiza a `nota_saida` para `situacao = 'CONFIRMADA'` e **confirma a transação**.
11. O sistema exibe **"Nota de saída confirmada. Estoque baixado e {N} parcela(s) a receber gerada(s)."** e retorna o identificador da nota.

## Fluxos de Exceção

- **E1 — Nota duplicada.** No passo 2, já existe nota com o mesmo `(numero, serie, cliente_id)`. Mensagem: **"Já existe uma nota de saída com este número, série e cliente."** O processo é interrompido.
- **E2 — Nota sem itens.** No passo 6, a nota não possui itens. Mensagem: **"Inclua ao menos um item antes de confirmar a nota de saída."** Confirmação abortada (rollback).
- **E3 — Quantidade inválida.** No passo 3, `quantidade <= 0`. Mensagem: **"A quantidade do item deve ser maior que zero."** O item não é gravado.
- **E4 — Estoque insuficiente.** No passo 7, algum produto não possui saldo. Mensagem: **"Estoque insuficiente para o produto '{nome}'. Saldo disponível: {saldo}, solicitado: {quantidade}."** A confirmação é abortada (rollback total, nenhum item é baixado).
- **E5 — Situação não permite confirmação.** No passo 6, a nota já está `CONFIRMADA` ou `CANCELADA`. Mensagem: **"Esta nota já foi confirmada ou cancelada e não pode ser confirmada novamente."**
- **E6 — Cliente inexistente ou inativo.** No passo 1/6, o cliente não existe ou está inativo. Mensagem: **"Cliente inválido ou inativo. Selecione um cliente cadastrado."** O processo é interrompido.
- **E7 — Falha na geração financeira.** No passo 9, a soma das parcelas não fecha com o `valor_total` ou a condição é inconsistente. Mensagem: **"Não foi possível gerar as parcelas: a soma das parcelas não confere com o total da nota."** Rollback total (estoque já decrementado é revertido).

---

## UC403 — Processo 3 — Emitir Nota de Serviço (NFS-e)

## Objetivo

Emitir a nota fiscal de serviço eletrônica (NFS-e) referente aos serviços prestados pelo estacionamento — a cobrança de uma estadia avulsa (originada de uma `movimentacao` de pátio) **ou** a mensalidade de um plano (originada de um `mensalista`) — bem como serviços correlatos como lavagem. O processo calcula o **ISS** (Imposto Sobre Serviços) sobre o valor do serviço, registra o documento em `nota_servico` e o transiciona para `EMITIDA`. Diferentemente das notas de mercadoria, a nota de serviço **não movimenta estoque**; seu efeito fiscal é o registro do imposto devido e a formalização da cobrança ao cliente.

## Regras de Negócio

- **RN301 — Cabeçalho obrigatório.** Toda nota de serviço exige `numero`, `serie`, `modelo` e um `cliente` cadastrado. `modelo` e `serie` assumem `'55'` e `'1'` por padrão.
- **RN302 — Unicidade do documento.** O par `(numero, serie)` é único (`uq_nota_servico`). Tentativa de duplicação é rejeitada.
- **RN303 — Origem exclusiva (XOR).** A nota pode referenciar **no máximo uma** origem: ou uma `movimentacao` (estadia avulsa) ou um `mensalista` (mensalidade), nunca ambas simultaneamente. A restrição `ck_nota_servico_origem` (`NOT (movimentacao_id IS NOT NULL AND mensalista_id IS NOT NULL)`) garante a exclusão mútua. É admitida nota sem origem vinculada (serviço avulso genérico, ex.: lavagem), referenciando apenas o `servico`.
- **RN304 — Valor do serviço.** O `valor_servico` é a base de cálculo. Quando a origem é uma `movimentacao`, sugere-se `movimentacao.valor_cobrado`; quando é um `mensalista`, sugere-se `mensalista.valor_mensal`; para serviço avulso, usa-se o `servico.valor_padrao`. O valor é editável pelo operador, respeitando `valor_servico >= 0`.
- **RN305 — Cálculo do ISS.** `valor_iss = valor_servico × (aliquota_iss ÷ 100)`. A `aliquota_iss` é limitada ao intervalo de 0 a 100 (`ck_nota_servico_aliquota`).
- **RN306 — ISS não excede a base.** O `valor_iss` nunca pode ser maior que o `valor_servico` (`ck_nota_servico_iss_base`).
- **RN307 — Coerência alíquota × ISS.** Se a `aliquota_iss` for zero, o `valor_iss` deve ser zero (`ck_nota_servico_iss_coerencia`: `aliquota_iss > 0 OR valor_iss = 0`). Serviço isento não gera imposto.
- **RN308 — Desconto.** O `valor_desconto` não pode exceder o `valor_servico` (`ck_nota_servico_desconto`).
- **RN309 — Total da nota.** O `valor_total` é o valor líquido a cobrar do cliente: `valor_total = valor_servico − valor_desconto`. O ISS é tributo destacado e **não é somado** ao total a cobrar (é retido/devido sobre a base, conforme prática de NFS-e). *(Regra de aplicação: a fórmula de composição do total é calculada na camada de serviço; o banco não possui constraint que vincule `valor_total` a `valor_servico − valor_desconto`.)*
- **RN310 — Situação e emissão.** A nota nasce `PENDENTE`. A ação **Emitir** transiciona para `EMITIDA`. Somente notas `PENDENTE` podem ser emitidas. A `data_emissao` assume a data atual quando não informada.
- **RN311 — Geração financeira opcional.** Quando informada `condicao_pagamento`/`forma_pagamento`, a emissão pode provisionar a cobrança em `conta_receber` vinculada ao cliente (e ao `mensalista`, quando a origem for mensalidade), seguindo as mesmas regras de parcelamento dos Processos 1 e 2. Para mensalidade, a parcela é vinculada via `conta_receber.mensalista_id`, respeitando a regra de origem exclusiva do contas a receber (`ck_conta_receber_origem`).
- **RN312 — Cancelamento.** Nota `EMITIDA` pode ser cancelada (`CANCELADA`), o que cancela eventuais parcelas geradas ainda não recebidas. Nota já `CANCELADA` não admite nova transição.
- **RN313 — Imutabilidade.** Após `EMITIDA`, `valor_servico`, `aliquota_iss`, `valor_iss` e `valor_desconto` não podem ser alterados.

## Atores

- **Ator principal:** Operador/Caixa (perfil `OPERADOR` ou `ADMIN`) ou rotina de fechamento (mensalidade).
- **Atores de apoio (entidades):** Cliente, Serviço, Movimentação, Mensalista, Condição de Pagamento, Forma de Pagamento.
- **Sistema impactado:** módulo Fiscal/Tributário (registro do ISS) e, opcionalmente, Financeiro (`conta_receber`). **Não impacta estoque.**

## Pré-Condição

1. Existe um **cliente** cadastrado para o tomador do serviço.
2. Quando a nota se origina de estadia, existe uma `movimentacao` correspondente (preferencialmente `FECHADO`, com `valor_cobrado` preenchido); quando se origina de mensalidade, existe um `mensalista` ativo.
3. A **alíquota de ISS** do município/serviço é conhecida.
4. O operador está autenticado e autorizado.

## Pós-Condição

**Em caso de sucesso (emissão):**
1. A `nota_servico` está persistida com `situacao = 'EMITIDA'`, `valor_iss` e `valor_total` calculados e a origem (`movimentacao_id` **ou** `mensalista_id`) corretamente vinculada — nunca ambas.
2. Se houver condição de pagamento, as parcelas de `conta_receber` foram geradas, vinculadas ao cliente (e ao mensalista, quando aplicável).
3. O estoque permanece **inalterado**.

**Em caso de falha:** rollback total; nenhuma nota ou parcela é persistida.

## Ativação

O processo é ativado quando o operador seleciona "Emitir Nota de Serviço", escolhe a origem (uma estadia, uma mensalidade ou serviço avulso) e aciona **Emitir**.

## Fluxo Principal

1. O operador abre o formulário de nota de serviço e seleciona o **cliente** e o `servico`.
2. O operador define a **origem** do serviço: vincula **uma** `movimentacao` (estadia avulsa) **ou** **um** `mensalista` (mensalidade), **ou** deixa ambos vazios para serviço avulso genérico. O sistema impede a seleção simultânea das duas origens; se ocorrer, dispara **E2**.
3. O sistema sugere o `valor_servico` conforme a origem (RN304); o operador pode ajustá-lo respeitando `>= 0`.
4. O operador informa a `aliquota_iss` e, opcionalmente, `valor_desconto`, `condicao_pagamento` e `forma_pagamento`. A `data_emissao` assume a data atual.
5. O sistema calcula `valor_iss = valor_servico × (aliquota_iss ÷ 100)` (RN305) e valida: ISS não excede a base (senão **E4**) e coerência alíquota×ISS (RN307). Valida também `valor_desconto <= valor_servico` (senão **E5**).
6. O sistema calcula `valor_total = valor_servico − valor_desconto` (RN309).
7. O operador aciona **Emitir**. O sistema abre uma transação, valida a unicidade de `(numero, serie)` (senão **E1**) e que a situação atual é `PENDENTE` (senão **E3**).
8. Se houver `condicao_pagamento`, o sistema **gera as parcelas** em `conta_receber` (RN311), vinculando-as ao cliente e, quando a origem for mensalidade, ao `mensalista`.
9. O sistema atualiza a `nota_servico` para `situacao = 'EMITIDA'`, persistindo `valor_iss` e `valor_total`, e **confirma a transação**.
10. O sistema exibe **"Nota de serviço emitida. ISS de R$ {valor_iss} sobre base de R$ {valor_servico}."** e retorna o identificador da nota.

## Fluxos de Exceção

- **E1 — Nota duplicada.** No passo 7, já existe nota com o mesmo `(numero, serie)`. Mensagem: **"Já existe uma nota de serviço com este número e série."** O processo é interrompido.
- **E2 — Origem dupla.** No passo 2, foram vinculadas simultaneamente uma movimentação e um mensalista. Mensagem: **"A nota de serviço deve ter uma única origem: estadia (movimentação) OU mensalidade, não ambas."** A emissão é bloqueada.
- **E3 — Situação não permite emissão.** No passo 7, a nota já está `EMITIDA` ou `CANCELADA`. Mensagem: **"Esta nota de serviço já foi emitida ou cancelada e não pode ser emitida novamente."**
- **E4 — ISS maior que a base.** No passo 5, o `valor_iss` calculado/informado excede o `valor_servico`. Mensagem: **"O valor do ISS não pode ser maior que o valor do serviço."** A emissão é bloqueada.
- **E5 — Desconto maior que a base.** No passo 5, `valor_desconto > valor_servico`. Mensagem: **"O desconto não pode ser maior que o valor do serviço."** A emissão é bloqueada.
- **E6 — Incoerência de isenção.** No passo 5, `aliquota_iss = 0` com `valor_iss > 0`. A restrição real é `ck_nota_servico_iss_coerencia` (`aliquota_iss > 0 OR valor_iss = 0`): se `valor_iss > 0`, então `aliquota_iss > 0`; é permitido `aliquota_iss > 0` com `valor_iss = 0` (alíquota definida sem imposto apurado). Mensagem: **"Alíquota de ISS zerada exige valor de ISS igual a zero."** A emissão é bloqueada (`ck_nota_servico_iss_coerencia`).
- **E7 — Cliente inexistente.** No passo 1, o cliente não existe. Mensagem: **"Cliente não encontrado para a nota de serviço."** O processo é interrompido.
- **E8 — Origem inexistente.** No passo 2, a `movimentacao` ou o `mensalista` selecionado não existe. Mensagem: **"A origem informada (estadia ou mensalidade) não foi encontrada."** A emissão é bloqueada.

---

## Histórico

| Data | Ação |
|------|------|
| 2026-06-14 | Criação da Seção 5 — Processos Fiscais (Registrar Compra, Registrar Venda, Emitir Nota de Serviço), aterrada em `database/schema.sql`. |
