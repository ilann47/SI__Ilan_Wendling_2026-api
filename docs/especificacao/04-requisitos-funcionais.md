> Links: [[README]] · [[03-der]] · [[05-casos-de-uso]]

# 4. Lista de Requisitos Funcionais

Esta seção apresenta a lista de Requisitos Funcionais (RF) do sistema **Estacionamento Kaneko**, derivada diretamente do modelo de dados (`database/schema.sql`) e do escopo aprovado para a versão 1. Os requisitos estão organizados em três grupos:

1. **Manter Cadastros** — operações básicas de manutenção (Listar, Inserir, Alterar, Excluir) sobre as entidades de cadastro do sistema.
2. **Processos de Negócio** — funcionalidades que combinam regras de negócio, transações e atualização de múltiplas entidades.
3. **Relatórios** — consultas consolidadas para apoio à operação e à gestão.

Cada cadastro segue o padrão de quatro operações de manutenção (CRUD). Para os cadastros, adota-se a regra geral de que a exclusão é, preferencialmente, **lógica** (inativação por meio do campo `ativo`), evitando a perda de histórico e a violação de integridade referencial, conforme as restrições `ON DELETE RESTRICT` definidas no banco. A exclusão física só é permitida quando o registro não possuir vínculos.

**Convenções:**
- A numeração dos RF segue o formato hierárquico `4.X.Y` (seção, cadastro/processo, operação).
- Os campos obrigatórios e as validações citadas refletem as restrições `NOT NULL`, `UNIQUE` e `CHECK` do esquema físico.
- O termo "registro ativo" refere-se ao campo booleano `ativo = TRUE`.

---

## Cadastros de Entidades

Esta subseção reúne os requisitos de manutenção (operações CRUD: Listar, Inserir, Alterar e Excluir) das entidades de cadastro do sistema, organizados por entidade.

## 4.1 Manter Cadastro de País

Entidade responsável por armazenar os países utilizados como nacionalidade de pessoas (funcionários, clientes e fornecedores) e como raiz da hierarquia geográfica (País → Estado → Cidade).

### 4.1.1 Listar Países
O sistema deve permitir listar os países cadastrados, exibindo nome, sigla, código e nacionalidade (gentílico), com filtro por nome e por situação (ativo/inativo) e ordenação por nome.

### 4.1.2 Inserir País
O sistema deve permitir cadastrar um novo país informando, no mínimo, o **nome** (obrigatório e único) e, opcionalmente, a sigla ISO, o código e a nacionalidade. Não é permitido cadastrar dois países com o mesmo nome (restrição `uq_pais_nome`).

### 4.1.3 Alterar País
O sistema deve permitir editar os dados de um país existente, mantendo a unicidade do nome e preservando os vínculos com estados e pessoas.

### 4.1.4 Excluir País
O sistema deve permitir excluir (inativar) um país. A exclusão física deve ser bloqueada quando houver estados, funcionários, clientes ou fornecedores vinculados a ele (`ON DELETE RESTRICT`).

---

## 4.2 Manter Cadastro de Estado

Entidade que representa as unidades federativas, vinculadas a um país.

### 4.2.1 Listar Estados
O sistema deve permitir listar os estados, exibindo nome, UF e país correspondente, com filtro por país, por UF e por situação.

### 4.2.2 Inserir Estado
O sistema deve permitir cadastrar um estado informando **nome**, **UF** (sigla de 2 caracteres) e o **país** (obrigatórios). Não é permitido cadastrar a mesma UF duas vezes para o mesmo país (restrição `uq_estado_uf_pais`).

### 4.2.3 Alterar Estado
O sistema deve permitir editar nome, UF e país de um estado, respeitando a unicidade de UF por país.

### 4.2.4 Excluir Estado
O sistema deve permitir excluir (inativar) um estado. A exclusão física deve ser bloqueada quando houver cidades vinculadas (`ON DELETE RESTRICT`).

---

## 4.3 Manter Cadastro de Cidade

Entidade que representa os municípios, vinculados a um estado. Utilizada nos endereços de funcionários, clientes e fornecedores.

### 4.3.1 Listar Cidades
O sistema deve permitir listar as cidades, exibindo nome, DDD, código IBGE e o estado/UF correspondente, com filtro por estado e por nome.

### 4.3.2 Inserir Cidade
O sistema deve permitir cadastrar uma cidade informando **nome** e **estado** (obrigatórios) e, opcionalmente, DDD e código IBGE. Não é permitido cadastrar duas cidades com o mesmo nome dentro do mesmo estado (restrição `uq_cidade_nome_estado`).

### 4.3.3 Alterar Cidade
O sistema deve permitir editar os dados de uma cidade, respeitando a unicidade do nome por estado.

### 4.3.4 Excluir Cidade
O sistema deve permitir excluir (inativar) uma cidade. A exclusão física deve ser bloqueada quando houver pessoas vinculadas a ela (`ON DELETE RESTRICT`).

---

## 4.4 Manter Cadastro de Cargo

Entidade que descreve as funções exercidas pelos funcionários, com parâmetros salariais e de jornada.

### 4.4.1 Listar Cargos
O sistema deve permitir listar os cargos, exibindo nome, descrição, salário-base, carga horária e indicador de exigência de CNH, com filtro por nome e por situação.

### 4.4.2 Inserir Cargo
O sistema deve permitir cadastrar um cargo informando o **nome** (obrigatório e único) e, opcionalmente, descrição, salário-base, carga horária mensal e a indicação de que o cargo exige CNH. O salário-base e a carga horária não podem ser negativos (restrição `ck_cargo_valores`).

### 4.4.3 Alterar Cargo
O sistema deve permitir editar os dados de um cargo, mantendo a unicidade do nome.

### 4.4.4 Excluir Cargo
O sistema deve permitir excluir (inativar) um cargo. A exclusão física deve ser bloqueada quando houver funcionários vinculados (`ON DELETE RESTRICT`).

---

## 4.5 Manter Cadastro de Funcionário

Entidade que representa os colaboradores do estacionamento, com dados pessoais, de endereço e contratuais.

### 4.5.1 Listar Funcionários
O sistema deve permitir listar os funcionários, exibindo nome, CPF, cargo, telefone e situação, com filtro por nome, por cargo e por situação (ativo/inativo).

### 4.5.2 Inserir Funcionário
O sistema deve permitir cadastrar um funcionário informando, no mínimo, **nome**, **CPF** e **cargo** (obrigatórios), além dos dados complementares (tipo pessoa, RG/IE, data de nascimento, sexo, estado civil, contatos, endereço completo, cidade, nacionalidade, CNH e validade, datas de admissão/demissão, salário e observação). O CPF deve ser único (restrição `uq_funcionario_cpf`); o salário, quando informado, não pode ser negativo; os domínios de tipo, sexo e estado civil são validados (restrições `ck_funcionario_*`).

### 4.5.3 Alterar Funcionário
O sistema deve permitir editar os dados de um funcionário, mantendo a unicidade do CPF e a integridade dos vínculos com cargo, cidade e nacionalidade.

### 4.5.4 Excluir Funcionário
O sistema deve permitir excluir (inativar/demitir) um funcionário. A exclusão física deve ser bloqueada quando houver usuário vinculado (`ON DELETE RESTRICT`). Recomenda-se o registro da data de demissão e a inativação, em vez da exclusão física.

---

## 4.6 Manter Cadastro de Usuário

Entidade responsável pelo controle de acesso ao sistema, podendo estar associada a um funcionário.

### 4.6.1 Listar Usuários
O sistema deve permitir listar os usuários, exibindo nome, login, e-mail, perfil de acesso e situação, com filtro por perfil e por situação. A senha nunca deve ser exibida.

### 4.6.2 Inserir Usuário
O sistema deve permitir cadastrar um usuário informando **nome**, **login** e **senha** (obrigatórios), além de e-mail, funcionário vinculado e **perfil** de acesso (`ADMIN` ou `OPERADOR`, padrão `OPERADOR`). A senha deve ser armazenada como hash, nunca em texto puro. O login é único (sem distinção de maiúsculas/minúsculas — `uq_usuario_login`), o e-mail é único quando informado e cada funcionário pode estar associado a no máximo um usuário (`uq_usuario_funcionario`).

### 4.6.3 Alterar Usuário
O sistema deve permitir editar nome, e-mail, perfil e situação de um usuário, além de redefinir a senha (com novo hash), mantendo as regras de unicidade de login e e-mail.

### 4.6.4 Excluir Usuário
O sistema deve permitir excluir (inativar) um usuário, bloqueando o acesso ao sistema. A inativação é preferível à exclusão física para preservar a rastreabilidade.

---

## 4.7 Manter Cadastro de Forma de Pagamento

Entidade que define os meios de pagamento aceitos (ex.: Dinheiro, Cartão, Pix, Boleto).

### 4.7.1 Listar Formas de Pagamento
O sistema deve permitir listar as formas de pagamento, exibindo nome, descrição e situação, com filtro por situação.

### 4.7.2 Inserir Forma de Pagamento
O sistema deve permitir cadastrar uma forma de pagamento informando **nome** (obrigatório e único) e **descrição** (obrigatória). Não é permitido cadastrar duas formas com o mesmo nome (restrição `uq_forma_pagamento_nome`).

### 4.7.3 Alterar Forma de Pagamento
O sistema deve permitir editar nome e descrição de uma forma de pagamento, mantendo a unicidade do nome.

### 4.7.4 Excluir Forma de Pagamento
O sistema deve permitir excluir (inativar) uma forma de pagamento. A exclusão física deve ser bloqueada quando houver contas a pagar, contas a receber, despesas avulsas (`fk_contas_pagar_avulsa_forma`) ou notas de serviço vinculadas (`ON DELETE RESTRICT`). As parcelas de condição de pagamento, por sua vez, não bloqueiam a exclusão: o vínculo é apenas anulado (`forma_pagamento_id` definido como nulo, `ON DELETE SET NULL`).

---

## 4.8 Manter Cadastro de Condição de Pagamento

Entidade que define os planos de parcelamento (número de parcelas, prazos e percentuais de juros, multa e desconto). Possui detalhamento opcional em parcelas (`parcela_condicao_pagamento`).

### 4.8.1 Listar Condições de Pagamento
O sistema deve permitir listar as condições de pagamento, exibindo nome, número de parcelas, prazos e percentuais aplicados, com filtro por nome e por situação.

### 4.8.2 Inserir Condição de Pagamento
O sistema deve permitir cadastrar uma condição de pagamento informando **nome** (obrigatório e único), número de parcelas, dias para a primeira parcela, dias entre parcelas e os percentuais de juros, multa e desconto. Opcionalmente, deve permitir detalhar cada parcela (número, dias para vencimento, percentual do total e forma de pagamento específica), exigindo que a soma dos percentuais seja coerente (100%). As restrições `ck_condicao_parcelas`, `ck_condicao_percentuais` e `ck_parcela_valores` devem ser respeitadas (valores não negativos; desconto e percentual de parcela entre 0 e 100).

### 4.8.3 Alterar Condição de Pagamento
O sistema deve permitir editar os parâmetros de uma condição de pagamento e suas parcelas, mantendo a unicidade do nome e a consistência dos percentuais.

### 4.8.4 Excluir Condição de Pagamento
O sistema deve permitir excluir (inativar) uma condição de pagamento. As parcelas associadas são removidas em cascata (`ON DELETE CASCADE`), porém a exclusão física deve ser bloqueada quando houver fornecedores, clientes, notas ou contas financeiras vinculados (`ON DELETE RESTRICT`).

---

## 4.9 Manter Cadastro de Fornecedor

Entidade que representa os fornecedores de produtos e serviços, com dados cadastrais, endereço, condição de pagamento padrão e múltiplos contatos (e-mails e telefones).

### 4.9.1 Listar Fornecedores
O sistema deve permitir listar os fornecedores, exibindo razão social/nome fantasia, documento (CNPJ/CPF), cidade e situação, com filtro por nome, por documento e por situação.

### 4.9.2 Inserir Fornecedor
O sistema deve permitir cadastrar um fornecedor informando, no mínimo, **nome** (razão social) e **documento** (obrigatórios), além de nome fantasia, apelido, tipo de pessoa (`FISICA`/`JURIDICA`, padrão `JURIDICA`), RG/IE, endereço completo, cidade, nacionalidade, condição de pagamento, limite de crédito e observação. O documento deve ser único (restrição `uq_fornecedor_documento`) e o limite de crédito não pode ser negativo. Deve ser possível cadastrar múltiplos e-mails e telefones (com tipo e indicador de principal), admitindo no máximo um contato principal de cada tipo (`uq_fornecedor_email_principal`, `uq_fornecedor_telefone_principal`).

### 4.9.3 Alterar Fornecedor
O sistema deve permitir editar os dados de um fornecedor e gerenciar seus contatos (incluir, alterar e remover e-mails/telefones), mantendo a unicidade do documento.

### 4.9.4 Excluir Fornecedor
O sistema deve permitir excluir (inativar) um fornecedor. Os contatos vinculados são removidos em cascata (`ON DELETE CASCADE`), mas a exclusão física deve ser bloqueada quando houver notas de entrada, contas a pagar ou vínculos produto-fornecedor associados (`ON DELETE RESTRICT`).

---

## 4.10 Manter Cadastro de Cliente

Entidade que representa os clientes do estacionamento (donos de planos mensalistas e destinatários de notas de saída/serviço).

### 4.10.1 Listar Clientes
O sistema deve permitir listar os clientes, exibindo nome, documento (CPF/CNPJ), telefone, cidade e situação, com filtro por nome, por documento e por situação.

### 4.10.2 Inserir Cliente
O sistema deve permitir cadastrar um cliente informando, no mínimo, **nome** e **documento** (obrigatórios), além de apelido, tipo de pessoa (`FISICA`/`JURIDICA`, padrão `FISICA`), RG/IE, data de nascimento, sexo, estado civil, contatos, endereço completo, cidade, nacionalidade, condição de pagamento, limite de crédito e observação. O documento deve ser único (restrição `uq_cliente_documento`); o limite de crédito não pode ser negativo; os domínios de tipo, sexo e estado civil são validados (restrições `ck_cliente_*`).

### 4.10.3 Alterar Cliente
O sistema deve permitir editar os dados de um cliente, mantendo a unicidade do documento e a integridade dos vínculos com cidade, nacionalidade e condição de pagamento.

### 4.10.4 Excluir Cliente
O sistema deve permitir excluir (inativar) um cliente. A exclusão física deve ser bloqueada quando houver veículos, mensalistas, notas de saída/serviço ou contas a receber vinculados (`ON DELETE RESTRICT`).

---

## 4.11 Manter Cadastro de Veículo

Entidade que representa os veículos. Quando vinculado a um cliente, configura um veículo de mensalista; quando sem cliente, refere-se a um veículo avulso.

### 4.11.1 Listar Veículos
O sistema deve permitir listar os veículos, exibindo placa, modelo, cor, tipo (`CARRO`/`MOTO`) e cliente (quando houver), com filtro por placa, por cliente e por situação.

### 4.11.2 Inserir Veículo
O sistema deve permitir cadastrar um veículo informando a **placa** (obrigatória e única) e o **tipo** (`CARRO`/`MOTO`, padrão `CARRO`), além de modelo, cor, cliente (opcional) e observação. A placa deve ser única (restrição `uq_veiculo_placa`) e o tipo deve respeitar o domínio (`ck_veiculo_tipo`).

### 4.11.3 Alterar Veículo
O sistema deve permitir editar os dados de um veículo, inclusive vincular ou desvincular um cliente, mantendo a unicidade da placa.

### 4.11.4 Excluir Veículo
O sistema deve permitir excluir (inativar) um veículo. A exclusão física deve ser bloqueada quando houver mensalistas ou movimentações de pátio associados (`ON DELETE RESTRICT`).

---

## 4.12 Manter Cadastro de Tarifa

Entidade que centraliza os preços vigentes do estacionamento (valor por hora, diária, tolerância e mensalidade).

### 4.12.1 Listar Tarifas
O sistema deve permitir listar as tarifas, exibindo descrição, valor por hora, valor da diária, tolerância (minutos) e valor da mensalidade, com filtro por situação.

### 4.12.2 Inserir Tarifa
O sistema deve permitir cadastrar uma tarifa informando **descrição**, **valor por hora**, **valor da diária** e **valor da mensalidade** (obrigatórios), além da tolerância em minutos (padrão 15). Todos os valores devem ser não negativos (restrição `ck_tarifa_valores`).

### 4.12.3 Alterar Tarifa
O sistema deve permitir editar os valores de uma tarifa, garantindo que a tarifa vigente reflita os preços corretos para os cálculos de saída de pátio.

### 4.12.4 Excluir Tarifa
O sistema deve permitir excluir (inativar) uma tarifa. A exclusão física deve ser bloqueada quando houver movimentações que a referenciem (`ON DELETE RESTRICT`).

---

## 4.13 Manter Cadastro de Categoria

Entidade de apoio que agrupa produtos da conveniência.

### 4.13.1 Listar Categorias
O sistema deve permitir listar as categorias, exibindo nome, descrição e situação, com filtro por nome e por situação.

### 4.13.2 Inserir Categoria
O sistema deve permitir cadastrar uma categoria informando o **nome** (obrigatório e único) e, opcionalmente, a descrição. Não é permitido cadastrar duas categorias com o mesmo nome (restrição `uq_categoria_nome`).

### 4.13.3 Alterar Categoria
O sistema deve permitir editar nome e descrição de uma categoria, mantendo a unicidade do nome.

### 4.13.4 Excluir Categoria
O sistema deve permitir excluir (inativar) uma categoria. A exclusão física deve ser bloqueada quando houver produtos vinculados (`ON DELETE RESTRICT`).

---

## 4.14 Manter Cadastro de Marca

Entidade de apoio que identifica a marca/fabricante dos produtos.

### 4.14.1 Listar Marcas
O sistema deve permitir listar as marcas, exibindo nome e situação, com filtro por nome e por situação.

### 4.14.2 Inserir Marca
O sistema deve permitir cadastrar uma marca informando o **nome** (obrigatório e único). Não é permitido cadastrar duas marcas com o mesmo nome (restrição `uq_marca_nome`).

### 4.14.3 Alterar Marca
O sistema deve permitir editar o nome de uma marca, mantendo a unicidade.

### 4.14.4 Excluir Marca
O sistema deve permitir excluir (inativar) uma marca. A exclusão física deve ser bloqueada quando houver produtos vinculados (`ON DELETE RESTRICT`).

---

## 4.15 Manter Cadastro de Unidade de Medida

Entidade de apoio que define as unidades de comercialização dos produtos (ex.: Unidade, Quilograma, Litro).

### 4.15.1 Listar Unidades de Medida
O sistema deve permitir listar as unidades de medida, exibindo nome, sigla e situação, com filtro por situação.

### 4.15.2 Inserir Unidade de Medida
O sistema deve permitir cadastrar uma unidade de medida informando **nome** e **sigla** (obrigatórios). A sigla deve ser única (restrição `uq_unidade_sigla`).

### 4.15.3 Alterar Unidade de Medida
O sistema deve permitir editar nome e sigla de uma unidade de medida, mantendo a unicidade da sigla.

### 4.15.4 Excluir Unidade de Medida
O sistema deve permitir excluir (inativar) uma unidade de medida. A exclusão física deve ser bloqueada quando houver produtos vinculados (`ON DELETE RESTRICT`).

---

## 4.16 Manter Cadastro de Serviço

Entidade que define os tipos de serviço prestados (ex.: Estacionamento Avulso, Mensalidade, Lavagem), utilizada na emissão de notas de serviço.

### 4.16.1 Listar Serviços
O sistema deve permitir listar os serviços, exibindo nome, descrição, valor padrão e situação, com filtro por nome e por situação.

### 4.16.2 Inserir Serviço
O sistema deve permitir cadastrar um serviço informando o **nome** (obrigatório e único) e, opcionalmente, descrição e valor padrão. O valor padrão, quando informado, não pode ser negativo (restrição `ck_servico_valor`).

### 4.16.3 Alterar Serviço
O sistema deve permitir editar nome, descrição e valor padrão de um serviço, mantendo a unicidade do nome.

### 4.16.4 Excluir Serviço
O sistema deve permitir excluir (inativar) um serviço. A exclusão física deve ser bloqueada quando houver notas de serviço vinculadas (`ON DELETE RESTRICT`).

---

## 4.17 Manter Cadastro de Produto

Entidade que representa os itens da loja de conveniência, com controle de estoque, preços e margens.

### 4.17.1 Listar Produtos
O sistema deve permitir listar os produtos, exibindo nome, referência/código de barras, marca, categoria, unidade, valor de venda, quantidade em estoque e situação, com filtro por nome, por marca, por categoria e por situação, destacando os produtos com estoque abaixo do mínimo.

### 4.17.2 Inserir Produto
O sistema deve permitir cadastrar um produto informando, no mínimo, **nome**, **marca** e **unidade de medida** (obrigatórios), além de código de barras, referência interna, categoria, valor de compra, valor de venda, percentual de lucro, quantidade em estoque, quantidade mínima, descrição e observação. O código de barras, quando informado, deve ser único (restrição `uq_produto_codigo_barras`); todos os valores e quantidades devem ser não negativos (restrição `ck_produto_valores`).

### 4.17.3 Alterar Produto
O sistema deve permitir editar os dados cadastrais e de precificação de um produto. A alteração do estoque por meio deste cadastro deve ser restrita; movimentações de estoque ocorrem preferencialmente via notas de entrada e saída (ver Processos de Negócio).

### 4.17.4 Excluir Produto
O sistema deve permitir excluir (inativar) um produto. A exclusão física deve ser bloqueada quando houver itens de nota de entrada/saída ou vínculos com fornecedores associados (`ON DELETE RESTRICT`).

---

## Processos de Negócio

Os processos de negócio descrevem funcionalidades transacionais que aplicam regras específicas e atualizam múltiplas entidades de forma coordenada. Cada processo deve ser executado de forma atômica (tudo-ou-nada), garantindo a consistência do banco.

## 4.18 Registrar Entrada e Saída de Pátio

Processo central do estacionamento, que controla o ticket de pátio (`movimentacao`) e calcula o valor devido na saída.

### 4.18.1 Registrar Entrada de Veículo
O sistema deve permitir registrar a entrada de um veículo no pátio, criando uma movimentação com `data_entrada` (data/hora atual) e situação `ABERTO`. O operador deve informar o veículo (cadastrado ou cadastrável no ato) e o sistema deve classificar a movimentação como `MENSALISTA` (quando o veículo possuir contrato de mensalista `ATIVO`, vinculando o `mensalista_id`) ou `AVULSO` (vinculando a tarifa vigente). Não é permitido registrar uma segunda entrada para um veículo que já esteja no pátio (restrição `uq_mov_veiculo_aberta` — no máximo uma movimentação `ABERTO` por veículo).

### 4.18.2 Registrar Saída de Veículo
O sistema deve permitir registrar a saída de um veículo com movimentação `ABERTO`, preenchendo `data_saida` (data/hora atual, não anterior à entrada — `ck_mov_saida`), calculando o `valor_cobrado` e alterando a situação para `FECHADO`. Para veículos avulsos, o cálculo deve considerar o tempo de permanência, o valor por hora, a tolerância (cortesia abaixo do tempo mínimo) e o teto da diária. Para mensalistas, o valor cobrado é zero (coberto pela mensalidade). A coerência entre tipo e contrato deve ser respeitada (`ck_mov_tipo_coerencia`).

### 4.18.3 Cancelar Movimentação
O sistema deve permitir cancelar uma movimentação aberta (alterando a situação para `CANCELADO`), em casos de erro de registro, mantendo o histórico para auditoria.

### 4.18.4 Consultar Pátio
O sistema deve permitir consultar, em tempo real, os veículos atualmente no pátio (movimentações com situação `ABERTO`), exibindo placa, horário de entrada, tipo e tempo decorrido.

## 4.19 Contratar Mensalista

Processo de criação e gestão do contrato mensal que vincula um cliente a um veículo.

### 4.19.1 Contratar Mensalista
O sistema deve permitir registrar um contrato de mensalista informando **cliente**, **veículo** e **valor mensal** (obrigatórios), além da data de início (padrão: data atual) e, opcionalmente, a data de fim, com situação inicial `ATIVO`. Não é permitido manter mais de um contrato `ATIVO` para o mesmo veículo (restrição `uq_mensalista_veiculo_ativo`). O valor mensal deve, por padrão, ser sugerido a partir do `valor_mensalidade` da tarifa vigente.

### 4.19.2 Suspender/Reativar Mensalista
O sistema deve permitir alterar a situação de um contrato entre `ATIVO` e `SUSPENSO`, refletindo na geração de contas a receber e na classificação das movimentações.

### 4.19.3 Encerrar/Cancelar Mensalista
O sistema deve permitir encerrar um contrato registrando a `data_fim` (não anterior à data de início — `ck_mensalista_periodo`) e alterando a situação para `CANCELADO`, liberando o veículo para um novo contrato.

### 4.19.4 Gerar Mensalidade
O sistema deve permitir gerar a conta a receber referente à mensalidade de um contrato `ATIVO`, criando o lançamento financeiro vinculado ao mensalista (ver 4.23).

## 4.20 Registrar Compra / Nota de Entrada

Processo de registro da compra de mercadorias, com atualização de estoque e geração do financeiro.

### 4.20.1 Registrar Nota de Entrada
O sistema deve permitir registrar uma nota de entrada informando **número**, **fornecedor** (obrigatórios), modelo, série, condição de pagamento, datas de emissão/chegada, tipo de frete (`CIF`/`FOB`/`SEM`) e os valores acessórios (frete, seguro, outras despesas, desconto). A nota deve conter um ou mais itens (produto, quantidade > 0, valor unitário, descontos e rateios). A situação inicial é `PENDENTE`. Não é permitido registrar a mesma nota (número + série + fornecedor) duas vezes (restrição `uq_nota_entrada`).

### 4.20.2 Confirmar Nota de Entrada
O sistema deve permitir confirmar uma nota de entrada `PENDENTE`, alterando sua situação para `CONFIRMADA`. Na confirmação, o sistema deve **somar** as quantidades dos itens ao estoque dos produtos, recalcular o custo final (com rateios de frete/seguro/outras despesas) e gerar as parcelas de contas a pagar conforme a condição de pagamento (ver 4.22).

### 4.20.3 Cancelar Nota de Entrada
O sistema deve permitir cancelar uma nota de entrada, alterando a situação para `CANCELADA`. Quando a nota já estiver confirmada, o cancelamento deve estornar o estoque adicionado e cancelar as contas a pagar geradas.

### 4.20.4 Consultar Notas de Entrada
O sistema deve permitir consultar e listar notas de entrada por fornecedor, período de emissão e situação, exibindo seus itens e valores totalizados.

## 4.21 Registrar Venda / Nota de Saída

Processo de registro da venda de produtos, com baixa de estoque e geração do financeiro.

### 4.21.1 Registrar Nota de Saída
O sistema deve permitir registrar uma nota de saída informando **número**, **cliente** (obrigatórios), modelo, série, condição de pagamento, datas, tipo de frete e valores acessórios. A nota deve conter um ou mais itens (produto, quantidade > 0, valor unitário, descontos). A situação inicial é `PENDENTE`. Não é permitido registrar a mesma nota (número + série + cliente) duas vezes (restrição `uq_nota_saida`).

### 4.21.2 Confirmar Nota de Saída
O sistema deve permitir confirmar uma nota de saída `PENDENTE`, alterando sua situação para `CONFIRMADA`. Na confirmação, o sistema deve **subtrair** as quantidades dos itens do estoque dos produtos (validando saldo suficiente) e gerar as parcelas de contas a receber conforme a condição de pagamento (ver 4.23).

### 4.21.3 Cancelar Nota de Saída
O sistema deve permitir cancelar uma nota de saída, alterando a situação para `CANCELADA`. Quando a nota já estiver confirmada, o cancelamento deve estornar o estoque (devolução) e cancelar as contas a receber geradas.

### 4.21.4 Consultar Notas de Saída
O sistema deve permitir consultar e listar notas de saída por cliente, período de emissão e situação, exibindo seus itens e valores totalizados.

## 4.22 Emitir Nota de Serviço

Processo de emissão da nota fiscal de serviço (NFS-e) do estacionamento, com cálculo de ISS, podendo originar-se de uma movimentação avulsa ou de uma mensalidade.

### 4.22.1 Emitir Nota de Serviço
O sistema deve permitir emitir uma nota de serviço informando **cliente** (obrigatório), serviço, condição e forma de pagamento, valor do serviço, alíquota de ISS e desconto, com situação inicial `PENDENTE`. A nota pode estar vinculada a uma movimentação **ou** a um mensalista, nunca a ambos simultaneamente (restrição `ck_nota_servico_origem`). O número + série deve ser único (restrição `uq_nota_servico`). O sistema deve calcular o valor do ISS a partir da alíquota e validar a coerência fiscal (ISS ≤ valor do serviço, desconto ≤ valor do serviço, alíquota entre 0 e 100 — `ck_nota_servico_*`).

### 4.22.2 Confirmar/Emitir Nota de Serviço
O sistema deve permitir efetivar a emissão de uma nota de serviço `PENDENTE`, alterando sua situação para `EMITIDA` e gerando, conforme a condição de pagamento, a conta a receber correspondente (ver 4.23).

### 4.22.3 Cancelar Nota de Serviço
O sistema deve permitir cancelar uma nota de serviço, alterando a situação para `CANCELADA` e cancelando as contas a receber geradas.

### 4.22.4 Consultar Notas de Serviço
O sistema deve permitir consultar e listar notas de serviço por cliente, período de emissão e situação.

## 4.23 Gerenciar Contas a Pagar

Processo de controle das obrigações financeiras da empresa, geradas a partir de notas de entrada ou lançadas manualmente.

### 4.23.1 Gerar/Lançar Conta a Pagar
O sistema deve gerar contas a pagar (parceladas) automaticamente na confirmação de notas de entrada, conforme a condição de pagamento, registrando fornecedor, valores, número da parcela, total de parcelas e data de vencimento (não anterior à emissão — `ck_conta_pagar_vencimento`). Não deve haver duas parcelas com o mesmo número para a mesma nota de entrada (restrição `uq_conta_pagar_parcela`).

### 4.23.2 Baixar (Pagar) Conta a Pagar
O sistema deve permitir registrar o pagamento (total ou parcial) de uma conta a pagar, informando data de pagamento, valor pago, descontos, juros e multa, atualizando a situação para `PARCIAL` ou `PAGA`. O valor pago não pode exceder o valor total (`ck_conta_pagar_pago_teto`); quando `PAGA`, a data de pagamento é obrigatória (`ck_conta_pagar_baixa`).

### 4.23.3 Cancelar Conta a Pagar
O sistema deve permitir cancelar uma conta a pagar pendente (situação `CANCELADA`), em casos de erro ou estorno de nota.

### 4.23.4 Consultar Contas a Pagar
O sistema deve permitir consultar e listar contas a pagar por fornecedor, situação e período de vencimento, totalizando os valores em aberto.

## 4.24 Gerenciar Contas a Receber

Processo de controle dos créditos a receber, gerados a partir de notas de saída, notas de serviço ou mensalidades.

### 4.24.1 Gerar/Lançar Conta a Receber
O sistema deve gerar contas a receber (parceladas) automaticamente na confirmação de notas de saída/serviço e na geração de mensalidades, registrando cliente, origem (nota ou mensalista — nunca ambos, `ck_conta_receber_origem`), valores, parcela e data de vencimento. Não deve haver duas parcelas com o mesmo número para a mesma nota de saída (restrição `uq_conta_receber_parcela`).

### 4.24.2 Baixar (Receber) Conta a Receber
O sistema deve permitir registrar o recebimento (total ou parcial) de uma conta a receber, informando data, valor recebido, descontos, juros e multa, atualizando a situação para `PARCIAL` ou `RECEBIDA`. O valor recebido não pode exceder o valor total (`ck_conta_receber_recebido_teto`); quando `RECEBIDA`, a data de recebimento é obrigatória (`ck_conta_receber_baixa`).

### 4.24.3 Cancelar Conta a Receber
O sistema deve permitir cancelar uma conta a receber pendente (situação `CANCELADA`).

### 4.24.4 Consultar Contas a Receber
O sistema deve permitir consultar e listar contas a receber por cliente, situação e período de vencimento, totalizando os valores em aberto.

## 4.25 Lançar Despesa Avulsa

Processo de registro de despesas sem nota fiscal de mercadoria (ex.: aluguel, luz, água, salário), por meio da entidade `contas_pagar_avulsa`.

### 4.25.1 Lançar Despesa Avulsa
O sistema deve permitir lançar uma despesa avulsa informando **descrição**, **valor original**, **valor total** e **data de vencimento** (obrigatórios), além de fornecedor (opcional), forma de pagamento, dados de nota (número/modelo/série), parcelamento e observação, com situação inicial `PENDENTE`. Os valores devem ser não negativos e coerentes (valor total ≥ valor original − desconto — `ck_contas_pagar_avulsa_total_min`), e o vencimento não pode ser anterior à emissão.

### 4.25.2 Baixar (Pagar) Despesa Avulsa
O sistema deve permitir registrar o pagamento (total ou parcial) de uma despesa avulsa, informando data de pagamento, valor pago, descontos, juros e multa, atualizando a situação para `PARCIAL` ou `PAGA`, respeitando o teto do valor pago e a obrigatoriedade da data de pagamento quando `PAGA`.

### 4.25.3 Cancelar Despesa Avulsa
O sistema deve permitir cancelar uma despesa avulsa pendente (situação `CANCELADA`).

### 4.25.4 Consultar Despesas Avulsas
O sistema deve permitir consultar e listar despesas avulsas por fornecedor, situação e período de vencimento.

---

## Relatórios

Os relatórios consolidam dados das entidades transacionais para apoiar a operação diária e a gestão do estacionamento. Todos devem permitir visualização em tela e, preferencialmente, exportação (PDF/CSV).

## 4.26 Relatório de Pátio Atual
O sistema deve emitir um relatório dos veículos atualmente no pátio, a partir das movimentações com situação `ABERTO`, exibindo placa, modelo, tipo de movimentação (avulso/mensalista), horário de entrada e tempo de permanência decorrido, com totalizador de veículos por tipo. Serve ao controle operacional em tempo real.

## 4.27 Relatório de Faturamento do Dia
O sistema deve emitir um relatório de faturamento de um dia (ou período) informado, consolidando os valores cobrados nas saídas de pátio (`movimentacao` `FECHADO`), as notas de saída e de serviço confirmadas/emitidas e os recebimentos efetivados, com totalização geral e por origem (estacionamento avulso, mensalidade, conveniência e serviços).

## 4.28 Relatório de Contas a Vencer
O sistema deve emitir um relatório das contas a pagar e a receber com vencimento dentro de um período informado e situação em aberto (`PENDENTE`/`PARCIAL`), agrupadas por data de vencimento e por contraparte (fornecedor/cliente), destacando os títulos vencidos e totalizando os valores. Inclui as despesas avulsas em aberto.

## 4.29 Relatório de Estoque Mínimo
O sistema deve emitir um relatório dos produtos cujo estoque atual (`quantidade`) esteja igual ou inferior ao estoque mínimo (`quantidade_minima`), exibindo produto, marca, categoria, quantidade atual, quantidade mínima e fornecedores associados, para apoiar a reposição de mercadorias.

## 4.30 Relatório de Mensalistas Ativos
O sistema deve emitir um relatório dos contratos de mensalista com situação `ATIVO`, exibindo cliente, veículo (placa/modelo), valor mensal, data de início e situação das mensalidades (contas a receber) vinculadas, totalizando a receita mensal recorrente.

---

## Resumo de Rastreabilidade

| Grupo | Itens | Faixa de RF |
|-------|-------|-------------|
| Manter Cadastros | 17 cadastros (País, Estado, Cidade, Cargo, Funcionário, Usuário, Forma de Pagamento, Condição de Pagamento, Fornecedor, Cliente, Veículo, Tarifa, Categoria, Marca, Unidade de Medida, Serviço, Produto) | 4.1 a 4.17 |
| Processos de Negócio | 8 processos | 4.18 a 4.25 |
| Relatórios | 5 relatórios | 4.26 a 4.30 |

Os requisitos funcionais aqui listados servem de base para os Casos de Uso detalhados em [[05-casos-de-uso]] e estão fundamentados no modelo de dados descrito em [[03-der]].
