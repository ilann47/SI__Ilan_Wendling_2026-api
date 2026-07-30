> Links: [[README]] · [[05-casos-de-uso]] · [[10-requisitos-nao-funcionais]]

# Relatório de Conformidade — Estacionamento Kaneko (v1)

**Documento:** Relatório de Conformidade e Plano de Refatoração
**Sistema:** Estacionamento Kaneko Ltda. (Maringá-PR) — controle de estacionamento (mensalista + avulso) com módulos de Conveniência, Fiscal e Financeiro
**Versão do sistema auditado:** v1 (modelagem + especificação). **Atualização 2026-06-16:** o backend foi implementado (Spring Boot 4 / Java 25, 279 testes automatizados) — ver a seção **"Atualização — Status de Implementação"** ao final.
**Natureza:** Auditoria de qualidade e conformidade com o modelo acadêmico de "Especificação de Requisitos por Casos de Uso" (padrão prática profissional de Sistemas de Informação)
**Artefatos-base da auditoria:** `database/schema.sql` (schema físico validado, 825 linhas) e o conjunto de casos de uso em `docs/especificacao/05-casos-de-uso/`
**Autor:** Arquitetura de Software / Auditoria de Qualidade

Este relatório está organizado em três partes. A **Parte A** confronta, seção a seção, a documentação produzida com as dez seções previstas no modelo acadêmico. A **Parte B** declara e justifica as divergências deliberadas em relação a esse modelo. A **Parte C** apresenta um plano de refatoração priorizado (P1 a P4), derivado da auditoria crítica dos casos de uso, cobrindo banco de dados, camada de serviço (backend) e arquitetura.

---

## PARTE A — Conformidade com o Modelo Acadêmico

O modelo de referência (Documento de Visão + Especificação de Requisitos por Casos de Uso) prevê dez seções. **Todas as dez foram produzidas.** A tabela a seguir indica, para cada seção, o estado de atendimento, o(s) arquivo(s) que a materializa(m) e observações de auditoria.

| # | Seção (modelo) | Atendida? | Arquivo(s) | Observações da auditoria |
|---|---|---|---|---|
| 1 | Introdução (contexto, objetivo, escopo, justificativa) | **Sim** | `docs/especificacao/01-introducao.md` | Apresenta a empresa fictícia, o objetivo do sistema, o escopo v1 (mensalista + avulso) e a delimitação do que está fora do escopo. Base do contrato de escopo verificado nas demais seções. |
| 2 | Análise do Negócio (regras de negócio, processos, atores) | **Sim** | `docs/especificacao/02-analise-negocio.md` | Descreve o negócio do estacionamento, os processos de pátio e os atores (ADMIN/OPERADOR). Origem das regras RNxxx referenciadas nos casos de uso. |
| 3 | Diagrama Entidade-Relacionamento (DER) | **Sim** | `docs/especificacao/03-der.md` | DER aprovado, coerente com o `schema.sql`. Cobre Geografia, RH, Pagamento, Pessoas, Pátio, Conveniência, Fiscal e Financeiro. |
| 4 | Lista de Requisitos Funcionais | **Sim** | `docs/especificacao/04-requisitos-funcionais.md` | Enumera os RFs (CRUDs, operação de pátio, consultas e relatórios). Define a política de exclusão lógica (`ativo = false`) reutilizada na Seção 6. |
| 5 | Casos de Uso (especificação detalhada) | **Sim** | `docs/especificacao/05-casos-de-uso/` (7 arquivos: `cadastros-geografia-rh.md`, `cadastros-pagamento-pessoas.md`, `cadastros-conveniencia.md`, `estacionamento-patio.md`, `processos-fiscais.md`, `processos-financeiros.md`, `logistica.md`) | Seção mais extensa. Casos de uso com fluxo principal, alternativos e de exceção. A RN001 ("acesso somente a usuário autenticado") consta nos seis arquivos. Esta auditoria crítica dos casos de uso é a origem dos achados da Parte C. |
| 6 | Protótipos (telas / interface) | **Sim** | `docs/especificacao/06-prototipos.md` + `prototipos/*.png` | Atendida por **capturas reais** da interface web (SPA React/MUI) — login, dashboard, pátio, listagem, cadastro, relatórios e logística (Seção 6.1.3) — acrescidas de **descrições conceituais de tela** mapeadas para os **endpoints REST** (Swagger/OpenAPI). Cada campo de tela é aterrado em coluna real do `schema.sql`. |
| 7 | Dicionário de Dados | **Sim** | `docs/especificacao/07-dicionario-de-dados.md` | Descreve tabelas, colunas, tipos e restrições, em correspondência direta com o `schema.sql`. |
| 8 | Diagramas de Classe (UML) | **Sim** | `docs/especificacao/08-diagramas-classe.md` | Modelo de classes/entidades e enums, base para o mapeamento JPA previsto no roadmap (Parte C, P4). |
| 9 | Diagramas de Sequência | **Sim** | `docs/especificacao/09-diagramas-sequencia.md` | Fluxos dinâmicos, incluindo entrada/saída de pátio e cálculo de tarifa — base do roadmap de operação de pátio (Parte C, P4). |
| 10 | Requisitos Não Funcionais | **Sim** | `docs/especificacao/10-requisitos-nao-funcionais.md` | Desempenho, segurança, portabilidade e tecnologia (Spring Boot 4 / Java 25 / PostgreSQL 17). Ancoram os requisitos de autenticação detalhados na Parte C, P3. |

**Conclusão da Parte A.** A documentação atende integralmente o modelo acadêmico de dez seções. As duas únicas observações de método são: (i) a Seção 6 atende ao requisito de "Protótipos" por meio de descrição conceitual + contrato de API, e não por mockups gráficos — divergência deliberada e justificada na Parte B.1; e (ii) a Seção 5, por sua natureza de especificação detalhada, foi particionada em seis arquivos temáticos para legibilidade, sem prejuízo da completude. Nenhuma seção ficou ausente ou incompleta.

---

## PARTE B — Divergências Deliberadas (com justificativa)

As divergências a seguir são **escolhas conscientes de projeto**, não lacunas. Cada uma é declarada, justificada e rastreada ao artefato correspondente.

### B.1 Interface — nascida como API REST (Swagger); front-end web (React/MUI) implementado depois, com capturas reais

**Contexto.** A entrega inicial (v1) foi um **backend Spring Boot 4 (Java 25)** que expõe a funcionalidade como **API REST**, com a documentação interativa **Swagger / OpenAPI** (springdoc, em `/{contexto}/swagger-ui.html`) como primeira interface de exploração e teste. **Posteriormente foi implementada uma interface web** — *Single Page Application* em **React + TypeScript + Material UI** — que consome essa API. Assim, a Seção 6 é atendida **tanto com capturas reais de tela quanto com descrições conceituais**; a divergência clássica ("sistema sem telas") **não se aplica mais**.

**Como a seção foi atendida.** A Seção **6.1.3** de `06-prototipos.md` embute **capturas reais** da interface em execução (login, dashboard, pátio, listagem com coluna/filtro de ID, cadastro com validação, relatórios e um cadastro do módulo de logística), armazenadas em `docs/especificacao/prototipos/*.png`. Em seguida, cada "protótipo" traz uma **descrição conceitual de tela** (propósito, campos exibidos, ações disponíveis) **mapeada para os endpoints REST** que a sustentam (`MÉTODO /api/recurso`, com resumo do *payload* e dos códigos de resposta). Cada campo de tela é aterrado no esquema físico (`database/schema.sql`): corresponde a uma coluna real e suas validações refletem restrições `NOT NULL`, `UNIQUE` e `CHECK` reais.

**Justificativa.** Manter, além das capturas, o mapeamento **tela → endpoint → coluna** preserva o valor pedagógico do capítulo (rastreabilidade campo→coluna→endpoint e contrato de API), indo além do que o modelo pede com apenas mockups.

### B.2 Autenticação — modelada e referenciada, porém não implementada na v1

**Divergência.** A tabela `usuario` (com coluna `perfil` restrita a `ADMIN`/`OPERADOR` pela constraint `ck_usuario_perfil`) está **modelada** no `schema.sql` (linhas 708–725), e a regra **RN001 — "acesso somente a usuário autenticado"** consta em **todos os casos de uso** (presente nos seis arquivos de `05-casos-de-uso/`). Entretanto, **login e segurança não foram implementados na v1**: não há fluxo de autenticação, emissão/validação de token, nem verificação efetiva de perfil em tempo de execução. O campo `usuario.senha` é declarado como `VARCHAR(255)` para armazenar *hash*, mas o *hashing* e a verificação são responsabilidade da aplicação, ainda inexistente.

**Justificativa.** A modelagem de identidade/perfil e a presença transversal da RN001 garantem que a especificação já contempla o controle de acesso como requisito de primeira classe. A **efetivação** (Spring Security + hash BCrypt) é trabalho de **fase de código**, formalizado na Parte C como item **P3**. Separar a modelagem (v1) da implementação de segurança (fase de código) é uma decisão de faseamento, não uma omissão.

### B.3 Empresa fictícia

**Divergência.** Todo o documento assume uma organização fictícia: **Estacionamento Kaneko Ltda.**, sediada em **Maringá-PR**. Não corresponde a cliente ou operação real.

**Justificativa.** É um projeto **acadêmico**. A empresa fictícia fornece contexto realista e coeso (cidade, regime fiscal municipal de ISS, porte de operação) sem implicar dados ou requisitos de uma organização real, mantendo o documento autossuficiente para fins didáticos.

### B.4 Escopo — sem multifilial; documentos fiscais como registros internos (sem SEFAZ); logística sem CT-e

**Divergência.** O sistema é **monofilial** (não há entidade de filial nem segregação multifilial). As notas fiscais — **NF-e** (`nota_entrada`/`nota_saida`) e **NFS-e** (`nota_servico`, com ISS) — são modeladas como **documentos fiscais internos**: registram número, série, modelo, valores e situação, **sem integração de transmissão à SEFAZ** (não há geração/assinatura de XML, comunicação com *webservices* fiscais, nem retorno de protocolo). O **frete** é modelado em duas camadas: (i) na nota, pelo `tipo_frete` (CIF/FOB/SEM) e pelos valores rateáveis; e (ii) pelo **Módulo de Logística** (`transportadora`, `veiculo_frota` e o vínculo N:N `transportadora_veiculo_frota`), que registra a transportadora responsável e a sua frota, com o fornecedor apontando para a transportadora padrão (`fornecedor.transportadora_id`). Não há, contudo, emissão de **Conhecimento de Transporte Eletrônico (CT-e)** nem integração fiscal do transporte.

**Justificativa.** O objetivo acadêmico é exercitar **modelagem fiscal e financeira coerente** (rateio de frete/seguro/despesas, condições e parcelas de pagamento, contas a pagar/receber, ISS sobre serviço) e a **modelagem da cadeia de abastecimento** (fornecedor → transportadora → frota), e não a **integração regulatória** com a SEFAZ, que exige certificados digitais, ambientes de homologação e conformidade legal fora do escopo de tempo e do propósito didático. A ausência de multifilial e de CT-e reduz a superfície do sistema ao essencial do caso de estudo (um estacionamento com conveniência), preservando profundidade onde ela é pedagogicamente relevante.

---

## PARTE C — Plano de Refatoração (Priorizado)

Os itens a seguir derivam da **auditoria crítica dos casos de uso** (`05-casos-de-uso/`) confrontada com o esquema físico (`database/schema.sql`). A priorização segue o critério **risco de corrupção de dados → correção de regra de negócio → segurança → entrega incremental**: garante-se primeiro que o banco não aceite estados inválidos (P1), depois que a aplicação faça cumprir as regras que as FKs não cobrem (P2), em seguida o controle de acesso (P3) e, por fim, a sequência de implementação (P4). Cada item indica o achado, a evidência no schema/casos de uso, a ação proposta e a justificativa da prioridade.

### P1 — Integridade de dados (camada de banco)

**Prioridade máxima.** Defeitos de integridade no banco são os mais caros e perigosos: uma vez gravado um registro inconsistente, ele contamina relatórios financeiros e o fechamento de caixa, e a correção retroativa é trabalhosa. O banco é a última linha de defesa e deve rejeitar estados impossíveis independentemente de bugs na aplicação (princípio *fail-fast* na borda dos dados).

**P1.1 — CHECK de composição de `valor_total` em `conta_pagar` e `conta_receber`.**
*Achado.* Hoje **apenas** `contas_pagar_avulsa` possui uma restrição de composição de valor — `ck_contas_pagar_avulsa_total_min CHECK (valor_total >= valor_original - valor_desconto)` (schema, linha 754). Em `conta_pagar` (linhas 610–646) e `conta_receber` (linhas 655–694) há apenas restrições de não-negatividade (`ck_conta_pagar_valores`, `ck_conta_receber_valores`) e teto de pago/recebido (`ck_conta_pagar_pago_teto`, `ck_conta_receber_recebido_teto`), mas **nenhuma** que vincule `valor_total` à fórmula `valor_total = valor_original - valor_desconto + valor_juros + valor_multa`. Essa igualdade é, atualmente, **garantida apenas pela aplicação** — que ainda não existe.
*Ação.* Adicionar a `conta_pagar` e `conta_receber` uma constraint de composição que ancore `valor_total` em seus componentes. Avaliar entre a **igualdade exata** (`CHECK (valor_total = valor_original - valor_desconto + valor_juros + valor_multa)`) e, caso o arredondamento de parcelas torne a igualdade frágil, uma **cota inferior** no padrão já adotado para a avulsa (`CHECK (valor_total >= valor_original - valor_desconto)`). Recomenda-se a igualdade exata por ser a regra real do negócio; havendo divergência de centavos por rateio, documentar e migrar para a cota mínima.
*Justificativa da prioridade.* É a divergência mais grave: sem o CHECK, qualquer falha de cálculo na futura camada de serviço grava títulos financeiros com total incoerente, sem detecção. O custo da migração é baixo (duas constraints) e o ganho de garantia é alto.

**P1.2 — Índice único parcial para "tarifa vigente" única.**
*Achado.* A tabela `tarifa` (linhas 274–287) não possui qualquer índice que limite a quantidade de tarifas com `ativo = TRUE`. Podem coexistir **múltiplas tarifas ativas** simultaneamente; o desempate ("qual tarifa aplicar") fica delegado ao serviço, que escolhe a **mais recente**. Isso é ambíguo e dependente de implementação — dois operadores podem ver preços diferentes conforme a ordenação aplicada.
*Ação.* Avaliar um **índice único parcial** garantindo no máximo uma tarifa vigente — por exemplo `CREATE UNIQUE INDEX uq_tarifa_vigente ON tarifa ((TRUE)) WHERE ativo = TRUE` (uma única linha ativa global) ou, se o modelo evoluir para vigência por tipo de veículo, particionar o índice por essa dimensão. Confirmar a semântica desejada antes de aplicar (hoje o seed cria uma única tarifa ativa, então o índice é compatível com o estado atual).
*Justificativa da prioridade.* Tarifa é a base do cálculo de cobrança avulsa; ambiguidade aqui propaga-se a todo o faturamento de pátio. O índice torna a regra "uma tarifa vigente" uma garantia do banco em vez de uma convenção de serviço.

**P1.3 — Índice único parcial `WHERE ativo` em `produto_fornecedor`.**
*Achado.* `produto_fornecedor` (linhas 773–787) possui `uq_produto_fornecedor UNIQUE (produto_id, fornecedor_id)` **incondicional** (linha 784). Como o vínculo tem coluna `ativo` e a reativação de um vínculo previamente inativado é tratada **no serviço**, a unicidade incondicional impede o padrão usual de "inativar e recriar": uma tentativa de recriar o par após inativação colidiria com a linha antiga, forçando a aplicação a reativar em vez de inserir.
*Ação.* Avaliar substituir a unicidade incondicional por um **índice único parcial** `CREATE UNIQUE INDEX uq_produto_fornecedor_ativo ON produto_fornecedor (produto_id, fornecedor_id) WHERE ativo = TRUE`, permitindo manter histórico de vínculos inativos sem colisão e exigindo unicidade somente entre os ativos. Decidir conscientemente entre "reativar o existente" (mantendo a constraint atual) ou "permitir histórico" (migrando para o índice parcial); alinhar a escolha com o caso de uso de produto/fornecedor.
*Justificativa da prioridade.* Impacto menor que P1.1/P1.2 (afeta o módulo de conveniência, não o faturamento), por isso é o último item de P1, mas pertence à integridade porque define o que o banco aceita como duplicidade.

### P2 — Regras de negócio na camada de serviço (backend)

**Segunda prioridade.** As *foreign keys* garantem **existência** do registro referenciado, mas **não** garantem que ele esteja **ATIVO** nem a **coerência** entre campos correlatos. Esses são vazios que só a camada de serviço pode preencher e que a auditoria dos casos de uso expôs.

**P2.1 — Validar que registros referenciados estejam ATIVOS.**
*Achado.* As FKs de `cidade`, nacionalidade/`pais`, `condicao_pagamento`, `marca`, `unidade_medida`, `categoria` e `cliente` aceitam qualquer registro existente, **inclusive inativos** (`ativo = FALSE`). Nada no schema impede, por exemplo, vincular um produto a uma `marca` inativa ou emitir nota para `cliente` inativo.
*Ação.* Nos *services* de cadastro e dos processos (fiscal, financeiro, pátio), validar `ativo = TRUE` dos referenciados antes de persistir, retornando erro de regra de negócio (`422`) quando inativo. Centralizar a checagem para evitar repetição.
*Justificativa.* Mantém a coerência referencial **semântica** que as FKs não cobrem; previne registros novos pendurados em cadastros desativados.

**P2.2 — Validar `mensalista` com `status = ATIVO` na entrada de pátio.**
*Achado.* A FK `fk_mov_mensalista` exige que o `mensalista_id` exista, mas a `movimentacao` (linhas 315–343) não verifica o `status` do contrato. É possível abrir uma movimentação MENSALISTA apontando para um contrato `SUSPENSO` ou `CANCELADO`.
*Ação.* No serviço de entrada de pátio, exigir `mensalista.status = 'ATIVO'` para movimentações do tipo MENSALISTA; caso contrário, tratar como avulso ou recusar a entrada, conforme o fluxo do caso de uso de pátio.
*Justificativa.* Evita conceder o benefício de mensalidade a contratos não vigentes — perda de receita e inconsistência com o caso de uso.

**P2.3 — Validar coerência entre `mensalista_id` e `veiculo_id`.**
*Achado.* `movimentacao` referencia `veiculo_id` e `mensalista_id` independentemente. O CHECK `ck_mov_tipo_coerencia` garante apenas que MENSALISTA tem `mensalista_id` e AVULSO não, mas **não** garante que o `mensalista_id` informado seja o contrato **daquele** `veiculo_id`. Pode-se abrir uma movimentação cujo contrato pertence a outro veículo.
*Ação.* No serviço, validar que `mensalista.veiculo_id = movimentacao.veiculo_id` (e, por extensão, que o cliente do contrato é o esperado) antes de abrir a movimentação MENSALISTA.
*Justificativa.* Impede faturar/beneficiar o veículo errado; é coerência cruzada que o banco não expressa de forma simples.

**P2.4 — Preencher `tarifa_id` na saída avulsa.**
*Achado.* Em `movimentacao`, `tarifa_id` é **anulável** (linha 319) e a cobrança avulsa é calculada na aplicação. Sem preenchê-lo na saída, perde-se a rastreabilidade de **qual tarifa** gerou o `valor_cobrado`, dificultando auditoria e contestação.
*Ação.* No serviço de saída avulsa, gravar em `tarifa_id` a tarifa efetivamente aplicada no cálculo, junto com `valor_cobrado` e `data_saida`.
*Justificativa.* Garante trilha de auditoria do faturamento de pátio (liga preço cobrado à tarifa vigente no momento), suportando os relatórios da Seção 4.

### P3 — Autenticação e segurança

**Terceira prioridade.** O controle de acesso é requisito declarado (RN001 em todos os casos de uso) e está **modelado** (`usuario`/`perfil`), mas **não efetivado** (ver Parte B.2). Vem depois de P1/P2 porque, em ambiente acadêmico controlado, dados corrompidos por falta de validação são um risco mais imediato que acesso indevido; ainda assim é pré-requisito para qualquer exposição real da API.

*Ação.* Implementar **Spring Security**; armazenar a senha como **hash BCrypt** em `usuario.senha` (nunca em claro, nunca logada); implementar os perfis **ADMIN** e **OPERADOR** (coluna `usuario.perfil`, constraint `ck_usuario_perfil`); efetivar a **RN001**, exigindo sessão/token autenticado em todos os endpoints exceto o login, e restringir os módulos administrativos ao perfil ADMIN. O contrato de segurança já antecipado em `06-prototipos.md` (cabeçalho `Authorization: Bearer <token>`) deve ser cumprido.
*Justificativa da prioridade.* Transforma a RN001 de intenção documentada em garantia executável; é bloqueante para produção, mas não-bloqueante para a validação de modelagem/regras que P1 e P2 endereçam.

### P4 — Roadmap de implementação

**Última etapa — sequência de entrega.** Não é um defeito, e sim a ordem recomendada para materializar o sistema sobre as fundações já validadas (schema + casos de uso). Vem por último porque pressupõe que P1 (integridade do banco) e o desenho de P2/P3 já estejam decididos, evitando retrabalho de migração e de regras.

Ordem recomendada:

1. **Flyway V1** embrulhando o `schema.sql` **já validado** (migration inicial imutável; as constraints de P1 entram como `V1` se decididas antes do primeiro deploy, ou como `V2` incremental caso contrário).
2. **Entidades JPA + enums** conforme a **Seção 8** (`08-diagramas-classe.md`) — mapeando tabelas, FKs e os domínios `CHECK` (tipos, situações, perfis) como enums.
3. **Repositories** Spring Data JPA por agregado.
4. **Services** com as **regras de negócio (RNxxx)**, incorporando explicitamente os itens **P2** (validação de ATIVO, coerência mensalista/veículo, `tarifa_id` na saída) e a composição de valores validada por P1.
5. **Controllers REST + OpenAPI/Swagger** conforme a **Seção 6** (`06-prototipos.md`) — cada tela conceitual vira o endpoint contratado, respeitando os códigos de resposta ali definidos.
6. **Operação de pátio** — entrada/saída e **cálculo de tarifa** conforme os fluxos da **Seção 9** (`09-diagramas-sequencia.md`).
7. **Consultas e relatórios** conforme a **Seção 4** (`04-requisitos-funcionais.md`).
8. Em paralelo a partir do passo 5: **autenticação (P3)** como *cross-cutting concern* (filtro/segurança aplicado a todos os controllers).

*Justificativa da prioridade.* A ordem respeita as dependências técnicas (banco → entidades → repositórios → serviços → controllers) e garante que cada camada superior só seja construída sobre uma inferior já validada, em linha com a Regra de Ouro "Entenda → Planeje → Valide → Execute → Verifique".

---

## Síntese da Priorização

| Prioridade | Foco | Natureza | Bloqueante para |
|---|---|---|---|
| **P1** | Integridade no banco (CHECK de composição; tarifa vigente única; índice parcial de vínculo) | Correção de schema | Confiabilidade de todo dado financeiro/tarifa |
| **P2** | Regras na camada de serviço (ATIVO, status mensalista, coerência, `tarifa_id`) | Regra de negócio | Faturamento e cadastros corretos |
| **P3** | Autenticação (Spring Security, BCrypt, perfis, RN001) | Segurança | Exposição real da API |
| **P4** | Roadmap de implementação (Flyway → JPA → services → controllers → pátio → relatórios) | Entrega incremental | Materialização da v1 codificada |

**Rastreabilidade.** Todos os achados desta Parte C estão ancorados em `database/schema.sql` (constraints e índices citados por nome/linha) e nos casos de uso de `docs/especificacao/05-casos-de-uso/` (RN001 e fluxos de pátio/fiscal/financeiro). Nenhuma recomendação introduz dependência ou requisito fora do escopo declarado na Parte B.

---

## Atualização — Status de Implementação (2026-06-16)

Esta seção registra o estado do **backend efetivamente codificado** sobre as fundações validadas (schema + casos de uso), atualizando o que, na auditoria original, constava como "backend ainda não implementado".

**Visão geral.** Aplicação **Spring Boot 4 / Java 25 / PostgreSQL 17** completa, organizada por funcionalidade (package-by-feature), com **293 testes de integração** verdes cobrindo todos os fluxos de cada módulo (criar, listar, buscar, 404, atualizar, excluir, filtros, e os conflitos/validações 409/400/401) e os processos de pátio, fiscais, financeiros e de relatório. **As 31 tabelas do schema estão mapeadas.** O `schema.sql` foi mantido **congelado** como fonte da verdade — nenhuma alteração de DDL foi feita; o Flyway aplica-o como `V1` imutável e o Hibernate roda em `ddl-auto=validate`.

**Estado por item do plano (Parte C):**

| Item | Situação | Observação |
|---|---|---|
| **P4 — Roadmap de implementação** | **Concluído** | Flyway V1 → entidades JPA + enums → repositories → services com RNxxx → controllers REST + OpenAPI/Swagger → operação de pátio → módulos Conveniência, Fiscal e Financeiro. |
| **P3 — Autenticação e segurança** | **Implementado** | Spring Security + **JWT HS256** (HMAC manual, sem dependência externa) + **BCrypt** em `usuario.senha`; perfis ADMIN/OPERADOR; **RN001** efetivada (`/api/**` exige token, exceto `/api/auth`, Swagger e actuator). Configurável por `app.security.enabled`. Senha nunca é exposta nem logada. |
| **P2 — Regras de negócio no serviço** | **Implementado** | Existência de todo referenciado validada (404). O `ativo = TRUE` do referenciado é exigido nos processos (cliente na nota de saída, fornecedor na nota de entrada, veículo na entrada de pátio → 409). A entrada de pátio só considera `mensalista.status = ATIVO`, com coerência `mensalista`↔`veículo` (o contrato é localizado pelo próprio veículo) e gravação da `tarifa_id` aplicada. Os cálculos fiscais/financeiros (rateio, parcelas, baixa, estoque com validação de saldo) estão completos. |
| **P1 — Integridade no banco** | **Não aplicado (por decisão)** | O schema foi **congelado**; as constraints P1.1–P1.3 não foram adicionadas. Como **mitigação**, a camada de serviço calcula e assegura a composição de `valor_total` (`valor_original − desconto + juros + multa`) ao gerar/lançar títulos. As constraints permanecem recomendadas para uma futura migração `V2`, quando o schema for reaberto. |

**Requisitos de consulta (Seção 4) — implementados após auditoria interna (2026-06-16).** Uma auditoria do backend apontou que a camada de consulta estava incompleta; foi então fechada:
- **Filtros de listagem (4.1–4.25):** todas as 25 listagens de cadastro ganharam filtros opcionais (por nome/documento/placa/descrição/UF, por FK e por situação/perfil) via JPA **Specification** + um helper reutilizável `FiltroSpecs` (evita o `:param IS NULL` que, no PostgreSQL, falha por não inferir o tipo do parâmetro).
- **Relatórios gerenciais (4.26–4.30):** implementados em `/api/relatorios` — pátio atual (permanência + totalizador por tipo), faturamento do dia/período (estacionamento, vendas, serviços e recebimentos), contas a vencer (a pagar/receber em aberto, vencidos destacados), estoque mínimo e mensalistas ativos (receita recorrente).
- **Integração pátio→financeiro:** `POST /api/mensalistas/{id}/mensalidade` gera o título a receber do contrato (req. 4.30).

**Decisões de implementação relevantes (além do plano):**

- **Observabilidade** — `X-Trace-Id` de correlação por requisição (gerado/propagado, exposto no header e no MDC dos logs); erros centralizados em **ProblemDetail (RFC 7807)** com o `traceId`; handler genérico para 500 (loga sem vazar *stacktrace*); health check via actuator.
- **Geração de títulos** — a confirmação de nota de **entrada** soma estoque + atualiza custo e gera `conta_pagar`; a de **saída** valida e baixa estoque e gera `conta_receber`; ambas distribuem o total nas parcelas da condição de pagamento (a última parcela fecha o total, evitando resíduo de arredondamento). A `nota_servico` calcula o **ISS** e não gera título (serviço pago no ato).
- **Configuração sem segredos no código** — banco, porta, segredo/validade do JWT e o liga/desliga da segurança são parametrizados por variáveis de ambiente, com padrões de desenvolvimento.

**Coerência documentação↔código (2026-06-17) — correções de divergências e declaração de pendências.** Uma revisão de coerência confrontou a documentação (RNF, casos de uso) com a implementação. Os itens abaixo foram **corrigidos no código** para alinhar com o que a especificação promete; os demais ficam **honestamente declarados** como parciais ou futuros — em vez de afirmar conformidade que o código não tem.

*Divergências corrigidas (documentação ↔ código agora coerentes):*
- **Exclusão lógica (RNF-AUD-04):** a exclusão de cadastros agora é **lógica** — `excluir()` faz `ativo = false` e a linha é preservada, em vez de `DELETE` físico. Aplica-se aos 18 cadastros que possuem o flag `ativo` (geografia, pessoas, RH, fornecedor, pagamento, veículo/tarifa, conveniência e usuário). Verificável em teste: após o `DELETE`, o `GET` retorna o registro com `ativo = false`.
- **RN001 — usuário ativo:** o filtro JWT passou a exigir usuário **existente e `ativo = true`** a cada requisição (um token de usuário inativado/removido deixa de valer mesmo dentro da validade), não apenas a presença de um token válido.
- **Cancelar Movimentação (RF 4.18.3 / UC350):** implementado `POST /api/movimentacoes/{id}/cancelamento` (apenas tickets `ABERTO`, para entradas registradas por engano), ativando o estado `CANCELADO` que antes existia no enum mas nunca era atribuído.
- **Cliente inativo em contrato de mensalista (UC303/E6):** o vínculo de um cliente inativo a um contrato passa a ser bloqueado (409).
- **Contagem de tabelas:** o texto do RNF (Seção 10.7) dizia "vinte e cinco tabelas"; corrigido para **31**, alinhado ao `schema.sql` e ao restante da documentação.

*Pendências declaradas (previstas, não implementadas nesta versão):*
- **Trilha de autor por operação (RNF-AUD-05):** a autorização por perfil identifica o responsável na sessão autenticada, mas a **persistência do autor** de cada operação (`created_by` / log de evento) **não foi implementada** — exigiria coluna nova, e o `schema.sql` está **congelado**. Recomendado para a futura `V2`.
- **Juros/multa da condição de pagamento:** os percentuais (`percentual_juros` / `percentual_multa`) são cadastrados, mas **ainda não são aplicados** automaticamente na geração/baixa de títulos (lança-se o valor da parcela). O cálculo automático por atraso fica para evolução.
- **Exclusão de contratos e documentos transacionais:** mensalistas, notas fiscais e contas avulsas mantêm exclusão **física** — decisão intencional (notas só podem ser excluídas enquanto `PENDENTE`/rascunho; a inativação lógica aplica-se aos **cadastros**, que carregam o flag `ativo`).

A instrução de execução (pré-requisitos, variáveis, Swagger, login e testes) está no [`README.md`](../../README.md) na raiz do projeto.

---

## Atualização — Evolução do Schema e Módulo de Logística (2026-07-03)

Após o congelamento inicial, o schema foi **reaberto** para evoluções incrementais versionadas por Flyway (migrações `V2` a `V6`), acompanhadas em toda a documentação. As mudanças com impacto neste relatório e no restante da especificação são:

- **V4 — Remoção de `nacionalidade_id`.** O campo `nacionalidade_id` (FK → `pais`) foi **removido** de `funcionario`, `fornecedor` e `cliente`, por ser redundante: o país já é alcançável pela cadeia **Cidade → Estado → País**. O DER (Seção 3) e o Dicionário (Seção 7) foram atualizados; a contagem de tabelas permanece, pois a remoção foi de colunas, não de entidades.
- **V5 — Custo do produto e lucro derivado.** O `produto` passou a distinguir o **preço de compra** (`valor_compra`, bruto) do **preço de custo** (`custo`, com rateio de frete/seguro), ambos mantidos pela Nota de Entrada e somente leitura no cadastro. O `percentual_lucro` tornou-se **derivado** — `(venda − custo) / custo` — podendo ser **negativo** (prejuízo), razão pela qual saiu do `CHECK ck_produto_valores`. O estoque (`quantidade`) permanece movimentado apenas pelas notas de entrada/saída.
- **V6 — Módulo de Logística.** Foram acrescentadas três entidades — `transportadora`, `veiculo_frota` e o vínculo N:N `transportadora_veiculo_frota` — além da FK `fornecedor.transportadora_id`, elevando o total para **34 tabelas**. A especificação ganhou o arquivo de casos de uso `05-casos-de-uso/logistica.md` (bloco UC601–UC603), os requisitos funcionais 4.31–4.33, e as respectivas entradas no DER, no Dicionário e nos Diagramas de Classe. O `veiculo_frota` (veículo de carga) é **distinto** do `veiculo` do pátio (carro do cliente).

Cobertura de testes automatizados atualizada para **318 testes de integração** verdes (Spring Boot 4 / Java 25 / PostgreSQL 17), incluindo os cadastros do módulo de logística.

---

*Fim do Relatório de Conformidade — Estacionamento Kaneko (v1).*

---

## Atualização — Arquitetura de Produto Enterprise (2026-07-29)

Foi adicionada uma camada documental evolutiva, sem alteração de código, migrations, APIs ou comportamento em produção. Os documentos `11` a `20` definem a arquitetura-alvo para os próximos ciclos do produto, com foco inicial em eventos e preservação integral dos módulos existentes.

### Artefatos adicionados

- `11-visao-produto-enterprise.md` — visão, implantação e princípios;
- `12-dominio-ddd.md` — linguagem ubíqua, 21 bounded contexts e context map;
- `13-event-storming.md` — comandos, eventos, policies e process managers;
- `14-modelagem-dominio.md` — catálogo AS-IS/EVOLUÇÃO/TARGET de entidades e value objects;
- `15-maquinas-de-estados.md` — transições e efeitos;
- `16-casos-de-uso-plataforma.md` — UC701 a UC730;
- `17-apis-enterprise.md` — APIs `/api/v1`, payloads, erros, idempotência e eventos públicos;
- `18-permissoes-rbac.md` — RBAC/ABAC, alçadas e segregação;
- `19-equipamentos-integracoes.md` — abstrações de hardware, Edge e ACLs;
- `20-roadmap-produto.md` — fundação, fases 1 a 10, NFRs e plano de evolução.

### Regra de conformidade

Os documentos originais continuam descrevendo o sistema implementado. Os documentos enterprise são normativos para evolução, mas suas entidades e capacidades marcadas `TARGET` não são consideradas implementadas. Toda futura mudança de código deverá atualizar tanto a documentação AS-IS afetada quanto a decisão TARGET correspondente.

### Divergências arquiteturais registradas

1. O projeto atual não possui organização/tenant; a arquitetura-alvo o torna obrigatório.
2. O pacote `patio` atual reúne responsabilidades que serão separadas por contexto.
3. Pátio opcional e ocupação por simples contagem não atendem inventário comercial.
4. O módulo atual de pagamento é cadastral, não um ledger transacional de gateway.
5. Não existem eventos de domínio, outbox, inbox ou idempotência na implementação atual.
6. `ADMIN/OPERADOR` não atende às alçadas enterprise.
7. Não existem eventos, pedidos, reservas, credenciais, check-in ou equipamentos no código atual.

Nenhuma dessas divergências foi corrigida nesta atualização documental; elas foram explicitadas e ordenadas no roadmap.

---

## Atualização — História 1: Organização/Tenant (2026-07-29)

A primeira entrega da fundação enterprise passou a estar implementada no código, sem antecipar Membership, RBAC contextual, auditoria, outbox, instalações ou eventos.

- Migration `V8__organizacao_tenant.sql` cria `organizacao`, com documento normalizado, constraints, índice único funcional e versionamento otimista.
- O agregado `Organizacao` possui os estados `PROVISIONANDO`, `ATIVA`, `SUSPENSA` e `ENCERRADA`; o provisionamento conclui em `ATIVA` dentro do mesmo caso de uso e `ENCERRADA` é terminal.
- Moeda usa código ISO 4217; fuso usa identificador IANA; região representa uma subdivisão ISO 3166-2.
- Foram publicados exclusivamente `POST /api/v1/organizations`, `GET /api/v1/organizations/{id}` e `PATCH /api/v1/organizations/{id}`.
- `ETag` deriva somente de `@Version`; PATCH exige `If-Match`, com `428` quando ausente e `412` quando divergente.
- Documento, ID, versão e status não são alteráveis pelo PATCH.
- Até a História 3, os endpoints são restritos ao perfil legado `ADMIN`.
- Nenhum dado legado foi associado automaticamente e nenhum `organizacao_id` foi antecipado em pátio ou usuário.
- A suíte automatizada passou de 318 para 332 testes, sem regressão nos testes preexistentes.

---

## Atualização — História 2: Membership e contexto organizacional (2026-07-29)

O vínculo entre a identidade global e o tenant passou a estar implementado sem
alterar `Colaborador`, `Funcionario`, `Patio`, `Movimentacao` ou o frontend Expo.

- Migration `V9__membership_organizacao_usuario.sql` cria `membro_organizacao`,
  com FKs restritivas, checks de estado e datas, versão otimista e índice único
  parcial que impede dois vínculos correntes e preserva o histórico encerrado.
- `MembroOrganizacao` nasce em `ATIVO`, permite `ATIVO ↔ SUSPENSO` e termina em
  `ENCERRADO`; cada mudança incrementa a versão JPA.
- A administração temporária por `ROLE_ADMIN` expõe POST, GET e PATCH de
  Membership; PATCH exige `If-Match` e usa `ETag` derivado exclusivamente de `@Version`.
- `GET /api/v1/me`, `GET /api/v1/me/organizations` e
  `POST /api/v1/me/active-organization` implementam descoberta e seleção de tenant.
- A seleção recebe somente `organizationId`; Membership, estado, perfil e versão
  são resolvidos no servidor. A listagem usa projeção direta e retorna apenas
  Membership e organização ativas.
- O login preserva compatibilidade, enquanto os novos tokens incluem claims
  padronizadas. O token contextual contém `org_id`, `membership_id` e
  `membership_version`; vínculo inválido ou desatualizado resulta em `401`.
- O perfil atual do `Usuario` no banco define a authority, inclusive para tokens
  legados; não é possível trocar tenant por header.
- A suíte automatizada passou de 332 para 348 testes, preservando os anteriores.

---

## Atualização — História 3: RBAC contextual (2026-07-29)

- Migrations `V10__rbac_contextual_organizacao.sql` e
  `V11__vigencia_atribuicao_acesso.sql` criam permissões, papéis, relação
  papel–permissão e atribuições versionadas com vigência.
- O primeiro membro recebe `ADMIN_ORGANIZACAO`; o administrador de plataforma
  deixa de administrar indiscriminadamente Memberships do tenant.
- Permissões são resolvidas no banco a cada requisição contextual e não são
  confiadas ao JWT, tornando concessões e revogações imediatamente efetivas.
- Organização e Membership passaram a exigir escopo e permissões contextuais.
- O último administrador ativo é protegido contra revogação, suspensão e encerramento.
- O escopo implementado é somente `ORGANIZACAO`; evento e pátio permanecem em suas histórias.
- A suíte passou de 348 para 357 testes sem regressões.

---

## Atualização — História 4: Auditoria (2026-07-29)

- A migration `V12__registro_auditoria_append_only.sql` cria a trilha com índices,
  snapshots JSONB, versões, contexto operacional e trigger que recusa UPDATE/DELETE.
- Organização, Membership e RBAC registram mutações na mesma transação do caso de uso;
  falha de auditoria provoca rollback integral.
- O contexto captura ator humano ou sistema, organização, origem, dispositivo, IP e
  `traceId`; os estados são allowlists redigidas, sem segredo, token ou documento.
- Registros recebem classe de retenção. Prazos e expurgo não foram inventados e
  permanecem dependentes de definição legal; nenhuma API pública foi antecipada.
- A suíte passou de 357 para 362 testes sem regressões.

---

## Atualização — História 5: Instalações/Pátio (2026-07-29)

- A migration `V13__instalacoes_patio_tenant.sql` evolui aditivamente a tabela
  `patio`: dados legados permanecem sem tenant, recebem capacidade operacional
  igual à física e não são atribuídos automaticamente a organizações.
- Novos pátios pertencem obrigatoriamente à organização do JWT contextual. O
  corpo não aceita `organizationId`, impedindo troca de tenant pelo cliente.
- Capacidade física positiva e `0 ≤ capacidade operacional ≤ capacidade física`
  são defendidas no domínio e no PostgreSQL; nome normalizado é único por tenant.
- `POST /api/v1/parking-facilities` exige `facilities:manage`, retorna `Location`
  e `ETag`, e registra `PATIO_CRIADO` na auditoria transacional.
- As APIs legadas `/api/patios` continuam operantes e enxergam exclusivamente
  registros legados sem organização; Venue, Setor e alteração de capacidade não
  foram antecipados.
- A suíte passou de 362 para 368 testes sem regressões.

---

## Atualização — História 6: Venue (2026-07-29)

- A migration `V14__venue_unidade_operacional.sql` cria `unidade_operacional`
  com ownership obrigatório, endereço, cidade, fuso, versão e unicidade de nome
  normalizado por tenant.
- `POST /api/v1/venues` deriva a organização exclusivamente do JWT contextual,
  exige `organizations:admin` e retorna `Location` e `ETag`.
- Venue nasce ativo e com tipo server-side `VENUE`; tipos adicionais e contatos
  não foram inventados porque a baseline ainda não fixa seus contratos.
- A aplicação valida organização/cidade ativas e fuso IANA; o PostgreSQL protege
  FKs, tipo aprovado, versão e unicidade.
- A criação registra `UNIDADE_CRIADA` com recurso lógico `VENUE` na mesma transação.
- A suíte passou de 368 para 373 testes sem regressões.

---

## Atualização — História 7: Evento (2026-07-30)

- A migration `V15__evento_idempotencia_outbox.sql` cria o agregado `evento`,
  o registro genérico de idempotência e a outbox PostgreSQL, além dos papéis
  `GESTOR`/`ORGANIZADOR` e da permissão `events:create`.
- `POST /api/v1/events` exige tenant contextual e `Idempotency-Key`, cria o
  evento em `RASCUNHO`, retorna checklist, `Location` e `ETag`.
- A relação evento–venue é protegida por FK composta no banco, impedindo
  associação entre organizações mesmo fora da aplicação.
- O lock transacional por chave serializa replays concorrentes; payload
  equivalente devolve o recurso original e payload divergente retorna `409`.
- Auditoria, `EventoCriado`, outbox e resultado idempotente são persistidos na
  mesma transação do agregado. O dispatcher da outbox permanece fora do escopo.
- Somente a criação foi exposta; configuração, alocação e publicação não foram
  antecipadas.
- A suíte passou de 373 para 383 testes sem regressões.

---

## Atualização — História 8: Alocação Evento–Pátio (2026-07-30)

- A migration `V16__alocacao_patio_evento.sql` cria a associação tenant-aware
  entre evento e pátio, constraints de período/capacidade e a permissão
  `inventory:manage` para `ADMIN_ORGANIZACAO` e `GESTOR`.
- `POST /api/v1/events/{id}/parking-allocations` é idempotente, aceita somente
  evento configurável e pátio ativo do tenant, exige interseção temporal e
  retorna `Location` e `ETag`.
- A soma das capacidades operacionais de alocações simultâneas é serializada
  por lock pessimista no pátio e não pode exceder a capacidade operacional
  física configurada para ele.
- O PostgreSQL também impede relações entre tenants, duplicidade evento–pátio,
  período invertido e hierarquia de capacidades inválida.
- `PATCH /api/v1/parking-allocations/{id}` altera exclusivamente as três
  capacidades e exige `If-Match`; versão divergente retorna `412`.
- Criação e alteração são auditadas; a criação persiste
  `CapacidadeAlocadaAoEvento` na outbox da mesma transação.
- Produtos, consumo de inventário, quotas, override e publicação não foram
  antecipados.
- A suíte passou de 383 para 393 testes sem regressões.

---

## Atualização — Produto de estacionamento / UC707 (2026-07-30)

- “Oferta” permanece o conceito funcional materializado por
  `ProdutoEstacionamento`; não foi criada entidade paralela ausente do DDD.
- A migration `V17__produto_estacionamento.sql` cria produto, benefícios e
  restrições normalizados, FK composta para alocação/evento/tenant, constraints,
  índices e a permissão `pricing:manage`.
- `POST /api/v1/events/{id}/parking-products` cria produto idempotente em
  `RASCUNHO`, com `Location` e `ETag`, sem antecipar publicação.
- A alocação é bloqueada pessimisticamente enquanto a soma de quotas é validada,
  impedindo que produtos concorrentes comprometam capacidade vendável superior
  à disponível.
- Categoria, direito, janela, quota e códigos de benefícios/restrições são
  campos explícitos; regras não foram escondidas em JSON genérico.
- A criação é auditada. Não há evento de outbox nessa transição, pois o evento
  aprovado é `ProdutoEstacionamentoPublicado`, reservado ao comando de publicação.
- Compatibilidade das categorias com setores físicos permanece pendente porque
  setor/categoria física ainda não está implementado na primeira entrega.
- A suíte passou de 393 para 406 testes sem regressões.

---

## Atualização — Lote de preço / UC708 (2026-07-30)

- A migration `V18__lote_preco.sql` cria `lote_preco` com FK composta
  produto/tenant, índices, unicidade de nome normalizado, checks monetários e
  trigger contra sobreposição ambígua na mesma prioridade.
- `POST /api/v1/parking-products/{id}/price-tiers` cria o lote de forma
  idempotente, tenant-aware e protegida por `pricing:manage`, retornando
  `Location` e `ETag`.
- O produto é bloqueado pessimisticamente durante a validação, serializando
  criações concorrentes; a aplicação e o PostgreSQL rejeitam períodos
  conflitantes com a mesma prioridade.
- A janela comercial representa ativação imediata ou agendada, sem introduzir
  uma máquina de estados de lote ausente da baseline.
- Valor zero suporta cortesia controlada; valores negativos, moeda divergente
  da organização e quantidade superior à quota do produto são rejeitados.
- A criação é auditada; snapshot de preço em reserva/pedido permanece para as
  histórias comerciais posteriores.
- A suíte passou de 406 para 416 testes sem regressões.

---

## Atualização — Publicação de produto (2026-07-30)

- `POST /api/v1/parking-products/{id}/publication` exige confirmação explícita
  e `If-Match`, retornando o produto publicado e seu novo `ETag`.
- A transição `RASCUNHO → PUBLICADO` somente ocorre quando existe ao menos um
  lote de preço; produto já publicado ou sem preço é rejeitado.
- O lock pessimista e a comparação com a versão JPA impedem publicação
  concorrente ou replay com versão obsoleta; ausência de versão retorna `428`
  e divergência retorna `412`.
- Auditoria e `ProdutoEstacionamentoPublicado` na outbox são persistidos na
  mesma transação do agregado.
- A suíte passou de 416 para 419 testes sem regressões.

---

## Atualização — Publicação de evento / UC702 (2026-07-30)

- A migration `V19__publicacao_evento.sql` adiciona `events:publish` somente a
  `ADMIN_ORGANIZACAO` e `GESTOR`; `ORGANIZADOR` permanece sem aprovação.
- `POST /api/v1/events/{id}/publication` exige confirmação e `If-Match`, com
  respostas `428` para precondição ausente e `412` para versão divergente.
- O evento é bloqueado pessimisticamente e somente publica com alocação de
  capacidade vendável positiva e ao menos um produto publicado no mesmo tenant.
- O comando materializa `RASCUNHO → CONFIGURADO → PUBLICADO`, incrementando a
  versão em cada transição e persistindo `EventoConfigurado` e `EventoPublicado`
  na outbox junto da auditoria final.
- Na primeira entrega, o publicador autenticado assume a responsabilidade e a
  política é fechada: publicação não abre vendas nem habilita acesso. Contratos
  explícitos continuam necessários antes dessas fases.
- A suíte passa de 419 para 423 testes sem regressões.

---

## Atualização — Disponibilidade da primeira entrega (2026-07-30)

- `GET /api/v1/events/{id}/availability` projeta somente produtos publicados de
  evento publicado no tenant autenticado, com filtro opcional por categoria.
- A leitura usa projeção direta dos produtos e uma consulta em lote dos preços,
  evitando carregar coleções de benefícios/restrições e consultas N+1.
- Entre lotes simultaneamente vigentes, a maior prioridade numérica vence e o
  ID desempata deterministicamente; lotes de mesma prioridade sobrepostos já
  são impedidos pela V18.
- O saldo é `min(quota do produto, quantidade do lote vigente)` enquanto ainda
  não existem reservas/vendas. `guaranteesHold=false` explicita que a consulta
  não protege a última vaga.
- Canal, hold e redução por reservado/vendido permanecem para UC709; nenhum
  estado ou migration foi antecipado.
- A suíte passa de 423 para 425 testes sem regressões.

---

## Atualização — Abertura imediata de vendas (2026-07-30)

- `POST /api/v1/events/{id}/sales-opening` exige `events:publish`, `If-Match` e
  evento `PUBLICADO`, retornando a nova representação e `ETag`.
- O comando executa somente `PUBLICADO → VENDAS_ABERTAS`, sob lock pessimista e
  comparação da versão JPA; replay com versão anterior retorna `412`.
- Auditoria `VENDAS_EVENTO_ABERTAS` e outbox `VendasDoEventoAbertas` são
  persistidas na mesma transação do agregado.
- `startsAt` futuro retorna `409` até existir scheduler confiável; a API não
  aceita agendamento que não possa executar.
- A consulta de disponibilidade permanece válida em `PUBLICADO` e
  `VENDAS_ABERTAS`; o hold comercial exige o segundo estado.
- Nenhuma migration ou permissão nova foi antecipada; a transição reutiliza
  `events:publish` de Gestor/Admin da organização.
- A suíte passa de 425 para 426 testes sem regressões.

---

## Atualização — Reserva de inventário / UC709 (2026-07-30)

- A migration `V20__reserva_inventario.sql` cria `reserva_inventario`, adiciona
  contadores reservado/vendido/bloqueado ao produto e protege no PostgreSQL a
  invariante `reservado + vendido + bloqueado <= quota`.
- `POST /api/v1/inventory-holds` exige `inventory:hold`, tenant ativo e chave
  idempotente; somente evento `VENDAS_ABERTAS`, produto publicado e lote vigente
  podem reservar.
- O produto é bloqueado pessimisticamente antes de calcular o saldo. Um teste
  com duas transações reais pela última vaga prova uma única reserva vencedora.
- O hold congela lote, preço e moeda, nasce `MANTIDA` e usa prazo padrão
  provisório/configurável de 15 minutos.
- `GET /api/v1/inventory-holds/{id}` e
  `POST /api/v1/inventory-holds/{id}/release` respeitam tenant e proprietário;
  liberação e expiração devolvem saldo exatamente uma vez.
- Um job interno processa vencimentos em lotes; consulta e nova reserva também
  materializam expirações relevantes usando a ordem de locks produto → reserva.
- Criação, expiração e liberação são auditadas. `InventarioReservado`,
  `ReservaExpirada` e `InventarioLiberado` entram na outbox transacional.
- A disponibilidade passa a subtrair os contadores comprometidos, mas continua
  com `guaranteesHold=false`.
- Confirmação como vendido, pedido, pagamento e identidade pública de cliente,
  caixa ou integração não foram antecipados.
- A suíte passa de 426 para 436 testes sem regressões.

---

## Atualização — Pedido em espera / UC710 parcial (2026-07-30)

- A migration `V21__pedido_estacionamento.sql` cria `pedido`,
  `comprador_pedido` e `item_pedido` com FKs tenant/ator/evento, unicidade por
  hold e número, snapshots imutáveis e constraints de estado e valores.
- `POST /api/v1/orders` exige `orders:create`, tenant ativo,
  `Idempotency-Key` e hold `MANTIDA` pertencente ao ator. Canal divergente,
  reserva vencida/liberada ou reutilizada são rejeitados.
- A criação percorre `CRIADO → AGUARDANDO_PAGAMENTO` atomicamente, congela os
  dados comerciais e deriva subtotal e total sem aceitar campos controlados.
- `GET /api/v1/orders/{id}` aplica tenant e ownership; documento do comprador é
  devolvido mascarado.
- `PedidoCriado`, `PedidoAguardandoPagamento` e `PedidoExpirado` entram na
  outbox; criação e expiração também são auditadas.
- Um job interno expira pedidos aguardando pagamento no prazo do hold. O hold
  continua reservado; sua própria expiração é a autoridade que devolve saldo.
- Pagamento, confirmação do inventário, credencial e check-in não foram
  antecipados.
- A suíte passa de 436 para 444 testes sem regressões.

---

## Atualização — Confirmação manual conciliável (2026-07-30)

- A migration `V22__confirmacao_manual_pedido.sql` cria a evidência tenant-aware
  `confirmacao_manual_pedido`, única por pedido, com ator e estado inicial
  `PENDENTE_CONCILIACAO`.
- `POST /api/v1/orders/{id}/manual-confirmation` exige
  `orders:manual-confirm`, `If-Match`, `Idempotency-Key`, meio, referência de
  evidência e motivo. Somente Admin e Gestor recebem a permissão.
- Pedido, produto e hold são bloqueados em ordem estável. A confirmação converte
  exatamente uma vez o contador reservado em vendido e avança pedido e hold
  para `CONFIRMADO`/`CONFIRMADA` na mesma transação.
- Replays idênticos retornam o resultado persistido; payload divergente retorna
  conflito e versão divergente retorna `412`.
- `InventarioConfirmado` e `PedidoConfirmado` entram na outbox. Não há
  `PagamentoAprovado`, pois a evidência manual ainda requer conciliação e não
  simula resposta de gateway.
- Testes cobrem domínio, API, RBAC, precondição, idempotência, expiração,
  auditoria/outbox e duas transações concorrentes sobre o mesmo pedido.
- A suíte passa de 444 para 450 testes sem regressões.

---

## Atualização — Emissão de credencial / UC711 (2026-07-30)

- As migrations `V23__credencial_acesso.sql` e
  `V24__credencial_escopo_integridade.sql` adicionam o snapshot de pátio ao item
  do pedido e criam `credencial_acesso` com FKs tenant-aware, validade, estado,
  versão e unicidade por unidade comprada. Evento, pátio, pedido e item são
  coerentes por constraints compostas.
- `POST /api/v1/orders/{id}/credentials` exige `credentials:issue`, tenant,
  ownership e `Idempotency-Key`; Admin, Gestor e Organizador recebem a permissão.
- Cada chamada emite a próxima unidade como `ATIVA`. O lock pessimista do pedido
  e a constraint `(item_pedido_id, numero_unidade)` impedem emissão excedente,
  inclusive em duas transações concorrentes.
- A última unidade transiciona o pedido para `ATENDIDO`. `CredencialEmitida` e
  `PedidoAtendido` entram na outbox e as mutações são auditadas.
- `GET /api/v1/credentials/{id}` aplica tenant e proprietário. Pedido ou
  credencial de outro ator é indistinguível de recurso inexistente.
- O código `CRD-...` é identificador público não autorizador e permanece
  separado da representação segura implementada posteriormente em UC712.
- Placa é associada automaticamente somente a pedido unitário; pedidos com mais
  unidades evitam vínculo ambíguo e aguardam associação individual futura.
- A suíte passa de 450 para 456 testes sem regressões.

---

## Atualização — Representação QR / UC712 (2026-07-30)

- As migrations `V25__representacao_qr.sql` e
  `V26__representacao_qr_hash_varchar.sql` versionam QR na credencial e criam
  `representacao_qr` com FK tenant-aware, hash único, validade e unicidade
  parcial da representação ainda não revogada.
- `POST /api/v1/credentials/{id}/qr-code` exige `credentials:issue`, tenant,
  ownership e `If-Match`; ausência retorna `428` e versão divergente retorna
  `412`.
- Somente credencial `ATIVA`, com meio preferido `QR` e validade futura aceita
  geração. Um lock pessimista e o `ETag` da credencial serializam concorrência.
- O token usa 32 bytes de `SecureRandom`, formato Base64 URL-safe e não contém
  dados pessoais. Ele é devolvido somente no `201`; PostgreSQL persiste apenas
  seu SHA-256 e consultas da credencial não expõem o segredo.
- Auditoria e `QRCodeGerado` carregam apenas identificadores, versão e validade,
  nunca o token ou seu hash.
- Testes cobrem domínio, API, RBAC, ownership, precondição, segredo não
  persistido, constraints, OpenAPI e duas gerações concorrentes.
- A suíte passa de 456 para 462 testes sem regressões.
