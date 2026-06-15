> Links: [[README]] · [[04-requisitos-funcionais]] · [[07-dicionario-de-dados]]

# Casos de Uso — Cadastros de Apoio do Módulo Conveniência

Este documento especifica os casos de uso dos cadastros de apoio do Módulo Conveniência & Comercial do sistema Estacionamento Kaneko: Categoria, Marca, Unidade de Medida, Serviço e Produto (incluindo o vínculo Produto-Fornecedor). Todos os casos seguem o padrão CRUD com consulta como fluxo principal e inserção, alteração e exclusão como fluxos alternativos. Os campos, validações, tamanhos, restrições de unicidade e regras de integridade referenciados foram aterrados no schema físico do banco de dados (PostgreSQL 17, banco `estacionamento_kaneko`).

A exclusão de registros referenciados por outras tabelas é tratada como **exclusão lógica** (inativação via campo `ativo`), uma vez que as chaves estrangeiras envolvidas adotam a política `ON DELETE RESTRICT`, impedindo a remoção física de registros vinculados.

## Convenções

Nas mensagens de exceção, o marcador `[nome do campo]` é um parâmetro substituído em tempo de execução pelo rótulo do campo correspondente à validação que falhou (por exemplo, "Nome", "Sigla", "Marca" ou "Unidade de Medida"). Trata-se de uma única mensagem parametrizada, e não de uma enumeração de alternativas.

---

## UC201 — Gerenciar Categoria

## Regras de Negócio

- **RN001:** O acesso ao caso de uso é permitido somente a usuário autenticado no sistema (perfil `ADMIN` ou `OPERADOR`).
- **RN002:** O campo **Nome** é de preenchimento obrigatório e limitado a 100 caracteres.
- **RN003:** O campo **Nome** é único em todo o cadastro de categorias (restrição `uq_categoria_nome`); não é permitido cadastrar duas categorias com o mesmo nome.
- **RN004:** O campo **Descrição** é opcional e limitado a 255 caracteres.
- **RN005:** Toda categoria é criada com a situação **Ativo** verdadeira por padrão.
- **RN006:** A exclusão de categoria vinculada a um ou mais produtos (referência em `produto.categoria_id`, política `ON DELETE RESTRICT`) é vedada fisicamente; nesse caso o sistema oferece a **exclusão lógica** (inativação via campo `ativo`).

## Nome do Caso de Uso

Gerenciar Categoria.

## Atores

Usuário autenticado (Administrador ou Operador).

## Pré-Condição

O usuário deve estar autenticado no sistema e possuir permissão de acesso ao módulo de cadastros de conveniência.

## Pós-Condição

A categoria é inserida, alterada, inativada ou excluída e a base de dados é atualizada de forma consistente.

## Ativação

Este caso de uso é iniciado quando o usuário seleciona a opção "Categorias" no menu de cadastros do módulo Conveniência.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Categorias" no menu de cadastros.
2. O sistema apresenta a lista de categorias cadastradas, exibindo as colunas: Nome, Descrição e Situação (Ativo/Inativo).
3. O sistema disponibiliza um campo de busca por Nome e um filtro por Situação.
4. O usuário informa, opcionalmente, um critério de busca e aciona "Pesquisar".
5. O sistema filtra e exibe os registros correspondentes ao critério informado.
6. O sistema disponibiliza as ações "Inserir", "Alterar" e "Excluir" sobre a lista.
7. O caso de uso é encerrado.

## Fluxos Alternativos

### Inserir

1. No fluxo principal, o usuário aciona a opção "Inserir".
2. O sistema apresenta o formulário de cadastro com os campos:
   - **Nome*** (texto, até 100 caracteres);
   - **Descrição** (texto, até 255 caracteres);
   - **Ativo** (situação, padrão verdadeiro).
3. O usuário preenche os campos e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2).
5. O sistema persiste a nova categoria com situação Ativo igual a verdadeiro.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Alterar

1. No fluxo principal, o usuário seleciona uma categoria e aciona "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário edita os campos desejados (Nome, Descrição, Ativo) e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2).
5. O sistema persiste as alterações.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Excluir

1. No fluxo principal, o usuário seleciona uma categoria e aciona "Excluir".
2. O sistema solicita a confirmação da operação.
3. O usuário confirma a exclusão.
4. O sistema verifica se a categoria está vinculada a algum produto (E3).
5. Não havendo vínculo, o sistema remove fisicamente o registro.
6. O sistema exibe a mensagem "Registro excluído com sucesso." e retorna à consulta.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** No fluxo Inserir ou Alterar, caso o campo Nome não tenha sido preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo Nome é obrigatório."** O sistema retorna ao formulário mantendo os dados informados.
- **E2 — Nome já cadastrado:** No fluxo Inserir ou Alterar, caso já exista outra categoria com o mesmo Nome, o sistema interrompe a gravação e exibe a mensagem: **"Nome já cadastrado para outra categoria."** O sistema retorna ao formulário mantendo os dados informados.
- **E3 — Registro vinculado a outro:** No fluxo Excluir, caso a categoria esteja vinculada a um ou mais produtos, o sistema impede a exclusão física e exibe a mensagem: **"Esta categoria está vinculada a produtos e não pode ser excluída. Deseja desativá-la?"** Caso o usuário confirme, o sistema realiza a exclusão lógica (define `ativo` igual a falso) e exibe a mensagem "Registro desativado com sucesso."; caso contrário, a operação é cancelada.

---

## UC202 — Gerenciar Marca

## Regras de Negócio

- **RN001:** O acesso ao caso de uso é permitido somente a usuário autenticado no sistema (perfil `ADMIN` ou `OPERADOR`).
- **RN002:** O campo **Nome** é de preenchimento obrigatório e limitado a 100 caracteres.
- **RN003:** O campo **Nome** é único em todo o cadastro de marcas (restrição `uq_marca_nome`); não é permitido cadastrar duas marcas com o mesmo nome.
- **RN004:** Toda marca é criada com a situação **Ativo** verdadeira por padrão.
- **RN005:** A exclusão de marca vinculada a um ou mais produtos (referência em `produto.marca_id`, política `ON DELETE RESTRICT`) é vedada fisicamente; nesse caso o sistema oferece a **exclusão lógica** (inativação via campo `ativo`).

## Nome do Caso de Uso

Gerenciar Marca.

## Atores

Usuário autenticado (Administrador ou Operador).

## Pré-Condição

O usuário deve estar autenticado no sistema e possuir permissão de acesso ao módulo de cadastros de conveniência.

## Pós-Condição

A marca é inserida, alterada, inativada ou excluída e a base de dados é atualizada de forma consistente.

## Ativação

Este caso de uso é iniciado quando o usuário seleciona a opção "Marcas" no menu de cadastros do módulo Conveniência.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Marcas" no menu de cadastros.
2. O sistema apresenta a lista de marcas cadastradas, exibindo as colunas: Nome e Situação (Ativo/Inativo).
3. O sistema disponibiliza um campo de busca por Nome e um filtro por Situação.
4. O usuário informa, opcionalmente, um critério de busca e aciona "Pesquisar".
5. O sistema filtra e exibe os registros correspondentes ao critério informado.
6. O sistema disponibiliza as ações "Inserir", "Alterar" e "Excluir" sobre a lista.
7. O caso de uso é encerrado.

## Fluxos Alternativos

### Inserir

1. No fluxo principal, o usuário aciona a opção "Inserir".
2. O sistema apresenta o formulário de cadastro com os campos:
   - **Nome*** (texto, até 100 caracteres);
   - **Ativo** (situação, padrão verdadeiro).
3. O usuário preenche os campos e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2).
5. O sistema persiste a nova marca com situação Ativo igual a verdadeiro.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Alterar

1. No fluxo principal, o usuário seleciona uma marca e aciona "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário edita os campos desejados (Nome, Ativo) e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2).
5. O sistema persiste as alterações.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Excluir

1. No fluxo principal, o usuário seleciona uma marca e aciona "Excluir".
2. O sistema solicita a confirmação da operação.
3. O usuário confirma a exclusão.
4. O sistema verifica se a marca está vinculada a algum produto (E3).
5. Não havendo vínculo, o sistema remove fisicamente o registro.
6. O sistema exibe a mensagem "Registro excluído com sucesso." e retorna à consulta.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** No fluxo Inserir ou Alterar, caso o campo Nome não tenha sido preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo Nome é obrigatório."** O sistema retorna ao formulário mantendo os dados informados.
- **E2 — Nome já cadastrado:** No fluxo Inserir ou Alterar, caso já exista outra marca com o mesmo Nome, o sistema interrompe a gravação e exibe a mensagem: **"Nome já cadastrado para outra marca."** O sistema retorna ao formulário mantendo os dados informados.
- **E3 — Registro vinculado a outro:** No fluxo Excluir, caso a marca esteja vinculada a um ou mais produtos, o sistema impede a exclusão física e exibe a mensagem: **"Esta marca está vinculada a produtos e não pode ser excluída. Deseja desativá-la?"** Caso o usuário confirme, o sistema realiza a exclusão lógica (define `ativo` igual a falso) e exibe a mensagem "Registro desativado com sucesso."; caso contrário, a operação é cancelada.

---

## UC203 — Gerenciar Unidade de Medida

## Regras de Negócio

- **RN001:** O acesso ao caso de uso é permitido somente a usuário autenticado no sistema (perfil `ADMIN` ou `OPERADOR`).
- **RN002:** O campo **Nome** é de preenchimento obrigatório e limitado a 100 caracteres.
- **RN003:** O campo **Sigla** é de preenchimento obrigatório e limitado a 6 caracteres.
- **RN004:** O campo **Sigla** é único em todo o cadastro de unidades de medida (restrição `uq_unidade_sigla`); não é permitido cadastrar duas unidades com a mesma sigla.
- **RN005:** Toda unidade de medida é criada com a situação **Ativo** verdadeira por padrão.
- **RN006:** A exclusão de unidade de medida vinculada a um ou mais produtos (referência em `produto.unidade_medida_id`, política `ON DELETE RESTRICT`) é vedada fisicamente; nesse caso o sistema oferece a **exclusão lógica** (inativação via campo `ativo`).

## Nome do Caso de Uso

Gerenciar Unidade de Medida.

## Atores

Usuário autenticado (Administrador ou Operador).

## Pré-Condição

O usuário deve estar autenticado no sistema e possuir permissão de acesso ao módulo de cadastros de conveniência.

## Pós-Condição

A unidade de medida é inserida, alterada, inativada ou excluída e a base de dados é atualizada de forma consistente.

## Ativação

Este caso de uso é iniciado quando o usuário seleciona a opção "Unidades de Medida" no menu de cadastros do módulo Conveniência.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Unidades de Medida" no menu de cadastros.
2. O sistema apresenta a lista de unidades de medida cadastradas, exibindo as colunas: Nome, Sigla e Situação (Ativo/Inativo).
3. O sistema disponibiliza um campo de busca por Nome ou Sigla e um filtro por Situação.
4. O usuário informa, opcionalmente, um critério de busca e aciona "Pesquisar".
5. O sistema filtra e exibe os registros correspondentes ao critério informado.
6. O sistema disponibiliza as ações "Inserir", "Alterar" e "Excluir" sobre a lista.
7. O caso de uso é encerrado.

## Fluxos Alternativos

### Inserir

1. No fluxo principal, o usuário aciona a opção "Inserir".
2. O sistema apresenta o formulário de cadastro com os campos:
   - **Nome*** (texto, até 100 caracteres);
   - **Sigla*** (texto, até 6 caracteres);
   - **Ativo** (situação, padrão verdadeiro).
3. O usuário preenche os campos e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2).
5. O sistema persiste a nova unidade de medida com situação Ativo igual a verdadeiro.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Alterar

1. No fluxo principal, o usuário seleciona uma unidade de medida e aciona "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário edita os campos desejados (Nome, Sigla, Ativo) e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2).
5. O sistema persiste as alterações.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Excluir

1. No fluxo principal, o usuário seleciona uma unidade de medida e aciona "Excluir".
2. O sistema solicita a confirmação da operação.
3. O usuário confirma a exclusão.
4. O sistema verifica se a unidade de medida está vinculada a algum produto (E3).
5. Não havendo vínculo, o sistema remove fisicamente o registro.
6. O sistema exibe a mensagem "Registro excluído com sucesso." e retorna à consulta.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** No fluxo Inserir ou Alterar, caso o campo Nome ou o campo Sigla não tenha sido preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo [nome do campo] é obrigatório."** O sistema retorna ao formulário mantendo os dados informados.
- **E2 — Sigla já cadastrada:** No fluxo Inserir ou Alterar, caso já exista outra unidade de medida com a mesma Sigla, o sistema interrompe a gravação e exibe a mensagem: **"Sigla já cadastrada para outra unidade de medida."** O sistema retorna ao formulário mantendo os dados informados.
- **E3 — Registro vinculado a outro:** No fluxo Excluir, caso a unidade de medida esteja vinculada a um ou mais produtos, o sistema impede a exclusão física e exibe a mensagem: **"Esta unidade de medida está vinculada a produtos e não pode ser excluída. Deseja desativá-la?"** Caso o usuário confirme, o sistema realiza a exclusão lógica (define `ativo` igual a falso) e exibe a mensagem "Registro desativado com sucesso."; caso contrário, a operação é cancelada.

---

## UC204 — Gerenciar Serviço

## Regras de Negócio

- **RN001:** O acesso ao caso de uso é permitido somente a usuário autenticado no sistema (perfil `ADMIN` ou `OPERADOR`).
- **RN002:** O campo **Nome** é de preenchimento obrigatório e limitado a 100 caracteres.
- **RN003:** O campo **Nome** é único em todo o cadastro de serviços (restrição `uq_servico_nome`); não é permitido cadastrar dois serviços com o mesmo nome.
- **RN004:** O campo **Descrição** é opcional e limitado a 255 caracteres.
- **RN005:** O campo **Valor Padrão** é opcional; quando informado, deve ser maior ou igual a zero (restrição `ck_servico_valor`).
- **RN006:** Todo serviço é criado com a situação **Ativo** verdadeira por padrão.
- **RN007:** A exclusão de serviço referenciado por uma ou mais notas de serviço (referência em `nota_servico.servico_id`, política `ON DELETE RESTRICT`) é vedada fisicamente; nesse caso o sistema oferece a **exclusão lógica** (inativação via campo `ativo`).

## Nome do Caso de Uso

Gerenciar Serviço.

## Atores

Usuário autenticado (Administrador ou Operador).

## Pré-Condição

O usuário deve estar autenticado no sistema e possuir permissão de acesso ao módulo de cadastros de conveniência.

## Pós-Condição

O serviço é inserido, alterado, inativado ou excluído e a base de dados é atualizada de forma consistente.

## Ativação

Este caso de uso é iniciado quando o usuário seleciona a opção "Serviços" no menu de cadastros do módulo Conveniência.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Serviços" no menu de cadastros.
2. O sistema apresenta a lista de serviços cadastrados, exibindo as colunas: Nome, Descrição, Valor Padrão e Situação (Ativo/Inativo).
3. O sistema disponibiliza um campo de busca por Nome e um filtro por Situação.
4. O usuário informa, opcionalmente, um critério de busca e aciona "Pesquisar".
5. O sistema filtra e exibe os registros correspondentes ao critério informado.
6. O sistema disponibiliza as ações "Inserir", "Alterar" e "Excluir" sobre a lista.
7. O caso de uso é encerrado.

## Fluxos Alternativos

### Inserir

1. No fluxo principal, o usuário aciona a opção "Inserir".
2. O sistema apresenta o formulário de cadastro com os campos:
   - **Nome*** (texto, até 100 caracteres);
   - **Descrição** (texto, até 255 caracteres);
   - **Valor Padrão** (numérico, decimal, maior ou igual a zero);
   - **Ativo** (situação, padrão verdadeiro).
3. O usuário preenche os campos e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2, E3).
5. O sistema persiste o novo serviço com situação Ativo igual a verdadeiro.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Alterar

1. No fluxo principal, o usuário seleciona um serviço e aciona "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro.
3. O usuário edita os campos desejados (Nome, Descrição, Valor Padrão, Ativo) e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2, E3).
5. O sistema persiste as alterações.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Excluir

1. No fluxo principal, o usuário seleciona um serviço e aciona "Excluir".
2. O sistema solicita a confirmação da operação.
3. O usuário confirma a exclusão.
4. O sistema verifica se o serviço está referenciado por alguma nota de serviço (E4).
5. Não havendo vínculo, o sistema remove fisicamente o registro.
6. O sistema exibe a mensagem "Registro excluído com sucesso." e retorna à consulta.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** No fluxo Inserir ou Alterar, caso o campo Nome não tenha sido preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo Nome é obrigatório."** O sistema retorna ao formulário mantendo os dados informados.
- **E2 — Nome já cadastrado:** No fluxo Inserir ou Alterar, caso já exista outro serviço com o mesmo Nome, o sistema interrompe a gravação e exibe a mensagem: **"Nome já cadastrado para outro serviço."** O sistema retorna ao formulário mantendo os dados informados.
- **E3 — Valor inválido:** No fluxo Inserir ou Alterar, caso o campo Valor Padrão seja informado com valor negativo, o sistema interrompe a gravação e exibe a mensagem: **"O Valor Padrão deve ser maior ou igual a zero."** O sistema retorna ao formulário mantendo os dados informados.
- **E4 — Registro vinculado a outro:** No fluxo Excluir, caso o serviço esteja referenciado por uma ou mais notas de serviço, o sistema impede a exclusão física e exibe a mensagem: **"Este serviço está vinculado a notas de serviço e não pode ser excluído. Deseja desativá-lo?"** Caso o usuário confirme, o sistema realiza a exclusão lógica (define `ativo` igual a falso) e exibe a mensagem "Registro desativado com sucesso."; caso contrário, a operação é cancelada.

---

## UC205 — Gerenciar Produto

## Regras de Negócio

- **RN001:** O acesso ao caso de uso é permitido somente a usuário autenticado no sistema (perfil `ADMIN` ou `OPERADOR`).
- **RN002:** O campo **Nome** é de preenchimento obrigatório e limitado a 120 caracteres.
- **RN003:** O campo **Marca** é de preenchimento obrigatório e deve referenciar uma marca cadastrada e ativa. A existência da marca referenciada é garantida pela chave estrangeira `fk_produto_marca` (`marca_id`, política `ON DELETE RESTRICT`); a exigência de que a marca esteja **ativa** é regra de aplicação, validada pelo serviço, não havendo restrição no schema que a imponha.
- **RN004:** O campo **Unidade de Medida** é de preenchimento obrigatório e deve referenciar uma unidade de medida cadastrada e ativa. A existência da unidade referenciada é garantida pela chave estrangeira `fk_produto_unidade` (`unidade_medida_id`, política `ON DELETE RESTRICT`); a exigência de que a unidade esteja **ativa** é regra de aplicação, validada pelo serviço, não havendo restrição no schema que a imponha.
- **RN005:** O campo **Categoria** é opcional e, quando informado, deve referenciar uma categoria cadastrada e ativa. A existência da categoria referenciada é garantida pela chave estrangeira `fk_produto_categoria` (`categoria_id`, política `ON DELETE RESTRICT`); a exigência de que a categoria esteja **ativa** é regra de aplicação, validada pelo serviço, não havendo restrição no schema que a imponha.
- **RN006:** O campo **Código de Barras** é opcional, limitado a 20 caracteres e, quando informado, deve ser único em todo o cadastro de produtos (restrição `uq_produto_codigo_barras`).
- **RN007:** O campo **Referência** (código interno) é opcional e limitado a 10 caracteres.
- **RN008:** Os campos **Valor de Compra**, **Valor de Venda**, **Percentual de Lucro**, **Quantidade** (estoque atual) e **Quantidade Mínima** devem ser maiores ou iguais a zero (restrição `ck_produto_valores`). Os valores padrão são zero para os quatro primeiros e um para a Quantidade Mínima.
- **RN009:** Os campos **Quantidade** e **Quantidade Mínima** aceitam casas decimais (precisão de três casas), de modo a comportar produtos vendidos por fração (por exemplo, quilograma ou litro).
- **RN010:** Os campos **Descrição** e **Observação** são opcionais e limitados a 255 caracteres cada.
- **RN011:** Todo produto é criado com a situação **Ativo** verdadeira por padrão.
- **RN012:** A exclusão de produto referenciado por itens de nota de entrada, itens de nota de saída ou vínculos produto-fornecedor (políticas `ON DELETE RESTRICT`) é vedada fisicamente; nesse caso o sistema oferece a **exclusão lógica** (inativação via campo `ativo`).
- **RN013:** Os vínculos de fornecedores do produto são gerenciados como itens-filhos do cadastro do produto, conforme detalhado no caso de uso UC206 — Gerenciar Fornecedores do Produto.

## Nome do Caso de Uso

Gerenciar Produto.

## Atores

Usuário autenticado (Administrador ou Operador).

## Pré-Condição

O usuário deve estar autenticado no sistema e possuir permissão de acesso ao módulo de cadastros de conveniência. Devem existir ao menos uma marca e uma unidade de medida ativas previamente cadastradas.

## Pós-Condição

O produto é inserido, alterado, inativado ou excluído e a base de dados é atualizada de forma consistente.

## Ativação

Este caso de uso é iniciado quando o usuário seleciona a opção "Produtos" no menu de cadastros do módulo Conveniência.

## Fluxo Principal

### Consultar

1. O usuário seleciona a opção "Produtos" no menu de cadastros.
2. O sistema apresenta a lista de produtos cadastrados, exibindo as colunas: Nome, Marca, Categoria, Código de Barras, Valor de Venda, Quantidade em estoque e Situação (Ativo/Inativo).
3. O sistema disponibiliza campos de busca por Nome, Código de Barras e Referência, além de filtros por Marca, Categoria e Situação.
4. O usuário informa, opcionalmente, um ou mais critérios de busca e aciona "Pesquisar".
5. O sistema filtra e exibe os registros correspondentes aos critérios informados.
6. O sistema disponibiliza as ações "Inserir", "Alterar" e "Excluir" sobre a lista.
7. O caso de uso é encerrado.

## Fluxos Alternativos

### Inserir

1. No fluxo principal, o usuário aciona a opção "Inserir".
2. O sistema apresenta o formulário de cadastro com os campos:
   - **Nome*** (texto, até 120 caracteres);
   - **Código de Barras** (texto, até 20 caracteres, único);
   - **Referência** (texto, até 10 caracteres);
   - **Marca*** (seleção de marca ativa);
   - **Unidade de Medida*** (seleção de unidade de medida ativa);
   - **Categoria** (seleção de categoria ativa);
   - **Valor de Compra** (numérico, decimal, maior ou igual a zero, padrão zero);
   - **Valor de Venda** (numérico, decimal, maior ou igual a zero, padrão zero);
   - **Percentual de Lucro** (numérico, decimal, maior ou igual a zero, padrão zero);
   - **Quantidade** (numérico, decimal com três casas, maior ou igual a zero, padrão zero);
   - **Quantidade Mínima** (numérico, decimal com três casas, maior ou igual a zero, padrão um);
   - **Descrição** (texto, até 255 caracteres);
   - **Observação** (texto, até 255 caracteres);
   - **Ativo** (situação, padrão verdadeiro);
   - **Fornecedores do Produto** (lista de itens-filhos, gerenciada conforme UC206).
3. O usuário preenche os campos e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2, E3, E4).
5. O sistema persiste o novo produto com situação Ativo igual a verdadeiro.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Alterar

1. No fluxo principal, o usuário seleciona um produto e aciona "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do registro, incluindo a lista de fornecedores vinculados.
3. O usuário edita os campos desejados e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2, E3, E4).
5. O sistema persiste as alterações.
6. O sistema exibe a mensagem "Registro salvo com sucesso." e retorna à consulta.

### Excluir

1. No fluxo principal, o usuário seleciona um produto e aciona "Excluir".
2. O sistema solicita a confirmação da operação.
3. O usuário confirma a exclusão.
4. O sistema verifica se o produto está referenciado por itens de nota de entrada, itens de nota de saída ou vínculos produto-fornecedor (E5).
5. Não havendo vínculo, o sistema remove fisicamente o registro.
6. O sistema exibe a mensagem "Registro excluído com sucesso." e retorna à consulta.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** No fluxo Inserir ou Alterar, caso o campo Nome, Marca ou Unidade de Medida não tenha sido preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo [nome do campo] é obrigatório."** O sistema retorna ao formulário mantendo os dados informados.
- **E2 — Código de barras já cadastrado:** No fluxo Inserir ou Alterar, caso o Código de Barras informado já esteja cadastrado em outro produto, o sistema interrompe a gravação e exibe a mensagem: **"Código de barras já cadastrado para outro produto."** O sistema retorna ao formulário mantendo os dados informados.
- **E3 — Valor inválido:** No fluxo Inserir ou Alterar, caso algum dos campos Valor de Compra, Valor de Venda, Percentual de Lucro, Quantidade ou Quantidade Mínima seja informado com valor negativo, o sistema interrompe a gravação e exibe a mensagem: **"Os valores e quantidades devem ser maiores ou iguais a zero."** O sistema retorna ao formulário mantendo os dados informados.
- **E4 — Referência inexistente ou inativa:** No fluxo Inserir ou Alterar, caso a Marca, a Unidade de Medida ou a Categoria informada não exista ou esteja inativa, o sistema interrompe a gravação e exibe a mensagem: **"O(A) [nome do campo] informado(a) não existe ou está inativo(a)."** O sistema retorna ao formulário mantendo os dados informados. A existência do registro referenciado é garantida pelas chaves estrangeiras `fk_produto_marca`, `fk_produto_unidade` e `fk_produto_categoria`; a exigência de que o registro esteja **ativo** é regra de aplicação, validada pelo serviço (não há `CHECK` nem gatilho no schema que imponha essa condição).
- **E5 — Registro vinculado a outro:** No fluxo Excluir, caso o produto esteja referenciado por itens de nota de entrada, itens de nota de saída ou vínculos produto-fornecedor, o sistema impede a exclusão física e exibe a mensagem: **"Este produto possui movimentações ou vínculos e não pode ser excluído. Deseja desativá-lo?"** Caso o usuário confirme, o sistema realiza a exclusão lógica (define `ativo` igual a falso) e exibe a mensagem "Registro desativado com sucesso."; caso contrário, a operação é cancelada.

---

## UC206 — Gerenciar Fornecedores do Produto

## Regras de Negócio

- **RN001:** O acesso ao caso de uso é permitido somente a usuário autenticado no sistema (perfil `ADMIN` ou `OPERADOR`).
- **RN002:** O vínculo produto-fornecedor é gerenciado a partir do cadastro de um produto existente (UC205); todo vínculo pertence obrigatoriamente a um produto (chave estrangeira `produto_id`, política `ON DELETE RESTRICT`).
- **RN003:** O campo **Fornecedor** é de preenchimento obrigatório e deve referenciar um fornecedor cadastrado e ativo (chave estrangeira `fornecedor_id`, política `ON DELETE RESTRICT`).
- **RN004:** Não é permitido vincular o mesmo fornecedor mais de uma vez ao mesmo produto (restrição de unicidade `uq_produto_fornecedor` sobre o par produto-fornecedor).
- **RN005:** O campo **Código do Produto no Fornecedor** é opcional e limitado a 50 caracteres.
- **RN006:** O campo **Custo** é opcional e, quando informado, deve ser maior ou igual a zero (restrição `ck_produto_fornecedor_custo`).
- **RN007:** Todo vínculo é criado com a situação **Ativo** verdadeira por padrão.
- **RN008:** A inativação de um vínculo produto-fornecedor (campo `ativo` igual a falso) preserva o histórico e remove o fornecedor da lista de fornecedores ativos do produto.

## Nome do Caso de Uso

Gerenciar Fornecedores do Produto.

## Atores

Usuário autenticado (Administrador ou Operador).

## Pré-Condição

O usuário deve estar autenticado, em manutenção de um produto previamente cadastrado, e devem existir fornecedores ativos cadastrados no sistema.

## Pós-Condição

O vínculo produto-fornecedor é inserido, alterado, inativado ou excluído e a base de dados é atualizada de forma consistente.

## Ativação

Este caso de uso é iniciado quando o usuário, no formulário de manutenção de um produto (UC205), aciona a aba ou seção "Fornecedores do Produto".

## Fluxo Principal

### Consultar

1. No formulário de manutenção de um produto, o usuário aciona a seção "Fornecedores do Produto".
2. O sistema apresenta a lista de fornecedores vinculados ao produto, exibindo as colunas: Fornecedor, Código do Produto no Fornecedor, Custo e Situação (Ativo/Inativo).
3. O sistema disponibiliza as ações "Inserir", "Alterar" e "Excluir" sobre a lista de vínculos.
4. O caso de uso é encerrado.

## Fluxos Alternativos

### Inserir

1. Na seção "Fornecedores do Produto", o usuário aciona a opção "Inserir".
2. O sistema apresenta o formulário de vínculo com os campos:
   - **Fornecedor*** (seleção de fornecedor ativo);
   - **Código do Produto no Fornecedor** (texto, até 50 caracteres);
   - **Custo** (numérico, decimal, maior ou igual a zero);
   - **Ativo** (situação, padrão verdadeiro).
3. O usuário preenche os campos e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E1, E2, E3).
5. Caso já exista um vínculo **inativo** entre o produto e o fornecedor selecionado, o sistema **reativa** esse vínculo (define `ativo` igual a verdadeiro) e atualiza seus dados com os valores informados, em vez de criar um novo registro — evitando a violação da restrição de unicidade incondicional `uq_produto_fornecedor (produto_id, fornecedor_id)`. Não havendo vínculo prévio, o sistema adiciona um novo vínculo à lista de fornecedores do produto com situação Ativo igual a verdadeiro.
6. O sistema exibe a mensagem "Fornecedor vinculado com sucesso." e retorna à lista de vínculos.

### Alterar

1. Na seção "Fornecedores do Produto", o usuário seleciona um vínculo e aciona "Alterar".
2. O sistema apresenta o formulário preenchido com os dados atuais do vínculo.
3. O usuário edita os campos desejados (Código do Produto no Fornecedor, Custo, Ativo) e aciona "Salvar".
4. O sistema valida os dados conforme as regras de negócio (E3).
5. O sistema persiste as alterações do vínculo.
6. O sistema exibe a mensagem "Vínculo alterado com sucesso." e retorna à lista de vínculos.

### Excluir

1. Na seção "Fornecedores do Produto", o usuário seleciona um vínculo e aciona "Excluir".
2. O sistema solicita a confirmação da operação.
3. O usuário confirma a exclusão.
4. O sistema realiza a **exclusão lógica** do vínculo produto-fornecedor (define `ativo` igual a falso), preservando o histórico conforme **RN008** e removendo o fornecedor da lista de fornecedores ativos do produto.
5. O sistema exibe a mensagem "Vínculo desativado com sucesso." e retorna à lista de vínculos.

## Fluxos de Exceção

- **E1 — Campo obrigatório não preenchido:** No fluxo Inserir, caso o campo Fornecedor não tenha sido preenchido, o sistema interrompe a gravação e exibe a mensagem: **"O campo Fornecedor é obrigatório."** O sistema retorna ao formulário mantendo os dados informados.
- **E2 — Fornecedor já vinculado e ativo:** No fluxo Inserir, caso o fornecedor selecionado já possua um vínculo **ativo** com o produto, o sistema interrompe a gravação e exibe a mensagem: **"Este fornecedor já está vinculado a este produto."** O sistema retorna ao formulário mantendo os dados informados. (Caso o vínculo existente esteja **inativo**, não há exceção: o sistema o reativa, conforme o passo 5 do fluxo Inserir.)
- **E3 — Custo inválido:** No fluxo Inserir ou Alterar, caso o campo Custo seja informado com valor negativo, o sistema interrompe a gravação e exibe a mensagem: **"O Custo deve ser maior ou igual a zero."** O sistema retorna ao formulário mantendo os dados informados.
