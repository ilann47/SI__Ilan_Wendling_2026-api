> Links: [[README]] · [[04-requisitos-funcionais]]

# Seção 5 — Processos Fiscais e de Movimentação de Mercadoria

Este documento especifica os **processos fiscais** do sistema Estacionamento Kaneko: o registro de compras de mercadoria para a conveniência (nota de entrada), o registro de vendas dessas mercadorias (nota de saída) e a emissão de nota fiscal de serviço (NFS-e) referente ao estacionamento e demais serviços prestados. Cada processo é descrito no formato estendido de caso de uso, com regras de negócio aterradas no comportamento real esperado das camadas de serviço (services) e nas restrições efetivas do esquema relacional (`database/schema.sql`), incluindo a atualização de estoque, a geração de contas a pagar/receber com suas parcelas, o cálculo do ISS e as transições de situação documental.

Documentos relacionados: [[03-der]] (modelo entidade-relacionamento), [[07-dicionario-de-dados]] (definição de cada campo e domínio), [[04-requisitos-funcionais]] (requisitos funcionais correspondentes) e [[10-requisitos-nao-funcionais]] (transação, integridade e desempenho).

---

## Convenções aplicáveis a todos os processos

Esta seção descreve o fluxo vigente com organização ativa. Consultas exigem `fiscal:read`; lançamento, revisão e confirmação exigem `fiscal:manage`. No detalhe, as contas e o estoque exigem também `finance:read` e `stock:read`, respectivamente. Informação sem permissão não é exibida como uma lista vazia. O sistema registra documentos e efeitos internos; não autoriza NF-e, não transmite devolução à SEFAZ e não executa operações bancárias.

## UC401 — Gerenciar Compra (Nota de Entrada)

### Objetivo e atores

Registrar o documento do fornecedor e confirmar integralmente a entrada. Atuam o operador de compras/estoque com gestão fiscal e o usuário com permissão de consulta. A confirmação não depende de Ordem de Compra nem de um registro separado de recebimento.

### Regras de negócio

| ID | Regra vigente |
|---|---|
| RN001 | O operador deve estar autenticado, com organização ativa e permissões do fluxo. |
| RN002 | A identidade fiscal é organização + fornecedor + número + série + modelo. Espaços externos, caixa e zeros iniciais em identificadores numéricos são normalizados na comparação. |
| RN003 | A emissão não pode ser posterior ao dia corrente. A regra vale para criação, edição, conferência e confirmação, inclusive no XML. A API manual usa a data corrente se a emissão for omitida; a interface exige preenchimento. |
| RN004 | A chegada, se informada, não pode ser anterior à emissão. |
| RN005 | Ao menos um item, com produto ativo do mesmo contexto, quantidade positiva e local de estoque ativo. |
| RN006 | A condição de pagamento e a transportadora ativas são herdadas do fornecedor se omitidas; podem ser substituídas. As parcelas da revisão ficam gravadas com valor, vencimento e forma aplicável. |
| RN007 | Nota confirmada com título que já teve baixa, inclusive parcial, não pode ser revertida. |
| RN008 | Sem estoque reversível ou diante de histórico de movimentos incompatível, a reversão é bloqueada. |
| RN009 | Valor e percentual de desconto de item devem concordar se ambos forem informados. O custo incorpora o rateio de despesas; o desconto geral reduz a obrigação, não o custo dos itens. |
| RN010 | O rascunho não altera estoque, custo nem financeiro. Uma única confirmação atualiza saldo por local e custo médio, registra movimentos e gera títulos a pagar para as parcelas positivas. |

### Pré-condição, pós-condição e ativação

**Pré-condição:** fornecedor, produtos e local de estoque do tenant cadastrados e ativos. **Ativação:** Estoque → Notas de Entrada → Nova nota ou Importar XML. **Pós-condição:** rascunho `PENDENTE` sem efeitos ou nota `CONFIRMADA` com estoque/custo/contas persistidos atomicamente.

### Fluxo principal — Entrada manual

1. O operador informa fornecedor, número, série, modelo, emissão e, quando disponível, chegada.
2. Seleciona o local de estoque, confere condição de pagamento e frete e informa itens, quantidades, valores e descontos.
3. Salva o rascunho. A API responde com a identificação, versão e parcelas calculadas; a edição de pendente exige a versão corrente.
4. Abre **Conferir valores e parcelas**. Confere mercadoria, custos por item, estimativa do custo médio e vencimentos da versão salva.
5. Marca a conferência e confirma. A API bloqueia a nota, valida a versão e registra os efeitos integralmente.
6. Consulta o detalhe para ver movimentações, saldos e títulos conforme as permissões.

### Fluxos alternativos

- **Consultar:** pesquisar por número, fornecedor ou situação na lista e abrir o documento. O detalhe não altera nenhum registro.
- **Editar:** salvar a nova composição da nota pendente e refazer a conferência; alterações não salvas não autorizam confirmação.
- **Cancelar ou excluir rascunho:** somente sem efeitos confirmados, com comportamento previsto pelo contrato da nota. Para nota confirmada, utilizar UC404.
- **Importar XML:** selecionar a NF-e de fornecedor, com destinatário correspondente à organização e moeda BRL. Associar fornecedor, local e todos os produtos; conferir a quantidade física de cada item. Divergência deixa a importação conciliada, sem confirmação. Com contagem completa e valores conciliados, consultar a prévia e confirmar. O sistema vincula a nota canônica, lança os efeitos uma única vez e retorna a mesma importação quando a confirmação é repetida.

### Fluxos de exceção

| ID | Situação e resposta |
|---|---|
| E1 | Emissão futura: “A data de emissão não pode ser futura.”; o documento não é gravado nem confirmado. |
| E2 | Chegada anterior à emissão: “A chegada não pode ser anterior à emissão.” |
| E3 | Identidade duplicada: indicar nota existente, sem criar outro documento. |
| E4 | Revisão desatualizada: “A nota foi alterada. Recarregue e revise antes de confirmar.” |
| E5 | XML com destinatário incorreto: “O destinatário do XML não corresponde à organização ativa.” |
| E6 | XML não conciliável: “O total calculado diverge do XML. Nenhum lançamento foi confirmado.” |
| E7 | Contagem divergente: “Conclua a conciliação e resolva a diferença da contagem antes de confirmar.” |

## UC402 — Gerenciar Venda (Nota de Saída)

### Objetivo e atores

Registrar diretamente a venda de mercadoria e os efeitos da saída. Atuam o operador com gestão fiscal e o usuário com leitura fiscal; não há Pedido de Venda como requisito ou entidade intermediária.

### Regras de negócio

| ID | Regra vigente |
|---|---|
| RN001 | A chave contextual é organização emitente + número + série + modelo. O cliente é destinatário, não faz parte da unicidade contextual. |
| RN002 | A nota tem cliente, local e itens da organização. A saída não pode deixar saldo negativo no local. |
| RN003 | O rascunho salva a composição e as parcelas efetivas. A confirmação usa a revisão e o cronograma salvos, registra custo vigente por item, não recalcula o custo médio e gera contas a receber. |
| RN004 | Rascunho não movimenta estoque nem gera títulos. Nota confirmada é imutável pelo cadastro comum; a reversão segue UC404. |

### Pré-condição, pós-condição e ativação

**Pré-condição:** cliente, produtos e local cadastrados; disponibilidade física para confirmar. **Ativação:** Vendas → Notas de Saída → Nova nota. **Pós-condição:** `PENDENTE` sem efeitos ou `CONFIRMADA` com baixa do local e recebíveis.

### Fluxo principal e alternativas

1. O operador abre a nova nota, preenche identificação, cliente, local, itens e valores.
2. Salva o rascunho e confere totais e vencimentos previstos.
3. Confirma a versão salva. O serviço valida disponibilidade, movimenta o razão, salva o custo de saída e gera títulos numa transação.
4. O operador verifica a situação, os movimentos e os recebíveis no detalhe. Pode consultar ou editar o rascunho; cancelamento e exclusão só se aplicam antes da confirmação.

### Fluxos de exceção

| ID | Situação e resposta |
|---|---|
| E1 | Estoque insuficiente: “O movimento deixaria o saldo de estoque negativo.”; toda a confirmação é desfeita. |
| E2 | Versão divergente: “A nota foi alterada. Recarregue e revise a versão atual.” |
| E3 | Identidade duplicada ou situação incompatível: o sistema rejeita a operação, sem repetir estoque nem cobrança. |

## UC404 — Reverter nota confirmada internamente

**Objetivo:** compensar estoque e cancelar títulos elegíveis, com histórico e autoria. **Ator:** usuário da organização ativa com `fiscal:manage`, `stock:manage` e `finance:manage`. **Pré-condição:** nota confirmada, versão vigente, razão reconciliável e títulos sem baixa. **Ativação:** ação de reversão no detalhe da nota. **Pós-condição:** nota `CANCELADA` com data/motivo de reversão, movimentos compensatórios e títulos cancelados sem apagar registros.

1. O operador informa motivo de até 500 caracteres e confirma a reversão da versão exibida.
2. O sistema verifica nota, títulos, histórico e estado atual do estoque sob bloqueio.
3. Compensa os movimentos, restaura o custo médio anterior na Entrada quando aplicável, cancela os títulos e grava auditoria em uma transação.
4. O detalhe apresenta a reversão sem sugerir transmissão fiscal ou estorno de pagamento.

**E1 — Baixa existente:** “A nota possui título com pagamento ou baixa. Não é possível reverter.” Na saída, a mensagem usa “recebimento ou baixa”. **E2 — Histórico incompatível:** “Há movimentos posteriores ou intercalados para um produto da nota.” **E3 — Divergência financeira:** “Os títulos vinculados não conciliam com o total da nota.” Em todos esses casos, a reversão é recusada.

## UC403 — Processo 3 — Registrar Nota de Serviço interna

O detalhamento abaixo é a especificação histórica do serviço avulso.
Para o fluxo atual vinculado a OS, consulte [ADR 25](../25-adr-ordem-servico.md).
O status interno `EMITIDA` não comprova transmissão ou autorização municipal.

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
| 2026-11-06 | Atualiza UC401/UC402 e documenta UC404 com datas, razão, cronograma e reversão do fluxo vigente. |
| 2026-06-14 | Criação da Seção 5 — Processos Fiscais (Registrar Compra, Registrar Venda, Emitir Nota de Serviço), aterrada em `database/schema.sql`. |
