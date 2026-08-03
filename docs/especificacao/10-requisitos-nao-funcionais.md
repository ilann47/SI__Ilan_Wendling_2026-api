> Links: [[README]] · [[09-diagramas-sequencia]]

# 10. Requisitos Não Funcionais

> **Classificação histórica:** os requisitos desta seção nasceram sobre a v1 de
> 34 tabelas. A autenticação, o RBAC contextual e o isolamento multiempresa
> vigentes/complementares estão nos documentos 18, 21 e 22; Flyway, e não
> `database/schema.sql`, define o schema executável atual.

Os requisitos não funcionais (RNF) descrevem as restrições de qualidade impostas ao Sistema de Controle de Estacionamento Kaneko — atributos que não se traduzem em funcionalidades isoladas, mas que condicionam a maneira como todas as funcionalidades devem se comportar. Diferentemente dos requisitos funcionais, que respondem à pergunta "o que o sistema faz", os requisitos não funcionais respondem à pergunta "com que qualidade o sistema faz". Esta seção foi elaborada de modo a ser concreta e verificável, ancorando cada requisito em elementos reais já presentes na arquitetura do projeto: a plataforma tecnológica adotada (Spring Boot 4.0.7 sobre Java 25, persistência em PostgreSQL 17), o esquema relacional definido em `database/schema.sql` (trinta e quatro tabelas, restrições de integridade do tipo `CHECK`, índices declarados e a tabela de controle de acesso `usuario`) e as dependências declaradas no `pom.xml` (entre elas o módulo de validação `spring-boot-starter-validation` e o módulo de observabilidade `spring-boot-starter-actuator`).

O sistema integra dois domínios complementares: o controle de pátio do estacionamento propriamente dito (mensalistas, avulsos, movimentação de entrada e saída, tarifação) e o módulo de conveniência e comercial (produtos, notas de entrada e saída, notas de serviço e financeiro de contas a pagar e a receber). Essa dualidade impõe exigências de qualidade que precisam considerar tanto a operação de balcão de alta frequência — onde a latência percebida pelo operador é crítica — quanto a integridade contábil e fiscal do módulo comercial, onde a consistência dos dados é inegociável.

Cada subseção a seguir é organizada em torno de um atributo de qualidade. Onde aplicável, os requisitos são apresentados em tabelas, identificados por código no formato `RNF-<categoria>-<sequência>`, acompanhados de descrição, critério de aceitação mensurável e da âncora técnica que o fundamenta no código ou no esquema do banco.

---

## 10.1. Segurança

A segurança do sistema abrange a autenticação dos operadores, a autorização baseada em perfil, a proteção das credenciais armazenadas, a validação rigorosa das entradas e a defesa da integridade dos dados na camada de persistência. O ponto central de ancoragem da segurança é a tabela `usuario`, definida em `database/schema.sql`, que materializa o controle de acesso ao sistema.

A tabela `usuario` declara explicitamente os campos `login` (identificador único, com unicidade garantida de forma insensível a maiúsculas e minúsculas pelo índice `uq_usuario_login` sobre `LOWER(login)`), `senha` (com largura `VARCHAR(255)`, dimensionada para armazenar o hash da senha e jamais o seu valor em texto puro) e `perfil`, restrito por uma cláusula `CHECK` aos valores `ADMIN` e `OPERADOR`. Essa restrição de banco — `CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('ADMIN','OPERADOR'))` — garante, na própria camada de dados, que nenhum perfil inválido possa ser persistido, independentemente de falhas na camada de aplicação.

A largura de duzentos e cinquenta e cinco caracteres do campo `senha` foi escolhida deliberadamente para acomodar hashes do algoritmo BCrypt (que produzem sessenta caracteres) com folga para futura migração a algoritmos mais robustos, como Argon2. O comentário no próprio esquema — `senha VARCHAR(255) NOT NULL, -- hash (auth e codigo, fora do banco)` — registra a decisão arquitetural de que a derivação e a verificação do hash ocorrem na camada de aplicação, nunca no banco.

A validação de entradas apoia-se na dependência `spring-boot-starter-validation`, declarada no `pom.xml`, que disponibiliza a especificação Bean Validation (Jakarta Validation). Todos os DTOs de entrada dos controladores REST devem ser anotados com restrições declarativas (`@NotNull`, `@NotBlank`, `@Size`, `@Email`, `@Positive`, `@CPF`/`@CNPJ` quando aplicável) e os métodos dos controladores devem marcar os parâmetros de corpo com `@Valid`, de modo que requisições malformadas sejam rejeitadas com resposta HTTP 400 antes de qualquer acesso ao banco — concretizando o princípio *fail fast*.

A segunda linha de defesa da integridade é o conjunto de restrições `CHECK` distribuídas por todo o esquema. Restrições como `ck_cliente_tipo CHECK (tipo IN ('FISICA','JURIDICA'))`, `ck_mov_status CHECK (status IN ('ABERTO','FECHADO','CANCELADO'))`, `ck_mensalista_status` e `ck_nota_servico_situacao` impedem que dados incoerentes cheguem ao banco mesmo que a validação de aplicação seja contornada. Adicionalmente, índices únicos parciais como `uq_mov_veiculo_aberta` (no máximo uma movimentação `ABERTO` por veículo) e `uq_mensalista_veiculo_ativo` (no máximo um contrato `ATIVO` por veículo) protegem regras de negócio sensíveis contra condições de corrida.

| Código | Requisito | Critério de aceitação | Âncora técnica |
|---|---|---|---|
| RNF-SEG-01 | O sistema deve autenticar todo usuário antes de permitir o acesso às funcionalidades. | Nenhuma operação de escrita é executada sem usuário autenticado; tentativa anônima retorna HTTP 401. | Tabela `usuario` (`login`, `senha`); índice `uq_usuario_login`. |
| RNF-SEG-02 | As senhas devem ser armazenadas exclusivamente como hash, nunca em texto puro. | Inspeção da coluna `usuario.senha` retorna apenas valores no formato de hash BCrypt; nenhum log registra a senha. | Campo `senha VARCHAR(255)`; derivação na camada de aplicação. |
| RNF-SEG-03 | O sistema deve aplicar autorização baseada em perfil (`ADMIN`, `OPERADOR`). | Operações administrativas (cadastros estruturais, exclusões) são negadas a perfil `OPERADOR` com HTTP 403. | Campo `usuario.perfil`; `CHECK ck_usuario_perfil`. |
| RNF-SEG-04 | Toda comunicação cliente-servidor deve trafegar sobre HTTPS/TLS. | Em produção, requisições por HTTP simples são redirecionadas ou recusadas; certificado TLS válido na borda. | Terminação TLS em *reverse proxy* à frente do serviço Spring Boot (porta `8086`). |
| RNF-SEG-05 | Toda entrada de dados deve ser validada antes do processamento. | DTOs inválidos retornam HTTP 400 com descrição do campo violado, sem atingir o banco. | `spring-boot-starter-validation` (Bean Validation); `@Valid` nos controladores. |
| RNF-SEG-06 | A integridade dos dados deve ser garantida na camada de persistência, independentemente da aplicação. | Tentativa de inserir valor fora do domínio é rejeitada pelo banco com violação de `CHECK`. | Restrições `CHECK` (tipo, status, situação, valores ≥ 0) em `schema.sql`. |
| RNF-SEG-07 | Regras de unicidade de negócio devem ser impostas a prova de concorrência. | Duas entradas simultâneas do mesmo veículo no pátio resultam em apenas uma movimentação `ABERTO`. | Índices únicos parciais `uq_mov_veiculo_aberta` e `uq_mensalista_veiculo_ativo`. |
| RNF-SEG-08 | Mensagens de erro não devem expor detalhes internos sensíveis. | Respostas de erro não retornam *stack trace*, nomes de tabela cru ou credenciais; o detalhamento fica nos logs internos. | Tratamento centralizado de exceções (`@ControllerAdvice`). |

O *endpoint* de saúde exposto pelo Actuator está configurado em `application.yml` com `show-details: when_authorized`, ou seja, detalhes do estado interno do sistema só são revelados a requisições autorizadas — uma decisão coerente com o princípio de menor exposição de informação.

---

## 10.2. Performance

Os requisitos de performance derivam do perfil de uso do estabelecimento: o registro de entrada e saída de veículos é uma operação de balcão, executada repetidamente ao longo do dia, na qual o tempo de resposta percebido pelo operador impacta diretamente a fluidez do atendimento e a formação de filas na portaria. Já as consultas do módulo financeiro (contas a pagar e a receber, posições por vencimento e por situação) precisam responder rapidamente mesmo com o crescimento do volume histórico de lançamentos.

A principal estratégia de performance adotada no esquema é a indexação sistemática. Todas as chaves estrangeiras possuem índice declarado — por exemplo `ix_mov_veiculo`, `ix_mov_mensalista`, `ix_cliente_cidade`, `ix_funcionario_cargo`, `ix_produto_categoria`, `ix_conta_pagar_fornecedor` e `ix_conta_receber_cliente` — o que acelera as junções e as buscas por relacionamento, evitando varreduras sequenciais completas nas tabelas. Essa decisão é deliberada: chaves estrangeiras sem índice são uma causa clássica de degradação em operações de junção e de verificação de integridade referencial.

Para o módulo financeiro, que concentra as consultas mais pesadas por intervalo de data, foram criados índices específicos sobre as colunas mais filtradas. Os índices `ix_conta_pagar_vencimento` e `ix_conta_receber_vencimento` aceleram a busca de títulos por `data_vencimento`, ao passo que os índices compostos `ix_conta_pagar_situ_venc (situacao, data_vencimento)` e `ix_conta_receber_situ_venc (situacao, data_vencimento)` otimizam a consulta mais frequente do contas a pagar/receber: "títulos `PENDENTE` vencendo em determinado período". A ordenação das colunas no índice composto — primeiro `situacao`, depois `data_vencimento` — segue a boa prática de posicionar a coluna de igualdade antes da coluna de faixa. O mesmo padrão se repete na tabela `nota_servico`, com `ix_nota_servico_situacao` e `ix_nota_servico_emissao`, e na tabela `movimentacao`, com `ix_mov_data_entrada` para relatórios por período.

| Código | Requisito | Critério de aceitação | Âncora técnica |
|---|---|---|---|
| RNF-PER-01 | O registro de entrada e de saída de veículo deve responder rapidamente. | Tempo de resposta do servidor inferior a 500 ms no percentil 95, sob carga típica de balcão. | Índices `ix_mov_veiculo`, `ix_mov_mensalista`, `uq_veiculo_placa`. |
| RNF-PER-02 | As consultas financeiras por vencimento devem ser eficientes. | Consulta de títulos por faixa de `data_vencimento` resolvida por índice (sem *seq scan*), abaixo de 1 s. | `ix_conta_pagar_vencimento`, `ix_conta_receber_vencimento`. |
| RNF-PER-03 | As consultas financeiras por situação e vencimento devem usar índice composto. | Plano de execução de "PENDENTE vencendo no período" utiliza o índice composto. | `ix_conta_pagar_situ_venc`, `ix_conta_receber_situ_venc`. |
| RNF-PER-04 | Toda junção por chave estrangeira deve ser amparada por índice. | Nenhuma FK do esquema fica sem índice associado. | Índices `ix_*` sobre todas as colunas FK em `schema.sql`. |
| RNF-PER-05 | A busca de veículo por placa deve ser imediata. | Localização de veículo por placa resolvida pelo índice único. | `uq_veiculo_placa` (UNIQUE sobre `placa`). |
| RNF-PER-06 | O pool de conexões deve evitar esgotamento sob concorrência. | Operações concorrentes de balcão não bloqueiam por falta de conexão. | Pool HikariCP padrão do Spring Boot; `open-in-view: false` em `application.yml`. |
| RNF-PER-07 | A serialização de respostas deve evitar tráfego desnecessário. | Campos nulos são omitidos das respostas JSON. | `jackson.default-property-inclusion: non_null` em `application.yml`. |

A configuração `open-in-view: false` presente no `application.yml` é uma decisão de performance relevante: ela impede o antipadrão *Open Session In View*, no qual a sessão de persistência permaneceria aberta durante a renderização da resposta, prolongando a retenção de conexões do pool e mascarando consultas N+1. Com essa opção desativada, as fronteiras transacionais ficam restritas à camada de serviço, favorecendo a liberação ágil das conexões.

---

## 10.3. Disponibilidade

A disponibilidade expressa a fração de tempo em que o sistema permanece operacional e apto a atender requisições. Para um estacionamento, a indisponibilidade do sistema durante o horário de funcionamento tem impacto operacional direto: impede o registro correto de entradas e saídas e, por consequência, o cálculo de tarifas e a emissão de comprovantes.

O sistema expõe o módulo de observabilidade do Spring Boot Actuator, declarado como dependência no `pom.xml` e configurado em `application.yml` para publicar os *endpoints* `health`, `info` e `metrics`. O *endpoint* `health` constitui a sonda de saúde que permite a uma camada de orquestração ou a um balanceador de carga verificar continuamente se a instância está saudável e, em caso negativo, redirecionar o tráfego ou reiniciar o processo.

| Código | Requisito | Critério de aceitação | Âncora técnica |
|---|---|---|---|
| RNF-DIS-01 | O sistema deve oferecer disponibilidade compatível com o horário de operação do estabelecimento. | Disponibilidade mensal mínima de 99% durante a janela de funcionamento. | Operação contínua do serviço na porta `8086`. |
| RNF-DIS-02 | O sistema deve expor sonda de saúde para monitoramento externo. | O *endpoint* `/actuator/health` responde `UP` quando o serviço e o banco estão operacionais. | `spring-boot-starter-actuator`; `management.endpoints.web.exposure.include: health,info,metrics`. |
| RNF-DIS-03 | A indisponibilidade do banco deve ser detectada e refletida na sonda de saúde. | Queda do PostgreSQL faz o `health` reportar `DOWN`. | Indicador de saúde do *datasource* (HikariCP/JDBC). |
| RNF-DIS-04 | Falhas em uma requisição não devem derrubar o processo do servidor. | Exceções de negócio e de validação são tratadas e retornam erro HTTP apropriado, mantendo o serviço no ar. | Tratamento centralizado de exceções; `fail fast` na validação. |
| RNF-DIS-05 | O detalhamento da saúde deve ser restrito a requisições autorizadas. | `/actuator/health` só exibe detalhes a chamadas autorizadas. | `management.endpoint.health.show-details: when_authorized`. |

A recuperação após falha apoia-se na estratégia de backup descrita na subseção 10.6: o tempo de recuperação alvo (RTO) e o ponto de recuperação alvo (RPO) são definidos pela política de cópias de segurança do PostgreSQL, de modo que a disponibilidade efetiva combina a robustez do processo de aplicação com a capacidade de restauração do banco.

---

## 10.4. Usabilidade

A usabilidade trata da facilidade com que os operadores executam as tarefas do dia a dia — predominantemente o registro de entrada e saída de veículos e a venda de itens da conveniência — com o mínimo de erros e de treinamento. Embora a interface de usuário não esteja totalmente especificada nesta fase, diversos requisitos de usabilidade já estão amparados pela modelagem de dados, que reduz a carga cognitiva e previne erros na origem.

A própria estrutura dos cadastros favorece a usabilidade: a hierarquia geográfica `pais → estado → cidade` permite o preenchimento de endereços por seleção encadeada em vez de digitação livre, reduzindo erros de grafia; o campo `apelido` em `cliente`, `funcionario` e `fornecedor` permite identificação rápida por um nome curto e familiar; e a `tarifa` padrão já semeada no banco (`INSERT INTO tarifa ... 'Tarifa padrao'`) garante que o sistema esteja operacional desde a primeira execução, sem exigir configuração prévia para o caso de uso mais comum.

As mensagens de erro de validação, originadas no Bean Validation, devem ser claras e referirem-se ao campo violado em linguagem compreensível pelo operador, e não a códigos técnicos. A tolerância de cortesia parametrizável na tarifa (`tolerancia_min`, com padrão de quinze minutos) é também um elemento de usabilidade orientado ao cliente final do estacionamento, pois evita cobranças percebidas como injustas em permanências muito curtas.

| Código | Requisito | Critério de aceitação | Âncora técnica |
|---|---|---|---|
| RNF-USA-01 | O preenchimento de endereços deve minimizar digitação livre e erros de grafia. | Cidade é selecionada a partir do estado, e estado a partir do país. | Hierarquia `pais`/`estado`/`cidade` com FKs e índices únicos compostos. |
| RNF-USA-02 | As mensagens de erro devem ser compreensíveis e indicar o campo problemático. | Erro de validação informa o campo e o motivo em linguagem natural. | Bean Validation (`spring-boot-starter-validation`). |
| RNF-USA-03 | O sistema deve estar pronto para operar imediatamente após a instalação. | Após carga do esquema, é possível registrar uma movimentação avulsa sem cadastro prévio de tarifa. | Seed `INSERT INTO tarifa` em `schema.sql`. |
| RNF-USA-04 | A identificação de pessoas deve ser ágil no atendimento. | Cliente, funcionário e fornecedor podem ser localizados por apelido. | Campo `apelido` em `cliente`, `funcionario`, `fornecedor`. |
| RNF-USA-05 | Datas e horários devem ser exibidos no fuso local do estabelecimento. | Registros de entrada e saída aparecem no horário de Brasília. | `jackson.time-zone` e `hibernate.jdbc.time_zone: America/Sao_Paulo` em `application.yml`. |

---

## 10.5. Escalabilidade

A escalabilidade descreve a capacidade do sistema de absorver crescimento — de volume de dados históricos, de número de operadores simultâneos e de unidades atendidas — sem reprojeto estrutural. Embora a versão atual seja explicitamente de filial única (decisão registrada no cabeçalho de `schema.sql`: "Sem multifilial"), a arquitetura escolhida permite evolução incremental.

No eixo de dados, o uso de chaves primárias `BIGINT GENERATED BY DEFAULT AS IDENTITY` em todas as tabelas oferece espaço de endereçamento amplo o bastante para o crescimento histórico de movimentações, notas e lançamentos financeiros sem risco de esgotamento de identificadores. No eixo de carga, a aplicação Spring Boot é projetada como serviço sem estado de sessão na camada de aplicação (`open-in-view: false`, configuração transacional na camada de serviço), o que viabiliza a escalabilidade horizontal: múltiplas instâncias podem operar atrás de um balanceador, compartilhando o mesmo PostgreSQL.

| Código | Requisito | Critério de aceitação | Âncora técnica |
|---|---|---|---|
| RNF-ESC-01 | O esquema deve suportar o crescimento histórico de registros sem esgotar identificadores. | Identificadores comportam o volume previsto de movimentações e lançamentos por anos de operação. | PKs `BIGINT GENERATED BY DEFAULT AS IDENTITY` em todas as tabelas. |
| RNF-ESC-02 | A aplicação deve permitir escalabilidade horizontal por múltiplas instâncias. | É possível executar mais de uma instância do serviço atrás de balanceador, contra o mesmo banco. | Serviço sem estado de sessão; `open-in-view: false`. |
| RNF-ESC-03 | O desempenho das consultas deve degradar suavemente com o aumento do volume. | Consultas indexadas mantêm tempo de resposta dentro do alvo à medida que as tabelas crescem. | Índices sobre FKs, `data_vencimento`, `situacao` e `data_entrada`. |
| RNF-ESC-04 | A modelagem deve permitir evolução para múltiplas filiais sem reescrita do núcleo. | Inclusão futura de dimensão de filial não exige alteração das regras de negócio centrais. | Modelagem normalizada e parametrização por tabelas de apoio (`tarifa`, `servico`). |

---

## 10.6. Backup

O backup garante a continuidade do negócio diante de falhas de hardware, corrupção de dados ou erro humano. Para um sistema que registra movimentação financeira e fiscal — notas de entrada, notas de saída, notas de serviço com ISS, contas a pagar e a receber — a perda de dados representa não apenas prejuízo operacional, mas também risco de não conformidade fiscal e contábil.

A estratégia de backup recai sobre os mecanismos nativos do PostgreSQL 17. Recomenda-se a combinação de cópias lógicas periódicas via `pg_dump` (preservando o esquema e os dados em formato restaurável) com o arquivamento contínuo de *Write-Ahead Log* (WAL), que viabiliza a recuperação a um ponto no tempo (*Point-in-Time Recovery*, PITR). A presença das colunas `created_at` e `updated_at` em todas as tabelas funciona como apoio à verificação de integridade pós-restauração, permitindo confirmar a janela temporal efetivamente recuperada.

| Código | Requisito | Critério de aceitação | Âncora técnica |
|---|---|---|---|
| RNF-BKP-01 | O banco de dados deve ser copiado periodicamente de forma automática. | Existe rotina diária de `pg_dump` do banco `estacionamento_kaneko`, com retenção definida. | PostgreSQL 17; banco `estacionamento_kaneko`. |
| RNF-BKP-02 | Deve ser possível recuperar o banco a um ponto no tempo. | Restauração PITR a um instante anterior a um incidente é demonstrável em ambiente de teste. | Arquivamento de WAL do PostgreSQL. |
| RNF-BKP-03 | O ponto de recuperação alvo (RPO) deve limitar a perda máxima de dados. | RPO não superior a 24 horas para a cópia lógica diária (menor com WAL contínuo). | Periodicidade do `pg_dump` + WAL. |
| RNF-BKP-04 | A restauração deve ser testada e ter tempo de recuperação conhecido. | Teste de restauração executado e documentado, com RTO medido. | Procedimento de restauração validado periodicamente. |
| RNF-BKP-05 | A integridade da cópia restaurada deve ser verificável. | Conferência da janela temporal restaurada pelos carimbos de data/hora. | `created_at`/`updated_at` em todas as tabelas. |

---

## 10.7. Auditoria

A auditoria trata da capacidade de reconstruir o histórico das operações: quando um registro foi criado, quando foi alterado pela última vez e em que estado se encontra ao longo do seu ciclo de vida. Esse atributo é especialmente sensível no módulo financeiro e fiscal, onde a rastreabilidade é exigência contábil.

A âncora primária da auditoria é a presença universal das colunas `created_at` e `updated_at`, ambas `TIMESTAMPTZ NOT NULL DEFAULT now()`, em todas as trinta e quatro tabelas do esquema. A coluna `created_at` registra o instante de criação do registro; a `updated_at` deve ser atualizada a cada alteração, refletindo a data e hora da última modificação. Como o tipo é `TIMESTAMPTZ` (com fuso horário) e o `time_zone` do JDBC está fixado em `America/Sao_Paulo`, os carimbos são consistentes e comparáveis.

O segundo pilar da auditoria é o registro de situação (estado) das entidades de negócio, que permite rastrear a transição pelo ciclo de vida. Documentos fiscais e financeiros carregam o campo `situacao`, restrito por `CHECK`: notas de entrada e de saída transitam entre `PENDENTE`, `CONFIRMADA` e `CANCELADA`; a nota de serviço entre `PENDENTE`, `EMITIDA` e `CANCELADA`; as contas a pagar e a receber entre `PENDENTE`, `PARCIAL`, `PAGA`/`RECEBIDA` e `CANCELADA`. As entidades operacionais carregam estado análogo: `movimentacao.status` (`ABERTO`, `FECHADO`, `CANCELADO`) e `mensalista.status` (`ATIVO`, `SUSPENSO`, `CANCELADO`). Restrições como `ck_conta_pagar_baixa` e `ck_conta_receber_baixa` garantem ainda a coerência entre a situação e a data de baixa — por exemplo, uma conta só pode estar `PAGA` se possuir `data_pagamento` preenchida — o que fortalece a confiabilidade da trilha de auditoria.

| Código | Requisito | Critério de aceitação | Âncora técnica |
|---|---|---|---|
| RNF-AUD-01 | Todo registro deve guardar o instante de sua criação. | Toda linha possui `created_at` preenchido automaticamente na inserção. | `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` em todas as tabelas. |
| RNF-AUD-02 | Toda alteração deve atualizar o carimbo de modificação. | `updated_at` reflete o instante da última alteração do registro. | `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`; atualização na camada de aplicação. |
| RNF-AUD-03 | O ciclo de vida dos documentos fiscais deve ser rastreável por situação. | A situação de notas e contas só assume valores válidos e coerentes com as datas de baixa. | Campos `situacao`/`status` com `CHECK`; `ck_conta_pagar_baixa`, `ck_conta_receber_baixa`. |
| RNF-AUD-04 | Não deve haver exclusão física que apague o histórico de cadastros. | Cadastros são inativados (`ativo = FALSE`) em vez de excluídos; FKs usam `ON DELETE RESTRICT`. | Campo `ativo` + `@SQLDelete` nas entidades de cadastro (o `DELETE` vira `UPDATE ... SET ativo = false`); `ON DELETE RESTRICT` nas FKs. |
| RNF-AUD-05 | As ações administrativas devem ser atribuíveis a um usuário. | **Parcial.** A autorização por perfil (ADMIN/OPERADOR) identifica o responsável na sessão autenticada; a trilha de autor por operação (`created_by`) está **prevista, não implementada** nesta versão — exigiria coluna adicional (esquema congelado). Ver relatório de conformidade. | Tabela `usuario` + autorização por perfil; `created_by` por registro: trabalho futuro. |
| RNF-AUD-06 | Os carimbos de auditoria devem ser temporalmente consistentes. | Datas e horas de auditoria são comparáveis em fuso único. | `TIMESTAMPTZ` + `hibernate.jdbc.time_zone: America/Sao_Paulo`. |

A política de inativação lógica (campo `ativo`) combinada com a cláusula `ON DELETE RESTRICT` nas chaves estrangeiras assegura que registros referenciados não sejam removidos fisicamente, preservando a trilha histórica e a integridade referencial — um requisito de auditoria tão importante quanto os carimbos de data e hora.

---

## 10.8. LGPD (Lei Geral de Proteção de Dados)

A Lei nº 13.709/2018 (Lei Geral de Proteção de Dados Pessoais) impõe obrigações sobre o tratamento de dados pessoais de pessoas naturais. O sistema trata dados pessoais de três categorias de titulares: clientes (mensalistas e compradores da conveniência), funcionários e fornecedores pessoa física. É portanto imprescindível mapear esses dados e definir os controles que assegurem conformidade.

Os dados pessoais tratados estão concretamente identificados no esquema. Nas tabelas `cliente` e `funcionario` figuram: documento de identificação (`documento` / `cpf`, que armazena CPF ou CNPJ), `rg_inscricao_estadual`, `data_nascimento`, `sexo`, `estado_civil`, dados de contato (`telefone`, `email`) e endereço completo (`endereco`, `numero`, `complemento`, `bairro`, `cep`, além do vínculo a `cidade`). A tabela `funcionario` agrega ainda dados sensíveis do ponto de vista trabalhista, como `salario`, `data_admissao`, `data_demissao` e `cnh`. A tabela `fornecedor` contém o `documento` (CPF/CNPJ) e dados de contato, e as tabelas auxiliares `fornecedor_email` e `fornecedor_telefone` armazenam múltiplos canais de contato.

A tabela abaixo consolida o inventário dos dados pessoais por entidade, requisito indispensável para o atendimento dos direitos do titular (acesso, correção, eliminação e portabilidade) previstos na LGPD.

| Entidade | Dados pessoais tratados | Base legal típica |
|---|---|---|
| `cliente` | nome, documento (CPF/CNPJ), RG/IE, data de nascimento, sexo, estado civil, telefone, e-mail, endereço completo | Execução de contrato (mensalidade) e cumprimento de obrigação legal/fiscal |
| `funcionario` | nome, CPF, RG/IE, data de nascimento, sexo, estado civil, telefone, e-mail, endereço, CNH, salário, datas de admissão/demissão | Execução de contrato de trabalho e obrigação legal |
| `fornecedor` (PF) | nome, documento (CPF/CNPJ), RG/IE, telefone, e-mail, endereço | Execução de contrato e obrigação legal/fiscal |
| `fornecedor_email` / `fornecedor_telefone` | endereços de e-mail e números de telefone de contato | Legítimo interesse / execução de contrato |
| `usuario` | nome, login, e-mail, vínculo a funcionário | Legítimo interesse (controle de acesso) |

| Código | Requisito | Critério de aceitação | Âncora técnica |
|---|---|---|---|
| RNF-LGPD-01 | O sistema deve manter inventário dos dados pessoais tratados. | Existe mapeamento dos campos pessoais por entidade (cliente, funcionário, fornecedor). | Colunas de dados pessoais em `cliente`, `funcionario`, `fornecedor`. |
| RNF-LGPD-02 | O acesso a dados pessoais deve ser restrito por perfil e autenticação. | Apenas usuários autenticados e autorizados acessam dados de clientes e funcionários. | Tabela `usuario` (`perfil ADMIN`/`OPERADOR`); RNF-SEG-01 a RNF-SEG-03. |
| RNF-LGPD-03 | Dados pessoais sensíveis não devem ser registrados em logs. | Logs não contêm CPF, salário, senha ou contato completo em texto puro. | Política de logging; tratamento de exceções sem dados pessoais. |
| RNF-LGPD-04 | O titular deve poder exercer o direito de acesso e correção dos seus dados. | É possível consultar e corrigir os dados pessoais de um titular identificado. | CRUD de `cliente`/`funcionario`/`fornecedor` com `updated_at`. |
| RNF-LGPD-05 | A eliminação de dados deve respeitar a retenção legal e a integridade referencial. | Dados sob obrigação fiscal são preservados pelo prazo legal; cadastros não referenciáveis podem ser inativados ou anonimizados. | Campo `ativo`; `ON DELETE RESTRICT`; retenção fiscal das notas. |
| RNF-LGPD-06 | O tráfego de dados pessoais deve ser cifrado em trânsito. | Comunicação que transporta dados pessoais ocorre sobre HTTPS/TLS. | Terminação TLS (RNF-SEG-04). |
| RNF-LGPD-07 | O sistema deve registrar a finalidade e a base legal do tratamento. | Cada categoria de dado pessoal possui finalidade e base legal documentadas. | Inventário desta subseção; documentação de privacidade. |

A conformidade com a LGPD apoia-se diretamente nos controles de segurança (subseção 10.1) e de auditoria (subseção 10.7): a autenticação e a autorização por perfil restringem quem acessa os dados pessoais; os carimbos `created_at`/`updated_at` e o campo `ativo` permitem rastrear e gerir o ciclo de vida do dado; e a cifragem em trânsito protege os dados durante a comunicação. Recomenda-se, em evolução futura, a adoção de mecanismos de anonimização ou pseudonimização para os cadastros que, embora não mais ativos, precisem ser mantidos por força de obrigação fiscal, de modo a minimizar a exposição de dados pessoais sem comprometer a integridade contábil exigida pelas notas e lançamentos financeiros.
