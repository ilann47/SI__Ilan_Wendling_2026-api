> Links: [[README]] · [[04-requisitos-funcionais]] · [[07-dicionario-de-dados]] · [[03-der]]

# Casos de Uso — Módulo de Logística (Abastecimento da Conveniência)

Este documento especifica os casos de uso do **Módulo de Logística** do sistema Estacionamento Kaneko, responsável por registrar **quem** realiza o frete das compras que abastecem a loja de conveniência e **em quais veículos**. O módulo é composto por três cadastros: Transportadora, Veículo de Frota e o vínculo Frota (Transportadora × Veículo). Os campos, validações, tamanhos, restrições de unicidade e regras de integridade referenciados foram aterrados no schema físico do banco de dados (PostgreSQL 17, banco `estacionamento_kaneko`, migração `V6__transportadora_frota.sql`).

O módulo modela a cadeia **Fornecedor → Transportadora → (N:N) → Veículo de Frota**: cada fornecedor pode indicar a sua transportadora padrão de frete (`fornecedor.transportadora_id`); cada transportadora possui uma frota de veículos de carga, representada pelo vínculo N:N `transportadora_veiculo_frota`. Ressalta-se que o **Veículo de Frota** (veículo de carga da transportadora) é entidade distinta do **Veículo** do pátio (`veiculo`), que representa o carro estacionado pelo cliente; ambos coexistem sem colisão.

A exclusão de registros referenciados por outras tabelas é tratada como **exclusão lógica** (inativação via campo `ativo`), uma vez que as chaves estrangeiras envolvidas adotam a política `ON DELETE RESTRICT`, impedindo a remoção física de registros vinculados.

## Convenções

Nas mensagens de exceção, o marcador `[nome do campo]` é um parâmetro substituído em tempo de execução pelo rótulo do campo correspondente à validação que falhou (por exemplo, "Nome", "Documento" ou "Placa"). Trata-se de uma única mensagem parametrizada, e não de uma enumeração de alternativas. Os identificadores de caso de uso deste módulo pertencem ao bloco **UC6xx** (Logística).

---

## UC601 — Gerenciar Transportadora

## Regras de Negócio

- **RN001:** O acesso ao caso de uso é permitido somente a usuário autenticado no sistema (perfil `ADMIN` ou `OPERADOR`).
- **RN002:** O campo **Nome** (razão social) é de preenchimento obrigatório e limitado a 150 caracteres.
- **RN003:** O campo **Documento** (CNPJ/CPF) é de preenchimento obrigatório, limitado a 20 caracteres e único em todo o cadastro de transportadoras (restrição `uq_transportadora_documento`). O documento é normalizado para conter **somente dígitos** antes da verificação de unicidade e da persistência, de modo que máscaras distintas do mesmo número (por exemplo, `12.345.678/0001-99` e `12345678000199`) sejam consideradas duplicadas.
- **RN004:** O campo **Tipo** de pessoa admite os valores `FISICA` e `JURIDICA`, assumindo `JURIDICA` por padrão (restrição `ck_transportadora_tipo`).
- **RN005:** Os campos **Nome Fantasia**, **RG/Inscrição Estadual**, **Telefone**, **E-mail**, **Endereço**, **Número**, **Complemento**, **Bairro**, **CEP** e **Observação** são opcionais, respeitando os limites de tamanho definidos no dicionário de dados.
- **RN006:** Os campos **Cidade** e **Condição de Pagamento** são opcionais e, quando informados, devem referenciar registros existentes (chaves estrangeiras `fk_transportadora_cidade` e `fk_transportadora_condicao`, política `ON DELETE RESTRICT`).
- **RN007:** Toda transportadora é criada com a situação **Ativo** verdadeira por padrão.
- **RN008:** A exclusão de transportadora referenciada por um ou mais fornecedores (`fornecedor.transportadora_id`, política `ON DELETE RESTRICT`) é vedada fisicamente; nesse caso o sistema oferece a **exclusão lógica** (inativação via campo `ativo`). Os vínculos de frota associados (`transportadora_veiculo_frota`) são removidos em cascata (`ON DELETE CASCADE`).

## Nome do Caso de Uso

Gerenciar Transportadora.

## Atores

Usuário autenticado (Administrador ou Operador).

## Pré-Condição

O usuário deve estar autenticado no sistema e possuir permissão de acesso ao módulo de logística.

## Pós-Condição

A transportadora é inserida, alterada, inativada ou excluída e a base de dados é atualizada de forma consistente.

## Ativação

Este caso de uso é iniciado quando o usuário seleciona a opção "Transportadoras" no menu de logística.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Transportadoras" no menu de logística.
2. O sistema apresenta a lista de transportadoras cadastradas, exibindo as colunas: Nome, Documento, Cidade e Situação (Ativo/Inativo).
3. O sistema disponibiliza campos de busca por Nome e por Documento e um filtro por Situação.
4. O usuário informa, opcionalmente, um ou mais critérios de busca e aciona "Pesquisar".
5. O sistema filtra e exibe os registros correspondentes aos critérios informados.
6. O sistema disponibiliza as ações "Inserir", "Alterar" e "Excluir" sobre a lista.
7. O caso de uso é encerrado.

## Fluxos Alternativos

### Inserir

1. No fluxo principal, o usuário aciona a opção "Inserir".
2. O sistema apresenta o formulário de cadastro com os campos:
   - **Nome*** (razão social, texto, até 150 caracteres);
   - **Nome Fantasia** (texto, até 120 caracteres);
   - **Tipo** (seleção `FISICA`/`JURIDICA`, padrão `JURIDICA`);
   - **Documento*** (CNPJ/CPF, texto, até 20 caracteres, único);
   - **RG/Inscrição Estadual** (texto, até 20 caracteres);
   - **Telefone** (texto, até 20 caracteres);
   - **E-mail** (texto, até 120 caracteres);
   - **Endereço**, **Número**, **Complemento**, **Bairro**, **CEP** (dados de endereço);
   - **Cidade** (seleção de cidade cadastrada);
   - **Condição de Pagamento** (seleção de condição cadastrada);
   - **Observação** (texto, até 255 caracteres);
   - **Ativo** (situação, padrão verdadeiro).
3. O usuário preenche os campos e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2).
5. O sistema persiste a nova transportadora com situação Ativo igual a verdadeiro.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Alterar

1. No fluxo principal, o usuário seleciona uma transportadora e aciona "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário edita os campos desejados e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2).
5. O sistema persiste as alterações.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Excluir

1. No fluxo principal, o usuário seleciona uma transportadora e aciona "Excluir".
2. O sistema solicita a confirmação da operação.
3. O usuário confirma a exclusão.
4. O sistema verifica se a transportadora está referenciada por algum fornecedor (E3).
5. Não havendo vínculo, o sistema remove fisicamente o registro (e, em cascata, seus vínculos de frota).
6. O sistema exibe a mensagem "Registro excluído com sucesso." e retorna à consulta.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** No fluxo Inserir ou Alterar, caso o campo Nome ou o campo Documento não tenha sido preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo [nome do campo] é obrigatório."** O sistema retorna ao formulário mantendo os dados informados.
- **E2 — Documento já cadastrado:** No fluxo Inserir ou Alterar, caso já exista outra transportadora com o mesmo Documento (comparado por dígitos), o sistema interrompe a gravação e exibe a mensagem: **"Já existe uma transportadora cadastrada com este documento."** O sistema retorna ao formulário mantendo os dados informados.
- **E3 — Registro vinculado a outro:** No fluxo Excluir, caso a transportadora esteja referenciada por um ou mais fornecedores, o sistema impede a exclusão física e exibe a mensagem: **"Esta transportadora está vinculada a fornecedores e não pode ser excluída. Deseja desativá-la?"** Caso o usuário confirme, o sistema realiza a exclusão lógica (define `ativo` igual a falso) e exibe a mensagem "Registro desativado com sucesso."; caso contrário, a operação é cancelada.

---

## UC602 — Gerenciar Veículo de Frota

## Regras de Negócio

- **RN001:** O acesso ao caso de uso é permitido somente a usuário autenticado no sistema (perfil `ADMIN` ou `OPERADOR`).
- **RN002:** O campo **Placa** é de preenchimento obrigatório, limitado a 10 caracteres e único em todo o cadastro de veículos de frota (restrição `uq_veiculo_frota_placa`). A placa é normalizada em **letras maiúsculas** antes da verificação de unicidade e da persistência.
- **RN003:** Os campos **Modelo** (até 50 caracteres) e **Marca** (até 50 caracteres) são opcionais.
- **RN004:** O campo **Ano**, quando informado, deve ser igual ou superior a 1900 (restrição `ck_veiculo_frota_ano`).
- **RN005:** O campo **Capacidade** (de carga) é opcional e, quando informado, deve ser maior ou igual a zero (restrição `ck_veiculo_frota_capacidade`), expresso com precisão de duas casas decimais.
- **RN006:** O campo **Observação** é opcional e limitado a 255 caracteres.
- **RN007:** Todo veículo de frota é criado com a situação **Ativo** verdadeira por padrão.
- **RN008:** O Veículo de Frota é entidade distinta do Veículo do pátio (`veiculo`): representa o veículo de **carga** de uma transportadora, e não o automóvel estacionado pelo cliente. Não há relacionamento entre as duas entidades.
- **RN009:** A exclusão de veículo de frota vinculado a uma ou mais transportadoras (`transportadora_veiculo_frota.veiculo_frota_id`, política `ON DELETE RESTRICT`) é vedada fisicamente; nesse caso o sistema oferece a **exclusão lógica** (inativação via campo `ativo`).

## Nome do Caso de Uso

Gerenciar Veículo de Frota.

## Atores

Usuário autenticado (Administrador ou Operador).

## Pré-Condição

O usuário deve estar autenticado no sistema e possuir permissão de acesso ao módulo de logística.

## Pós-Condição

O veículo de frota é inserido, alterado, inativado ou excluído e a base de dados é atualizada de forma consistente.

## Ativação

Este caso de uso é iniciado quando o usuário seleciona a opção "Veículos de Frota" no menu de logística.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Veículos de Frota" no menu de logística.
2. O sistema apresenta a lista de veículos de frota cadastrados, exibindo as colunas: Placa, Modelo, Marca, Ano, Capacidade e Situação (Ativo/Inativo).
3. O sistema disponibiliza campos de busca por Placa e por Marca e um filtro por Situação.
4. O usuário informa, opcionalmente, um critério de busca e aciona "Pesquisar".
5. O sistema filtra e exibe os registros correspondentes ao critério informado.
6. O sistema disponibiliza as ações "Inserir", "Alterar" e "Excluir" sobre a lista.
7. O caso de uso é encerrado.

## Fluxos Alternativos

### Inserir

1. No fluxo principal, o usuário aciona a opção "Inserir".
2. O sistema apresenta o formulário de cadastro com os campos:
   - **Placa*** (texto, até 10 caracteres, único, normalizada em maiúsculas);
   - **Modelo** (texto, até 50 caracteres);
   - **Marca** (texto, até 50 caracteres);
   - **Ano** (numérico inteiro, ≥ 1900);
   - **Capacidade** (numérico decimal, ≥ 0);
   - **Observação** (texto, até 255 caracteres);
   - **Ativo** (situação, padrão verdadeiro).
3. O usuário preenche os campos e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2, E3).
5. O sistema persiste o novo veículo de frota com situação Ativo igual a verdadeiro.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Alterar

1. No fluxo principal, o usuário seleciona um veículo de frota e aciona "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário edita os campos desejados e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2, E3).
5. O sistema persiste as alterações.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Excluir

1. No fluxo principal, o usuário seleciona um veículo de frota e aciona "Excluir".
2. O sistema solicita a confirmação da operação.
3. O usuário confirma a exclusão.
4. O sistema verifica se o veículo está vinculado a alguma transportadora (E4).
5. Não havendo vínculo, o sistema remove fisicamente o registro.
6. O sistema exibe a mensagem "Registro excluído com sucesso." e retorna à consulta.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** No fluxo Inserir ou Alterar, caso o campo Placa não tenha sido preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo Placa é obrigatório."** O sistema retorna ao formulário mantendo os dados informados.
- **E2 — Placa já cadastrada:** No fluxo Inserir ou Alterar, caso já exista outro veículo de frota com a mesma Placa, o sistema interrompe a gravação e exibe a mensagem: **"Já existe um veículo de frota com esta placa."** O sistema retorna ao formulário mantendo os dados informados.
- **E3 — Valor inválido:** No fluxo Inserir ou Alterar, caso o campo Ano seja informado com valor inferior a 1900 ou o campo Capacidade seja informado com valor negativo, o sistema interrompe a gravação e exibe a mensagem: **"O(A) [nome do campo] informado(a) é inválido(a)."** O sistema retorna ao formulário mantendo os dados informados.
- **E4 — Registro vinculado a outro:** No fluxo Excluir, caso o veículo de frota esteja vinculado a uma ou mais transportadoras, o sistema impede a exclusão física e exibe a mensagem: **"Este veículo está vinculado a transportadoras e não pode ser excluído. Deseja desativá-lo?"** Caso o usuário confirme, o sistema realiza a exclusão lógica (define `ativo` igual a falso) e exibe a mensagem "Registro desativado com sucesso."; caso contrário, a operação é cancelada.

---

## UC603 — Gerenciar Frota da Transportadora

## Regras de Negócio

- **RN001:** O acesso ao caso de uso é permitido somente a usuário autenticado no sistema (perfil `ADMIN` ou `OPERADOR`).
- **RN002:** O campo **Transportadora** é de preenchimento obrigatório e deve referenciar uma transportadora cadastrada (chave estrangeira `fk_tvf_transportadora`, política `ON DELETE CASCADE`).
- **RN003:** O campo **Veículo de Frota** é de preenchimento obrigatório e deve referenciar um veículo de frota cadastrado (chave estrangeira `fk_tvf_veiculo`, política `ON DELETE RESTRICT`).
- **RN004:** Não é permitido vincular o mesmo veículo mais de uma vez à mesma transportadora (restrição de unicidade `uq_transp_veiculo_frota` sobre o par transportadora-veículo).
- **RN005:** O relacionamento é **N:N**: uma transportadora pode ter vários veículos e um mesmo veículo pode compor a frota de mais de uma transportadora.
- **RN006:** Todo vínculo é criado com a situação **Ativo** verdadeira por padrão.
- **RN007:** A exclusão de um vínculo de frota é **lógica** (campo `ativo` igual a falso), preservando o histórico e removendo o veículo da frota ativa da transportadora.

## Nome do Caso de Uso

Gerenciar Frota da Transportadora.

## Atores

Usuário autenticado (Administrador ou Operador).

## Pré-Condição

O usuário deve estar autenticado no sistema e devem existir transportadoras e veículos de frota previamente cadastrados.

## Pós-Condição

O vínculo transportadora-veículo é inserido, alterado, inativado ou excluído e a base de dados é atualizada de forma consistente.

## Ativação

Este caso de uso é iniciado quando o usuário seleciona a opção "Frota (Transportadora × Veículo)" no menu de logística.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Frota (Transportadora × Veículo)" no menu de logística.
2. O sistema apresenta a lista de vínculos cadastrados, exibindo as colunas: Transportadora, Placa (do veículo) e Situação (Ativo/Inativo).
3. O sistema disponibiliza as ações "Inserir", "Alterar" e "Excluir" sobre a lista.
4. O caso de uso é encerrado.

## Fluxos Alternativos

### Inserir

1. No fluxo principal, o usuário aciona a opção "Inserir".
2. O sistema apresenta o formulário de vínculo com os campos:
   - **Transportadora*** (seleção de transportadora cadastrada);
   - **Veículo de Frota*** (seleção de veículo de frota cadastrado).
3. O usuário preenche os campos e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2, E3).
5. O sistema persiste o novo vínculo com situação Ativo igual a verdadeiro.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Alterar

1. No fluxo principal, o usuário seleciona um vínculo e aciona "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do vínculo.
3. O usuário edita a transportadora e/ou o veículo e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2, E3).
5. O sistema persiste as alterações.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Excluir

1. No fluxo principal, o usuário seleciona um vínculo e aciona "Excluir".
2. O sistema solicita a confirmação da operação.
3. O usuário confirma a exclusão.
4. O sistema realiza a **exclusão lógica** do vínculo (define `ativo` igual a falso), preservando o histórico conforme **RN007**.
5. O sistema exibe a mensagem "Registro excluído com sucesso." e retorna à consulta.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** No fluxo Inserir ou Alterar, caso o campo Transportadora ou o campo Veículo de Frota não tenha sido preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo [nome do campo] é obrigatório."** O sistema retorna ao formulário mantendo os dados informados.
- **E2 — Vínculo já existente:** No fluxo Inserir ou Alterar, caso o veículo selecionado já esteja vinculado à transportadora selecionada, o sistema interrompe a gravação e exibe a mensagem: **"Este veículo já está vinculado a esta transportadora."** O sistema retorna ao formulário mantendo os dados informados.
- **E3 — Referência inexistente:** No fluxo Inserir ou Alterar, caso a transportadora ou o veículo informado não exista, o sistema interrompe a gravação e exibe a mensagem: **"O(A) [nome do campo] informado(a) não existe."** O sistema retorna ao formulário mantendo os dados informados.

---

## Histórico

| Data | Ação |
|------|------|
| 2026-07-03 | Elaboração da Seção 5.7 — Casos de uso do Módulo de Logística (UC601 Transportadora, UC602 Veículo de Frota, UC603 Frota da Transportadora), aterrada no pacote `com.kaneko.estacionamento.logistica` e na migração `V6__transportadora_frota.sql`. |
