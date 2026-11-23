> Links: [[README]] · [[04-requisitos-funcionais]]

# 5. Processos — Módulo Financeiro

Esta seção especifica os processos de negócio do módulo financeiro do **Estacionamento Kaneko**, responsável pelo controle de obrigações com fornecedores (contas a pagar) e de direitos sobre clientes (contas a receber). Cada processo é descrito sob a ótica do comportamento esperado da camada de serviço (services) da aplicação, ancorado nas tabelas, restrições (`CHECK`), chaves estrangeiras e índices definidos no schema do banco `estacionamento_kaneko`.

Dois processos compõem esta seção: **Gerenciar Conta a Pagar** (baixa de títulos vinculados a `nota_entrada` ou lançados manualmente com fornecedor) e **Gerenciar Conta a Receber** (baixa de títulos vinculados a `nota_saida`, `nota_servico` ou `mensalista`, ou lançados manualmente com cliente). Ambos compartilham o ciclo de vida de baixa **PENDENTE → PARCIAL → PAGA/RECEBIDA** e as invariantes de integridade financeira garantidas no banco.

> **Convenções de ciclo de vida (válidas para os três processos)**
> - **PENDENTE**: parcela gerada, ainda sem qualquer baixa. `valor_pago`/`valor_recebido = 0` e a data de baixa (`data_pagamento`/`data_recebimento`) é nula.
> - **PARCIAL**: houve baixa parcial — o valor amortizado é maior que zero, porém inferior ao `valor_total`. A data de baixa permanece nula.
> - **PAGA / RECEBIDA**: o valor amortizado igualou o `valor_total`. A data de baixa torna-se **obrigatória** (restrições `ck_*_baixa`).
> - **CANCELADA**: parcela invalidada administrativamente; não recebe novas baixas. A data de baixa permanece nula.
>
> A restrição `ck_*_pago_teto` / `ck_*_recebido_teto` garante, no próprio banco, que **o valor amortizado nunca ultrapasse o `valor_total`** (o requisito "valor_pago ≤ valor_total" é, portanto, uma invariante de banco, não apenas de aplicação).

---

## 5.1 Processo: UC501 — Gerenciar Conta a Pagar

### Objetivo e atores

Consultar obrigações, identificar o documento de origem e registrar pagamentos parciais ou integrais. O usuário deve pertencer à organização ativa; `finance:read` permite consultar e `finance:manage` permite lançar, baixar ou cancelar.

### Regras de negócio

| ID | Regra vigente |
|---|---|
| RN001 | Uma compra confirmada gera os títulos conforme as parcelas salvas; também existe lançamento manual sem documento fiscal. |
| RN002 | A baixa recebe valor positivo e data opcional; não aceita valor superior ao saldo. O título fica `PARCIAL` ou `PAGA`. |
| RN003 | A mesma chave com o mesmo valor e data não insere outra baixa; devolve o estado atual. A mesma chave com dados diferentes é rejeitada. |
| RN004 | Título com baixa não é cancelável. A conta fiscal não tem edição direta. |
| RN005 | A baixa é registro interno; não envia ordem de pagamento ao banco. |

### Pré-condição, pós-condição e ativação

**Pré-condição:** título da organização ativa e saldo suficiente. **Ativação:** Financeiro → Contas a Pagar → título → registrar baixa. **Pós-condição:** pagamento lançado, saldo e situação atualizados e histórico preservado.

### Fluxo principal e alternativas

1. Pesquisar/filtrar a lista por situação, vencimento ou contraparte.
2. Abrir a conta e conferir valor, saldo, vencimento, fornecedor, origem e histórico.
3. Informar o valor pago e a data e confirmar o registro. O serviço bloqueia o título, registra a baixa e devolve os dados atualizados.
4. Conferir a situação e a nova linha de histórico. Se o título não tem baixa, é possível cancelá-lo; a consulta continua disponível. Um lançamento manual não altera a origem ou a composição de uma nota existente.

### Fluxos de exceção

**E1 — Excedeu o saldo:** “O valor da baixa excede o saldo devedor do título.” **E2 — Chave reutilizada com dados diferentes:** “Esta chave de baixa já foi utilizada com outro valor ou data.” **E3 — Cancelamento com baixa:** “Um título com baixa não pode ser cancelado; os lançamentos devem ser preservados.”

## 5.2 Processo: UC502 — Gerenciar Conta a Receber

### Objetivo, atores e regras de negócio

Consultar créditos e registrar recebimentos parciais ou integrais. Aplicam-se as mesmas permissões e a mesma proteção de histórico do UC501.

| ID | Regra vigente |
|---|---|
| RN001 | Contas podem vir de Nota de Saída, Nota de Serviço, mensalidade ou lançamento manual, conforme o vínculo existente. |
| RN002 | O recebimento informa valor positivo e data opcional. A baixa não pode exceder o saldo; a situação passa a `PARCIAL` ou `RECEBIDA`. |
| RN003 | Repetição da mesma chave com os mesmos dados não duplica o recebimento. Títulos com baixa não podem ser cancelados. |
| RN004 | Não há edição direta do título; o registro de baixa não executa cobrança bancária. |

### Pré-condição, pós-condição e ativação

**Pré-condição:** título da organização ativa e saldo disponível. **Ativação:** Financeiro → Contas a Receber → título → registrar baixa. **Pós-condição:** recebimento persistido e histórico disponível para conferência.

### Fluxo principal e alternativas

1. Filtrar e abrir o título. Conferir cliente, origem, vencimento e saldo.
2. Informar valor/data, confirmar e conferir o resultado no título e no histórico.
3. Para um título sem baixa, é possível cancelar com a ação correspondente; a operação mantém o registro para rastreio.

### Fluxos de exceção

**E1 — Excedeu o saldo:** “O valor da baixa excede o saldo a receber do título.” **E2 — Chave/carga divergentes:** “Esta chave de baixa já foi utilizada com outro valor ou data.” **E3 — Cancelamento com baixa:** “Um título com baixa não pode ser cancelado; os lançamentos devem ser preservados.”

## 5.4 Síntese das Invariantes Financeiras

Os dois processos compartilham um conjunto de invariantes garantidas no banco `estacionamento_kaneko`, que constituem a fronteira de integridade do módulo financeiro independentemente da lógica de aplicação:

| Invariante | UC501 — Conta a Pagar | UC502 — Conta a Receber |
|------------|-----------------------|-------------------------|
| Valor amortizado ≤ valor total | `ck_conta_pagar_pago_teto` | `ck_conta_receber_recebido_teto` |
| Quitação exige data de baixa | `ck_conta_pagar_baixa` | `ck_conta_receber_baixa` |
| Vencimento ≥ emissão | `ck_conta_pagar_vencimento` | `ck_conta_receber_vencimento` |
| Data de baixa ≥ emissão | `ck_conta_pagar_data_pagto` | `ck_conta_receber_data_receb` |
| Parcela válida (1 ≤ nº ≤ total) | `ck_conta_pagar_parcela` | `ck_conta_receber_parcela` |
| Situações permitidas | PENDENTE, PARCIAL, PAGA, CANCELADA | PENDENTE, PARCIAL, RECEBIDA, CANCELADA |
| Unicidade de parcela por nota | `uq_conta_pagar_parcela` | `uq_conta_receber_parcela` |

---

## Módulos relacionados

- [[04-requisitos-funcionais]] — requisitos funcionais que originam estes processos.
- [[03-der]] — modelo entidade-relacionamento das tabelas `conta_pagar` e `conta_receber`.
- [[07-dicionario-de-dados]] — definição detalhada dos campos e domínios.

## Histórico

| Data | Ação |
|------|------|
| 2026-06-14 | Criação dos processos financeiros (Gerenciar Conta a Pagar, Gerenciar Conta a Receber, Lançar Despesa Avulsa), ancorados no schema `estacionamento_kaneko`. |
| 2026-11-10 | Descontinuação do UC503. Os identificadores UC501 e UC502 permanecem inalterados. |
