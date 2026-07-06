> Links: [[README]] · [[04-requisitos-funcionais]] · [[07-dicionario-de-dados]]

# Casos de Uso — Cadastros de Pagamento e Pessoas

Este documento especifica os casos de uso dos cadastros de **Forma de Pagamento**, **Condição de Pagamento** (com as suas parcelas), **Fornecedor** (com os contatos de e-mail e telefone) e **Cliente**. Todas as regras, validações, tamanhos de campo, restrições de unicidade (`UNIQUE`), verificações (`CHECK`), chaves estrangeiras (`FK`) e a exclusão lógica (via campo `ativo`) estão aterradas no schema real do banco `estacionamento_kaneko` (`database/schema.sql`).

Convenções adotadas neste documento:

- Campos obrigatórios são marcados com asterisco (*).
- A exclusão é sempre **lógica** (campo `ativo` recebe `FALSE`) quando o registro possui vínculo com outros registros, em razão das restrições `ON DELETE RESTRICT` definidas no schema.
- As mensagens de erro indicadas nos Fluxos de Exceção são exibidas ao operador exatamente como descritas.

---

## UC101 — Gerenciar Forma de Pagamento

## Regras de Negócio

- **RN001:** O acesso ao caso de uso é permitido somente a usuário autenticado e ativo no sistema (`usuario.ativo = TRUE`).
- **RN002:** O nome da forma de pagamento é obrigatório, possui no máximo 100 caracteres e deve ser único em todo o sistema (`uq_forma_pagamento_nome`). Não é permitido cadastrar duas formas de pagamento com o mesmo nome.
- **RN003:** A descrição é obrigatória e possui no máximo 255 caracteres.
- **RN004:** Toda forma de pagamento é criada com a situação ativa (`ativo = TRUE`) por padrão.
- **RN005:** A forma de pagamento não pode ser excluída fisicamente quando estiver vinculada a notas de serviço, contas a pagar, contas a receber ou contas a pagar avulsas (restrições `ON DELETE RESTRICT`). Nesse caso, deve ser realizada a exclusão lógica (desativação), definindo `ativo = FALSE`. As parcelas de condição de pagamento não impedem a exclusão física: a chave estrangeira `fk_parcela_forma` é `ON DELETE SET NULL`, de modo que o vínculo da parcela é apenas anulado.

## Nome do Caso de Uso

Gerenciar Forma de Pagamento.

## Atores

Operador, Administrador.

## Pré-Condição

O usuário deve estar autenticado e ativo no sistema.

## Pós-Condição

A forma de pagamento é inserida, alterada, desativada ou excluída, e a base de dados reflete a operação realizada.

## Ativação

O caso de uso é ativado quando o usuário acessa a opção "Formas de Pagamento" no menu de cadastros do sistema.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Formas de Pagamento" no menu de cadastros.
2. O sistema apresenta a lista das formas de pagamento cadastradas, exibindo nome, descrição e situação (ativo/inativo).
3. O usuário informa, opcionalmente, um filtro por nome e/ou por situação.
4. O sistema atualiza a listagem conforme o filtro informado.
5. O usuário seleciona um registro para visualizar, ou aciona uma das ações "Inserir", "Alterar" ou "Excluir".

## Fluxos Alternativos

### Inserir

1. O usuário aciona a ação "Inserir".
2. O sistema apresenta o formulário de cadastro com os campos:
   - Nome* (texto, máximo 100 caracteres);
   - Descrição* (texto, máximo 255 caracteres);
   - Ativo (sim/não, padrão "sim").
3. O usuário preenche os campos e confirma a operação.
4. O sistema valida os dados informados (ver Fluxos de Exceção E1 e E2).
5. O sistema persiste a nova forma de pagamento com `ativo = TRUE` por padrão.
6. O sistema exibe a mensagem "Forma de pagamento cadastrada com sucesso." e retorna à listagem.

### Alterar

1. O usuário seleciona uma forma de pagamento na listagem e aciona a ação "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário altera os campos desejados (Nome*, Descrição*, Ativo) e confirma a operação.
4. O sistema valida os dados informados (ver Fluxos de Exceção E1 e E2).
5. O sistema persiste as alterações.
6. O sistema exibe a mensagem "Forma de pagamento alterada com sucesso." e retorna à listagem.

### Excluir

1. O usuário seleciona uma forma de pagamento na listagem e aciona a ação "Excluir".
2. O sistema solicita a confirmação da exclusão.
3. O usuário confirma a exclusão.
4. O sistema verifica a existência de vínculos do registro (ver Fluxo de Exceção E3).
5. Não havendo vínculos, o sistema exclui fisicamente o registro.
6. O sistema exibe a mensagem "Forma de pagamento excluída com sucesso." e retorna à listagem.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** Caso o usuário não informe o campo Nome ou o campo Descrição, o sistema interrompe a gravação e exibe a mensagem: **"O campo [nome do campo] é obrigatório."**
- **E2 — Nome já cadastrado:** Caso o nome informado já exista em outra forma de pagamento (violação de `uq_forma_pagamento_nome`), o sistema interrompe a gravação e exibe a mensagem: **"Já existe uma forma de pagamento cadastrada com este nome."**
- **E3 — Registro vinculado a outro:** Caso a forma de pagamento esteja vinculada a notas de serviço ou contas (a pagar, a receber ou avulsas), o sistema impede a exclusão física e exibe a mensagem: **"Esta forma de pagamento está vinculada a outros registros e não pode ser excluída. Deseja desativá-la?"** Em caso de confirmação, o sistema realiza a exclusão lógica (`ativo = FALSE`) e exibe "Forma de pagamento desativada com sucesso."

---

## UC102 — Gerenciar Condição de Pagamento

## Regras de Negócio

- **RN001:** O acesso ao caso de uso é permitido somente a usuário autenticado e ativo no sistema (`usuario.ativo = TRUE`).
- **RN002:** O nome da condição de pagamento é obrigatório, possui no máximo 100 caracteres e deve ser único em todo o sistema (`uq_condicao_pagamento_nome`).
- **RN003:** O número de parcelas é obrigatório e deve ser maior ou igual a 1 (`ck_condicao_parcelas`). O valor padrão é 1.
- **RN004:** O prazo de vencimento é definido individualmente por parcela, pelo campo `dias` de `parcela_condicao_pagamento` (dias a partir da data de emissão da nota). A condição de pagamento **não** possui campos de prazo no cabeçalho.
- **RN005:** Os percentuais de juros e de multa são obrigatórios e devem ser maiores ou iguais a 0 (`ck_condicao_percentuais`). O percentual de desconto é obrigatório e deve estar entre 0 e 100, inclusive (`ck_condicao_percentuais`).
- **RN006:** Uma condição de pagamento possui zero ou mais parcelas detalhadas em `parcela_condicao_pagamento`. Cada parcela exige um número sequencial (`numero`, maior ou igual a 1), os dias até o vencimento (`dias`, maior ou igual a 0) e o percentual do valor total (`percentual`, entre 0 e 100), conforme `ck_parcela_valores`.
- **RN007:** O número sequencial de cada parcela deve ser único dentro da mesma condição de pagamento (`uq_parcela_condicao_numero`). Não é permitido repetir o mesmo número de parcela na mesma condição.
- **RN008:** Cada parcela pode, opcionalmente, indicar uma forma de pagamento específica (`forma_pagamento_id`). Caso a forma de pagamento referenciada seja excluída, o vínculo da parcela é anulado (`ON DELETE SET NULL`).
- **RN009:** Recomenda-se que a soma dos percentuais das parcelas seja igual a 100% e que a quantidade de parcelas detalhadas seja coerente com o campo `numero_parcelas` da condição. Esta consistência é validada na camada de aplicação.
- **RN010:** As parcelas de uma condição são excluídas em cascata quando a condição é excluída fisicamente (`ON DELETE CASCADE`).
- **RN011:** A condição de pagamento não pode ser excluída fisicamente quando estiver vinculada a fornecedores, clientes, notas (entrada, saída ou serviço) ou contas (a pagar ou a receber), em razão das restrições `ON DELETE RESTRICT`. Nesse caso, deve ser realizada a exclusão lógica (`ativo = FALSE`).

## Nome do Caso de Uso

Gerenciar Condição de Pagamento.

## Atores

Operador, Administrador.

## Pré-Condição

O usuário deve estar autenticado e ativo no sistema. Para associar formas de pagamento às parcelas, deve existir ao menos uma forma de pagamento cadastrada.

## Pós-Condição

A condição de pagamento e as suas parcelas são inseridas, alteradas, desativadas ou excluídas, e a base de dados reflete a operação realizada.

## Ativação

O caso de uso é ativado quando o usuário acessa a opção "Condições de Pagamento" no menu de cadastros do sistema.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Condições de Pagamento" no menu de cadastros.
2. O sistema apresenta a lista das condições de pagamento cadastradas, exibindo nome, número de parcelas, percentual de desconto e situação (ativo/inativo).
3. O usuário informa, opcionalmente, um filtro por nome e/ou por situação.
4. O sistema atualiza a listagem conforme o filtro informado.
5. O usuário seleciona um registro para visualizar as suas parcelas, ou aciona uma das ações "Inserir", "Alterar" ou "Excluir".

## Fluxos Alternativos

### Inserir

1. O usuário aciona a ação "Inserir".
2. O sistema apresenta o formulário de cadastro com os campos da condição:
   - Nome* (texto, máximo 100 caracteres);
   - Número de parcelas* (inteiro, maior ou igual a 1, padrão 1);
   - Percentual de juros* (numérico, maior ou igual a 0, padrão 0);
   - Percentual de multa* (numérico, maior ou igual a 0, padrão 0);
   - Percentual de desconto* (numérico, entre 0 e 100, padrão 0);
   - Ativo (sim/não, padrão "sim").
3. O usuário, na grade de parcelas, adiciona uma ou mais parcelas, informando para cada uma:
   - Número da parcela* (inteiro, maior ou igual a 1, único na condição);
   - Dias para o vencimento* (inteiro, maior ou igual a 0);
   - Percentual do valor total* (numérico, entre 0 e 100);
   - Forma de pagamento (seleção, opcional).
4. O usuário confirma a operação.
5. O sistema valida os dados da condição e de cada parcela (ver Fluxos de Exceção E1, E2, E3, E4 e E5).
6. O sistema persiste a condição de pagamento com `ativo = TRUE` por padrão e as respectivas parcelas.
7. O sistema exibe a mensagem "Condição de pagamento cadastrada com sucesso." e retorna à listagem.

### Alterar

1. O usuário seleciona uma condição de pagamento na listagem e aciona a ação "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais da condição e a grade com as suas parcelas.
3. O usuário altera os campos da condição, bem como inclui, edita ou remove parcelas da grade.
4. O usuário confirma a operação.
5. O sistema valida os dados da condição e de cada parcela (ver Fluxos de Exceção E1, E2, E3, E4 e E5).
6. O sistema persiste as alterações da condição e sincroniza as parcelas (inclusões, alterações e remoções).
7. O sistema exibe a mensagem "Condição de pagamento alterada com sucesso." e retorna à listagem.

### Excluir

1. O usuário seleciona uma condição de pagamento na listagem e aciona a ação "Excluir".
2. O sistema solicita a confirmação da exclusão.
3. O usuário confirma a exclusão.
4. O sistema verifica a existência de vínculos do registro (ver Fluxo de Exceção E6).
5. Não havendo vínculos, o sistema exclui fisicamente a condição e, em cascata, as suas parcelas (`ON DELETE CASCADE`).
6. O sistema exibe a mensagem "Condição de pagamento excluída com sucesso." e retorna à listagem.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** Caso o usuário não informe um campo obrigatório da condição (Nome, Número de parcelas, Percentual de juros, Percentual de multa ou Percentual de desconto) ou de uma parcela (Número da parcela, Dias para o vencimento ou Percentual), o sistema interrompe a gravação e exibe a mensagem: **"O campo [nome do campo] é obrigatório."**
- **E2 — Nome já cadastrado:** Caso o nome informado já exista em outra condição de pagamento (violação de `uq_condicao_pagamento_nome`), o sistema interrompe a gravação e exibe a mensagem: **"Já existe uma condição de pagamento cadastrada com este nome."**
- **E3 — Valor inválido na condição:** Caso o número de parcelas seja menor que 1, os percentuais de juros ou de multa sejam negativos, ou o percentual de desconto esteja fora do intervalo de 0 a 100 (violação de `ck_condicao_parcelas` ou `ck_condicao_percentuais`), o sistema interrompe a gravação e exibe a mensagem: **"Valor inválido informado. Verifique a quantidade de parcelas e os percentuais (desconto entre 0 e 100; demais valores não podem ser negativos)."**
- **E4 — Número de parcela duplicado:** Caso seja informado o mesmo número de parcela mais de uma vez na mesma condição (violação de `uq_parcela_condicao_numero`), o sistema interrompe a gravação e exibe a mensagem: **"Já existe uma parcela com este número nesta condição de pagamento."**
- **E5 — Valor inválido na parcela:** Caso o número da parcela seja menor que 1, os dias sejam negativos, ou o percentual esteja fora do intervalo de 0 a 100 (violação de `ck_parcela_valores`), o sistema interrompe a gravação e exibe a mensagem: **"Valor inválido na parcela. O número deve ser maior ou igual a 1, os dias não podem ser negativos e o percentual deve estar entre 0 e 100."**
- **E6 — Registro vinculado a outro:** Caso a condição de pagamento esteja vinculada a fornecedores, clientes, notas ou contas, o sistema impede a exclusão física e exibe a mensagem: **"Esta condição de pagamento está vinculada a outros registros e não pode ser excluída. Deseja desativá-la?"** Em caso de confirmação, o sistema realiza a exclusão lógica (`ativo = FALSE`) e exibe "Condição de pagamento desativada com sucesso."

---

## UC103 — Gerenciar Fornecedor

## Regras de Negócio

- **RN001:** O acesso ao caso de uso é permitido somente a usuário autenticado e ativo no sistema (`usuario.ativo = TRUE`).
- **RN002:** O nome (razão social) do fornecedor é obrigatório e possui no máximo 120 caracteres.
- **RN003:** O documento (CNPJ/CPF) é obrigatório, possui no máximo 20 caracteres e deve ser único em todo o sistema (`uq_fornecedor_documento`). Não é permitido cadastrar dois fornecedores com o mesmo documento.
- **RN004:** O tipo é obrigatório e deve assumir um dos valores "FISICA" ou "JURIDICA" (`ck_fornecedor_tipo`). O valor padrão é "JURIDICA".
- **RN005:** O limite de crédito é obrigatório, deve ser maior ou igual a 0 (`ck_fornecedor_limite`) e tem valor padrão 0.
- **RN006:** A cidade (`cidade_id`) e a condição de pagamento (`condicao_pagamento_id`), quando informadas, devem referenciar registros existentes (`fk_fornecedor_cidade`, `fk_fornecedor_condicao`, ambas `ON DELETE RESTRICT` — as chaves estrangeiras garantem apenas a integridade referencial, isto é, a existência do registro referenciado). A exigência de que esses registros estejam **ativos** é regra de aplicação, validada pelo serviço, pois o schema não possui CHECK ou gatilho que rejeite referências a registros inativos.
- **RN007:** Um fornecedor pode possuir múltiplos e-mails (`fornecedor_email`) e múltiplos telefones (`fornecedor_telefone`). Em cada lista, no máximo um contato pode ser marcado como principal (`uq_fornecedor_email_principal` e `uq_fornecedor_telefone_principal`).
- **RN008:** Cada e-mail de contato exige o endereço de e-mail (obrigatório, máximo 120 caracteres) e, quando informado, o tipo deve ser um dos valores "COMERCIAL", "FINANCEIRO", "COMPRAS", "VENDAS" ou "SUPORTE" (`ck_fornecedor_email_tipo`).
- **RN009:** Cada telefone de contato exige o número (obrigatório, máximo 20 caracteres) e, quando informado, o tipo deve ser um dos valores "COMERCIAL", "RESIDENCIAL", "CELULAR", "FAX" ou "WHATSAPP" (`ck_fornecedor_telefone_tipo`).
- **RN010:** Os e-mails e telefones de contato são excluídos em cascata quando o fornecedor é excluído fisicamente (`ON DELETE CASCADE`).
- **RN011:** O fornecedor não pode ser excluído fisicamente quando estiver vinculado a notas de entrada, contas a pagar, contas a pagar avulsas ou ao relacionamento produto x fornecedor, em razão das restrições `ON DELETE RESTRICT`. Nesse caso, deve ser realizada a exclusão lógica (`ativo = FALSE`).

## Nome do Caso de Uso

Gerenciar Fornecedor.

## Atores

Operador, Administrador.

## Pré-Condição

O usuário deve estar autenticado e ativo no sistema. Para associar localização ou condição de pagamento, devem existir os respectivos registros (cidade e condição de pagamento) cadastrados e ativos.

## Pós-Condição

O fornecedor e os seus contatos de e-mail e telefone são inseridos, alterados, desativados ou excluídos, e a base de dados reflete a operação realizada.

## Ativação

O caso de uso é ativado quando o usuário acessa a opção "Fornecedores" no menu de cadastros do sistema.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Fornecedores" no menu de cadastros.
2. O sistema apresenta a lista dos fornecedores cadastrados, exibindo nome (razão social), nome fantasia, documento e situação (ativo/inativo).
3. O usuário informa, opcionalmente, um filtro por nome, por documento e/ou por situação.
4. O sistema atualiza a listagem conforme o filtro informado.
5. O usuário seleciona um registro para visualizar, ou aciona uma das ações "Inserir", "Alterar" ou "Excluir".

## Fluxos Alternativos

### Inserir

1. O usuário aciona a ação "Inserir".
2. O sistema apresenta o formulário de cadastro com os campos:
   - Nome / Razão social* (texto, máximo 120 caracteres);
   - Nome fantasia (texto, máximo 120 caracteres);
   - Apelido (texto, máximo 60 caracteres);
   - Tipo* (seleção: FISICA ou JURIDICA, padrão JURIDICA);
   - Documento / CNPJ ou CPF* (texto, máximo 20 caracteres, único);
   - RG / Inscrição estadual (texto, máximo 20 caracteres);
   - Endereço (texto, máximo 120 caracteres);
   - Número (texto, máximo 10 caracteres);
   - Complemento (texto, máximo 100 caracteres);
   - Bairro (texto, máximo 60 caracteres);
   - CEP (texto, máximo 9 caracteres);
   - Cidade (seleção, opcional);
   - Condição de pagamento (seleção, opcional);
   - Limite de crédito* (numérico, maior ou igual a 0, padrão 0);
   - Observação (texto, máximo 255 caracteres);
   - Ativo (sim/não, padrão "sim").
3. Na grade de e-mails, o usuário adiciona zero ou mais e-mails, informando para cada um:
   - E-mail* (texto, máximo 120 caracteres);
   - Tipo (seleção: COMERCIAL, FINANCEIRO, COMPRAS, VENDAS ou SUPORTE; padrão COMERCIAL);
   - Principal (sim/não; no máximo um por fornecedor);
   - Ativo (sim/não, padrão "sim").
4. Na grade de telefones, o usuário adiciona zero ou mais telefones, informando para cada um:
   - Telefone* (texto, máximo 20 caracteres);
   - Tipo (seleção: COMERCIAL, RESIDENCIAL, CELULAR, FAX ou WHATSAPP; padrão COMERCIAL);
   - Principal (sim/não; no máximo um por fornecedor);
   - Ativo (sim/não, padrão "sim").
5. O usuário confirma a operação.
6. O sistema valida os dados do fornecedor e dos contatos (ver Fluxos de Exceção E1, E2, E3, E4 e E5).
7. O sistema persiste o fornecedor com `ativo = TRUE` por padrão e os respectivos contatos de e-mail e telefone.
8. O sistema exibe a mensagem "Fornecedor cadastrado com sucesso." e retorna à listagem.

### Alterar

1. O usuário seleciona um fornecedor na listagem e aciona a ação "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do fornecedor e as grades com os seus e-mails e telefones.
3. O usuário altera os campos desejados, bem como inclui, edita ou remove e-mails e telefones das grades.
4. O usuário confirma a operação.
5. O sistema valida os dados do fornecedor e dos contatos (ver Fluxos de Exceção E1, E2, E3, E4 e E5).
6. O sistema persiste as alterações do fornecedor e sincroniza os contatos (inclusões, alterações e remoções).
7. O sistema exibe a mensagem "Fornecedor alterado com sucesso." e retorna à listagem.

### Excluir

1. O usuário seleciona um fornecedor na listagem e aciona a ação "Excluir".
2. O sistema solicita a confirmação da exclusão.
3. O usuário confirma a exclusão.
4. O sistema verifica a existência de vínculos do registro (ver Fluxo de Exceção E6).
5. Não havendo vínculos, o sistema exclui fisicamente o fornecedor e, em cascata, os seus e-mails e telefones (`ON DELETE CASCADE`).
6. O sistema exibe a mensagem "Fornecedor excluído com sucesso." e retorna à listagem.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** Caso o usuário não informe um campo obrigatório do fornecedor (Nome, Tipo, Documento ou Limite de crédito) ou de um contato (E-mail na grade de e-mails, ou Telefone na grade de telefones), o sistema interrompe a gravação e exibe a mensagem: **"O campo [nome do campo] é obrigatório."**
- **E2 — Documento já cadastrado:** Caso o documento informado já exista em outro fornecedor (violação de `uq_fornecedor_documento`), o sistema interrompe a gravação e exibe a mensagem: **"Já existe um fornecedor cadastrado com este documento."**
- **E3 — Valor inválido:** Caso o tipo informado seja diferente de "FISICA" ou "JURIDICA" (`ck_fornecedor_tipo`), o limite de crédito seja negativo (`ck_fornecedor_limite`), ou o tipo de um e-mail ou telefone esteja fora dos valores permitidos (`ck_fornecedor_email_tipo`, `ck_fornecedor_telefone_tipo`), o sistema interrompe a gravação e exibe a mensagem: **"Valor inválido informado. Verifique o tipo do fornecedor, o limite de crédito e os tipos de contato."**
- **E4 — Mais de um contato principal:** Caso seja marcado mais de um e-mail como principal, ou mais de um telefone como principal (violação de `uq_fornecedor_email_principal` ou `uq_fornecedor_telefone_principal`), o sistema interrompe a gravação e exibe a mensagem: **"Apenas um e-mail e um telefone podem ser marcados como principal."**
- **E5 — Referência inexistente ou inativa:** Caso a cidade ou a condição de pagamento informada não exista, as chaves estrangeiras (`fk_fornecedor_cidade`, `fk_fornecedor_condicao`) impedem a gravação; caso o registro exista mas esteja inativo, a validação de aplicação interrompe a operação (o banco não rejeita referências a registros inativos). Em ambos os casos o sistema exibe a mensagem: **"O registro relacionado informado não foi encontrado ou está inativo."**
- **E6 — Registro vinculado a outro:** Caso o fornecedor esteja vinculado a notas de entrada, contas a pagar, contas a pagar avulsas ou ao relacionamento produto x fornecedor, o sistema impede a exclusão física e exibe a mensagem: **"Este fornecedor está vinculado a outros registros e não pode ser excluído. Deseja desativá-lo?"** Em caso de confirmação, o sistema realiza a exclusão lógica (`ativo = FALSE`) e exibe "Fornecedor desativado com sucesso."

---

## UC104 — Gerenciar Cliente

## Regras de Negócio

- **RN001:** O acesso ao caso de uso é permitido somente a usuário autenticado e ativo no sistema (`usuario.ativo = TRUE`).
- **RN002:** O nome do cliente é obrigatório e possui no máximo 120 caracteres.
- **RN003:** O documento (CPF/CNPJ) é obrigatório, possui no máximo 20 caracteres e deve ser único em todo o sistema (`uq_cliente_documento`). Não é permitido cadastrar dois clientes com o mesmo documento.
- **RN004:** O tipo é obrigatório e deve assumir um dos valores "FISICA" ou "JURIDICA" (`ck_cliente_tipo`). O valor padrão é "FISICA".
- **RN005:** Quando informado, o sexo deve ser um dos valores "MASCULINO", "FEMININO" ou "OUTRO" (`ck_cliente_sexo`).
- **RN006:** Quando informado, o estado civil deve ser um dos valores "SOLTEIRO", "CASADO", "SEPARADO", "DIVORCIADO", "VIUVO" ou "OUTRO" (`ck_cliente_civil`).
- **RN007:** O limite de crédito é obrigatório, deve ser maior ou igual a 0 (`ck_cliente_limite`) e tem valor padrão 0.
- **RN008:** A cidade (`cidade_id`) e a condição de pagamento (`condicao_pagamento_id`), quando informadas, devem referenciar registros existentes (`fk_cliente_cidade`, `fk_cliente_condicao`, ambas `ON DELETE RESTRICT` — as chaves estrangeiras garantem apenas a integridade referencial, isto é, a existência do registro referenciado). A exigência de que esses registros estejam **ativos** é regra de aplicação, validada pelo serviço, pois o schema não possui CHECK ou gatilho que rejeite referências a registros inativos.
- **RN009:** O cliente não pode ser excluído fisicamente quando estiver vinculado a veículos, contratos de mensalista, notas de saída, notas de serviço ou contas a receber, em razão das restrições `ON DELETE RESTRICT`. Nesse caso, deve ser realizada a exclusão lógica (`ativo = FALSE`).

## Nome do Caso de Uso

Gerenciar Cliente.

## Atores

Operador, Administrador.

## Pré-Condição

O usuário deve estar autenticado e ativo no sistema. Para associar localização ou condição de pagamento, devem existir os respectivos registros (cidade e condição de pagamento) cadastrados e ativos.

## Pós-Condição

O cliente é inserido, alterado, desativado ou excluído, e a base de dados reflete a operação realizada.

## Ativação

O caso de uso é ativado quando o usuário acessa a opção "Clientes" no menu de cadastros do sistema.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Clientes" no menu de cadastros.
2. O sistema apresenta a lista dos clientes cadastrados, exibindo nome, documento, telefone e situação (ativo/inativo).
3. O usuário informa, opcionalmente, um filtro por nome, por documento e/ou por situação.
4. O sistema atualiza a listagem conforme o filtro informado.
5. O usuário seleciona um registro para visualizar, ou aciona uma das ações "Inserir", "Alterar" ou "Excluir".

## Fluxos Alternativos

### Inserir

1. O usuário aciona a ação "Inserir".
2. O sistema apresenta o formulário de cadastro com os campos:
   - Nome* (texto, máximo 120 caracteres);
   - Apelido (texto, máximo 60 caracteres);
   - Tipo* (seleção: FISICA ou JURIDICA, padrão FISICA);
   - Documento / CPF ou CNPJ* (texto, máximo 20 caracteres, único);
   - RG / Inscrição estadual (texto, máximo 20 caracteres);
   - Data de nascimento (data);
   - Sexo (seleção: MASCULINO, FEMININO ou OUTRO);
   - Estado civil (seleção: SOLTEIRO, CASADO, SEPARADO, DIVORCIADO, VIUVO ou OUTRO);
   - Telefone (texto, máximo 20 caracteres);
   - E-mail (texto, máximo 120 caracteres);
   - Endereço (texto, máximo 120 caracteres);
   - Número (texto, máximo 10 caracteres);
   - Complemento (texto, máximo 100 caracteres);
   - Bairro (texto, máximo 60 caracteres);
   - CEP (texto, máximo 9 caracteres);
   - Cidade (seleção, opcional);
   - Condição de pagamento (seleção, opcional);
   - Limite de crédito* (numérico, maior ou igual a 0, padrão 0);
   - Observação (texto, máximo 255 caracteres);
   - Ativo (sim/não, padrão "sim").
3. O usuário preenche os campos e confirma a operação.
4. O sistema valida os dados informados (ver Fluxos de Exceção E1, E2, E3 e E4).
5. O sistema persiste o novo cliente com `ativo = TRUE` por padrão.
6. O sistema exibe a mensagem "Cliente cadastrado com sucesso." e retorna à listagem.

### Alterar

1. O usuário seleciona um cliente na listagem e aciona a ação "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário altera os campos desejados e confirma a operação.
4. O sistema valida os dados informados (ver Fluxos de Exceção E1, E2, E3 e E4).
5. O sistema persiste as alterações.
6. O sistema exibe a mensagem "Cliente alterado com sucesso." e retorna à listagem.

### Excluir

1. O usuário seleciona um cliente na listagem e aciona a ação "Excluir".
2. O sistema solicita a confirmação da exclusão.
3. O usuário confirma a exclusão.
4. O sistema verifica a existência de vínculos do registro (ver Fluxo de Exceção E5).
5. Não havendo vínculos, o sistema exclui fisicamente o registro.
6. O sistema exibe a mensagem "Cliente excluído com sucesso." e retorna à listagem.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** Caso o usuário não informe um campo obrigatório (Nome, Tipo, Documento ou Limite de crédito), o sistema interrompe a gravação e exibe a mensagem: **"O campo [nome do campo] é obrigatório."**
- **E2 — Documento já cadastrado:** Caso o documento informado já exista em outro cliente (violação de `uq_cliente_documento`), o sistema interrompe a gravação e exibe a mensagem: **"Já existe um cliente cadastrado com este documento."**
- **E3 — Valor inválido:** Caso o tipo informado seja diferente de "FISICA" ou "JURIDICA" (`ck_cliente_tipo`), o sexo seja diferente dos valores permitidos (`ck_cliente_sexo`), o estado civil seja diferente dos valores permitidos (`ck_cliente_civil`), ou o limite de crédito seja negativo (`ck_cliente_limite`), o sistema interrompe a gravação e exibe a mensagem: **"Valor inválido informado. Verifique o tipo, o sexo, o estado civil e o limite de crédito."**
- **E4 — Referência inexistente ou inativa:** Caso a cidade ou a condição de pagamento informada não exista, as chaves estrangeiras (`fk_cliente_cidade`, `fk_cliente_condicao`) impedem a gravação; caso o registro exista mas esteja inativo, a validação de aplicação interrompe a operação (o banco não rejeita referências a registros inativos). Em ambos os casos o sistema exibe a mensagem: **"O registro relacionado informado não foi encontrado ou está inativo."**
- **E5 — Registro vinculado a outro:** Caso o cliente esteja vinculado a veículos, contratos de mensalista, notas de saída, notas de serviço ou contas a receber, o sistema impede a exclusão física e exibe a mensagem: **"Este cliente está vinculado a outros registros e não pode ser excluído. Deseja desativá-lo?"** Em caso de confirmação, o sistema realiza a exclusão lógica (`ativo = FALSE`) e exibe "Cliente desativado com sucesso."

---

## Módulos relacionados

[[04-requisitos-funcionais]] · [[07-dicionario-de-dados]] · [[03-der]]

## Histórico

| Data | Ação |
|------------|-----------------------------------------------------------------|
| 2026-06-14 | Criação dos casos de uso UC101–UC104 (Forma de Pagamento, Condição de Pagamento, Fornecedor e Cliente), aterrados no schema real `database/schema.sql`. |
