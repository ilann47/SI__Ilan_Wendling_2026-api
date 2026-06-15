> Links: [[README]] · [[04-requisitos-funcionais]] · [[07-dicionario-de-dados]]

# Casos de Uso — Cadastros de Geografia e Recursos Humanos

Este documento especifica os casos de uso dos cadastros de apoio do sistema Estacionamento Kaneko relativos à Geografia (País, Estado, Cidade) e aos Recursos Humanos e Acesso (Cargo, Funcionário, Usuário). Todas as regras de negócio, validações, tamanhos de campo, restrições de unicidade, verificações (*checks*) e chaves estrangeiras foram aterradas no esquema físico do banco de dados (`database/schema.sql`).

Convenções adotadas neste documento:

- Campos obrigatórios são marcados com asterisco (*).
- A exclusão de registros que possuam vínculos é realizada por exclusão lógica, por meio do campo `ativo` (inativação), preservando a integridade referencial.
- O acesso a qualquer funcionalidade exige usuário previamente autenticado no sistema.

---

## UC001 — Gerenciar País

## Regras de Negócio

- **RN001:** O acesso à funcionalidade é permitido somente a usuário autenticado no sistema.
- **RN002:** O campo Nome é de preenchimento obrigatório e limitado a 60 caracteres.
- **RN003:** O Nome do país deve ser único no sistema (restrição `uq_pais_nome`). Não é permitido cadastrar dois países com o mesmo nome.
- **RN004:** O campo Sigla, quando informado, é limitado a 3 caracteres (padrão ISO, ex.: BRA).
- **RN005:** O campo Código, quando informado, é limitado a 5 caracteres.
- **RN006:** O campo Nacionalidade (gentílico, ex.: Brasileiro), quando informado, é limitado a 100 caracteres.
- **RN007:** Todo país é cadastrado, por padrão, como Ativo.
- **RN008:** A exclusão física de um país é vedada quando existirem Estados, Funcionários (nacionalidade) ou demais registros a ele vinculados (restrição `ON DELETE RESTRICT`). Nesse caso, o sistema oferece a inativação (exclusão lógica) do registro.

## Nome do Caso de Uso

Gerenciar País.

## Atores

Administrador, Operador (usuário autenticado).

## Pré-Condição

O usuário deve estar autenticado no sistema e possuir permissão de acesso ao módulo de cadastros.

## Pós-Condição

O registro de país encontra-se inserido, alterado, inativado ou excluído na base de dados, conforme a operação executada.

## Ativação

O caso de uso é ativado quando o usuário seleciona a opção "País" no menu de cadastros do sistema.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "País" no menu de cadastros.
2. O sistema apresenta a tela de consulta de países, com a relação de registros cadastrados e os campos de filtro (Nome, Sigla e situação Ativo/Inativo).
3. O usuário informa, opcionalmente, os critérios de filtro desejados e aciona o comando "Pesquisar".
4. O sistema consulta a base de dados e exibe a lista de países que atendem aos critérios informados, apresentando as colunas Nome, Sigla, Código, Nacionalidade e situação (Ativo/Inativo).
5. O sistema disponibiliza as ações de Inserir, Alterar e Excluir.

## Fluxos Alternativos

### Inserir

1. O usuário aciona o comando "Inserir" na tela de consulta.
2. O sistema apresenta o formulário de cadastro de país com os campos:
   - Nome * (até 60 caracteres)
   - Sigla (até 3 caracteres)
   - Código (até 5 caracteres)
   - Nacionalidade (até 100 caracteres)
   - Ativo (sim/não — padrão: sim)
3. O usuário preenche os campos e aciona o comando "Salvar".
4. O sistema valida o preenchimento do campo obrigatório (Nome).
5. O sistema valida a unicidade do Nome.
6. O sistema persiste o novo registro e exibe a mensagem "Registro salvo com sucesso.".
7. O sistema retorna à tela de consulta com a lista atualizada.

### Alterar

1. O usuário seleciona um país na lista de consulta e aciona o comando "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário altera os campos desejados (Nome *, Sigla, Código, Nacionalidade, Ativo) e aciona o comando "Salvar".
4. O sistema valida o preenchimento do campo obrigatório e a unicidade do Nome.
5. O sistema persiste as alterações e exibe a mensagem "Registro alterado com sucesso.".
6. O sistema retorna à tela de consulta com a lista atualizada.

### Excluir

1. O usuário seleciona um país na lista de consulta e aciona o comando "Excluir".
2. O sistema solicita a confirmação da exclusão.
3. O usuário confirma a operação.
4. O sistema verifica a existência de registros vinculados (Estados, Funcionários, Fornecedores, Clientes).
5. Não havendo vínculos, o sistema exclui fisicamente o registro e exibe a mensagem "Registro excluído com sucesso.".
6. O sistema retorna à tela de consulta com a lista atualizada.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** Nos fluxos Inserir ou Alterar, caso o campo Nome não esteja preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo Nome é de preenchimento obrigatório."**. O fluxo retorna ao formulário para correção.
- **E2 — Nome já cadastrado:** Nos fluxos Inserir ou Alterar, caso o Nome informado já exista para outro país, o sistema interrompe a gravação e exibe a mensagem: **"Já existe um país cadastrado com este nome."**. O fluxo retorna ao formulário para correção.
- **E3 — Registro vinculado a outro:** No fluxo Excluir, caso o país possua registros vinculados, o sistema impede a exclusão física e exibe a mensagem: **"Este país está vinculado a outros registros e não pode ser excluído. Deseja desativá-lo?"**. Confirmada a opção, o sistema realiza a exclusão lógica, atribuindo "não" ao campo Ativo, e exibe a mensagem "Registro desativado com sucesso.". Caso o usuário não confirme, a operação é cancelada e o registro é mantido inalterado.

---

## UC002 — Gerenciar Estado

## Regras de Negócio

- **RN001:** O acesso à funcionalidade é permitido somente a usuário autenticado no sistema.
- **RN002:** O campo Nome é de preenchimento obrigatório e limitado a 60 caracteres.
- **RN003:** O campo UF (sigla, ex.: PR, SP) é de preenchimento obrigatório e limitado a 3 caracteres (padrão ISO 3166-2).
- **RN004:** O campo País é de preenchimento obrigatório e deve referenciar um país previamente cadastrado (chave estrangeira `fk_estado_pais`).
- **RN005:** A combinação País + UF deve ser única no sistema (restrição `uq_estado_uf_pais`). Não é permitido cadastrar a mesma UF para o mesmo país mais de uma vez.
- **RN006:** Todo estado é cadastrado, por padrão, como Ativo.
- **RN007:** A exclusão física de um estado é vedada quando existirem Cidades a ele vinculadas (restrição `ON DELETE RESTRICT`). Nesse caso, o sistema oferece a inativação (exclusão lógica) do registro.

## Nome do Caso de Uso

Gerenciar Estado.

## Atores

Administrador, Operador (usuário autenticado).

## Pré-Condição

O usuário deve estar autenticado no sistema e possuir permissão de acesso ao módulo de cadastros. Deve existir, no mínimo, um País cadastrado.

## Pós-Condição

O registro de estado encontra-se inserido, alterado, inativado ou excluído na base de dados, conforme a operação executada.

## Ativação

O caso de uso é ativado quando o usuário seleciona a opção "Estado" no menu de cadastros do sistema.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Estado" no menu de cadastros.
2. O sistema apresenta a tela de consulta de estados, com a relação de registros cadastrados e os campos de filtro (Nome, UF, País e situação Ativo/Inativo).
3. O usuário informa, opcionalmente, os critérios de filtro desejados e aciona o comando "Pesquisar".
4. O sistema consulta a base de dados e exibe a lista de estados que atendem aos critérios, apresentando as colunas Nome, UF, País e situação (Ativo/Inativo).
5. O sistema disponibiliza as ações de Inserir, Alterar e Excluir.

## Fluxos Alternativos

### Inserir

1. O usuário aciona o comando "Inserir" na tela de consulta.
2. O sistema apresenta o formulário de cadastro de estado com os campos:
   - Nome * (até 60 caracteres)
   - UF * (até 3 caracteres)
   - País * (seleção de país cadastrado)
   - Ativo (sim/não — padrão: sim)
3. O usuário preenche os campos e aciona o comando "Salvar".
4. O sistema valida o preenchimento dos campos obrigatórios (Nome, UF e País).
5. O sistema valida a unicidade da combinação País + UF.
6. O sistema persiste o novo registro e exibe a mensagem "Registro salvo com sucesso.".
7. O sistema retorna à tela de consulta com a lista atualizada.

### Alterar

1. O usuário seleciona um estado na lista de consulta e aciona o comando "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário altera os campos desejados (Nome *, UF *, País *, Ativo) e aciona o comando "Salvar".
4. O sistema valida o preenchimento dos campos obrigatórios e a unicidade da combinação País + UF.
5. O sistema persiste as alterações e exibe a mensagem "Registro alterado com sucesso.".
6. O sistema retorna à tela de consulta com a lista atualizada.

### Excluir

1. O usuário seleciona um estado na lista de consulta e aciona o comando "Excluir".
2. O sistema solicita a confirmação da exclusão.
3. O usuário confirma a operação.
4. O sistema verifica a existência de Cidades vinculadas ao estado.
5. Não havendo vínculos, o sistema exclui fisicamente o registro e exibe a mensagem "Registro excluído com sucesso.".
6. O sistema retorna à tela de consulta com a lista atualizada.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** Nos fluxos Inserir ou Alterar, caso algum dos campos Nome, UF ou País não esteja preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo [Nome/UF/País] é de preenchimento obrigatório."**, indicando o campo correspondente. O fluxo retorna ao formulário para correção.
- **E2 — UF já cadastrada para o país:** Nos fluxos Inserir ou Alterar, caso a combinação País + UF informada já exista, o sistema interrompe a gravação e exibe a mensagem: **"Já existe um estado cadastrado com esta UF para o país selecionado."**. O fluxo retorna ao formulário para correção.
- **E3 — Registro vinculado a outro:** No fluxo Excluir, caso o estado possua Cidades vinculadas, o sistema impede a exclusão física e exibe a mensagem: **"Este estado está vinculado a uma ou mais cidades e não pode ser excluído. Deseja desativá-lo?"**. Confirmada a opção, o sistema realiza a exclusão lógica, atribuindo "não" ao campo Ativo, e exibe a mensagem "Registro desativado com sucesso.". Caso o usuário não confirme, a operação é cancelada e o registro é mantido inalterado.

---

## UC003 — Gerenciar Cidade

## Regras de Negócio

- **RN001:** O acesso à funcionalidade é permitido somente a usuário autenticado no sistema.
- **RN002:** O campo Nome é de preenchimento obrigatório e limitado a 80 caracteres.
- **RN003:** O campo Estado é de preenchimento obrigatório e deve referenciar um estado previamente cadastrado (chave estrangeira `fk_cidade_estado`).
- **RN004:** A combinação Estado + Nome deve ser única no sistema (restrição `uq_cidade_nome_estado`). Não é permitido cadastrar duas cidades com o mesmo nome dentro do mesmo estado.
- **RN005:** O campo DDD, quando informado, é limitado a 4 caracteres.
- **RN006:** O campo Código IBGE, quando informado, é limitado a 10 caracteres.
- **RN007:** Toda cidade é cadastrada, por padrão, como Ativa.
- **RN008:** A exclusão física de uma cidade é vedada quando existirem Funcionários, Fornecedores ou Clientes a ela vinculados (restrição `ON DELETE RESTRICT`). Nesse caso, o sistema oferece a inativação (exclusão lógica) do registro.

## Nome do Caso de Uso

Gerenciar Cidade.

## Atores

Administrador, Operador (usuário autenticado).

## Pré-Condição

O usuário deve estar autenticado no sistema e possuir permissão de acesso ao módulo de cadastros. Deve existir, no mínimo, um Estado cadastrado.

## Pós-Condição

O registro de cidade encontra-se inserido, alterado, inativado ou excluído na base de dados, conforme a operação executada.

## Ativação

O caso de uso é ativado quando o usuário seleciona a opção "Cidade" no menu de cadastros do sistema.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Cidade" no menu de cadastros.
2. O sistema apresenta a tela de consulta de cidades, com a relação de registros cadastrados e os campos de filtro (Nome, Estado, DDD e situação Ativo/Inativo).
3. O usuário informa, opcionalmente, os critérios de filtro desejados e aciona o comando "Pesquisar".
4. O sistema consulta a base de dados e exibe a lista de cidades que atendem aos critérios, apresentando as colunas Nome, Estado, DDD, Código IBGE e situação (Ativo/Inativo).
5. O sistema disponibiliza as ações de Inserir, Alterar e Excluir.

## Fluxos Alternativos

### Inserir

1. O usuário aciona o comando "Inserir" na tela de consulta.
2. O sistema apresenta o formulário de cadastro de cidade com os campos:
   - Nome * (até 80 caracteres)
   - Estado * (seleção de estado cadastrado)
   - DDD (até 4 caracteres)
   - Código IBGE (até 10 caracteres)
   - Ativo (sim/não — padrão: sim)
3. O usuário preenche os campos e aciona o comando "Salvar".
4. O sistema valida o preenchimento dos campos obrigatórios (Nome e Estado).
5. O sistema valida a unicidade da combinação Estado + Nome.
6. O sistema persiste o novo registro e exibe a mensagem "Registro salvo com sucesso.".
7. O sistema retorna à tela de consulta com a lista atualizada.

### Alterar

1. O usuário seleciona uma cidade na lista de consulta e aciona o comando "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário altera os campos desejados (Nome *, Estado *, DDD, Código IBGE, Ativo) e aciona o comando "Salvar".
4. O sistema valida o preenchimento dos campos obrigatórios e a unicidade da combinação Estado + Nome.
5. O sistema persiste as alterações e exibe a mensagem "Registro alterado com sucesso.".
6. O sistema retorna à tela de consulta com a lista atualizada.

### Excluir

1. O usuário seleciona uma cidade na lista de consulta e aciona o comando "Excluir".
2. O sistema solicita a confirmação da exclusão.
3. O usuário confirma a operação.
4. O sistema verifica a existência de registros vinculados (Funcionários, Fornecedores, Clientes).
5. Não havendo vínculos, o sistema exclui fisicamente o registro e exibe a mensagem "Registro excluído com sucesso.".
6. O sistema retorna à tela de consulta com a lista atualizada.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** Nos fluxos Inserir ou Alterar, caso algum dos campos Nome ou Estado não esteja preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo [Nome/Estado] é de preenchimento obrigatório."**, indicando o campo correspondente. O fluxo retorna ao formulário para correção.
- **E2 — Cidade já cadastrada para o estado:** Nos fluxos Inserir ou Alterar, caso a combinação Estado + Nome informada já exista, o sistema interrompe a gravação e exibe a mensagem: **"Já existe uma cidade cadastrada com este nome para o estado selecionado."**. O fluxo retorna ao formulário para correção.
- **E3 — Registro vinculado a outro:** No fluxo Excluir, caso a cidade possua registros vinculados, o sistema impede a exclusão física e exibe a mensagem: **"Esta cidade está vinculada a outros registros e não pode ser excluída. Deseja desativá-la?"**. Confirmada a opção, o sistema realiza a exclusão lógica, atribuindo "não" ao campo Ativo, e exibe a mensagem "Registro desativado com sucesso.". Caso o usuário não confirme, a operação é cancelada e o registro é mantido inalterado.

---

## UC004 — Gerenciar Cargo

## Regras de Negócio

- **RN001:** O acesso à funcionalidade é permitido somente a usuário autenticado no sistema.
- **RN002:** O campo Nome é de preenchimento obrigatório e limitado a 100 caracteres.
- **RN003:** O Nome do cargo deve ser único no sistema (restrição `uq_cargo_nome`). Não é permitido cadastrar dois cargos com o mesmo nome.
- **RN004:** O campo Descrição, quando informado, é limitado a 255 caracteres.
- **RN005:** O campo Salário Base é numérico, com até 10 dígitos e 2 casas decimais, e não pode ser negativo (verificação `ck_cargo_valores`). Na ausência de valor informado, assume-se zero.
- **RN006:** O campo Carga Horária (horas mensais), quando informado, é numérico com até 6 dígitos e 2 casas decimais e não pode ser negativo (verificação `ck_cargo_valores`).
- **RN007:** O campo Requer CNH é booleano (sim/não), com valor padrão "não".
- **RN008:** Todo cargo é cadastrado, por padrão, como Ativo.
- **RN009:** A exclusão física de um cargo é vedada quando existirem Funcionários a ele vinculados (restrição `ON DELETE RESTRICT`). Nesse caso, o sistema oferece a inativação (exclusão lógica) do registro.

## Nome do Caso de Uso

Gerenciar Cargo.

## Atores

Administrador, Operador (usuário autenticado).

## Pré-Condição

O usuário deve estar autenticado no sistema e possuir permissão de acesso ao módulo de cadastros.

## Pós-Condição

O registro de cargo encontra-se inserido, alterado, inativado ou excluído na base de dados, conforme a operação executada.

## Ativação

O caso de uso é ativado quando o usuário seleciona a opção "Cargo" no menu de cadastros do sistema.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Cargo" no menu de cadastros.
2. O sistema apresenta a tela de consulta de cargos, com a relação de registros cadastrados e os campos de filtro (Nome e situação Ativo/Inativo).
3. O usuário informa, opcionalmente, os critérios de filtro desejados e aciona o comando "Pesquisar".
4. O sistema consulta a base de dados e exibe a lista de cargos que atendem aos critérios, apresentando as colunas Nome, Salário Base, Carga Horária, Requer CNH e situação (Ativo/Inativo).
5. O sistema disponibiliza as ações de Inserir, Alterar e Excluir.

## Fluxos Alternativos

### Inserir

1. O usuário aciona o comando "Inserir" na tela de consulta.
2. O sistema apresenta o formulário de cadastro de cargo com os campos:
   - Nome * (até 100 caracteres)
   - Descrição (até 255 caracteres)
   - Salário Base (numérico, 10 dígitos e 2 casas decimais — padrão: 0)
   - Carga Horária (numérico, horas mensais)
   - Requer CNH (sim/não — padrão: não)
   - Ativo (sim/não — padrão: sim)
3. O usuário preenche os campos e aciona o comando "Salvar".
4. O sistema valida o preenchimento do campo obrigatório (Nome).
5. O sistema valida a unicidade do Nome e a não negatividade dos valores (Salário Base e Carga Horária).
6. O sistema persiste o novo registro e exibe a mensagem "Registro salvo com sucesso.".
7. O sistema retorna à tela de consulta com a lista atualizada.

### Alterar

1. O usuário seleciona um cargo na lista de consulta e aciona o comando "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário altera os campos desejados (Nome *, Descrição, Salário Base, Carga Horária, Requer CNH, Ativo) e aciona o comando "Salvar".
4. O sistema valida o preenchimento do campo obrigatório, a unicidade do Nome e a não negatividade dos valores.
5. O sistema persiste as alterações e exibe a mensagem "Registro alterado com sucesso.".
6. O sistema retorna à tela de consulta com a lista atualizada.

### Excluir

1. O usuário seleciona um cargo na lista de consulta e aciona o comando "Excluir".
2. O sistema solicita a confirmação da exclusão.
3. O usuário confirma a operação.
4. O sistema verifica a existência de Funcionários vinculados ao cargo.
5. Não havendo vínculos, o sistema exclui fisicamente o registro e exibe a mensagem "Registro excluído com sucesso.".
6. O sistema retorna à tela de consulta com a lista atualizada.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** Nos fluxos Inserir ou Alterar, caso o campo Nome não esteja preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo Nome é de preenchimento obrigatório."**. O fluxo retorna ao formulário para correção.
- **E2 — Nome já cadastrado:** Nos fluxos Inserir ou Alterar, caso o Nome informado já exista para outro cargo, o sistema interrompe a gravação e exibe a mensagem: **"Já existe um cargo cadastrado com este nome."**. O fluxo retorna ao formulário para correção.
- **E3 — Valor inválido:** Nos fluxos Inserir ou Alterar, caso o Salário Base ou a Carga Horária sejam informados com valor negativo, o sistema interrompe a gravação e exibe a mensagem: **"O valor informado não pode ser negativo."**. O fluxo retorna ao formulário para correção.
- **E4 — Registro vinculado a outro:** No fluxo Excluir, caso o cargo possua Funcionários vinculados, o sistema impede a exclusão física e exibe a mensagem: **"Este cargo está vinculado a um ou mais funcionários e não pode ser excluído. Deseja desativá-lo?"**. Confirmada a opção, o sistema realiza a exclusão lógica, atribuindo "não" ao campo Ativo, e exibe a mensagem "Registro desativado com sucesso.". Caso o usuário não confirme, a operação é cancelada e o registro é mantido inalterado.

---

## UC005 — Gerenciar Funcionário

## Regras de Negócio

- **RN001:** O acesso à funcionalidade é permitido somente a usuário autenticado no sistema.
- **RN002:** O campo Nome é de preenchimento obrigatório e limitado a 120 caracteres.
- **RN003:** O campo CPF é de preenchimento obrigatório, limitado a 20 caracteres, e deve ser único no sistema (restrição `uq_funcionario_cpf`). Não é permitido cadastrar dois funcionários com o mesmo CPF.
- **RN004:** O campo Cargo é de preenchimento obrigatório e deve referenciar um cargo previamente cadastrado (chave estrangeira `fk_funcionario_cargo`).
- **RN005:** O campo Tipo é de preenchimento obrigatório e restrito aos valores "Física" ou "Jurídica" (verificação `ck_funcionario_tipo`); o valor padrão é "Física".
- **RN006:** O campo Sexo, quando informado, é restrito aos valores "Masculino", "Feminino" ou "Outro" (verificação `ck_funcionario_sexo`).
- **RN007:** O campo Estado Civil, quando informado, é restrito aos valores "Solteiro", "Casado", "Separado", "Divorciado", "Viúvo" ou "Outro" (verificação `ck_funcionario_civil`).
- **RN008:** O campo Salário, quando informado, é numérico com até 10 dígitos e 2 casas decimais e não pode ser negativo (verificação `ck_funcionario_salario`).
- **RN009:** Os campos Cidade e Nacionalidade (país), quando informados, devem referenciar registros previamente cadastrados (chaves estrangeiras `fk_funcionario_cidade` e `fk_funcionario_nacionalidade`).
- **RN010:** A Data de Admissão é obrigatória; na ausência de valor informado, assume a data corrente.
- **RN011:** Aplicam-se os limites de tamanho dos demais campos textuais: Apelido (60), RG/Inscrição Estadual (20), Telefone (20), E-mail (120), Endereço (120), Número (10), Complemento (100), Bairro (60), CEP (9), CNH (20) e Observação (255).
- **RN012:** Todo funcionário é cadastrado, por padrão, como Ativo.
- **RN013:** A exclusão física de um funcionário é vedada quando existir Usuário a ele vinculado (restrição `ON DELETE RESTRICT`). Nesse caso, o sistema oferece a inativação (exclusão lógica) do registro.

## Nome do Caso de Uso

Gerenciar Funcionário.

## Atores

Administrador, Operador (usuário autenticado).

## Pré-Condição

O usuário deve estar autenticado no sistema e possuir permissão de acesso ao módulo de cadastros. Deve existir, no mínimo, um Cargo cadastrado.

## Pós-Condição

O registro de funcionário encontra-se inserido, alterado, inativado ou excluído na base de dados, conforme a operação executada.

## Ativação

O caso de uso é ativado quando o usuário seleciona a opção "Funcionário" no menu de cadastros do sistema.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Funcionário" no menu de cadastros.
2. O sistema apresenta a tela de consulta de funcionários, com a relação de registros cadastrados e os campos de filtro (Nome, CPF, Cargo e situação Ativo/Inativo).
3. O usuário informa, opcionalmente, os critérios de filtro desejados e aciona o comando "Pesquisar".
4. O sistema consulta a base de dados e exibe a lista de funcionários que atendem aos critérios, apresentando as colunas Nome, CPF, Cargo, Telefone e situação (Ativo/Inativo).
5. O sistema disponibiliza as ações de Inserir, Alterar e Excluir.

## Fluxos Alternativos

### Inserir

1. O usuário aciona o comando "Inserir" na tela de consulta.
2. O sistema apresenta o formulário de cadastro de funcionário com os campos:
   - Nome * (até 120 caracteres)
   - Apelido (até 60 caracteres)
   - Tipo * (Física/Jurídica — padrão: Física)
   - CPF * (até 20 caracteres)
   - RG/Inscrição Estadual (até 20 caracteres)
   - Data de Nascimento (data)
   - Sexo (Masculino/Feminino/Outro)
   - Estado Civil (Solteiro/Casado/Separado/Divorciado/Viúvo/Outro)
   - Telefone (até 20 caracteres)
   - E-mail (até 120 caracteres)
   - Endereço (até 120 caracteres)
   - Número (até 10 caracteres)
   - Complemento (até 100 caracteres)
   - Bairro (até 60 caracteres)
   - CEP (até 9 caracteres)
   - Cidade (seleção de cidade cadastrada)
   - Nacionalidade (seleção de país cadastrado)
   - Cargo * (seleção de cargo cadastrado)
   - CNH (até 20 caracteres)
   - Data de Validade da CNH (data)
   - Data de Admissão * (data — padrão: data atual)
   - Data de Demissão (data)
   - Salário (numérico, 10 dígitos e 2 casas decimais)
   - Observação (até 255 caracteres)
   - Ativo (sim/não — padrão: sim)
3. O usuário preenche os campos e aciona o comando "Salvar".
4. O sistema valida o preenchimento dos campos obrigatórios (Nome, Tipo, CPF, Cargo e Data de Admissão).
5. O sistema valida a unicidade do CPF, os domínios de Tipo, Sexo e Estado Civil, e a não negatividade do Salário.
6. O sistema persiste o novo registro e exibe a mensagem "Registro salvo com sucesso.".
7. O sistema retorna à tela de consulta com a lista atualizada.

### Alterar

1. O usuário seleciona um funcionário na lista de consulta e aciona o comando "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário altera os campos desejados e aciona o comando "Salvar".
4. O sistema valida o preenchimento dos campos obrigatórios (Nome, Tipo, CPF, Cargo e Data de Admissão), a unicidade do CPF, os domínios de Tipo, Sexo e Estado Civil, e a não negatividade do Salário.
5. O sistema persiste as alterações e exibe a mensagem "Registro alterado com sucesso.".
6. O sistema retorna à tela de consulta com a lista atualizada.

### Excluir

1. O usuário seleciona um funcionário na lista de consulta e aciona o comando "Excluir".
2. O sistema solicita a confirmação da exclusão.
3. O usuário confirma a operação.
4. O sistema verifica a existência de Usuário vinculado ao funcionário.
5. Não havendo vínculos, o sistema exclui fisicamente o registro e exibe a mensagem "Registro excluído com sucesso.".
6. O sistema retorna à tela de consulta com a lista atualizada.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** Nos fluxos Inserir ou Alterar, caso algum dos campos Nome, Tipo, CPF, Cargo ou Data de Admissão não esteja preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo [Nome/Tipo/CPF/Cargo/Data de Admissão] é de preenchimento obrigatório."**, indicando o campo correspondente. O fluxo retorna ao formulário para correção.
- **E2 — Documento já cadastrado:** Nos fluxos Inserir ou Alterar, caso o CPF informado já exista para outro funcionário, o sistema interrompe a gravação e exibe a mensagem: **"Já existe um funcionário cadastrado com este CPF."**. O fluxo retorna ao formulário para correção.
- **E3 — Valor inválido:** Nos fluxos Inserir ou Alterar, caso o Salário seja informado com valor negativo, o sistema interrompe a gravação e exibe a mensagem: **"O valor do salário não pode ser negativo."**. O fluxo retorna ao formulário para correção.
- **E4 — Valor de domínio inválido:** Nos fluxos Inserir ou Alterar, caso os campos Tipo, Sexo ou Estado Civil sejam informados com valor fora do domínio permitido, o sistema interrompe a gravação e exibe a mensagem: **"O valor informado para o campo [Tipo/Sexo/Estado Civil] é inválido."**, indicando o campo correspondente. O fluxo retorna ao formulário para correção.
- **E5 — Registro vinculado a outro:** No fluxo Excluir, caso o funcionário possua Usuário vinculado, o sistema impede a exclusão física e exibe a mensagem: **"Este funcionário está vinculado a um usuário do sistema e não pode ser excluído. Deseja desativá-lo?"**. Confirmada a opção, o sistema realiza a exclusão lógica, atribuindo "não" ao campo Ativo, e exibe a mensagem "Registro desativado com sucesso.". Caso o usuário não confirme, a operação é cancelada e o registro é mantido inalterado.

---

## UC006 — Gerenciar Usuário

## Regras de Negócio

- **RN001:** O acesso à funcionalidade é permitido somente a usuário autenticado no sistema. A gestão de usuários é restrita ao ator Administrador.
- **RN002:** O campo Nome é de preenchimento obrigatório e limitado a 120 caracteres.
- **RN003:** O campo Login é de preenchimento obrigatório e limitado a 60 caracteres, devendo ser único no sistema, sem distinção entre maiúsculas e minúsculas (restrição `uq_usuario_login` sobre `LOWER(login)`). Não é permitido cadastrar dois usuários com o mesmo login.
- **RN004:** O campo Senha é de preenchimento obrigatório. A senha é armazenada de forma cifrada (hash), nunca em texto puro; o sistema não exibe nem registra o valor da senha.
- **RN005:** O campo E-mail, quando informado, é limitado a 120 caracteres e deve ser único no sistema, sem distinção entre maiúsculas e minúsculas (restrição `uq_usuario_email` sobre `LOWER(email)`).
- **RN006:** O campo Funcionário, quando informado, deve referenciar um funcionário previamente cadastrado (chave estrangeira `fk_usuario_funcionario`) e é exclusivo: cada funcionário pode estar vinculado a, no máximo, um usuário (restrição `uq_usuario_funcionario`).
- **RN007:** O campo Perfil é de preenchimento obrigatório e restrito aos valores "Admin" ou "Operador" (verificação `ck_usuario_perfil`); o valor padrão é "Operador".
- **RN008:** Todo usuário é cadastrado, por padrão, como Ativo. A inativação do usuário (campo Ativo) impede seu acesso ao sistema, sendo o meio preferencial de revogação de acesso em substituição à exclusão física.
- **RN009:** A exclusão de um usuário deve ser realizada preferencialmente por inativação (exclusão lógica), preservando o histórico de operações associado.

## Nome do Caso de Uso

Gerenciar Usuário.

## Atores

Administrador (usuário autenticado com perfil de administração).

## Pré-Condição

O usuário deve estar autenticado no sistema com perfil de Administrador. Quando o usuário for associado a um colaborador, deve existir o respectivo Funcionário cadastrado.

## Pós-Condição

O registro de usuário encontra-se inserido, alterado, inativado ou excluído na base de dados, conforme a operação executada.

## Ativação

O caso de uso é ativado quando o usuário seleciona a opção "Usuário" no menu de cadastros (administração) do sistema.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Usuário" no menu de cadastros.
2. O sistema apresenta a tela de consulta de usuários, com a relação de registros cadastrados e os campos de filtro (Nome, Login, Perfil e situação Ativo/Inativo).
3. O usuário informa, opcionalmente, os critérios de filtro desejados e aciona o comando "Pesquisar".
4. O sistema consulta a base de dados e exibe a lista de usuários que atendem aos critérios, apresentando as colunas Nome, Login, E-mail, Funcionário, Perfil e situação (Ativo/Inativo). O campo Senha não é exibido em nenhuma circunstância.
5. O sistema disponibiliza as ações de Inserir, Alterar e Excluir.

## Fluxos Alternativos

### Inserir

1. O usuário aciona o comando "Inserir" na tela de consulta.
2. O sistema apresenta o formulário de cadastro de usuário com os campos:
   - Nome * (até 120 caracteres)
   - Login * (até 60 caracteres)
   - Senha * (cifrada)
   - E-mail (até 120 caracteres)
   - Funcionário (seleção de funcionário cadastrado)
   - Perfil * (Admin/Operador — padrão: Operador)
   - Ativo (sim/não — padrão: sim)
3. O usuário preenche os campos e aciona o comando "Salvar".
4. O sistema valida o preenchimento dos campos obrigatórios (Nome, Login, Senha e Perfil).
5. O sistema valida a unicidade do Login e do E-mail (quando informado), a unicidade do vínculo com o Funcionário (quando informado) e o domínio do Perfil.
6. O sistema cifra a senha e persiste o novo registro, exibindo a mensagem "Registro salvo com sucesso.".
7. O sistema retorna à tela de consulta com a lista atualizada.

### Alterar

1. O usuário seleciona um usuário na lista de consulta e aciona o comando "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro, mantendo o campo Senha em branco (a senha somente é alterada quando um novo valor for informado).
3. O usuário altera os campos desejados (Nome *, Login *, Senha, E-mail, Funcionário, Perfil *, Ativo) e aciona o comando "Salvar".
4. O sistema valida o preenchimento dos campos obrigatórios, a unicidade do Login e do E-mail, a unicidade do vínculo com o Funcionário e o domínio do Perfil.
5. O sistema persiste as alterações (cifrando a senha apenas se um novo valor for informado) e exibe a mensagem "Registro alterado com sucesso.".
6. O sistema retorna à tela de consulta com a lista atualizada.

### Excluir

1. O usuário seleciona um usuário na lista de consulta e aciona o comando "Excluir".
2. O sistema solicita a confirmação da exclusão e recomenda a inativação como alternativa preferencial.
3. O usuário confirma a operação.
4. O sistema realiza preferencialmente a inativação (exclusão lógica), atribuindo "não" ao campo Ativo, e exibe a mensagem "Registro desativado com sucesso.". Caso a exclusão física seja efetivada e não haja impedimentos, o sistema exibe a mensagem "Registro excluído com sucesso.".
5. O sistema retorna à tela de consulta com a lista atualizada.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** Nos fluxos Inserir ou Alterar, caso algum dos campos Nome, Login, Senha ou Perfil não esteja preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo [Nome/Login/Senha/Perfil] é de preenchimento obrigatório."**, indicando o campo correspondente. No fluxo Alterar, a Senha é considerada obrigatória somente quando se pretende redefini-la. O fluxo retorna ao formulário para correção.
- **E2 — Login já cadastrado:** Nos fluxos Inserir ou Alterar, caso o Login informado já exista para outro usuário, o sistema interrompe a gravação e exibe a mensagem: **"Já existe um usuário cadastrado com este login."**. O fluxo retorna ao formulário para correção.
- **E3 — E-mail já cadastrado:** Nos fluxos Inserir ou Alterar, caso o E-mail informado já exista para outro usuário, o sistema interrompe a gravação e exibe a mensagem: **"Já existe um usuário cadastrado com este e-mail."**. O fluxo retorna ao formulário para correção.
- **E4 — Funcionário já vinculado a outro usuário:** Nos fluxos Inserir ou Alterar, caso o Funcionário selecionado já esteja vinculado a outro usuário, o sistema interrompe a gravação e exibe a mensagem: **"Este funcionário já está vinculado a outro usuário."**. O fluxo retorna ao formulário para correção.
- **E5 — Valor de domínio inválido:** Nos fluxos Inserir ou Alterar, caso o campo Perfil seja informado com valor fora do domínio permitido, o sistema interrompe a gravação e exibe a mensagem: **"O valor informado para o campo Perfil é inválido."**. O fluxo retorna ao formulário para correção.

---

## Histórico

| Data | Ação |
| --- | --- |
| 2026-06-14 | Criação dos casos de uso dos cadastros de Geografia (País, Estado, Cidade) e RH/Acesso (Cargo, Funcionário, Usuário), aterrados em `database/schema.sql`. |
