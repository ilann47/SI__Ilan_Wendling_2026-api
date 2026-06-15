> Links: [[README]] · [[04-requisitos-funcionais]] · [[09-diagramas-sequencia]]

# 6. Protótipos

## 6.1 Nota sobre a Natureza dos Protótipos (Divergência Documentada)

> **Divergência em relação ao modelo acadêmico tradicional.** O modelo de "Especificação de Requisitos por Casos de Uso" prevê, nesta seção, a apresentação de **protótipos de telas** (mockups, wireframes ou capturas de uma interface gráfica). O sistema **Estacionamento Kaneko** na sua versão 1 (v1), entretanto, **não possui telas próprias**: a entrega da v1 é um **backend Spring Boot 4 (Java 25)** que expõe a sua funcionalidade exclusivamente como uma **API REST**, cuja interface de exploração e teste é a documentação interativa **Swagger / OpenAPI** (springdoc), disponível em `/{contexto}/swagger-ui.html`.

Por essa razão, os "protótipos" apresentados neste capítulo **não são imagens de telas**, mas sim **descrições conceituais de tela** — a especificação textual de *como uma tela deveria se comportar* (propósito, campos exibidos, ações disponíveis) — sistematicamente **mapeada para os endpoints REST** que a sustentariam. Cada protótipo conceitual cumpre, portanto, duas funções:

1. **Função de projeto de interface (UX):** descreve a tela que um futuro front-end (web ou mobile, previsto apenas para versões posteriores) deverá implementar, indicando o propósito, os campos exibidos e as ações disponíveis. Os campos descritos estão **aterrados no esquema físico** (`database/schema.sql`): cada campo de tela corresponde a uma coluna real de uma tabela, e cada validação de tela reflete uma restrição `NOT NULL`, `UNIQUE` ou `CHECK` do banco.
2. **Função de contrato técnico (API):** para cada ação de tela (listar, abrir, salvar, excluir, gerar relatório), indica-se o **endpoint REST correspondente** — método HTTP, caminho e um resumo do *payload* (corpo da requisição ou parâmetros), no formato `MÉTODO /api/recurso`.

### 6.1.1 Convenções de Mapeamento REST

Todo o sistema adota um **padrão CRUD uniforme** sobre os recursos. Para um recurso genérico `recurso`, o contrato é:

| Tela conceitual | Verbo HTTP | Caminho | Resumo do *payload* |
|---|---|---|---|
| Listagem | `GET` | `/api/recursos` | *query params*: `?nome=&ativo=&page=&size=&sort=` (filtro, paginação, ordenação) |
| Detalhe (abrir) | `GET` | `/api/recursos/{id}` | — (sem corpo) |
| Cadastro (inserir) | `POST` | `/api/recursos` | corpo JSON com os campos da entidade (sem `id`) |
| Edição (alterar) | `PUT` | `/api/recursos/{id}` | corpo JSON com os campos a atualizar |
| Exclusão | `DELETE` | `/api/recursos/{id}` | — (exclusão preferencialmente **lógica**: inativa via `ativo = false`) |

Convenções gerais válidas para todos os protótipos:

- **Respostas:** `200 OK` (consulta/atualização), `201 Created` (inserção, com `Location`), `204 No Content` (exclusão lógica/física sem corpo), `400 Bad Request` (validação), `404 Not Found` (id inexistente), `409 Conflict` (violação de unicidade ou de regra `RESTRICT`), `422 Unprocessable Entity` (regra de negócio).
- **Exclusão lógica vs. física:** seguindo a Seção [[04-requisitos-funcionais]], `DELETE` realiza preferencialmente **inativação** (`ativo = false`); a remoção física é bloqueada (`409`) quando há vínculos protegidos por `ON DELETE RESTRICT`.
- **Segurança:** todos os endpoints, exceto o login, exigem autenticação (cabeçalho `Authorization: Bearer <token>`). O perfil do usuário (`ADMIN` / `OPERADOR`, coluna `usuario.perfil`) determina o acesso aos módulos administrativos.
- **Aterramento no schema:** os nomes de campo apresentados nas telas correspondem às colunas reais; restrições de tela (obrigatoriedade, unicidade, faixas de valor) refletem `NOT NULL`, `UNIQUE` e `CHECK` do `schema.sql`.

### 6.1.2 Organização dos Protótipos

Os protótipos estão agrupados conforme os módulos do sistema:

- **6.2 Acesso / Login** — autenticação e sessão.
- **6.3 Dashboard Operacional do Pátio** — painel-resumo de operação.
- **6.4 Módulo Geografia** — País, Estado, Cidade.
- **6.5 Módulo RH / Usuários** — Cargo, Funcionário, Usuário.
- **6.6 Módulo Pessoas** — Cliente, Fornecedor (e contatos do fornecedor).
- **6.7 Módulo Pátio** — Veículo, Tarifa, Mensalista, Movimentação.
- **6.8 Módulo Conveniência** — Categoria, Marca, Unidade de Medida, Serviço, Produto.
- **6.9 Módulo Fiscal** — Nota de Entrada, Nota de Saída, Nota de Serviço.
- **6.10 Módulo Financeiro** — Formas e Condições de Pagamento, Contas a Pagar, Contas a Receber, Contas a Pagar Avulsa.

Para evitar repetição exaustiva, as telas de **cadastro de apoio** (que seguem rigorosamente o CRUD-padrão da tabela acima) são descritas de forma sintética, enquanto as telas que carregam **regras de negócio relevantes** (Movimentação, Mensalista, Notas, Contas) recebem descrição detalhada. Cada protótipo cobre, conforme aplicável, os estados de **Listagem, Cadastro/Inserir, Edição, Exclusão** e, ao final de cada módulo, os **Relatórios**.

---

## 6.2 Acesso / Login

### 6.2.1 Tela Conceitual — Login

**Propósito.** Autenticar o operador ou administrador no sistema, estabelecendo a sessão a partir da qual todas as demais operações serão autorizadas. É a porta de entrada única do sistema; nenhuma outra tela é acessível sem uma sessão válida.

**Campos exibidos.**
- **Login** (`usuario.login`) — obrigatório; identificador único do usuário (comparação *case-insensitive*, conforme índice `uq_usuario_login` sobre `LOWER(login)`).
- **Senha** (`usuario.senha`) — obrigatório; campo mascarado. No banco é armazenada apenas como *hash*; a verificação ocorre na aplicação, jamais é trafegada ou registrada em claro.

**Ações.**
- **Entrar** — submete as credenciais; em caso de sucesso, recebe o *token* de sessão e o perfil (`usuario.perfil`), redirecionando ao Dashboard.
- **Sair (logout)** — encerra a sessão corrente.

**Regras e mensagens.** Usuário inativo (`usuario.ativo = false`) é impedido de entrar. Credenciais inválidas produzem mensagem genérica ("login ou senha inválidos"), sem revelar qual campo falhou. O perfil retornado condiciona a visibilidade dos módulos administrativos (RH/Usuários, por exemplo, restrito a `ADMIN`).

### 6.2.2 Mapeamento REST — Acesso

| Ação de tela | Verbo + Caminho | *Payload* resumido |
|---|---|---|
| Autenticar (entrar) | `POST /api/auth/login` | `{ login, senha }` → resposta `{ token, perfil, nome, expiraEm }` |
| Dados do usuário logado | `GET /api/auth/me` | — (lê o *token*) → `{ id, nome, login, perfil }` |
| Encerrar sessão | `POST /api/auth/logout` | — (invalida o *token*) |

---

## 6.3 Dashboard Operacional do Pátio

### 6.3.1 Tela Conceitual — Dashboard

**Propósito.** Oferecer ao operador a visão imediata do estado do pátio assim que ele se autentica: quantos veículos estão no pátio agora, qual a receita do dia e quais pendências exigem ação. É uma tela de **leitura consolidada** (somente consulta), composta por *cards* de indicadores e listas resumidas — não há cadastro nesta tela.

**Indicadores exibidos (cards), aterrados no schema.**
- **Veículos no pátio agora** — contagem de `movimentacao` com `status = 'ABERTO'` (regra reforçada pelo índice único `uq_mov_veiculo_aberta`).
- **Entradas e saídas do dia** — contagem de `movimentacao` por `data_entrada` / `data_saida` do dia corrente.
- **Receita avulsa do dia** — soma de `movimentacao.valor_cobrado` das saídas (`status = 'FECHADO'`) de hoje.
- **Mensalistas ativos** — contagem de `mensalista` com `status = 'ATIVO'`.
- **Contas a receber vencendo hoje / em atraso** — de `conta_receber` por `data_vencimento` e `situacao IN ('PENDENTE','PARCIAL')`.
- **Produtos abaixo do mínimo** — `produto` onde `quantidade < quantidade_minima` (alerta de reposição da conveniência).

**Listas resumidas.**
- **Pátio atual** — veículos com movimentação aberta: placa (`veiculo.placa`), tipo (`movimentacao.tipo` AVULSO/MENSALISTA), hora de entrada (`data_entrada`) e tempo decorrido.
- **Últimas saídas** — placa, permanência e `valor_cobrado`.

**Ações.** A partir dos *cards*, o operador pode **navegar** (atalhos) para: registrar entrada/saída (Movimentação, 6.7), abrir a lista de mensalistas, ou abrir contas a receber em atraso. A tela em si não altera dados.

### 6.3.2 Mapeamento REST — Dashboard

| Ação de tela | Verbo + Caminho | *Payload* resumido |
|---|---|---|
| Carregar painel-resumo | `GET /api/dashboard/resumo` | — → `{ veiculosNoPatio, entradasHoje, saidasHoje, receitaAvulsaHoje, mensalistasAtivos, contasReceberVencendoHoje, contasReceberAtrasadas, produtosAbaixoMinimo }` |
| Lista do pátio atual | `GET /api/movimentacoes?status=ABERTO` | *query*: `status=ABERTO` → itens com placa, tipo, `data_entrada` |
| Últimas saídas | `GET /api/movimentacoes?status=FECHADO&data=hoje` | *query*: período → placa, permanência, `valor_cobrado` |

---

## 6.4 Módulo Geografia

Hierarquia **País → Estado → Cidade**, base para a nacionalidade e o endereço de pessoas. As três telas seguem o CRUD-padrão (6.1.1), com exclusão **lógica** (`ativo`) e bloqueio de exclusão física por `ON DELETE RESTRICT`.

### 6.4.1 Telas Conceituais

**País.** *Listagem* exibe nome, sigla, código e nacionalidade (gentílico), com filtro por nome e por situação. *Cadastro/Edição* expõe os campos `nome` (obrigatório e único — `uq_pais_nome`), `sigla`, `codigo`, `nacionalidade` e `ativo`. *Exclusão* inativa; bloqueada (409) se houver estados, funcionários, clientes ou fornecedores vinculados.

**Estado.** *Listagem* exibe nome, UF e país, com filtro por país e UF. *Cadastro/Edição* expõe `nome`, `uf` (até 3 caracteres), `pais_id` (seletor de país) e `ativo`; a combinação UF+país é única (`uq_estado_uf_pais`). *Exclusão* bloqueada se houver cidades vinculadas.

**Cidade.** *Listagem* exibe nome, DDD, código IBGE e estado, com filtro por estado. *Cadastro/Edição* expõe `nome`, `ddd`, `codigo_ibge`, `estado_id` (seletor) e `ativo`; nome único por estado (`uq_cidade_nome_estado`). *Exclusão* bloqueada se houver pessoas vinculadas.

### 6.4.2 Mapeamento REST — Geografia

| Recurso | Listar | Detalhe | Inserir | Alterar | Excluir |
|---|---|---|---|---|---|
| País | `GET /api/paises` | `GET /api/paises/{id}` | `POST /api/paises` | `PUT /api/paises/{id}` | `DELETE /api/paises/{id}` |
| Estado | `GET /api/estados` | `GET /api/estados/{id}` | `POST /api/estados` | `PUT /api/estados/{id}` | `DELETE /api/estados/{id}` |
| Cidade | `GET /api/cidades` | `GET /api/cidades/{id}` | `POST /api/cidades` | `PUT /api/cidades/{id}` | `DELETE /api/cidades/{id}` |

*Payloads:* País `{ nome, sigla?, codigo?, nacionalidade?, ativo? }`; Estado `{ nome, uf, paisId, ativo? }`; Cidade `{ nome, ddd?, codigoIbge?, estadoId, ativo? }`. Filtros típicos: `?nome=`, `?ativo=`, `?paisId=` (estados), `?estadoId=` (cidades), além de `page`, `size`, `sort`.

### 6.4.3 Relatórios — Geografia

- **Cidades por Estado/País** — listagem hierárquica para conferência cadastral. `GET /api/relatorios/geografia/cidades?paisId=&estadoId=`.

---

## 6.5 Módulo RH / Usuários

Cobre **Cargo**, **Funcionário** e **Usuário** (acesso ao sistema). O cadastro de Usuário e a alteração de perfil são restritos a `ADMIN`.

### 6.5.1 Telas Conceituais

**Cargo.** *Listagem*: nome, salário-base, carga horária e exigência de CNH (`requer_cnh`). *Cadastro/Edição*: `nome` (único — `uq_cargo_nome`), `descricao`, `salario_base` (≥ 0), `carga_horaria` (≥ 0), `requer_cnh`, `ativo`. *Exclusão* lógica; física bloqueada se houver funcionários no cargo.

**Funcionário.** *Listagem*: nome, CPF, cargo, situação. Filtros por nome, cargo e situação. *Cadastro/Edição* (formulário extenso): dados pessoais — `nome`, `apelido`, `tipo` (FISICA/JURIDICA), `cpf` (único — `uq_funcionario_cpf`), `rg_inscricao_estadual`, `data_nascimento`, `sexo`, `estado_civil`; contato — `telefone`, `email`; endereço — `endereco`, `numero`, `complemento`, `bairro`, `cep`, `cidade_id` (seletor); vínculo — `nacionalidade_id` (país), `cargo_id` (obrigatório); CNH — `cnh`, `data_validade_cnh` (exigível quando o cargo tem `requer_cnh = true`); contrato — `data_admissao`, `data_demissao`, `salario` (≥ 0); `observacao`, `ativo`. *Exclusão* lógica (inativação / demissão via `data_demissao`); física bloqueada se houver usuário vinculado.

**Usuário.** *Listagem*: nome, login, perfil, situação. *Cadastro/Edição*: `nome`, `login` (único, *case-insensitive*), `senha` (somente na criação/redefinição; armazenada como *hash*), `email` (único quando informado), `funcionario_id` (vínculo opcional 1:1 — `uq_usuario_funcionario`), `perfil` (`ADMIN`/`OPERADOR`), `ativo`. A senha **nunca** é exibida em listagens ou detalhes.

### 6.5.2 Mapeamento REST — RH / Usuários

| Recurso | Listar | Detalhe | Inserir | Alterar | Excluir |
|---|---|---|---|---|---|
| Cargo | `GET /api/cargos` | `GET /api/cargos/{id}` | `POST /api/cargos` | `PUT /api/cargos/{id}` | `DELETE /api/cargos/{id}` |
| Funcionário | `GET /api/funcionarios` | `GET /api/funcionarios/{id}` | `POST /api/funcionarios` | `PUT /api/funcionarios/{id}` | `DELETE /api/funcionarios/{id}` |
| Usuário | `GET /api/usuarios` | `GET /api/usuarios/{id}` | `POST /api/usuarios` | `PUT /api/usuarios/{id}` | `DELETE /api/usuarios/{id}` |

*Payloads:* Cargo `{ nome, descricao?, salarioBase, cargaHoraria?, requerCnh, ativo? }`; Funcionário `{ nome, tipo, cpf, cargoId, cidadeId?, nacionalidadeId?, dataAdmissao, salario?, ... }`; Usuário `{ nome, login, senha, email?, funcionarioId?, perfil, ativo? }`. Operação adicional: `PUT /api/usuarios/{id}/senha` (redefinir senha) com `{ senhaAtual?, novaSenha }`.

### 6.5.3 Relatórios — RH

- **Funcionários por Cargo** — `GET /api/relatorios/rh/funcionarios?cargoId=&ativo=`.
- **CNH a vencer** — funcionários cujo `data_validade_cnh` vence no período. `GET /api/relatorios/rh/cnh-a-vencer?ate=`.

---

## 6.6 Módulo Pessoas

Cobre **Cliente** (dono de plano mensalista e/ou comprador na conveniência) e **Fornecedor** (com múltiplos e-mails e telefones). Ambos compartilham a estrutura de pessoa física/jurídica com endereço e condição de pagamento padrão.

### 6.6.1 Telas Conceituais

**Cliente.** *Listagem*: nome, documento (CPF/CNPJ), telefone, situação; filtros por nome, documento e situação. *Cadastro/Edição*: `nome`, `apelido`, `tipo` (FISICA/JURIDICA), `documento` (único — `uq_cliente_documento`), `rg_inscricao_estadual`, `data_nascimento`, `sexo`, `estado_civil`, contato (`telefone`, `email`), endereço (`endereco`, `numero`, `complemento`, `bairro`, `cep`, `cidade_id`), `nacionalidade_id`, `condicao_pagamento_id` (seletor), `limite_credito` (≥ 0), `observacao`, `ativo`. *Exclusão* lógica; física bloqueada se houver veículos, mensalistas, notas de saída/serviço ou contas a receber vinculados (`ON DELETE RESTRICT`).

**Fornecedor.** *Listagem*: razão social, nome fantasia, documento, situação. *Cadastro/Edição*: `nome` (razão social), `nome_fantasia`, `apelido`, `tipo` (default JURIDICA), `documento` (único — `uq_fornecedor_documento`), `rg_inscricao_estadual`, endereço, `cidade_id`, `nacionalidade_id`, `condicao_pagamento_id`, `limite_credito`, `observacao`, `ativo`. A tela inclui **sub-grids** para contatos:
- **E-mails** (`fornecedor_email`): `email`, `tipo` (COMERCIAL/FINANCEIRO/COMPRAS/VENDAS/SUPORTE), `principal` (apenas um por fornecedor — `uq_fornecedor_email_principal`).
- **Telefones** (`fornecedor_telefone`): `telefone`, `tipo` (COMERCIAL/RESIDENCIAL/CELULAR/FAX/WHATSAPP), `principal` (único — `uq_fornecedor_telefone_principal`).

Os contatos seguem `ON DELETE CASCADE`: excluir o fornecedor remove seus e-mails/telefones.

### 6.6.2 Mapeamento REST — Pessoas

| Recurso | Listar | Detalhe | Inserir | Alterar | Excluir |
|---|---|---|---|---|---|
| Cliente | `GET /api/clientes` | `GET /api/clientes/{id}` | `POST /api/clientes` | `PUT /api/clientes/{id}` | `DELETE /api/clientes/{id}` |
| Fornecedor | `GET /api/fornecedores` | `GET /api/fornecedores/{id}` | `POST /api/fornecedores` | `PUT /api/fornecedores/{id}` | `DELETE /api/fornecedores/{id}` |
| E-mails do fornecedor | `GET /api/fornecedores/{id}/emails` | — | `POST /api/fornecedores/{id}/emails` | `PUT /api/fornecedores/{id}/emails/{emailId}` | `DELETE /api/fornecedores/{id}/emails/{emailId}` |
| Telefones do fornecedor | `GET /api/fornecedores/{id}/telefones` | — | `POST /api/fornecedores/{id}/telefones` | `PUT /api/fornecedores/{id}/telefones/{telId}` | `DELETE /api/fornecedores/{id}/telefones/{telId}` |

*Payloads:* Cliente `{ nome, tipo, documento, cidadeId?, nacionalidadeId?, condicaoPagamentoId?, limiteCredito?, ... }`; Fornecedor `{ nome, nomeFantasia?, tipo, documento, condicaoPagamentoId?, limiteCredito?, emails:[{email,tipo,principal}], telefones:[{telefone,tipo,principal}] }`.

### 6.6.3 Relatórios — Pessoas

- **Clientes com mensalista ativo** — `GET /api/relatorios/clientes/mensalistas-ativos`.
- **Fornecedores por produto fornecido** (N:N via `produto_fornecedor`) — `GET /api/relatorios/fornecedores?produtoId=`.

---

## 6.7 Módulo Pátio

Núcleo operacional do sistema: **Veículo**, **Tarifa**, **Mensalista** (contrato) e **Movimentação** (o ticket de entrada/saída). Concentra as regras de negócio mais relevantes.

### 6.7.1 Tela Conceitual — Veículo

*Listagem*: placa, modelo, tipo (CARRO/MOTO), cliente (quando mensalista), situação; filtro por placa e por cliente. *Cadastro/Edição*: `placa` (única — `uq_veiculo_placa`), `modelo`, `cor`, `tipo`, `cliente_id` (opcional — vazio para veículo avulso; preenchido para mensalista), `observacao`, `ativo`. *Exclusão* lógica; física bloqueada se houver movimentações ou mensalistas vinculados.

### 6.7.2 Tela Conceitual — Tarifa

*Listagem*: descrição, valor/hora, valor diária (teto), tolerância (min) e mensalidade; destaque para a tarifa vigente (`ativo = true`). *Cadastro/Edição*: `descricao`, `valor_hora` (≥ 0), `valor_diaria` (teto diário do avulso, ≥ 0), `tolerancia_min` (cortesia — abaixo desse tempo, gratuito), `valor_mensalidade` (valor-padrão do plano), `ativo`. O sistema já nasce com uma tarifa-padrão (*seed*: "Tarifa padrao", R$10/h, R$50 diária, 15 min, R$250 mensal).

### 6.7.3 Tela Conceitual — Mensalista (Contrato)

**Propósito.** Gerir o contrato que vincula um **cliente** a um **veículo** por uma **mensalidade**. É um cadastro com regra de negócio: **no máximo um contrato `ATIVO` por veículo** (`uq_mensalista_veiculo_ativo`).

*Listagem*: cliente, veículo (placa), valor mensal, vigência (`data_inicio`–`data_fim`) e `status` (ATIVO/SUSPENSO/CANCELADO). *Cadastro/Edição*: `cliente_id` (obrigatório), `veiculo_id` (obrigatório), `valor_mensal`, `data_inicio`, `data_fim` (nula = vigente), `status`. *Ações de fluxo*: **Ativar**, **Suspender** e **Cancelar** o contrato (transições de `status`), além de **encerrar** (preencher `data_fim`). *Exclusão* preferencialmente por mudança de status (CANCELADO), preservando histórico.

### 6.7.4 Tela Conceitual — Movimentação (Ticket de Pátio)

**Propósito.** É a tela mais usada na operação. Registra a **entrada** e a **saída** de um veículo, calculando o valor a cobrar no caso avulso. Regra central: **no máximo uma movimentação `ABERTA` por veículo** (`uq_mov_veiculo_aberta`) — um veículo não entra duas vezes.

**Fluxo de Entrada.** O operador informa a **placa**. O sistema identifica se o veículo é **mensalista** (contrato ATIVO) ou **avulso**:
- Mensalista → cria `movimentacao` com `tipo = 'MENSALISTA'`, `mensalista_id` preenchido, `tarifa_id` nulo (coerência garantida por `ck_mov_tipo_coerencia`).
- Avulso → cria `movimentacao` com `tipo = 'AVULSO'`, `mensalista_id` nulo e `tarifa_id` = tarifa vigente.

Campos registrados: `veiculo_id`, `data_entrada` (default `now()`), `status = 'ABERTO'`.

**Fluxo de Saída.** O operador localiza a movimentação aberta (por placa) e aciona **Registrar Saída**. O sistema preenche `data_saida` e calcula `valor_cobrado`:
- **Mensalista:** R$ 0,00 (coberto pela mensalidade).
- **Avulso:** com base na permanência (`data_saida − data_entrada`), aplicando `tarifa.valor_hora`, respeitando a `tolerancia_min` (gratuito abaixo dela) e o teto `valor_diaria` por dia. Marca `status = 'FECHADO'`. A saída pode **gerar a Nota de Serviço** e/ou a **Conta a Receber** correspondente (ver 6.9 e 6.10).

*Listagem*: placa, tipo, entrada, saída, permanência, valor cobrado, status; filtros por status (ABERTO/FECHADO/CANCELADO) e período. *Cancelamento*: `status = 'CANCELADO'` para tickets registrados por engano.

### 6.7.5 Mapeamento REST — Pátio

| Recurso | Listar | Detalhe | Inserir | Alterar | Excluir |
|---|---|---|---|---|---|
| Veículo | `GET /api/veiculos` | `GET /api/veiculos/{id}` | `POST /api/veiculos` | `PUT /api/veiculos/{id}` | `DELETE /api/veiculos/{id}` |
| Tarifa | `GET /api/tarifas` | `GET /api/tarifas/{id}` | `POST /api/tarifas` | `PUT /api/tarifas/{id}` | `DELETE /api/tarifas/{id}` |
| Mensalista | `GET /api/mensalistas` | `GET /api/mensalistas/{id}` | `POST /api/mensalistas` | `PUT /api/mensalistas/{id}` | `DELETE /api/mensalistas/{id}` |
| Movimentação | `GET /api/movimentacoes` | `GET /api/movimentacoes/{id}` | `POST /api/movimentacoes/entrada` | `PUT /api/movimentacoes/{id}/saida` | `DELETE /api/movimentacoes/{id}` |

*Endpoints de fluxo (regras de negócio):*

| Ação | Verbo + Caminho | *Payload* resumido |
|---|---|---|
| Registrar entrada | `POST /api/movimentacoes/entrada` | `{ placa }` → identifica mensalista/avulso e abre o ticket |
| Registrar saída | `PUT /api/movimentacoes/{id}/saida` | — → preenche `data_saida`, calcula `valor_cobrado`, fecha |
| Cancelar ticket | `PUT /api/movimentacoes/{id}/cancelar` | `{ motivo? }` → `status = CANCELADO` |
| Suspender contrato | `PUT /api/mensalistas/{id}/suspender` | — → `status = SUSPENSO` |
| Cancelar contrato | `PUT /api/mensalistas/{id}/cancelar` | `{ dataFim? }` → `status = CANCELADO` |

### 6.7.6 Relatórios — Pátio

- **Movimentações por período** (entradas/saídas, receita avulsa) — `GET /api/relatorios/movimentacoes?de=&ate=&tipo=`.
- **Ocupação atual do pátio** — `GET /api/relatorios/patio/ocupacao` (veículos com `status = ABERTO`).
- **Mensalistas ativos / vencimentos** — `GET /api/relatorios/mensalistas?status=`.

---

## 6.8 Módulo Conveniência

Cadastros de apoio da loja de conveniência — **Categoria**, **Marca**, **Unidade de Medida**, **Serviço** — e o cadastro central de **Produto** (com estoque). As quatro primeiras telas seguem o CRUD-padrão.

### 6.8.1 Telas Conceituais — Apoio

- **Categoria** (`categoria`): `nome` (único), `descricao`, `ativo`. Agrupa produtos.
- **Marca** (`marca`): `nome` (único), `ativo`.
- **Unidade de Medida** (`unidade_medida`): `nome`, `sigla` (única — `uq_unidade_sigla`, ex.: UN, KG, L), `ativo`.
- **Serviço** (`servico`): `nome` (único), `descricao`, `valor_padrao` (≥ 0), `ativo`. Tipos de serviço prestado (Estacionamento Avulso, Mensalidade, Lavagem) — referenciados pela Nota de Serviço.

### 6.8.2 Tela Conceitual — Produto

*Listagem*: nome, código de barras, marca, categoria, estoque atual e mínimo, valor de venda; **alerta visual** quando `quantidade < quantidade_minima`. Filtros por nome, marca, categoria e por "abaixo do mínimo". *Cadastro/Edição*: `nome`, `codigo_barras` (único quando informado — `uq_produto_codigo_barras`), `referencia` (código interno), `marca_id` (obrigatório), `unidade_medida_id` (obrigatório), `categoria_id`, `valor_compra` (≥ 0), `valor_venda` (≥ 0), `percentual_lucro` (margem-alvo), `quantidade` (estoque atual — aceita fração para KG/L), `quantidade_minima`, `descricao`, `observacao`, `ativo`. O estoque (`quantidade`) é **movimentado pelas notas** (entrada soma, saída subtrai ao confirmar) e não editado livremente na rotina. A tela inclui sub-grid de **fornecedores do produto** (`produto_fornecedor`: `codigo_prod`, `custo`).

### 6.8.3 Mapeamento REST — Conveniência

| Recurso | Listar | Detalhe | Inserir | Alterar | Excluir |
|---|---|---|---|---|---|
| Categoria | `GET /api/categorias` | `GET /api/categorias/{id}` | `POST /api/categorias` | `PUT /api/categorias/{id}` | `DELETE /api/categorias/{id}` |
| Marca | `GET /api/marcas` | `GET /api/marcas/{id}` | `POST /api/marcas` | `PUT /api/marcas/{id}` | `DELETE /api/marcas/{id}` |
| Unidade de Medida | `GET /api/unidades-medida` | `GET /api/unidades-medida/{id}` | `POST /api/unidades-medida` | `PUT /api/unidades-medida/{id}` | `DELETE /api/unidades-medida/{id}` |
| Serviço | `GET /api/servicos` | `GET /api/servicos/{id}` | `POST /api/servicos` | `PUT /api/servicos/{id}` | `DELETE /api/servicos/{id}` |
| Produto | `GET /api/produtos` | `GET /api/produtos/{id}` | `POST /api/produtos` | `PUT /api/produtos/{id}` | `DELETE /api/produtos/{id}` |
| Fornecedores do produto | `GET /api/produtos/{id}/fornecedores` | — | `POST /api/produtos/{id}/fornecedores` | `PUT /api/produtos/{id}/fornecedores/{pfId}` | `DELETE /api/produtos/{id}/fornecedores/{pfId}` |

*Payload Produto:* `{ nome, codigoBarras?, referencia?, marcaId, unidadeMedidaId, categoriaId?, valorCompra, valorVenda, percentualLucro?, quantidadeMinima, ... }`.

### 6.8.4 Relatórios — Conveniência

- **Posição de estoque** (atual × mínimo) — `GET /api/relatorios/produtos/estoque?abaixoMinimo=`.
- **Produtos por categoria/marca** — `GET /api/relatorios/produtos?categoriaId=&marcaId=`.

---

## 6.9 Módulo Fiscal

Documentos fiscais: **Nota de Entrada** (compra → soma estoque), **Nota de Saída** (venda → subtrai estoque) e **Nota de Serviço** (NFS-e do estacionamento, com ISS). Todas têm cabeçalho + itens (exceto a de serviço, com valor único de serviço) e um ciclo de vida por `situacao`.

### 6.9.1 Tela Conceitual — Nota de Entrada

**Propósito.** Registrar a compra de mercadorias do fornecedor e, ao **confirmar**, dar entrada no estoque dos produtos.

*Listagem*: número/série, fornecedor, data de emissão, valor total, `situacao` (PENDENTE/CONFIRMADA/CANCELADA). *Cadastro/Edição* (cabeçalho): `numero`, `modelo` (default 55), `serie`, `fornecedor_id` (obrigatório), `condicao_pagamento_id`, `data_emissao`, `data_chegada`, `tipo_frete` (CIF/FOB/SEM), valores (`valor_produtos`, `valor_frete`, `valor_seguro`, `outras_despesas`, `valor_desconto`, `valor_total`), `observacao`. Trio (`numero`, `serie`, `fornecedor_id`) único (`uq_nota_entrada`). **Grid de itens** (`item_nota_entrada`): `produto_id`, `sequencia`, `quantidade` (> 0), `valor_unitario`, descontos, rateios (frete/seguro/outras) e `custo_preco_final`. *Ações de fluxo*: **Confirmar** (situação → CONFIRMADA, soma estoque, pode gerar Contas a Pagar) e **Cancelar**.

### 6.9.2 Tela Conceitual — Nota de Saída

**Propósito.** Registrar a venda de produtos da conveniência e, ao **confirmar**, baixar o estoque.

*Listagem*: número/série, cliente, emissão, total, `situacao`. *Cadastro/Edição* (cabeçalho): `numero`, `modelo`, `serie`, `cliente_id` (obrigatório), `condicao_pagamento_id`, `data_emissao`, `data_saida`, `tipo_frete`, valores e `observacao`. Trio (`numero`, `serie`, `cliente_id`) único (`uq_nota_saida`). **Grid de itens** (`item_nota_saida`) análogo ao da entrada. *Ações*: **Confirmar** (baixa estoque — bloqueia se faltar saldo — e pode gerar Contas a Receber) e **Cancelar**.

### 6.9.3 Tela Conceitual — Nota de Serviço (NFS-e)

**Propósito.** Documentar o serviço de estacionamento prestado, com **ISS**. A origem pode ser uma **movimentação avulsa** *ou* uma **mensalidade** (nunca ambas — `ck_nota_servico_origem`).

*Listagem*: número/série, cliente, serviço, valor total, ISS, `situacao` (PENDENTE/EMITIDA/CANCELADA). *Cadastro/Edição*: `numero`, `serie` (par único — `uq_nota_servico`), `cliente_id` (obrigatório), `servico_id`, origem (`movimentacao_id` **ou** `mensalista_id`), `condicao_pagamento_id`, `forma_pagamento_id`, `data_emissao`, `valor_servico`, `aliquota_iss` (0–100), `valor_iss` (≤ valor do serviço — `ck_nota_servico_iss_base`; coerência alíquota/valor por `ck_nota_servico_iss_coerencia`), `valor_desconto` (≤ serviço), `valor_total`, `observacao`. *Ações*: **Emitir** (situação → EMITIDA) e **Cancelar**.

### 6.9.4 Mapeamento REST — Fiscal

| Recurso | Listar | Detalhe | Inserir | Alterar | Excluir/Cancelar |
|---|---|---|---|---|---|
| Nota de Entrada | `GET /api/notas-entrada` | `GET /api/notas-entrada/{id}` | `POST /api/notas-entrada` | `PUT /api/notas-entrada/{id}` | `DELETE /api/notas-entrada/{id}` |
| Nota de Saída | `GET /api/notas-saida` | `GET /api/notas-saida/{id}` | `POST /api/notas-saida` | `PUT /api/notas-saida/{id}` | `DELETE /api/notas-saida/{id}` |
| Nota de Serviço | `GET /api/notas-servico` | `GET /api/notas-servico/{id}` | `POST /api/notas-servico` | `PUT /api/notas-servico/{id}` | `DELETE /api/notas-servico/{id}` |

*Endpoints de fluxo:*

| Ação | Verbo + Caminho | Efeito |
|---|---|---|
| Confirmar entrada | `PUT /api/notas-entrada/{id}/confirmar` | situação → CONFIRMADA; **soma** estoque; gera contas a pagar |
| Confirmar saída | `PUT /api/notas-saida/{id}/confirmar` | situação → CONFIRMADA; **baixa** estoque; gera contas a receber |
| Emitir nota de serviço | `PUT /api/notas-servico/{id}/emitir` | situação → EMITIDA |
| Cancelar (qualquer nota) | `PUT /api/notas-{tipo}/{id}/cancelar` | situação → CANCELADA |

*Payload (cabeçalho + itens):* `{ numero, serie, fornecedorId|clienteId, dataEmissao, condicaoPagamentoId?, itens:[{ produtoId, quantidade, valorUnitario, valorDesconto? }] }`. A Nota de Serviço usa `{ clienteId, servicoId?, movimentacaoId?|mensalistaId?, valorServico, aliquotaIss }`.

### 6.9.5 Relatórios — Fiscal

- **Notas por período e situação** — `GET /api/relatorios/notas?tipo=entrada|saida|servico&de=&ate=&situacao=`.
- **ISS apurado no período** (notas de serviço EMITIDAS) — `GET /api/relatorios/fiscal/iss?de=&ate=`.

---

## 6.10 Módulo Financeiro

Contas a pagar e a receber, formas e condições de pagamento, e despesas avulsas (sem nota). As contas-título são geradas a partir das notas/contratos e **baixadas** (quitadas) na rotina financeira.

### 6.10.1 Telas Conceituais — Pagamento (apoio)

**Forma de Pagamento** (`forma_pagamento`): `nome` (único), `descricao` (obrigatória), `ativo` — ex.: Dinheiro, Cartão, Pix, Boleto.

**Condição de Pagamento** (`condicao_pagamento`): `nome` (único), `numero_parcelas` (≥ 1), `dias_primeira_parcela`, `dias_entre_parcelas`, percentuais de `juros`/`multa`/`desconto`, `ativo`. *Sub-grid* de parcelas (`parcela_condicao_pagamento`): `numero`, `dias`, `percentual` (0–100), `forma_pagamento_id` (opcional por parcela); a soma dos percentuais das parcelas compõe 100% do título.

### 6.10.2 Tela Conceitual — Contas a Pagar

**Propósito.** Controlar os títulos a pagar (de notas de entrada ou avulsos), por parcela, e registrar a **baixa**.

*Listagem*: fornecedor, número/total de parcela, valor total, vencimento, `situacao` (PENDENTE/PARCIAL/PAGA/CANCELADA); filtros por situação, fornecedor e período de vencimento; destaque para **vencidas**. *Detalhe/Edição*: `fornecedor_id`, `nota_entrada_id` (origem), `condicao_pagamento_id`, `forma_pagamento_id`, `numero_parcela`/`total_parcelas`, `valor_original`, `valor_desconto`, `valor_juros`, `valor_multa`, `valor_total`, `valor_pago` (≤ total), `data_emissao`, `data_vencimento` (≥ emissão), `data_pagamento`, `observacao`. *Ação de fluxo*: **Baixar** (registrar pagamento) — informa `valor_pago`, `data_pagamento` e `forma_pagamento`; situação passa a PARCIAL ou PAGA conforme `ck_conta_pagar_baixa`.

### 6.10.3 Tela Conceitual — Contas a Receber

Análoga à de pagar, voltada ao **cliente**. *Detalhe/Edição*: `cliente_id`, origem `nota_saida_id` **ou** `mensalista_id` (nunca ambas — `ck_conta_receber_origem`), `condicao_pagamento_id`, `forma_pagamento_id`, parcelas, valores (`valor_original`, `valor_recebido` ≤ total, descontos/juros/multa, `valor_total`), `data_emissao`, `data_vencimento`, `data_recebimento`, `situacao` (PENDENTE/PARCIAL/RECEBIDA/CANCELADA). *Ação de fluxo*: **Baixar** (registrar recebimento).

### 6.10.4 Tela Conceitual — Contas a Pagar Avulsa

**Propósito.** Lançar despesas sem nota fiscal (aluguel, luz, água, salários). *Campos*: `descricao` (obrigatória), `fornecedor_id` (opcional), `forma_pagamento_id`, dados de nota opcionais (`numero_nota`, `modelo`, `serie`), parcelas, valores e datas, `situacao`. Mesmo fluxo de **baixa** das contas a pagar.

### 6.10.5 Mapeamento REST — Financeiro

| Recurso | Listar | Detalhe | Inserir | Alterar | Excluir |
|---|---|---|---|---|---|
| Forma de Pagamento | `GET /api/formas-pagamento` | `GET /api/formas-pagamento/{id}` | `POST /api/formas-pagamento` | `PUT /api/formas-pagamento/{id}` | `DELETE /api/formas-pagamento/{id}` |
| Condição de Pagamento | `GET /api/condicoes-pagamento` | `GET /api/condicoes-pagamento/{id}` | `POST /api/condicoes-pagamento` | `PUT /api/condicoes-pagamento/{id}` | `DELETE /api/condicoes-pagamento/{id}` |
| Contas a Pagar | `GET /api/contas-pagar` | `GET /api/contas-pagar/{id}` | `POST /api/contas-pagar` | `PUT /api/contas-pagar/{id}` | `DELETE /api/contas-pagar/{id}` |
| Contas a Receber | `GET /api/contas-receber` | `GET /api/contas-receber/{id}` | `POST /api/contas-receber` | `PUT /api/contas-receber/{id}` | `DELETE /api/contas-receber/{id}` |
| Contas a Pagar Avulsa | `GET /api/contas-pagar-avulsa` | `GET /api/contas-pagar-avulsa/{id}` | `POST /api/contas-pagar-avulsa` | `PUT /api/contas-pagar-avulsa/{id}` | `DELETE /api/contas-pagar-avulsa/{id}` |

*Endpoints de fluxo (baixa de título):*

| Ação | Verbo + Caminho | *Payload* resumido |
|---|---|---|
| Baixar conta a pagar | `PUT /api/contas-pagar/{id}/baixar` | `{ valorPago, dataPagamento, formaPagamentoId, valorDesconto?, valorJuros?, valorMulta? }` |
| Baixar conta a receber | `PUT /api/contas-receber/{id}/baixar` | `{ valorRecebido, dataRecebimento, formaPagamentoId, ... }` |
| Baixar despesa avulsa | `PUT /api/contas-pagar-avulsa/{id}/baixar` | `{ valorPago, dataPagamento, formaPagamentoId, ... }` |

### 6.10.6 Relatórios — Financeiro

- **Fluxo de caixa / contas por vencimento** — `GET /api/relatorios/financeiro/fluxo?de=&ate=`.
- **Contas a receber em atraso** (inadimplência) — `GET /api/relatorios/contas-receber?situacao=PENDENTE&vencidasAte=`.
- **Contas a pagar em aberto** — `GET /api/relatorios/contas-pagar?situacao=PENDENTE,PARCIAL`.

---

## 6.11 Encerramento

Os protótipos acima descrevem, de forma conceitual e aterrada no esquema físico (`database/schema.sql`), o conjunto de telas que a API REST do **Estacionamento Kaneko** sustenta na v1. Reforça-se a divergência registrada em **6.1**: a interface efetivamente entregue é a **documentação OpenAPI/Swagger** dos endpoints aqui mapeados; as telas gráficas (front-end web/mobile) permanecem fora do escopo da versão 1 e poderão ser construídas em versões futuras, consumindo exatamente o contrato REST aqui especificado. A sequência exata das interações que cada tela dispara é detalhada em [[09-diagramas-sequencia]].
