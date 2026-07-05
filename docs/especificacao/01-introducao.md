> Links: [[README]] · [[02-analise-negocio]]

# 1. Introdução

Este documento integra a especificação de requisitos do Sistema de Controle do **Estacionamento Kaneko**, elaborado segundo o modelo de prática profissional de Sistemas de Informação. Esta seção apresenta a finalidade do documento, delimita o escopo do produto de software — explicitando o que está dentro e fora do alcance da versão 1 (v1) — e descreve a empresa para a qual o sistema é proposto. As seções seguintes da especificação tratam da análise do negócio, dos requisitos funcionais e não funcionais, dos casos de uso, do modelo de dados (Diagrama Entidade-Relacionamento e dicionário de dados) e dos demais artefatos técnicos.

A delimitação de escopo aqui apresentada está fundamentada diretamente no modelo físico de dados do sistema, materializado no arquivo `database/schema.sql` (PostgreSQL 17, banco `estacionamento_kaneko`), que reúne 34 tabelas distribuídas pelos domínios de geografia, pessoas, condições e formas de pagamento, recursos humanos, pátio (mensalista e avulso), conveniência com controle de estoque, documentos fiscais, financeiro a pagar e a receber e logística (transportadora e frota).

---

## 1.1 Finalidade

A finalidade deste documento é registrar, de forma completa e não ambígua, os requisitos do Sistema de Controle do Estacionamento Kaneko, servindo como referência única e formal para todas as partes interessadas — equipe de desenvolvimento, responsáveis pela operação do estabelecimento e avaliadores acadêmicos do projeto.

O documento tem os seguintes propósitos específicos:

- **Estabelecer o entendimento comum do problema.** Descrever, em linguagem acessível ao negócio e precisa o suficiente para a engenharia, qual problema o sistema resolve: o controle informatizado de um estacionamento que opera simultaneamente nas modalidades rotativa (avulsa) e mensalista, acoplado a uma loja de conveniência com gestão de estoque, emissão de documentos fiscais e controle financeiro.
- **Delimitar o produto.** Definir com precisão a fronteira do sistema (Seção 1.2), separando as funcionalidades que serão construídas na versão 1 daquelas conscientemente deixadas fora do escopo, evitando expectativas indevidas e desvios de planejamento.
- **Fundamentar a rastreabilidade.** Servir de base para que cada requisito funcional, caso de uso e entidade do modelo de dados possa ser rastreado até uma necessidade de negócio concreta, e vice-versa.
- **Orientar a verificação.** Fornecer os critérios a partir dos quais o sistema poderá ser validado, garantindo que o produto entregue corresponda ao que foi especificado.

O público-alvo deste documento compreende: o analista de sistemas e a equipe de desenvolvimento, que o utilizam como insumo de projeto e implementação; os usuários e gestores do Estacionamento Kaneko, que o empregam para validar se o sistema atende às regras do negócio; e a banca/avaliação acadêmica, que o adota como evidência do processo de engenharia de requisitos aplicado ao projeto.

---

## 1.2 Escopo

O produto de software é uma aplicação de gestão para um estacionamento com loja de conveniência. O sistema é disponibilizado como uma **API REST** documentada via **Swagger/OpenAPI**, sem interface gráfica própria nesta versão — o consumo ocorre por clientes HTTP (Swagger UI, ferramentas de teste de API ou um front-end a ser desenvolvido em fase posterior). O backend é construído sobre Spring Boot (Java) com persistência em PostgreSQL, e o esquema de banco descrito em `schema.sql` constitui a fonte de verdade do domínio.

O sistema cobre o ciclo operacional completo do estabelecimento: o cadastro das entidades de apoio (geografia, pessoas, pagamento e produtos), o controle do pátio nas modalidades avulsa e mensalista, a operação da conveniência com movimentação de estoque, a escrituração dos documentos fiscais correspondentes e a apuração financeira de contas a pagar e a receber.

### 1.2.1 Dentro do escopo (versão 1)

As funcionalidades a seguir integram a versão 1 e estão suportadas por tabelas concretas no modelo de dados.

| Domínio | Funcionalidades incluídas | Tabelas de suporte (schema.sql) |
|---|---|---|
| **Cadastros geográficos** | Cadastro hierárquico de país, estado e cidade, utilizado como referência de endereçamento de pessoas. | `pais`, `estado`, `cidade` |
| **Cadastros de pessoas** | Cadastro de clientes (pessoa física ou jurídica, dono do plano mensalista e destinatário de notas), fornecedores (com múltiplos e-mails e telefones) e funcionários, além de cargos de RH. Suporte a tipo de pessoa, documento (CPF/CNPJ), endereço, dados de contato e nacionalidade. | `cliente`, `fornecedor`, `fornecedor_email`, `fornecedor_telefone`, `funcionario`, `cargo` |
| **Pagamento** | Cadastro de formas de pagamento (dinheiro, cartão, Pix, boleto) e de condições de pagamento parametrizáveis (número de parcelas, dias até a primeira parcela, intervalo entre parcelas, percentuais de juros, multa e desconto), com detalhamento parcela a parcela. | `forma_pagamento`, `condicao_pagamento`, `parcela_condicao_pagamento` |
| **Pátio — mensalista** | Gestão do contrato de mensalista vinculando cliente e veículo, com valor mensal, vigência (início/fim), status (ativo, suspenso, cancelado) e a regra de no máximo um contrato ativo por veículo. | `mensalista`, `veiculo`, `cliente` |
| **Pátio — avulso (rotativo)** | Registro da movimentação de pátio (ticket de entrada/saída) para veículos avulsos e mensalistas, com cálculo de cobrança a partir da tarifa vigente (valor por hora, teto de diária e tolerância de cortesia), controle de status (aberto, fechado, cancelado) e a regra de no máximo uma movimentação aberta por veículo. | `movimentacao`, `tarifa`, `veiculo` |
| **Conveniência com estoque** | Cadastro de produtos com categoria, marca e unidade de medida, controle de estoque (quantidade atual, quantidade mínima, fração para itens vendidos por peso/volume), precificação (custo, venda e margem) e vínculo N:N produto × fornecedor. | `produto`, `categoria`, `marca`, `unidade_medida`, `produto_fornecedor`, `servico` |
| **Documentos fiscais (escrituração)** | Registro de nota de entrada (compra de mercadoria, que acresce o estoque na confirmação), nota de saída (venda de mercadoria, que reduz o estoque na confirmação) e nota de serviço (NFS-e do estacionamento, com base de cálculo e alíquota de ISS, originada de uma movimentação avulsa ou de uma mensalidade). Inclui itens com rateio de frete, seguro e despesas. | `nota_entrada`, `item_nota_entrada`, `nota_saida`, `item_nota_saida`, `nota_servico` |
| **Financeiro — a pagar e a receber** | Controle de contas a pagar (originadas de notas de entrada e de despesas avulsas como aluguel, energia e água) e contas a receber (originadas de notas de saída e de mensalidades), com parcelamento, baixa por situação (pendente, parcial, paga/recebida, cancelada), e composição de valores (original, pago/recebido, desconto, juros, multa, total). | `conta_pagar`, `conta_receber`, `contas_pagar_avulsa` |

De forma sintética, estão **dentro do escopo da v1**: (i) os cadastros geográficos, de pessoas e de pagamento; (ii) a operação de pátio mensalista e avulsa; (iii) a conveniência com controle de estoque; (iv) a escrituração de documentos fiscais de entrada, saída e serviço; e (v) o financeiro a pagar e a receber.

### 1.2.2 Fora do escopo (versão 1)

As funcionalidades a seguir são deliberadamente excluídas da versão 1. A exclusão é uma decisão de projeto para concentrar o esforço no núcleo operacional do estabelecimento; a maioria destes itens é candidata a fases futuras.

| Item fora do escopo | Justificativa / situação na v1 |
|---|---|
| **Operação multifilial** | O sistema atende a uma única unidade do Estacionamento Kaneko. Não há modelagem de filial, e nenhuma tabela carrega segregação por estabelecimento. A introdução de múltiplas filiais é tratada como evolução futura. |
| **Autenticação e controle de acesso** | A tabela `usuario` (com `login`, `senha` em hash, `perfil` ADMIN/OPERADOR e vínculo opcional a funcionário) está **modelada** no banco, porém a autenticação, a autorização e a gestão de sessão **não são implementadas** na v1. As regras de login, hashing e perfil permanecem fora da aplicação nesta versão, conforme registrado no próprio `schema.sql`. |
| **NF-e eletrônica / integração com a SEFAZ** | As notas (entrada, saída e serviço) são registradas para fins de **escrituração e controle interno**, com seus campos fiscais (número, modelo, série, valores, ISS). Não há emissão eletrônica autorizada, transmissão à SEFAZ, geração de XML, assinatura digital ou retorno de protocolo. |
| **Transportadora / logística de entrega** | Embora as notas registrem tipo de frete (CIF, FOB, SEM) e valor de frete, não há cadastro de transportadora nem gestão de expedição, rastreamento ou conhecimento de transporte. |
| **Interface gráfica própria (telas)** | A versão 1 expõe exclusivamente uma **API REST documentada via Swagger/OpenAPI**. Não há telas próprias, portal web ou aplicativo. A experiência de uso ocorre pela documentação interativa da API e por clientes HTTP. |

Em resumo, estão **fora do escopo da v1**: a operação multifilial; a autenticação de usuários (modelada, mas não implementada); a emissão eletrônica de NF-e e a integração com a SEFAZ; o cadastro e a operação de transportadora; e qualquer interface gráfica própria — a interação se dá unicamente por API REST com documentação Swagger.

---

## 1.3 A Empresa

> *Empresa de caráter fictício, concebida para fins acadêmicos com características de operação plausíveis e representativas do segmento.*

O **Estacionamento Kaneko Ltda.** é uma empresa de pequeno porte do ramo de estacionamento rotativo e mensalista, com loja de conveniência anexa, sediada na região central de **Maringá, no estado do Paraná**. A razão social remonta ao seu fundador, o senhor Hideo Kaneko, descendente da comunidade nipo-brasileira tradicional da cidade, que inaugurou o negócio com uma operação familiar de poucas vagas.

### 1.3.1 Breve histórico

O estabelecimento iniciou suas atividades em 2009 como um pátio simples de guarda de veículos, atendendo ao comércio do entorno mediante anotações manuais em fichas de papel. Com o adensamento da região central e o aumento da rotatividade, a empresa formalizou os planos de mensalista para clientes recorrentes — comerciantes, profissionais liberais e moradores próximos — e, a partir de 2016, agregou uma pequena loja de conveniência junto à guarita, oferecendo bebidas, lanches e itens de utilidade ao público em trânsito. O crescimento progressivo do volume de veículos, de mensalistas e do giro de mercadorias tornou o controle manual insuficiente e propenso a erros, motivando a contratação deste sistema de informação.

### 1.3.2 Operação atual

A operação do Estacionamento Kaneko caracteriza-se pelos seguintes números e práticas, representativos de um estabelecimento de pequeno porte:

| Aspecto | Caracterização |
|---|---|
| **Capacidade do pátio** | Aproximadamente 60 vagas, distribuídas entre automóveis e motocicletas. |
| **Modalidade avulsa (rotativa)** | Cobrança por tempo de permanência, com valor por hora ou fração, teto de diária e tolerância de cortesia para permanências muito curtas, conforme a tarifa vigente. |
| **Modalidade mensalista** | Cerca de 40 contratos ativos de mensalistas, vinculando cliente e veículo, com mensalidade fixa e renovação contínua. |
| **Loja de conveniência** | Algumas centenas de itens em estoque (bebidas, lanches, tabacaria e utilidades), com reposição por fornecedores locais e controle de estoque mínimo. |
| **Quadro funcional** | Equipe enxuta de operadores de guarita e caixa, sob administração direta do proprietário. |
| **Faturamento** | Composto por três frentes — tarifas avulsas, mensalidades e vendas da conveniência —, o que justifica a necessidade de um financeiro integrado de contas a receber e a pagar. |

O perfil descrito sustenta as decisões de escopo da Seção 1.2: por se tratar de uma operação de unidade única, gerida diretamente pelo proprietário, a versão 1 prioriza o núcleo operacional (pátio, conveniência, fiscal e financeiro) e posterga funcionalidades de maior complexidade institucional, como multifilial, autenticação plena e emissão fiscal eletrônica.

---

## Documentos relacionados

- [[02-analise-negocio]] — Análise do negócio, regras e processos do Estacionamento Kaneko.
- [[README]] — Visão geral do projeto e índice da documentação.

## Histórico

| Data | Ação |
|---|---|
| 2026-06-14 | Criação da Seção 1 (Introdução: Finalidade, Escopo e A Empresa), fundamentada no `database/schema.sql`. |
