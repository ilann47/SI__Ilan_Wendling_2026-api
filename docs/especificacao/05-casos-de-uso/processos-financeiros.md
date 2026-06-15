> Links: [[README]] · [[04-requisitos-funcionais]]

# 5. Processos — Módulo Financeiro

Esta seção especifica os processos de negócio do módulo financeiro do **Estacionamento Kaneko**, responsável pelo controle de obrigações com fornecedores (contas a pagar) e de direitos sobre clientes (contas a receber), bem como pelo registro de despesas operacionais sem nota fiscal. Cada processo é descrito sob a ótica do comportamento esperado da camada de serviço (services) da aplicação, ancorado nas tabelas, restrições (`CHECK`), chaves estrangeiras e índices definidos no schema do banco `estacionamento_kaneko`.

Três processos compõem esta seção: **Gerenciar Conta a Pagar** (baixa de parcelas originadas de `nota_entrada`), **Gerenciar Conta a Receber** (baixa de parcelas originadas de `nota_saida` ou de contrato de `mensalista`) e **Lançar Despesa Avulsa** (registro de despesa sem nota em `contas_pagar_avulsa`). Os três compartilham o mesmo ciclo de vida de baixa — **PENDENTE → PARCIAL → PAGA/RECEBIDA** — e as mesmas invariantes de integridade financeira garantidas no banco.

> **Convenções de ciclo de vida (válidas para os três processos)**
> - **PENDENTE**: parcela gerada, ainda sem qualquer baixa. `valor_pago`/`valor_recebido = 0` e a data de baixa (`data_pagamento`/`data_recebimento`) é nula.
> - **PARCIAL**: houve baixa parcial — o valor amortizado é maior que zero, porém inferior ao `valor_total`. A data de baixa permanece nula.
> - **PAGA / RECEBIDA**: o valor amortizado igualou o `valor_total`. A data de baixa torna-se **obrigatória** (restrições `ck_*_baixa`).
> - **CANCELADA**: parcela invalidada administrativamente; não recebe novas baixas. A data de baixa permanece nula.
>
> A restrição `ck_*_pago_teto` / `ck_*_recebido_teto` garante, no próprio banco, que **o valor amortizado nunca ultrapasse o `valor_total`** (o requisito "valor_pago ≤ valor_total" é, portanto, uma invariante de banco, não apenas de aplicação).

---

## 5.1 Processo: UC501 — Gerenciar Conta a Pagar

### Objetivo

Permitir o registro e a baixa de obrigações financeiras do estabelecimento perante seus fornecedores, representadas por parcelas na tabela `conta_pagar` e originadas, em regra, da confirmação de uma `nota_entrada`. O processo conduz cada parcela ao longo do ciclo **PENDENTE → PARCIAL → PAGA**, calculando juros, multa e desconto no momento da baixa, registrando a `data_pagamento` na quitação e mantendo a `situacao` coerente com os valores efetivamente amortizados.

### Regras de Negócio

| Código | Regra |
|--------|-------|
| RN001 | Toda conta a pagar pertence obrigatoriamente a um `fornecedor` (FK `fornecedor_id` NOT NULL). A `nota_entrada` de origem é opcional (`nota_entrada_id` pode ser nulo, para obrigações lançadas sem vínculo a nota). |
| RN002 | A geração de parcelas a partir de uma `nota_entrada` é disparada **na confirmação da nota** (situação `CONFIRMADA`). A condição de pagamento (`condicao_pagamento`) e suas `parcela_condicao_pagamento` determinam o número de parcelas, os dias de vencimento e o percentual de cada parcela sobre o `valor_total` da nota. |
| RN003 | Para uma mesma nota, a combinação (`nota_entrada_id`, `numero_parcela`) é única (índice `uq_conta_pagar_parcela`, aplicado apenas quando `nota_entrada_id` não é nulo). Não é possível gerar a parcela 2 duas vezes para a mesma nota. |
| RN004 | O `numero_parcela` deve ser maior ou igual a 1 e menor ou igual ao `total_parcelas` (restrição `ck_conta_pagar_parcela`). |
| RN005 | A composição `valor_total = valor_original − valor_desconto + valor_juros + valor_multa` é **regra de aplicação**: a igualdade é calculada e aplicada pela camada de serviço, não imposta pelo banco. O `ck_conta_pagar_valores` garante apenas a **não negatividade** de todos os componentes (`valor_* >= 0`). |
| RN006 | A `data_vencimento` deve ser maior ou igual à `data_emissao` (restrição `ck_conta_pagar_vencimento`). |
| RN007 | O `valor_pago` acumulado **nunca pode exceder** o `valor_total` da parcela (restrição `ck_conta_pagar_pago_teto`). É a invariante que materializa o requisito "valor_pago ≤ valor_total". |
| RN008 | A `situacao` é derivada do valor amortizado: `valor_pago = 0` → **PENDENTE**; `0 < valor_pago < valor_total` → **PARCIAL**; `valor_pago = valor_total` → **PAGA**. O domínio da `situacao` é restrito ao enum {PENDENTE, PARCIAL, PAGA, CANCELADA} pela restrição `ck_conta_pagar_situacao`. **Nota:** qualquer valor fora desse domínio é barrado pelo próprio banco, independentemente da lógica de aplicação. |
| RN009 | Quando a `situacao` é **PAGA**, a `data_pagamento` é obrigatória; quando é **PENDENTE** ou **PARCIAL**, a `data_pagamento` deve permanecer nula; **CANCELADA** dispensa data (restrição `ck_conta_pagar_baixa`). |
| RN010 | A `data_pagamento`, quando informada, deve ser maior ou igual à `data_emissao` (restrição `ck_conta_pagar_data_pagto`). |
| RN011 | Juros e multa são calculados sobre o atraso (vencimento ultrapassado), tomando como base os percentuais da `condicao_pagamento` (`percentual_juros`, `percentual_multa`); o desconto aplica `percentual_desconto` em pagamentos dentro do prazo. O resultado é persistido em `valor_juros`, `valor_multa` e `valor_desconto`, e recompõe o `valor_total` (ver RN005). |
| RN012 | Uma parcela **CANCELADA** ou **PAGA** não admite novas baixas. A baixa só opera sobre parcelas em **PENDENTE** ou **PARCIAL**. |
| RN013 | Não é permitido excluir fisicamente um `fornecedor`, `nota_entrada`, `condicao_pagamento` ou `forma_pagamento` referenciado por uma conta a pagar (todas as FKs com `ON DELETE RESTRICT`). |

### Atores

- **Ator primário:** Gerente ou Proprietário (perfil ADMIN), responsável por registrar a baixa das obrigações.
- **Atores de sistema:** Serviço de Confirmação de Nota de Entrada (gera as parcelas — ver RN002) e Serviço de Baixa de Conta a Pagar (executa a amortização).

### Pré-Condição

- Existe, na tabela `conta_pagar`, ao menos uma parcela em situação **PENDENTE** ou **PARCIAL**.
- O `fornecedor` da parcela está cadastrado e ativo.
- Há uma `forma_pagamento` ativa disponível para registrar o meio da baixa.

### Pós-Condição

- A parcela teve seu `valor_pago` atualizado e a `situacao` recalculada para **PARCIAL** (baixa parcial) ou **PAGA** (quitação).
- Em caso de quitação, a `data_pagamento` foi registrada.
- O campo `updated_at` da parcela foi atualizado.

### Ativação

O processo é ativado:
1. **Automaticamente**, quando uma `nota_entrada` é confirmada — gerando as parcelas em `conta_pagar` (ver [[04-requisitos-funcionais]]).
2. **Manualmente**, quando o usuário seleciona uma parcela em aberto e aciona a ação "Registrar baixa / pagamento".

### Fluxo Principal

1. O usuário acessa a listagem de contas a pagar, filtrando por `situacao` e `data_vencimento` (apoiado pelo índice `ix_conta_pagar_situ_venc`).
2. O usuário seleciona a parcela a quitar e informa: a `data_pagamento`, o `valor_pago` desta baixa e a `forma_pagamento_id`.
3. O serviço **valida** que a parcela está em situação **PENDENTE** ou **PARCIAL** (RN012); caso contrário, segue para o fluxo de exceção E1.
4. O serviço **calcula encargos**: se a `data_pagamento` for posterior à `data_vencimento`, aplica `percentual_juros` e `percentual_multa` da `condicao_pagamento` sobre o `valor_original`, persistindo `valor_juros` e `valor_multa`; se houver `percentual_desconto` aplicável dentro do prazo, persiste `valor_desconto` (RN011).
5. O serviço **recompõe** o `valor_total` da parcela: `valor_total = valor_original − valor_desconto + valor_juros + valor_multa` (RN005).
6. O serviço **valida o teto**: o `valor_pago` acumulado (anterior + baixa informada) não pode exceder o `valor_total` (RN007); caso exceda, segue para o fluxo de exceção E2.
7. O serviço **acumula** o valor: `valor_pago = valor_pago_anterior + valor_da_baixa`.
8. O serviço **deriva a situação** (RN008):
   - se `valor_pago = valor_total` → `situacao = 'PAGA'` e grava a `data_pagamento` informada;
   - se `0 < valor_pago < valor_total` → `situacao = 'PARCIAL'` e mantém `data_pagamento` nula.
9. O serviço **valida a coerência data/situação** (RN009): em quitação exige `data_pagamento` não nula; em baixa parcial exige `data_pagamento` nula. Inconsistência segue para E3.
10. O serviço **persiste** a parcela e atualiza `updated_at`. A transação é confirmada.
11. O sistema apresenta a confirmação da baixa, com o novo saldo devedor (`valor_total − valor_pago`).

### Fluxos de Exceção

- **E1 — Parcela não passível de baixa.** A parcela selecionada não está em situação **PENDENTE** nem **PARCIAL**. O serviço interrompe a operação e exibe: *"Somente parcelas com situação PENDENTE ou PARCIAL podem receber baixa."* Nenhuma alteração é persistida.
- **E2 — Valor da baixa excede o saldo.** O `valor_pago` resultante ultrapassaria o `valor_total` (violaria `ck_conta_pagar_pago_teto`). O serviço rejeita a operação e exibe: *"Valor informado excede o saldo devedor da parcela (R$ {saldo})."* Nenhuma alteração é persistida.
- **E3 — Data de pagamento ausente na quitação.** A baixa quita a parcela (`valor_pago = valor_total`), porém a `data_pagamento` não foi informada (violaria `ck_conta_pagar_baixa`). O serviço rejeita e exibe: *"Informe a data de pagamento para quitar a parcela."*
- **E4 — Data de pagamento anterior à emissão.** A `data_pagamento` informada é anterior à `data_emissao` (violaria `ck_conta_pagar_data_pagto`). O serviço rejeita e exibe: *"A data de pagamento não pode ser anterior à data de emissão da conta."*
- **E5 — Parcela duplicada na geração.** Na geração automática a partir da nota, já existe a parcela (`nota_entrada_id`, `numero_parcela`) (violaria `uq_conta_pagar_parcela`). O serviço aborta a geração e exibe: *"Parcela {numero_parcela} já existe para esta nota de entrada."*

---

## 5.2 Processo: UC502 — Gerenciar Conta a Receber

### Objetivo

Permitir o registro e a baixa de direitos financeiros do estabelecimento sobre seus clientes, representados por parcelas na tabela `conta_receber` e originados da confirmação de uma `nota_saida` (venda de conveniência) **ou** de um contrato de `mensalista` (mensalidade recorrente). O processo conduz cada parcela ao longo do ciclo **PENDENTE → PARCIAL → RECEBIDA**, calcula encargos e desconto na baixa, registra a `data_recebimento` na quitação e mantém a `situacao` coerente com os valores efetivamente recebidos.

### Regras de Negócio

| Código | Regra |
|--------|-------|
| RN014 | Toda conta a receber pertence obrigatoriamente a um `cliente` (FK `cliente_id` NOT NULL). |
| RN015 | A origem da parcela é **exclusiva**: ou está vinculada a uma `nota_saida` (`nota_saida_id`), ou a um contrato de `mensalista` (`mensalista_id`), **nunca aos dois ao mesmo tempo** (restrição `ck_conta_receber_origem`). Ambos podem ser nulos para um recebimento avulso lançado manualmente. |
| RN016 | A geração de parcelas a partir de `nota_saida` é disparada **na confirmação da nota** (`CONFIRMADA`), aplicando a `condicao_pagamento` e suas `parcela_condicao_pagamento`. A geração a partir de `mensalista` ocorre na recorrência mensal da mensalidade. |
| RN017 | Para uma mesma nota de saída, a combinação (`nota_saida_id`, `numero_parcela`) é única (índice `uq_conta_receber_parcela`, aplicado apenas quando `nota_saida_id` não é nulo). |
| RN018 | O `numero_parcela` deve ser maior ou igual a 1 e menor ou igual ao `total_parcelas` (restrição `ck_conta_receber_parcela`). |
| RN019 | A composição `valor_total = valor_original − valor_desconto + valor_juros + valor_multa` é **regra de aplicação**: a igualdade é calculada e aplicada pela camada de serviço, não imposta pelo banco. O `ck_conta_receber_valores` garante apenas a **não negatividade** de todos os componentes (`valor_* >= 0`). |
| RN020 | A `data_vencimento` deve ser maior ou igual à `data_emissao` (restrição `ck_conta_receber_vencimento`). |
| RN021 | O `valor_recebido` acumulado **nunca pode exceder** o `valor_total` da parcela (restrição `ck_conta_receber_recebido_teto`) — invariante do requisito "valor_recebido ≤ valor_total". |
| RN022 | A `situacao` é derivada do valor recebido: `valor_recebido = 0` → **PENDENTE**; `0 < valor_recebido < valor_total` → **PARCIAL**; `valor_recebido = valor_total` → **RECEBIDA**. O domínio da `situacao` é restrito ao enum {PENDENTE, PARCIAL, RECEBIDA, CANCELADA} pela restrição `ck_conta_receber_situacao`. **Nota:** qualquer valor fora desse domínio é barrado pelo próprio banco, independentemente da lógica de aplicação. |
| RN023 | Quando a `situacao` é **RECEBIDA**, a `data_recebimento` é obrigatória; quando é **PENDENTE** ou **PARCIAL**, a `data_recebimento` deve permanecer nula; **CANCELADA** dispensa data (restrição `ck_conta_receber_baixa`). |
| RN024 | A `data_recebimento`, quando informada, deve ser maior ou igual à `data_emissao` (restrição `ck_conta_receber_data_receb`). |
| RN025 | Juros, multa e desconto seguem a mesma lógica das contas a pagar (RN011), com base nos percentuais da `condicao_pagamento`, persistidos em `valor_juros`, `valor_multa` e `valor_desconto`. |
| RN026 | Uma parcela **CANCELADA** ou **RECEBIDA** não admite novas baixas. A baixa só opera sobre parcelas em **PENDENTE** ou **PARCIAL**. |
| RN027 | Não é permitido excluir fisicamente um `cliente`, `nota_saida`, `mensalista`, `condicao_pagamento` ou `forma_pagamento` referenciado por uma conta a receber (todas as FKs com `ON DELETE RESTRICT`). |

### Atores

- **Ator primário:** Caixa ou Gerente (perfis OPERADOR/ADMIN), responsável por registrar o recebimento.
- **Atores de sistema:** Serviço de Confirmação de Nota de Saída e Serviço de Recorrência de Mensalidade (geram as parcelas — ver RN016) e Serviço de Baixa de Conta a Receber (executa a amortização).

### Pré-Condição

- Existe, na tabela `conta_receber`, ao menos uma parcela em situação **PENDENTE** ou **PARCIAL**.
- O `cliente` da parcela está cadastrado e ativo.
- Há uma `forma_pagamento` ativa disponível para registrar o meio da baixa.

### Pós-Condição

- A parcela teve seu `valor_recebido` atualizado e a `situacao` recalculada para **PARCIAL** (recebimento parcial) ou **RECEBIDA** (quitação).
- Em caso de quitação, a `data_recebimento` foi registrada.
- O campo `updated_at` da parcela foi atualizado.

### Ativação

O processo é ativado:
1. **Automaticamente**, quando uma `nota_saida` é confirmada ou quando a recorrência de uma mensalidade gera a parcela do mês (ver [[04-requisitos-funcionais]]).
2. **Manualmente**, quando o usuário seleciona uma parcela em aberto e aciona a ação "Registrar recebimento".

### Fluxo Principal

1. O usuário acessa a listagem de contas a receber, filtrando por `situacao` e `data_vencimento` (apoiado pelo índice `ix_conta_receber_situ_venc`).
2. O usuário seleciona a parcela a baixar e informa: a `data_recebimento`, o `valor_recebido` desta baixa e a `forma_pagamento_id`.
3. O serviço **valida** que a parcela está em situação **PENDENTE** ou **PARCIAL** (RN026); caso contrário, segue para o fluxo de exceção E1.
4. O serviço **calcula encargos**: se a `data_recebimento` for posterior à `data_vencimento`, aplica `percentual_juros` e `percentual_multa` da `condicao_pagamento`, persistindo `valor_juros` e `valor_multa`; aplica `valor_desconto` quando cabível dentro do prazo (RN025).
5. O serviço **recompõe** o `valor_total`: `valor_total = valor_original − valor_desconto + valor_juros + valor_multa` (RN019).
6. O serviço **valida o teto**: o `valor_recebido` acumulado (anterior + baixa informada) não pode exceder o `valor_total` (RN021); caso exceda, segue para o fluxo de exceção E2.
7. O serviço **acumula** o valor: `valor_recebido = valor_recebido_anterior + valor_da_baixa`.
8. O serviço **deriva a situação** (RN022):
   - se `valor_recebido = valor_total` → `situacao = 'RECEBIDA'` e grava a `data_recebimento` informada;
   - se `0 < valor_recebido < valor_total` → `situacao = 'PARCIAL'` e mantém `data_recebimento` nula.
9. O serviço **valida a coerência data/situação** (RN023): em quitação exige `data_recebimento` não nula; em baixa parcial exige `data_recebimento` nula. Inconsistência segue para E3.
10. O serviço **persiste** a parcela e atualiza `updated_at`. A transação é confirmada.
11. O sistema apresenta a confirmação do recebimento, com o novo saldo a receber (`valor_total − valor_recebido`).

### Fluxos de Exceção

- **E1 — Parcela não passível de baixa.** A parcela selecionada não está em situação **PENDENTE** nem **PARCIAL**. O serviço interrompe e exibe: *"Somente parcelas com situação PENDENTE ou PARCIAL podem receber baixa."* Nenhuma alteração é persistida.
- **E2 — Valor do recebimento excede o saldo.** O `valor_recebido` resultante ultrapassaria o `valor_total` (violaria `ck_conta_receber_recebido_teto`). O serviço rejeita e exibe: *"Valor informado excede o saldo a receber da parcela (R$ {saldo})."*
- **E3 — Data de recebimento ausente na quitação.** A baixa quita a parcela (`valor_recebido = valor_total`), mas a `data_recebimento` não foi informada (violaria `ck_conta_receber_baixa`). O serviço rejeita e exibe: *"Informe a data de recebimento para quitar a parcela."*
- **E4 — Data de recebimento anterior à emissão.** A `data_recebimento` é anterior à `data_emissao` (violaria `ck_conta_receber_data_receb`). O serviço rejeita e exibe: *"A data de recebimento não pode ser anterior à data de emissão da conta."*
- **E5 — Origem ambígua na geração.** Na geração da parcela informaram-se simultaneamente `nota_saida_id` e `mensalista_id` (violaria `ck_conta_receber_origem`). O serviço aborta e exibe: *"A conta a receber não pode ter origem em nota de saída e em mensalista ao mesmo tempo."*
- **E6 — Parcela duplicada na geração.** Já existe a parcela (`nota_saida_id`, `numero_parcela`) (violaria `uq_conta_receber_parcela`). O serviço aborta e exibe: *"Parcela {numero_parcela} já existe para esta nota de saída."*

---

## 5.3 Processo: UC503 — Lançar Despesa Avulsa

### Objetivo

Permitir o registro de despesas operacionais do estabelecimento que **não decorrem de uma nota de entrada** — tais como aluguel, energia elétrica, água, salários e demais custos fixos ou eventuais — na tabela `contas_pagar_avulsa`. Cada despesa é registrada como uma ou mais parcelas que percorrem o ciclo **PENDENTE → PARCIAL → PAGA**, com a mesma mecânica de baixa das contas a pagar, porém **dispensando o vínculo a `nota_entrada`** e a uma `condicao_pagamento`.

### Regras de Negócio

| Código | Regra |
|--------|-------|
| RN028 | A despesa avulsa **não possui nota fiscal estruturada**: o `fornecedor_id` é **opcional** (pode ser nulo, para despesas sem fornecedor cadastrado, como contas de consumo). Os campos `numero_nota`, `modelo` e `serie` são meramente informativos e opcionais. |
| RN029 | A `descricao` é **obrigatória** (campo `descricao` NOT NULL) e identifica a natureza da despesa (ex.: "Aluguel — junho/2026", "Energia elétrica"). |
| RN030 | O `valor_total` deve ser **maior ou igual a** `valor_original − valor_desconto` (restrição `ck_contas_pagar_avulsa_total_min`), impedindo que o total fique abaixo do valor líquido de desconto. |
| RN031 | Todos os valores (`valor_original`, `valor_pago`, `valor_desconto`, `valor_juros`, `valor_multa`, `valor_total`) são não negativos (restrição `ck_contas_pagar_avulsa_valores`). |
| RN032 | O `valor_pago` acumulado **nunca pode exceder** o `valor_total` (restrição `ck_contas_pagar_avulsa_pago_teto`). |
| RN033 | O `numero_parcela` deve ser maior ou igual a 1 e menor ou igual ao `total_parcelas` (restrição `ck_contas_pagar_avulsa_parcela`). Despesas parceladas geram múltiplos registros com `numero_parcela` sequencial. |
| RN034 | A `data_vencimento` deve ser maior ou igual à `data_emissao` (restrição `ck_contas_pagar_avulsa_vencimento`). |
| RN035 | A `situacao` é derivada do valor pago: `valor_pago = 0` → **PENDENTE**; `0 < valor_pago < valor_total` → **PARCIAL**; `valor_pago = valor_total` → **PAGA**. O domínio da `situacao` é restrito ao enum {PENDENTE, PARCIAL, PAGA, CANCELADA} pela restrição `ck_contas_pagar_avulsa_situacao`. **Nota:** qualquer valor fora desse domínio é barrado pelo próprio banco, independentemente da lógica de aplicação. |
| RN036 | Quando a `situacao` é **PAGA**, a `data_pagamento` é obrigatória; **PENDENTE**/**PARCIAL** exigem `data_pagamento` nula; **CANCELADA** dispensa data (restrição `ck_contas_pagar_avulsa_baixa`). |
| RN037 | A `data_pagamento`, quando informada, deve ser maior ou igual à `data_emissao` (restrição `ck_contas_pagar_avulsa_data_pagto`). |
| RN038 | Diferentemente da `conta_pagar`, a despesa avulsa **não admite vínculo a `condicao_pagamento`**: o número de parcelas e os vencimentos são informados manualmente pelo usuário no lançamento. |
| RN039 | Não é permitido excluir fisicamente um `fornecedor` ou `forma_pagamento` referenciado por uma despesa avulsa (FKs com `ON DELETE RESTRICT`). |

### Atores

- **Ator primário:** Gerente ou Proprietário (perfil ADMIN), responsável por lançar e baixar as despesas operacionais.
- **Atores de sistema:** Serviço de Despesa Avulsa (persiste e baixa os registros em `contas_pagar_avulsa`).

### Pré-Condição

- O usuário possui perfil com permissão para lançar despesas (ADMIN).
- Quando informado, o `fornecedor` está cadastrado; quando informada, a `forma_pagamento` está ativa.

### Pós-Condição

- A(s) parcela(s) da despesa foi(ram) registrada(s) em `contas_pagar_avulsa` com `situacao = 'PENDENTE'`; ou, em caso de baixa, com `situacao` recalculada para **PARCIAL** ou **PAGA** e a `data_pagamento` registrada na quitação.
- O campo `updated_at` da despesa foi atualizado.

### Ativação

O processo é ativado **manualmente**, quando o usuário aciona a ação "Lançar despesa avulsa" no módulo financeiro, ou quando seleciona uma despesa em aberto e aciona "Registrar baixa / pagamento".

### Fluxo Principal

**Lançamento da despesa**

1. O usuário aciona "Lançar despesa avulsa" e informa: a `descricao` (obrigatória — RN029), o `valor_original`, a `data_emissao`, a `data_vencimento`, o `total_parcelas` e, opcionalmente, o `fornecedor_id`, a `forma_pagamento_id` e os dados informativos de nota (`numero_nota`, `modelo`, `serie`).
2. O serviço **valida** a presença da `descricao` (RN029) e que `data_vencimento ≥ data_emissao` (RN034); falhas seguem para E1 e E2.
3. O serviço **calcula** o `valor_total` de cada parcela aplicando eventual desconto: garante `valor_total ≥ valor_original − valor_desconto` (RN030); violação segue para E3.
4. Para despesas parceladas (`total_parcelas > 1`), o serviço **gera** um registro por parcela, com `numero_parcela` sequencial de 1 a `total_parcelas` e a `data_vencimento` de cada parcela conforme informado pelo usuário (RN038).
5. O serviço **persiste** cada parcela com `situacao = 'PENDENTE'`, `valor_pago = 0` e `data_pagamento` nula.

**Baixa da despesa**

6. O usuário seleciona uma parcela em **PENDENTE** ou **PARCIAL** e informa a `data_pagamento`, o `valor_pago` desta baixa e a `forma_pagamento_id`.
7. O serviço **valida** a situação da parcela (somente PENDENTE/PARCIAL); caso contrário, segue para E4.
8. O serviço **valida o teto** (`valor_pago` acumulado ≤ `valor_total` — RN032); excesso segue para E5.
9. O serviço **acumula** o `valor_pago` e **deriva a situação** (RN035): quitação → **PAGA** com `data_pagamento` gravada; baixa parcial → **PARCIAL** com `data_pagamento` nula.
10. O serviço **valida a coerência data/situação** (RN036); inconsistência segue para E6.
11. O serviço **persiste** a parcela, atualiza `updated_at` e confirma a transação.
12. O sistema apresenta a confirmação, com o novo saldo devedor da despesa.

### Fluxos de Exceção

- **E1 — Descrição não informada.** A `descricao` está vazia (violaria a obrigatoriedade NOT NULL). O serviço rejeita e exibe: *"Informe a descrição da despesa."*
- **E2 — Vencimento anterior à emissão.** A `data_vencimento` é anterior à `data_emissao` (violaria `ck_contas_pagar_avulsa_vencimento`). O serviço rejeita e exibe: *"A data de vencimento não pode ser anterior à data de emissão."*
- **E3 — Total abaixo do valor líquido.** O `valor_total` informado é inferior a `valor_original − valor_desconto` (violaria `ck_contas_pagar_avulsa_total_min`). O serviço rejeita e exibe: *"O valor total não pode ser inferior ao valor original menos o desconto."*
- **E4 — Parcela não passível de baixa.** A parcela não está em situação **PENDENTE** nem **PARCIAL**. O serviço interrompe e exibe: *"Somente despesas com situação PENDENTE ou PARCIAL podem receber baixa."*
- **E5 — Valor da baixa excede o saldo.** O `valor_pago` resultante ultrapassaria o `valor_total` (violaria `ck_contas_pagar_avulsa_pago_teto`). O serviço rejeita e exibe: *"Valor informado excede o saldo devedor da despesa (R$ {saldo})."*
- **E6 — Data de pagamento ausente na quitação.** A baixa quita a despesa (`valor_pago = valor_total`), mas a `data_pagamento` não foi informada (violaria `ck_contas_pagar_avulsa_baixa`). O serviço rejeita e exibe: *"Informe a data de pagamento para quitar a despesa."*
- **E7 — Data de pagamento anterior à emissão.** A `data_pagamento` é anterior à `data_emissao` (violaria `ck_contas_pagar_avulsa_data_pagto`). O serviço rejeita e exibe: *"A data de pagamento não pode ser anterior à data de emissão da despesa."*

---

## 5.4 Síntese das Invariantes Financeiras

Os três processos compartilham um conjunto de invariantes garantidas no banco `estacionamento_kaneko`, que constituem a fronteira de integridade do módulo financeiro independentemente da lógica de aplicação:

| Invariante | UC501 — Conta a Pagar | UC502 — Conta a Receber | UC503 — Despesa Avulsa |
|------------|-----------------------|-------------------------|------------------------|
| Valor amortizado ≤ valor total | `ck_conta_pagar_pago_teto` | `ck_conta_receber_recebido_teto` | `ck_contas_pagar_avulsa_pago_teto` |
| Quitação exige data de baixa | `ck_conta_pagar_baixa` | `ck_conta_receber_baixa` | `ck_contas_pagar_avulsa_baixa` |
| Vencimento ≥ emissão | `ck_conta_pagar_vencimento` | `ck_conta_receber_vencimento` | `ck_contas_pagar_avulsa_vencimento` |
| Data de baixa ≥ emissão | `ck_conta_pagar_data_pagto` | `ck_conta_receber_data_receb` | `ck_contas_pagar_avulsa_data_pagto` |
| Parcela válida (1 ≤ nº ≤ total) | `ck_conta_pagar_parcela` | `ck_conta_receber_parcela` | `ck_contas_pagar_avulsa_parcela` |
| Situações permitidas | PENDENTE, PARCIAL, PAGA, CANCELADA | PENDENTE, PARCIAL, RECEBIDA, CANCELADA | PENDENTE, PARCIAL, PAGA, CANCELADA |
| Unicidade de parcela por nota | `uq_conta_pagar_parcela` | `uq_conta_receber_parcela` | — (sem vínculo a nota) |

---

## Módulos relacionados

- [[04-requisitos-funcionais]] — requisitos funcionais que originam estes processos.
- [[03-der]] — modelo entidade-relacionamento das tabelas `conta_pagar`, `conta_receber` e `contas_pagar_avulsa`.
- [[07-dicionario-de-dados]] — definição detalhada dos campos e domínios.

## Histórico

| Data | Ação |
|------|------|
| 2026-06-14 | Criação dos processos financeiros (Gerenciar Conta a Pagar, Gerenciar Conta a Receber, Lançar Despesa Avulsa), ancorados no schema `estacionamento_kaneko`. |
