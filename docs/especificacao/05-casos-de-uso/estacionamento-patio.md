> Links: [[README]] · [[04-requisitos-funcionais]]

# 5. Casos de Uso — Cadastros do Pátio e Processo de Movimentação

Esta seção especifica os casos de uso do núcleo operacional do Estacionamento Kaneko: os **cadastros** de Veículo, Tarifa e Mensalista, e o **processo** de Registrar Entrada e Saída de Veículo (movimentação de pátio), com o cálculo da tarifa avulsa. Todas as validações, tamanhos de campo, restrições de unicidade (`UNIQUE`), verificações (`CHECK`), chaves estrangeiras (`FK`) e regras de exclusão lógica foram aterradas no schema real do banco `estacionamento_kaneko` (arquivo `database/schema.sql`, futura migration Flyway V1).

Convenções desta seção:

- Campos de formulário marcados com asterisco (*) são de preenchimento obrigatório.
- A exclusão é **lógica** (campo `ativo` = `FALSE`) quando a entidade possui vínculos com outros registros; nunca é feita exclusão física que viole as `FK ON DELETE RESTRICT` do schema.
- O acesso a qualquer caso de uso exige usuário autenticado e ativo (tabela `usuario`, perfis `ADMIN` ou `OPERADOR`).
- Identificadores dos casos de uso seguem o padrão `UCxxx` por entidade.

---

## UC301 — Gerenciar Veículo

### Regras de Negócio

- **RN001** — O acesso ao caso de uso é permitido somente a usuário autenticado e ativo no sistema (`usuario.ativo = TRUE`), de perfil `ADMIN` ou `OPERADOR`.
- **RN002** — A `placa` é de preenchimento obrigatório, possui no máximo 10 caracteres e deve ser **única** em todo o sistema (`uq_veiculo_placa UNIQUE (placa)`). Não é permitido cadastrar dois veículos com a mesma placa.
- **RN003** — O `tipo` do veículo é obrigatório e restrito ao domínio `CARRO` ou `MOTO` (`ck_veiculo_tipo CHECK (tipo IN ('CARRO','MOTO'))`); o valor padrão é `CARRO`.
- **RN004** — O `cliente_id` é **opcional**: quando preenchido, deve referenciar um cliente existente (`fk_veiculo_cliente`); um veículo sem cliente representa um veículo de uso avulso e um veículo com cliente representa um veículo passível de vínculo a contrato mensalista.
- **RN005** — Os campos `modelo` (até 60 caracteres), `cor` (até 30 caracteres) e `observacao` (até 255 caracteres) são opcionais.
- **RN006** — O veículo possui controle de exclusão lógica por meio do campo `ativo` (booleano, padrão `TRUE`).
- **RN007** — A exclusão física de um veículo é **restringida** quando houver registros vinculados em `mensalista` (`fk_mensalista_veiculo ON DELETE RESTRICT`) ou em `movimentacao` (`fk_mov_veiculo ON DELETE RESTRICT`). Nesses casos, o sistema oferece a **desativação** (`ativo = FALSE`) em vez da exclusão.

### Nome do Caso de Uso

Gerenciar Veículo

### Atores

Administrador, Operador (usuário autenticado).

### Pré-Condição

Usuário autenticado e ativo no sistema. Para vincular um cliente ao veículo, o cliente deve estar previamente cadastrado.

### Pós-Condição

Veículo inserido, alterado, desativado/excluído ou simplesmente consultado, com integridade preservada conforme as restrições do schema.

### Ativação

O usuário aciona a opção "Veículos" no menu de cadastros do sistema.

### Fluxo Principal

#### Consultar

1. O sistema apresenta a listagem dos veículos cadastrados, exibindo placa, modelo, cor, tipo e cliente vinculado (quando houver).
2. O usuário pode informar um filtro de pesquisa (por placa, modelo ou cliente).
3. O sistema retorna os registros que atendem ao filtro informado.
4. O usuário seleciona um registro para visualizar seus detalhes ou aciona as opções Inserir, Alterar ou Excluir.

### Fluxos Alternativos

#### Inserir

1. O usuário aciona a opção "Inserir".
2. O sistema apresenta o formulário em branco com os campos:
   - Placa* (texto, até 10 caracteres, único)
   - Tipo* (seleção: CARRO, MOTO — padrão CARRO)
   - Modelo (texto, até 60 caracteres)
   - Cor (texto, até 30 caracteres)
   - Cliente (seleção de cliente cadastrado — opcional)
   - Observação (texto, até 255 caracteres)
3. O usuário preenche os campos e confirma a operação.
4. O sistema valida os campos obrigatórios, a unicidade da placa e a coerência do tipo (vide Fluxos de Exceção).
5. O sistema persiste o veículo com `ativo = TRUE` e retorna à listagem com mensagem de sucesso.

#### Alterar

1. O usuário seleciona um veículo na listagem e aciona a opção "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário edita os campos desejados e confirma a operação.
4. O sistema valida os campos obrigatórios e, caso a placa tenha sido alterada, valida sua unicidade.
5. O sistema persiste as alterações e retorna à listagem com mensagem de sucesso.

#### Excluir

1. O usuário seleciona um veículo na listagem e aciona a opção "Excluir".
2. O sistema verifica a existência de vínculos em `mensalista` e `movimentacao`.
3. Não havendo vínculos, o sistema solicita confirmação e realiza a exclusão física do registro.
4. Havendo vínculos, o sistema oferece a desativação lógica (vide Fluxo de Exceção E4).

### Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** No Inserir/Alterar, se a placa ou o tipo não forem informados, o sistema interrompe a gravação e exibe a mensagem: **"O campo [nome do campo] é obrigatório."**
- **E2 — Placa já cadastrada:** No Inserir/Alterar, se a placa informada já existir em outro veículo (violação de `uq_veiculo_placa`), o sistema exibe a mensagem: **"Placa já cadastrada."** e mantém o usuário no formulário.
- **E3 — Tipo inválido:** Se o tipo informado não pertencer ao domínio `CARRO`/`MOTO` (violação de `ck_veiculo_tipo`), o sistema exibe a mensagem: **"Tipo de veículo inválido. Selecione CARRO ou MOTO."**
- **E4 — Registro vinculado a outro:** No Excluir, se o veículo possuir vínculo em `mensalista` ou `movimentacao`, o sistema exibe a mensagem: **"Veículo vinculado a outro registro. Deseja desativar?"**. Em caso afirmativo, o sistema define `ativo = FALSE`; em caso negativo, a operação é cancelada.

---

## UC302 — Gerenciar Tarifa

### Regras de Negócio

- **RN001** — O acesso ao caso de uso é permitido somente a usuário autenticado e ativo no sistema, de perfil `ADMIN` ou `OPERADOR`.
- **RN002** — A `descricao` é de preenchimento obrigatório e possui no máximo 80 caracteres.
- **RN003** — Os valores monetários `valor_hora`, `valor_diaria` e `valor_mensalidade` são obrigatórios, possuem precisão `NUMERIC(10,2)` e não podem ser negativos (`ck_tarifa_valores CHECK (valor_hora >= 0 AND valor_diaria >= 0 AND valor_mensalidade >= 0 AND tolerancia_min >= 0)`).
- **RN004** — A `tolerancia_min` (tolerância de cortesia, em minutos) é um inteiro obrigatório, não pode ser negativo e possui valor padrão `15`. Veículos avulsos cuja permanência seja inferior ou igual a esse limite não são cobrados.
- **RN005** — O `valor_diaria` funciona como **teto diário** da cobrança avulsa: nenhuma diária avulsa pode ultrapassar esse valor (regra aplicada no processo de saída — UC350).
- **RN006** — A tarifa não possui restrição de unicidade no schema; podem coexistir múltiplas tarifas e, inclusive, **várias tarifas marcadas como ativas** ao mesmo tempo — o único controle de situação é o booleano `ativo`, sem índice que garanta uma tarifa vigente única. Quando há mais de uma tarifa ativa, a regra de desempate **do serviço** (camada de aplicação, não do schema) é selecionar a tarifa **ativa mais recente** (maior `id`). A tarifa efetivamente aplicada em uma movimentação é registrada em `movimentacao.tarifa_id`. **Observação:** a unicidade de tarifa vigente é candidata a refatoração futura, via índice único parcial (ex.: `UNIQUE (...) WHERE ativo = TRUE`).
- **RN007** — A tarifa possui controle de exclusão lógica por meio do campo `ativo` (booleano, padrão `TRUE`).
- **RN008** — A exclusão física de uma tarifa é **restringida** quando houver movimentações vinculadas (`fk_mov_tarifa ON DELETE RESTRICT`). Nesse caso, o sistema oferece a desativação (`ativo = FALSE`).

### Nome do Caso de Uso

Gerenciar Tarifa

### Atores

Administrador, Operador (usuário autenticado).

### Pré-Condição

Usuário autenticado e ativo no sistema.

### Pós-Condição

Tarifa inserida, alterada, desativada/excluída ou consultada, com os valores e a tolerância coerentes com as restrições do schema.

### Ativação

O usuário aciona a opção "Tarifas" no menu de cadastros do sistema.

### Fluxo Principal

#### Consultar

1. O sistema apresenta a listagem das tarifas cadastradas, exibindo descrição, valor por hora, valor da diária, tolerância e valor da mensalidade.
2. O usuário pode informar um filtro de pesquisa (por descrição).
3. O sistema retorna os registros que atendem ao filtro informado.
4. O usuário seleciona um registro para visualizar seus detalhes ou aciona as opções Inserir, Alterar ou Excluir.

### Fluxos Alternativos

#### Inserir

1. O usuário aciona a opção "Inserir".
2. O sistema apresenta o formulário em branco com os campos:
   - Descrição* (texto, até 80 caracteres)
   - Valor por hora* (decimal, NUMERIC(10,2), ≥ 0)
   - Valor da diária* (decimal, NUMERIC(10,2), ≥ 0 — teto diário)
   - Tolerância (minutos)* (inteiro, ≥ 0 — padrão 15)
   - Valor da mensalidade* (decimal, NUMERIC(10,2), ≥ 0)
3. O usuário preenche os campos e confirma a operação.
4. O sistema valida os campos obrigatórios e a não-negatividade dos valores (vide Fluxos de Exceção).
5. O sistema persiste a tarifa com `ativo = TRUE` e retorna à listagem com mensagem de sucesso.

#### Alterar

1. O usuário seleciona uma tarifa na listagem e aciona a opção "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário edita os campos desejados e confirma a operação.
4. O sistema valida os campos obrigatórios e a não-negatividade dos valores.
5. O sistema persiste as alterações e retorna à listagem com mensagem de sucesso.

#### Excluir

1. O usuário seleciona uma tarifa na listagem e aciona a opção "Excluir".
2. O sistema verifica a existência de movimentações vinculadas (`movimentacao.tarifa_id`).
3. Não havendo vínculos, o sistema solicita confirmação e realiza a exclusão física do registro.
4. Havendo vínculos, o sistema oferece a desativação lógica (vide Fluxo de Exceção E3).

### Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** No Inserir/Alterar, se a descrição, o valor por hora, o valor da diária, a tolerância ou o valor da mensalidade não forem informados, o sistema interrompe a gravação e exibe a mensagem: **"O campo [nome do campo] é obrigatório."**
- **E2 — Valor inválido:** Se algum dos valores monetários ou a tolerância for negativo (violação de `ck_tarifa_valores`), o sistema exibe a mensagem: **"Os valores da tarifa e a tolerância não podem ser negativos."**
- **E3 — Registro vinculado a outro:** No Excluir, se a tarifa estiver vinculada a alguma movimentação, o sistema exibe a mensagem: **"Tarifa vinculada a outro registro. Deseja desativar?"**. Em caso afirmativo, o sistema define `ativo = FALSE`; em caso negativo, a operação é cancelada.

---

## UC303 — Gerenciar Mensalista

### Regras de Negócio

- **RN001** — O acesso ao caso de uso é permitido somente a usuário autenticado e ativo no sistema, de perfil `ADMIN` ou `OPERADOR`.
- **RN002** — O `cliente_id` é obrigatório e deve referenciar um cliente existente (`fk_mensalista_cliente ON DELETE RESTRICT`). **Importante:** a chave estrangeira garante apenas a **existência** do cliente; ela **não** verifica `cliente.ativo`. A exigência de que o cliente esteja **ATIVO** (`cliente.ativo = TRUE`) é uma regra **de aplicação** (camada de serviço), não uma restrição do schema. Ao tentar vincular um cliente **INATIVO**, o serviço bloqueia a operação (vide Fluxo de Exceção E6).
- **RN003** — O `veiculo_id` é obrigatório e deve referenciar um veículo existente (`fk_mensalista_veiculo ON DELETE RESTRICT`).
- **RN004** — O `valor_mensal` é obrigatório, possui precisão `NUMERIC(10,2)` e, por consistência com o processo de cadastro, não deve ser negativo. Sugere-se, na inclusão, o `valor_mensalidade` da tarifa vigente como valor padrão.
- **RN005** — A `data_inicio` é obrigatória, do tipo `DATE`, com valor padrão igual à data corrente (`CURRENT_DATE`).
- **RN006** — A `data_fim` é opcional; quando informada, deve ser maior ou igual à `data_inicio` (`ck_mensalista_periodo CHECK (data_fim IS NULL OR data_fim >= data_inicio)`). `data_fim` nula indica contrato vigente.
- **RN007** — O `status` é obrigatório, restrito ao domínio `ATIVO`, `SUSPENSO` ou `CANCELADO` (`ck_mensalista_status`), com valor padrão `ATIVO`.
- **RN008** — Existe no máximo **um contrato ATIVO por veículo** (`uq_mensalista_veiculo_ativo UNIQUE (veiculo_id) WHERE status = 'ATIVO'`). Não é permitido cadastrar/ativar um segundo contrato `ATIVO` para o mesmo veículo. Contratos `SUSPENSO`/`CANCELADO` não são restringidos por esse índice.
- **RN009** — A exclusão física de um mensalista é **restringida** quando houver movimentações vinculadas (`fk_mov_mensalista ON DELETE RESTRICT`) ou contas a receber vinculadas (`fk_conta_receber_mensalista ON DELETE RESTRICT`). Nesses casos, o encerramento do contrato é feito por mudança de `status` para `CANCELADO` e/ou preenchimento de `data_fim`, em vez de exclusão física.

### Nome do Caso de Uso

Gerenciar Mensalista

### Atores

Administrador, Operador (usuário autenticado).

### Pré-Condição

Usuário autenticado e ativo no sistema. Cliente e veículo previamente cadastrados.

### Pós-Condição

Contrato mensalista inserido, alterado, cancelado/excluído ou consultado, respeitando o limite de um contrato ATIVO por veículo.

### Ativação

O usuário aciona a opção "Mensalistas" no menu de cadastros do sistema.

### Fluxo Principal

#### Consultar

1. O sistema apresenta a listagem dos contratos mensalistas, exibindo cliente, veículo (placa), valor mensal, data de início, data de fim e status.
2. O usuário pode informar um filtro de pesquisa (por cliente, placa do veículo ou status).
3. O sistema retorna os registros que atendem ao filtro informado.
4. O usuário seleciona um registro para visualizar seus detalhes ou aciona as opções Inserir, Alterar ou Excluir.

### Fluxos Alternativos

#### Inserir

1. O usuário aciona a opção "Inserir".
2. O sistema apresenta o formulário em branco com os campos:
   - Cliente* (seleção de cliente cadastrado)
   - Veículo* (seleção de veículo cadastrado)
   - Valor mensal* (decimal, NUMERIC(10,2), ≥ 0 — sugerido a partir da tarifa vigente)
   - Data de início* (data — padrão data corrente)
   - Data de fim (data — opcional; ≥ data de início)
   - Status* (seleção: ATIVO, SUSPENSO, CANCELADO — padrão ATIVO)
3. O usuário preenche os campos e confirma a operação.
4. O sistema valida os campos obrigatórios, a coerência do período, o domínio do status e a regra de um contrato ATIVO por veículo (vide Fluxos de Exceção).
5. O sistema persiste o contrato e retorna à listagem com mensagem de sucesso.

#### Alterar

1. O usuário seleciona um contrato na listagem e aciona a opção "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário edita os campos desejados (por exemplo, valor mensal, data de fim ou status) e confirma a operação.
4. O sistema valida a coerência do período e, caso o status seja definido como `ATIVO`, valida a inexistência de outro contrato `ATIVO` para o mesmo veículo.
5. O sistema persiste as alterações e retorna à listagem com mensagem de sucesso.

#### Excluir

1. O usuário seleciona um contrato na listagem e aciona a opção "Excluir".
2. O sistema verifica a existência de vínculos em `movimentacao` e `conta_receber`.
3. Não havendo vínculos, o sistema solicita confirmação e realiza a exclusão física do registro.
4. Havendo vínculos, o sistema oferece o encerramento do contrato em vez da exclusão (vide Fluxo de Exceção E5).

### Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** No Inserir/Alterar, se cliente, veículo, valor mensal, data de início ou status não forem informados, o sistema interrompe a gravação e exibe a mensagem: **"O campo [nome do campo] é obrigatório."**
- **E2 — Veículo já possui contrato ativo:** No Inserir/Alterar, ao tentar gravar um contrato `ATIVO` para um veículo que já possui contrato `ATIVO` (violação de `uq_mensalista_veiculo_ativo`), o sistema exibe a mensagem: **"Este veículo já possui um contrato mensalista ativo."**
- **E3 — Período inválido:** Se a data de fim for anterior à data de início (violação de `ck_mensalista_periodo`), o sistema exibe a mensagem: **"A data de fim não pode ser anterior à data de início."**
- **E4 — Status inválido:** Se o status informado não pertencer ao domínio `ATIVO`/`SUSPENSO`/`CANCELADO` (violação de `ck_mensalista_status`), o sistema exibe a mensagem: **"Status do contrato inválido. Selecione ATIVO, SUSPENSO ou CANCELADO."**
- **E5 — Registro vinculado a outro:** No Excluir, se o contrato possuir vínculo em `movimentacao` ou `conta_receber`, o sistema exibe a mensagem: **"Mensalista vinculado a outro registro. Deseja cancelar o contrato?"**. Em caso afirmativo, o sistema define `status = CANCELADO` e preenche a `data_fim`; em caso negativo, a operação é cancelada.
- **E6 — Cliente inativo:** No Inserir/Alterar, ao tentar vincular um cliente cujo `cliente.ativo = FALSE` (regra de aplicação, não imposta pela `fk_mensalista_cliente`), o sistema interrompe a gravação e exibe a mensagem: **"Cliente inativo. Reative o cliente antes de vinculá-lo a um contrato mensalista."** e mantém o usuário no formulário.

---

## UC350 — Registrar Entrada e Saída de Veículo (Movimentação de Pátio)

### Nome do Caso de Uso

Registrar Entrada e Saída de Veículo (Operação de Pátio)

### Objetivo

Controlar o ciclo de permanência de um veículo no pátio do estacionamento, registrando a **entrada** (abertura do ticket) e a **saída** (fechamento do ticket), aplicando o cálculo da tarifa avulsa quando cabível e isentando os veículos mensalistas. O processo garante, por construção, que um mesmo veículo não tenha mais de um ticket aberto simultaneamente e que o tipo da movimentação (`AVULSO`/`MENSALISTA`) permaneça coerente com a presença ou ausência de contrato.

### Regras de Negócio

- **RN001** — O acesso ao processo é permitido somente a usuário autenticado e ativo no sistema, de perfil `ADMIN` ou `OPERADOR`.
- **RN002** — Cada movimentação está obrigatoriamente associada a um veículo (`fk_mov_veiculo ON DELETE RESTRICT`).
- **RN003** — Existe no máximo **uma movimentação ABERTA por veículo** (`uq_mov_veiculo_aberta UNIQUE (veiculo_id) WHERE status = 'ABERTO'`). Tentar registrar a entrada de um veículo que já está no pátio (com ticket `ABERTO`) é bloqueado.
- **RN004** — O `tipo` da movimentação é obrigatório e restrito ao domínio `AVULSO` ou `MENSALISTA` (`ck_mov_tipo`).
- **RN005** — A coerência de tipo é garantida por `ck_mov_tipo_coerencia`: quando `tipo = 'MENSALISTA'`, o `mensalista_id` é obrigatório (`NOT NULL`); quando `tipo = 'AVULSO'`, o `mensalista_id` deve ser nulo. **Atenção:** essa verificação do schema exige apenas a presença (ou ausência) do `mensalista_id` — ela **não** valida o `status` do contrato. A exigência de que o contrato esteja `ATIVO` (e não `SUSPENSO`/`CANCELADO`) é uma regra **de aplicação** (camada de serviço): na entrada, o sistema determina o tipo conforme a existência de contrato com `status = 'ATIVO'` para o veículo; havendo apenas contrato `SUSPENSO`/`CANCELADO`, o veículo é tratado como **AVULSO** (vide Fluxo de Exceção E7).
- **RN006** — A movimentação avulsa registra a `tarifa_id` aplicada. **O schema não garante uma tarifa vigente única** — apenas o booleano `tarifa.ativo` existe, e podem coexistir várias tarifas ativas. Quando há mais de uma tarifa ativa, a regra de desempate **do serviço** (camada de aplicação, não do schema) é aplicar a tarifa **ativa mais recente** (maior `id`). Para movimentação mensalista, a `tarifa_id` permanece nula. **Observação:** a unicidade de tarifa vigente é candidata a refatoração futura, via índice único parcial.
- **RN007** — O `status` da movimentação é obrigatório, restrito ao domínio `ABERTO`, `FECHADO` ou `CANCELADO` (`ck_mov_status`), iniciando em `ABERTO` na entrada.
- **RN008** — A `data_saida`, quando preenchida, deve ser maior ou igual à `data_entrada` (`ck_mov_saida CHECK (data_saida IS NULL OR data_saida >= data_entrada)`). `data_entrada` e `data_saida` são instantes (`TIMESTAMPTZ`).
- **RN009 — Cálculo da tarifa avulsa (na saída):** Seja `permanencia` a diferença entre `data_saida` e `data_entrada`.
  1. **Cortesia:** se a permanência total em minutos for **menor ou igual** à `tolerancia_min` da tarifa, o `valor_cobrado` é `0,00` (estadia cortesia).
  2. **Horas arredondadas:** caso contrário, as horas são arredondadas **para cima** (qualquer fração de hora conta como hora cheia), e o subtotal é `horas_arredondadas × tarifa.valor_hora`.
  3. **Teto diário:** o valor cobrado por dia de permanência é limitado ao `tarifa.valor_diaria` (o subtotal nunca ultrapassa o teto da diária por período de 24 horas). Permanências superiores a um dia acumulam diárias respeitando o mesmo teto por dia.
  4. O `valor_cobrado` final é registrado em `movimentacao.valor_cobrado` (`NUMERIC(10,2)`).
- **RN010 — Mensalista não paga:** para `tipo = 'MENSALISTA'`, a saída registra `valor_cobrado = 0,00` (a cobrança ocorre via mensalidade/contas a receber do contrato, fora deste processo). Não há aplicação de tarifa avulsa.
- **RN011** — O fechamento de uma movimentação muda o `status` de `ABERTO` para `FECHADO`, preenche a `data_saida` e o `valor_cobrado`. Após o fechamento, o veículo deixa de ocupar o índice de unicidade `uq_mov_veiculo_aberta`, podendo ter nova entrada registrada.
- **RN012** — O cancelamento (`status = 'CANCELADO'`) é destinado a entradas registradas por engano; também libera o índice de unicidade do veículo.

### Atores

Administrador, Operador (usuário autenticado).

### Pré-Condição

Usuário autenticado e ativo. Veículo previamente cadastrado. Para movimentação avulsa, deve existir ao menos uma tarifa ativa. Para movimentação mensalista, deve existir contrato `ATIVO` para o veículo.

### Pós-Condição

Na entrada: movimentação criada com `status = ABERTO`, `data_entrada` registrada e tipo coerente. Na saída: movimentação com `status = FECHADO`, `data_saida` e `valor_cobrado` preenchidos conforme as regras de cálculo.

### Ativação

O usuário aciona a opção "Pátio / Movimentação" no menu operacional do sistema e seleciona "Registrar Entrada" ou "Registrar Saída".

### Fluxo Principal

#### A) Registrar Entrada

1. O usuário aciona "Registrar Entrada" e informa a placa do veículo (ou seleciona o veículo cadastrado).
2. O sistema localiza o veículo. Caso não exista, encaminha ao Fluxo de Exceção E1.
3. O sistema verifica se já existe movimentação `ABERTO` para o veículo (`uq_mov_veiculo_aberta`). Em caso positivo, encaminha ao Fluxo de Exceção E2.
4. O sistema verifica a existência de contrato mensalista `ATIVO` para o veículo:
   - **Com contrato ativo:** define `tipo = 'MENSALISTA'`, preenche `mensalista_id` com o contrato vigente e mantém `tarifa_id` nula (atende `ck_mov_tipo_coerencia`).
   - **Sem contrato ativo:** define `tipo = 'AVULSO'`, mantém `mensalista_id` nulo e preenche `tarifa_id` com a tarifa vigente/ativa.
5. O sistema cria o registro de movimentação com `data_entrada = now()`, `status = 'ABERTO'` e `valor_cobrado` nulo.
6. O sistema confirma a entrada e exibe o ticket (veículo, tipo, horário de entrada).

#### B) Registrar Saída

1. O usuário aciona "Registrar Saída" e informa a placa do veículo (ou seleciona a movimentação aberta).
2. O sistema localiza a movimentação `ABERTO` do veículo. Caso não exista, encaminha ao Fluxo de Exceção E3.
3. O sistema define `data_saida = now()` e valida que `data_saida >= data_entrada` (`ck_mov_saida`); caso a validação falhe, encaminha ao Fluxo de Exceção E4.
4. O sistema calcula o `valor_cobrado`:
   - Se `tipo = 'MENSALISTA'`: `valor_cobrado = 0,00` (mensalista não paga avulso — RN010).
   - Se `tipo = 'AVULSO'`: aplica a regra RN009 sobre a tarifa registrada (`tarifa_id`):
     a. Calcula a permanência em minutos entre `data_entrada` e `data_saida`.
     b. Se a permanência ≤ `tolerancia_min`, define `valor_cobrado = 0,00` (cortesia).
     c. Caso contrário, arredonda as horas para cima, multiplica por `valor_hora` e aplica o teto `valor_diaria` por dia de permanência.
5. O sistema atualiza a movimentação com `status = 'FECHADO'`, `data_saida` e `valor_cobrado`, liberando o índice `uq_mov_veiculo_aberta`.
6. O sistema exibe o comprovante de saída com o tempo de permanência e o valor cobrado (ou indicação de cortesia/isenção mensalista).

### Fluxos de Exceção

- **E1 — Veículo não cadastrado (entrada):** Se a placa informada não corresponder a nenhum veículo, o sistema exibe a mensagem: **"Veículo não cadastrado. Realize o cadastro antes de registrar a entrada."** e interrompe a operação.
- **E2 — Veículo já no pátio (entrada):** Se o veículo já possuir movimentação `ABERTO` (violação de `uq_mov_veiculo_aberta`), o sistema exibe a mensagem: **"Este veículo já está no pátio. Não é possível registrar uma nova entrada."** e interrompe a operação.
- **E3 — Movimentação aberta inexistente (saída):** Se não houver movimentação `ABERTO` para o veículo, o sistema exibe a mensagem: **"Não há entrada aberta para este veículo."** e interrompe a operação.
- **E4 — Data de saída anterior à entrada:** Se a data/hora de saída for anterior à de entrada (violação de `ck_mov_saida`), o sistema exibe a mensagem: **"A data de saída não pode ser anterior à data de entrada."** e não fecha a movimentação.
- **E5 — Incoerência de tipo:** Se, por alguma inconsistência interna, a movimentação apresentar `tipo = 'MENSALISTA'` sem `mensalista_id` ou `tipo = 'AVULSO'` com `mensalista_id` preenchido (violação de `ck_mov_tipo_coerencia`), o sistema exibe a mensagem: **"Tipo de movimentação incoerente com o contrato do veículo."** e não persiste a operação.
- **E6 — Tarifa indisponível (entrada avulsa):** Se, ao registrar a entrada de um veículo avulso, não existir tarifa ativa para aplicação, o sistema exibe a mensagem: **"Nenhuma tarifa ativa configurada. Cadastre uma tarifa antes de registrar entradas avulsas."** e interrompe a operação.
- **E7 — Contrato mensalista não vigente (entrada):** Se o `mensalista_id` indicado pertencer a um contrato com `status = 'SUSPENSO'` ou `status = 'CANCELADO'` (situação não barrada pela `ck_mov_tipo_coerencia`, que apenas exige `mensalista_id NOT NULL`), o serviço **bloqueia** o registro como mensalista e exibe a mensagem: **"Contrato mensalista suspenso ou cancelado. Não é possível registrar a entrada como mensalista; registre a entrada como AVULSO."**, oferecendo o registro **AVULSO** do veículo.

### Faturamento

Esta nota cruza o processo de saída com o faturamento, sem detalhá-lo aqui:

- **Saída avulsa (UC350):** após o fechamento com `valor_cobrado > 0`, é **opcional** emitir uma Nota de Serviço (NFS-e) referente à movimentação (vide [[processos-fiscais|UC403 — Emitir Nota de Serviço (NFS-e)]]; `nota_servico.movimentacao_id`), e a cobrança correspondente gera uma **conta a receber** do cliente (vide [[processos-financeiros|UC502 — Gerenciar Conta a Receber]]; origem da venda de serviço).
- **Mensalista:** não há cobrança avulsa na saída (RN010); a cobrança ocorre via **contas a receber do contrato** (`conta_receber.mensalista_id`), fora deste processo.

---

## Módulos relacionados

[[README]] · [[04-requisitos-funcionais]]

## Histórico

| Data       | Ação                                                                                  |
|------------|---------------------------------------------------------------------------------------|
| 2026-06-14 | Criação da seção: cadastros (Veículo, Tarifa, Mensalista) + processo de Movimentação de Pátio, aterrados em `database/schema.sql`. |
