> Links: [[01-introducao]] · [[03-der]] · [[11-visao-produto-enterprise]] · [[12-dominio-ddd]] · [[20-roadmap-produto]] · [[21-status-implementacao]] · [[relatorio-conformidade]]

# Especificação de Requisitos — Sistema Estacionamento Kaneko

Este diretório reúne a especificação de requisitos do **Sistema de Controle do Estacionamento Kaneko**, elaborada segundo o modelo de prática profissional de Sistemas de Informação (Documento de Visão e Especificação de Requisitos orientada a Casos de Uso). Trata-se de um **projeto acadêmico**, proposto para uma **empresa fictícia** — o Estacionamento Kaneko —, cujo objetivo é registrar de forma completa e não ambígua os requisitos do produto de software, servindo como referência única e formal para a equipe de desenvolvimento, para os responsáveis pela operação do estabelecimento e para os avaliadores acadêmicos. Toda a delimitação de escopo está fundamentada no modelo físico de dados materializado em `database/schema.sql` (PostgreSQL 17, banco `estacionamento_kaneko`, 34 tabelas).

---

## Índice

| # | Documento | Descrição |
|---|-----------|-----------|
| 01 | [Introdução](01-introducao.md) | Finalidade do documento, delimitação de escopo (dentro e fora da v1), definições e descrição da empresa fictícia. |
| 02 | [Análise de Negócio](02-analise-negocio.md) | Contexto do negócio, processos atuais, regras gerais e necessidades que justificam o sistema. |
| 03 | [Diagrama Entidade-Relacionamento (DER)](03-der.md) | Modelo lógico de dados com as 34 entidades persistentes, atributos e relacionamentos, em Mermaid `erDiagram`. |
| 04 | [Requisitos Funcionais](04-requisitos-funcionais.md) | Catálogo dos requisitos funcionais (RF) e sua rastreabilidade para os casos de uso. |
| 05 | [Casos de Uso](05-casos-de-uso/) | Especificação detalhada dos casos de uso, organizada em seis arquivos por bloco funcional (ver subitens abaixo). |
| 05.1 | [Cadastros — Geografia e RH](05-casos-de-uso/cadastros-geografia-rh.md) | Casos de uso de país, estado, cidade, cargo, funcionário e usuário (bloco UC0xx). |
| 05.2 | [Cadastros — Pagamento e Pessoas](05-casos-de-uso/cadastros-pagamento-pessoas.md) | Casos de uso de formas e condições de pagamento, fornecedores e clientes (bloco UC1xx). |
| 05.3 | [Cadastros — Conveniência](05-casos-de-uso/cadastros-conveniencia.md) | Casos de uso de produtos, categorias e controle de estoque da conveniência (bloco UC2xx). |
| 05.4 | [Estacionamento — Pátio](05-casos-de-uso/estacionamento-patio.md) | Casos de uso de entrada/saída de veículos, mensalistas e avulsos (hora/diária) (bloco UC3xx). |
| 05.5 | [Processos Fiscais](05-casos-de-uso/processos-fiscais.md) | Casos de uso de emissão e controle de documentos fiscais (bloco UC4xx). |
| 05.6 | [Processos Financeiros](05-casos-de-uso/processos-financeiros.md) | Casos de uso do contas a pagar e a receber (bloco UC5xx). |
| 05.7 | [Módulo de Logística](05-casos-de-uso/logistica.md) | Casos de uso de transportadora, veículo de frota e frota (Transportadora × Veículo) do abastecimento da conveniência (bloco UC6xx). |
| 06 | [Protótipos](06-prototipos.md) | Esboços de interface e fluxos de tela que ilustram a interação com o sistema. |
| 07 | [Dicionário de Dados](07-dicionario-de-dados.md) | Descrição tabela a tabela do modelo físico: campos, tipos, restrições e chaves. |
| 08 | [Diagramas de Classe](08-diagramas-classe.md) | Modelo estático das classes de domínio, em Mermaid `classDiagram`. |
| 09 | [Diagramas de Sequência](09-diagramas-sequencia.md) | Fluxos de interação entre objetos para os principais cenários, em Mermaid `sequenceDiagram`. |
| 10 | [Requisitos Não-Funcionais](10-requisitos-nao-funcionais.md) | Requisitos de desempenho, segurança, usabilidade, confiabilidade e demais atributos de qualidade. |
| — | [Relatório de Conformidade](relatorio-conformidade.md) | Análise de aderência ao modelo de prática profissional e registro das divergências deliberadas. |

---

## Convenções Adotadas

As convenções a seguir são aplicadas de forma uniforme a todos os documentos desta especificação:

- **Identificação dos casos de uso.** Cada caso de uso recebe um identificador no formato `UCxxx`, numerado por bloco funcional: `0xx` Geografia/RH, `1xx` Pagamento/Pessoas, `2xx` Conveniência, `3xx` Pátio, `4xx` Fiscal e `5xx` Financeiro.
- **Regras de negócio.** As regras de negócio são identificadas como `RNxxx`, numeradas dentro do escopo de cada caso de uso.
- **Exceções.** Os fluxos de exceção são identificados sequencialmente como `E1`, `E2`, ..., `En`, e cada um especifica a **mensagem de erro exata** apresentada ao usuário.
- **Mensagens parametrizadas.** Mensagens que dependem do campo em validação utilizam o marcador `[nome do campo]` como ponto de substituição (por exemplo, `O campo [nome do campo] é obrigatório.`).
- **Hierarquia de cabeçalhos.** O título do arquivo é `H1`; o caso de uso e suas seções internas são `H2`; as sub-ações de manutenção (**Inserir**, **Alterar**, **Excluir**) são `H3`.
- **Diagramas.** Todos os diagramas são expressos em **Mermaid**, utilizando `erDiagram` (entidade-relacionamento), `classDiagram` (classes) e `sequenceDiagram` (sequência).
- **Modelo físico.** O modelo físico está definido em `database/schema.sql` (PostgreSQL 17, **34 tabelas**), com chave primária `id` do tipo `IDENTITY`, unicidade de negócio aplicada ao trio fiscal, campos de auditoria em `TIMESTAMPTZ`, exclusão lógica por meio do campo `ativo` e chaves estrangeiras com políticas `ON DELETE RESTRICT` / `CASCADE` / `SET NULL` conforme a semântica de cada relacionamento.

---

## Divergências Deliberadas do Modelo

A especificação adota, de forma consciente, algumas divergências em relação ao modelo de prática profissional. Cada uma está justificada em detalhe no [Relatório de Conformidade](relatorio-conformidade.md); em resumo:

1. **Sem telas próprias na v1.** A interface da versão 1 é a **API REST documentada via Swagger/OpenAPI**, e não um conjunto de telas de aplicação; os protótipos têm caráter ilustrativo.
2. **Autenticação modelada, não implementada.** O módulo de autenticação/login foi **modelado** (tabela `usuario`, perfis `ADMIN` e `OPERADOR`), porém **não é implementado** no v1.
3. **Empresa fictícia.** O Estacionamento Kaneko é uma empresa fictícia, criada exclusivamente para fins acadêmicos.
4. **Escopo reduzido.** O sistema não contempla operação **multifilial** nem **integração fiscal** de transmissão à SEFAZ (as notas são registros internos). O **frete** é modelado: além do tipo (CIF/FOB) e dos valores rateáveis na nota, o sistema possui o **Módulo de Logística** (transportadora, veículo de frota e a frota N:N), com o fornecedor apontando para a sua transportadora padrão — sem, contudo, emissão de conhecimento de transporte eletrônico (CT-e).

---

## Arquitetura de Produto Enterprise (visão evolutiva)

Os documentos 01 a 10 e os casos de uso existentes continuam sendo a especificação do comportamento **AS-IS**. A arquitetura definitiva de evolução, com foco inicial em eventos e preservação dos módulos existentes, está nos documentos 11 a 20:

| # | Documento | Conteúdo |
|---|---|---|
| 11 | [Visão de Produto Enterprise](11-visao-produto-enterprise.md) | Propósito, visão, público, SaaS/on-premise, multiempresa, multipátio, multievento e princípios. |
| 12 | [Domínio e Context Map](12-dominio-ddd.md) | Linguagem ubíqua, bounded contexts, agregados, value objects, invariantes, serviços e dependências. |
| 13 | [Event Storming](13-event-storming.md) | Comandos, eventos de domínio, policies e process managers. |
| 14 | [Modelagem Completa do Domínio](14-modelagem-dominio.md) | Entidades atuais e futuras, atributos, estados, relacionamentos, validações e auditoria. |
| 15 | [Máquinas de Estados](15-maquinas-de-estados.md) | Estados, transições, atores, regras e efeitos colaterais. |
| 16 | [Casos de Uso da Plataforma](16-casos-de-uso-plataforma.md) | Eventos, vendas, credenciais, acesso, ERP, equipamentos e integrações. |
| 17 | [Arquitetura de APIs](17-apis-enterprise.md) | Contratos REST, payloads, respostas, status, idempotência, versionamento e eventos públicos. |
| 18 | [Permissões e RBAC](18-permissoes-rbac.md) | Papéis, permissões, escopos, ABAC e segregação de funções. |
| 19 | [Equipamentos e Integrações](19-equipamentos-integracoes.md) | Portas canônicas, Edge, contingência, ACL, webhooks, retries e telemetria. |
| 20 | [Roadmap do Produto](20-roadmap-produto.md) | Fundação, dez fases, riscos, impactos, NFRs e plano de evolução. |
| 21 | [Estado de Implementação](21-status-implementacao.md) | Classificação das histórias, dependências, bloqueios externos e condições de retomada. |
| — | [Registro Central de Bloqueios](bloqueios-externos.md) | Dependências externas, decisões de negócio e infraestrutura que impedem histórias específicas. |

### Regra de prevalência

- Para saber o que o software **faz hoje**, prevalecem código, migrations e documentos AS-IS.
- Para orientar novas decisões e implementações, prevalecem os documentos 11 a 20.
- Uma capacidade TARGET não deve ser apresentada como implementada até ser incorporada ao código, testes e documentação AS-IS.
- Nenhuma evolução elimina funcionalidades existentes sem plano explícito de compatibilidade e migração.
