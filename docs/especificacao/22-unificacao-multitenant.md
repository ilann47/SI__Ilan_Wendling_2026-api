> Links: [[README]] · [[12-dominio-ddd]] · [[14-modelagem-dominio]] · [[16-casos-de-uso-plataforma]] · [[18-permissoes-rbac]] · [[20-roadmap-produto]] · [[21-status-implementacao]] · [[relatorio-conformidade]]

# 22. Unificação dos Módulos Empresariais e Multi-Tenancy

## 1. Introdução

### 1.1 Finalidade

Este documento formaliza a evolução dos módulos empresariais existentes para o
mesmo modelo multiempresa já utilizado por Organização, Membership, RBAC,
Instalações, Eventos, Inventário, Pedidos, Credenciais e Acesso. Ele corrige a
interpretação de que os módulos anteriores seriam temporários: Fiscal,
Financeiro, Conveniência, Pessoas, Fornecedores, Logística, RH e Operação de
Pátio são capacidades permanentes do produto.

### 1.2 Escopo

Estão no escopo:

- ownership explícito por organização;
- isolamento no controller, service, repository e PostgreSQL;
- RBAC contextual por domínio;
- migração aditiva de dados e contratos;
- preservação das rotas `/api/*` durante a transição;
- integração entre operação, conveniência, fiscal, financeiro e eventos;
- especificação futura de Ordem de Compra e Ordem de Serviço como agregados
  distintos de notas fiscais.

Não estão no escopo desta fundação: associar automaticamente dados históricos a
uma organização, simular transmissão fiscal, pagamento capturado, gateway,
conciliação externa, equipamento ou operação offline.

### 1.3 Produto e empresa

O Kaneko é uma plataforma de gestão de estacionamentos operada por múltiplas
organizações. Cada organização pode administrar eventos, pátios, clientes,
fornecedores, produtos, serviços, estoque, documentos internos e obrigações
financeiras. A organização é a fronteira primária de segurança e ownership; o
evento é uma modalidade operacional interna a essa fronteira, e não um sistema
separado.

## 2. Análise do negócio

### 2.1 Problemas atuais

O código AS-IS possui dois modelos de ownership coexistentes. Os módulos
enterprise implementados após `V8__organizacao_tenant.sql` derivam a organização
do JWT contextual. As tabelas originadas em `V1__init_schema.sql`, `V6` e `V7`
permanecem globais, com unicidades e consultas sem organização. Isso produz os
seguintes problemas:

1. um usuário contextual pode consultar dados empresariais de outra organização;
2. o nome da organização no frontend não garante que a coleção exibida pertença a ela;
3. documentos iguais de empresas diferentes colidem em constraints globais;
4. referências podem cruzar clientes, fornecedores, produtos e documentos;
5. eventos não conseguem consolidar conveniência, fiscal e financeiro com
   linhagem confiável;
6. ocultar menus reduz funcionalidade, mas não corrige autorização.

### 2.2 Envolvidos

- administrador da plataforma: provisiona organizações sem operar seus dados;
- administrador da organização: governa cadastros, acessos e configurações;
- gestor: acompanha operação, vendas, estoque e resultado;
- compras: administra fornecedores, ordens e recebimentos;
- operador de estoque/conveniência: mantém catálogo, saldo e movimentos;
- operador de estacionamento: executa entrada, saída e atendimento;
- fiscal tributário: administra documentos e integrações fiscais;
- financeiro: administra títulos, baixas, conciliação e fechamento;
- RH: administra pessoas, cargos e vínculos laborais;
- auditor: consulta trilhas sem modificar origens;
- cliente: acessa somente seus próprios recursos quando houver identidade pública.

### 2.3 Usuários e responsabilidades

| Papel | Responsabilidade multi-tenant | Restrições principais |
|---|---|---|
| Administrador da plataforma | Provisionar tenant e primeiro vínculo | Não acessa dados empresariais sem contexto autorizado |
| Administrador da organização | Administrar todos os domínios internos | Não atravessa organização |
| Gestor | Operação, catálogo, estoque e relatórios | Sem administração de segurança por padrão |
| Compras | Fornecedores, ordens e recebimento | Sem baixa financeira incompatível com segregação |
| Estoque/Conveniência | Produto, serviço, saldo e PDV | Sem alterar documentos confirmados |
| Operador | Entrada, saída e movimentação | Sem preço, fiscal ou baixa financeira |
| Fiscal tributário | Documento fiscal interno/externo | Sem alterar estoque fora do workflow |
| Financeiro | Contas, baixas, conciliação e fechamento | Sem reescrever origem comercial/fiscal |
| RH | Cargo, funcionário e vínculo | Sem acessar credenciais de clientes |
| Auditor | Leitura de trilhas e relatórios | Sem comandos mutáveis |

## 3. DER de ownership

### 3.1 Relacionamentos principais TARGET

```mermaid
erDiagram
    ORGANIZACAO ||--o{ MEMBRO_ORGANIZACAO : possui
    USUARIO ||--o{ MEMBRO_ORGANIZACAO : participa
    ORGANIZACAO ||--o{ CARGO : possui
    ORGANIZACAO ||--o{ FUNCIONARIO : emprega
    MEMBRO_ORGANIZACAO ||--o| FUNCIONARIO : representa
    ORGANIZACAO ||--o{ FORMA_PAGAMENTO : configura
    ORGANIZACAO ||--o{ CONDICAO_PAGAMENTO : configura
    CONDICAO_PAGAMENTO ||--|{ PARCELA_CONDICAO : compoe
    ORGANIZACAO ||--o{ CLIENTE : atende
    ORGANIZACAO ||--o{ FORNECEDOR : contrata
    ORGANIZACAO ||--o{ TRANSPORTADORA : contrata
    TRANSPORTADORA ||--o{ VEICULO_FROTA : possui
    ORGANIZACAO ||--o{ CATEGORIA : classifica
    ORGANIZACAO ||--o{ MARCA : classifica
    ORGANIZACAO ||--o{ UNIDADE_MEDIDA : utiliza
    ORGANIZACAO ||--o{ PRODUTO : comercializa
    ORGANIZACAO ||--o{ SERVICO : presta
    PRODUTO }o--o{ FORNECEDOR : fornecido_por
    ORGANIZACAO ||--o{ PATIO : opera
    CLIENTE ||--o{ VEICULO : possui
    PATIO ||--o{ TARIFA : pratica
    CLIENTE ||--o{ MENSALISTA : contrata
    VEICULO ||--o{ MOVIMENTACAO : realiza
    PATIO ||--o{ MOVIMENTACAO : recebe
    ORGANIZACAO ||--o{ ORDEM_COMPRA : emite
    FORNECEDOR ||--o{ ORDEM_COMPRA : recebe
    ORDEM_COMPRA ||--|{ ITEM_ORDEM_COMPRA : contem
    ORDEM_COMPRA ||--o{ RECEBIMENTO : origina
    RECEBIMENTO ||--o{ NOTA_ENTRADA : documenta
    NOTA_ENTRADA ||--|{ ITEM_NOTA_ENTRADA : contem
    NOTA_ENTRADA ||--o{ CONTA_PAGAR : gera
    ORGANIZACAO ||--o{ VENDA_ADMINISTRATIVA : realiza
    CLIENTE ||--o{ VENDA_ADMINISTRATIVA : compra
    VENDA_ADMINISTRATIVA ||--o{ NOTA_SAIDA : documenta
    NOTA_SAIDA ||--|{ ITEM_NOTA_SAIDA : contem
    NOTA_SAIDA ||--o{ CONTA_RECEBER : gera
    ORGANIZACAO ||--o{ ORDEM_SERVICO : executa
    CLIENTE ||--o{ ORDEM_SERVICO : solicita
    ORDEM_SERVICO ||--|{ ITEM_ORDEM_SERVICO : contem
    ORDEM_SERVICO ||--o{ NOTA_SERVICO : documenta
    NOTA_SERVICO ||--o{ CONTA_RECEBER : gera
    ORGANIZACAO ||--o{ EVENTO : promove
    EVENTO ||--o{ PEDIDO : recebe
    PEDIDO ||--o{ CREDENCIAL_ACESSO : concede
    CREDENCIAL_ACESSO ||--o{ SESSAO_ESTACIONAMENTO : utiliza
```

Ordem de Compra, Recebimento, Venda Administrativa e Ordem de Serviço aparecem
como entidades TARGET. Elas ainda não existem no código e exigem ADR, máquina de
estados e contrato próprios antes de migration.

### 3.2 Classes de ownership

| Classe | Regra | Exemplos |
|---|---|---|
| Global | Sem `organizacao_id`; compartilhado deliberadamente | `usuario`, `pais`, `estado`, `cidade`, `permissao`, `papel` |
| Raiz tenant | `organizacao_id` direto, imutável e obrigatório para novos registros | cliente, fornecedor, produto, serviço, documento, conta, movimento |
| Filho tenant | Owner inequivocamente derivado do pai; carrega `organizacao_id` quando precisa validar múltiplos pais | contato deriva do fornecedor; parcela, item fiscal e vínculo N:N carregam owner |
| Compatibilidade histórica | `organizacao_id` opcional somente para linhas preexistentes | tabelas migradas enquanto o backfill não foi decidido |

## 4. Requisitos funcionais da unificação

| ID | Requisito |
|---|---|
| RF-MT-001 | Derivar a organização exclusivamente do principal contextual validado. |
| RF-MT-002 | Listar, buscar, criar, alterar e inativar registros somente no tenant ativo. |
| RF-MT-003 | Responder 404 para ID inexistente ou pertencente a outro tenant. |
| RF-MT-004 | Rejeitar `organizationId` enviado pelo cliente em payloads empresariais. |
| RF-MT-005 | Permitir identificadores iguais em organizações distintas quando a regra é local ao tenant. |
| RF-MT-006 | Impedir no banco qualquer referência empresarial entre organizações. |
| RF-MT-007 | Preservar linhas históricas sem owner até reconciliação explícita e auditável. |
| RF-MT-008 | Manter URLs compatíveis enquanto controller, service e repository passam a ser contextuais. |
| RF-MT-009 | Auditar criação, alteração, inativação e transições materiais. |
| RF-MT-010 | Integrar compra, estoque, fiscal e financeiro pela mesma origem rastreável. |
| RF-MT-011 | Integrar venda/serviço, fiscal e contas a receber pela mesma origem rastreável. |
| RF-MT-012 | Consolidar evento, operação, conveniência e financeiro sem confundir seus agregados. |

Processos obrigatórios:

- selecionar tenant e resolver permissões;
- reconciliar ownership histórico;
- registrar compra e recebimento;
- confirmar nota de entrada e gerar efeitos de estoque/financeiro;
- registrar venda administrativa e seus efeitos;
- executar ordem de serviço e faturamento;
- operar estacionamento de evento, rotativo e mensalista por origens explícitas;
- fechar operação e produzir relatórios filtrados pela organização/evento.

## 5. Casos de uso transversais

### 5.1 Regras de negócio

- RN-MT-001: todo comando empresarial exige Membership ativa e permissão efetiva.
- RN-MT-002: o tenant do JWT não pode ser substituído pelo cliente.
- RN-MT-003: toda busca por ID empresarial combina `id` e `organizacao_id`.
- RN-MT-004: constraints únicas empresariais incluem `organizacao_id`.
- RN-MT-005: relações críticas usam FK composta com `organizacao_id`.
- RN-MT-006: linha histórica sem owner não aparece numa consulta contextual.
- RN-MT-007: backfill exige escolha explícita de organização, ator, motivo e auditoria.
- RN-MT-008: documento confirmado não muda de organização nem de origem.
- RN-MT-009: `Usuario` permanece global; vínculo laboral pertence à Membership.
- RN-MT-010: geografia permanece global e somente registros ativos podem ser referenciados.

### 5.2 UC731 — Manter cadastro empresarial no tenant ativo

**Atores:** usuário contextual com permissão do domínio.
**Pré-condição:** organização e Membership ativas.
**Pós-condição:** registro criado/alterado com ownership e auditoria consistentes.
**Ativação:** usuário acessa uma tela de cadastro empresarial.

**Fluxo principal:**

1. O frontend envia JWT contextual sem `organizationId` no corpo.
2. O filtro valida usuário, Membership, versão e organização.
3. O controller exige permissão contextual.
4. O service usa `principal.organizacaoId()` como argumento obrigatório.
5. O repository filtra por ID e organização.
6. O PostgreSQL valida unicidade e FKs compostas.
7. O service registra auditoria e devolve DTO.

**Fluxos alternativos:** listar com paginação; alterar com versão esperada;
inativar preservando histórico.

**Exceções:** E1 token global: 403; E2 tenant divergente: 404; E3 referência
cruzada: 404 na aplicação e violação de FK no banco; E4 duplicidade local: 409;
E5 payload tenta controlar tenant: 400.

### 5.3 UC732 — Reconciliar registro histórico sem organização

**Atores:** administrador autorizado de migração e auditor.
**Pré-condição:** registro possui `organizacao_id IS NULL`; destino está ativo.
**Pós-condição:** registro e seu grafo compatível recebem owner único e auditável.

**Fluxo principal:** identifica o grafo; simula conflitos; valida referências;
aplica owner em ordem transacional; registra ator, motivo, antes/depois e lote.

**Exceções:** E1 owner ambíguo: nenhuma alteração; E2 dependência em outro owner:
rejeição; E3 conflito de unicidade no destino: relatório sem backfill parcial.

Esse caso de uso é TARGET. Nenhum endpoint ou job correspondente existe no AS-IS.

### 5.4 UC733 — Executar rota compatível com contexto organizacional

**Atores:** consumidor existente autenticado.
**Pré-condição:** JWT contextual válido.
**Pós-condição:** a URL anterior produz o mesmo contrato funcional sobre dados
do tenant, sem caminho de consulta global.

O token global somente pode acessar uma superfície temporária de reconciliação
explicitamente autorizada; não pode executar operação empresarial normal.

## 6. Protótipos funcionais

O shell deve conservar os grupos Eventos, Operação, Fiscal, Financeiro,
Cadastros, Conveniência, Logística, RH e Sistema. Cada item é filtrado por
permissão contextual e protegido também na rota direta.

Padrões obrigatórios de tela:

- listagem: tenant visível, paginação, filtros, vazio e erro distintos;
- cadastro/edição: nenhuma entrada de tenant, referências somente do tenant;
- detalhe: origem, status, versão, timestamps e ações permitidas;
- exclusão/inativação: confirmação e motivo quando auditável;
- relatórios: organização implícita e evento/pátio como filtros internos;
- reconciliação histórica futura: simulação, conflitos, lote e confirmação forte.

As telas atuais em `frontend/src/resources` são reutilizáveis, mas seus endpoints
não são considerados seguros até a conclusão da respectiva história backend.

## 7. Dicionário de dados da migração

### 7.1 Campos transversais

| Campo | Tipo | Obrigatório | Regra | Observações |
|---|---|---:|---|---|
| `organizacao_id` | `BIGINT` | Novos registros: sim | FK `organizacao(id)`; imutável | Inicialmente opcional para preservar histórico |
| `version` | `BIGINT` | Sim quando agregado mutável | valor não negativo | Origem do ETag/If-Match |
| `created_at` | `TIMESTAMPTZ` | Sim | definido na criação | Auditoria técnica |
| `updated_at` | `TIMESTAMPTZ` | Sim | atualizado em mutação | Não substitui auditoria de domínio |
| `ativo/status` | domínio existente | Conforme entidade | transições explícitas | Exclusão lógica quando já prevista |

### 7.2 Matriz de ownership AS-IS × TARGET

| Domínio/tabelas | AS-IS | TARGET | Dependência/constraint principal | Onda |
|---|---|---|---|---:|
| `pais`, `estado`, `cidade` | Global | Global | unicidades geográficas permanecem globais | 0 |
| `usuario` | Global | Global | login/e-mail globais; identidade não recebe tenant | 0 |
| `organizacao`, `membro_organizacao` | Tenant foundation | Preservar | fonte do contexto | 0 |
| `permissao`, `papel`, `papel_permissao` | Global estrutural | Preservar/ampliar | catálogo global, atribuição contextual | 0 |
| `cargo`, `funcionario` | Global | Tenant direto | cargo e CPF únicos por organização; funcionário liga à Membership | 1 |
| `forma_pagamento` | Global | Tenant direto | nome único por organização | 1 |
| `condicao_pagamento` | Global | Tenant direto | nome único por organização | 1 |
| `parcela_condicao_pagamento` | Filho global | Filho tenant direto | FK composta para condição e forma do tenant | 1 |
| `cliente` | Global | Tenant direto | documento único por organização | 1 |
| `fornecedor` | Global | Tenant direto | documento, condição e transportadora do tenant | 1 |
| `fornecedor_email`, `fornecedor_telefone` | Filho global | Filho tenant derivado | FK para fornecedor é ownership inequívoco; não duplicar owner sem necessidade | 1 |
| `transportadora`, `veiculo_frota` | Global | Tenant direto | documento/placa únicos por organização | 1 |
| `transportadora_veiculo_frota` | Vínculo global | Filho tenant direto | ambas as pontas no mesmo tenant | 1 |
| `categoria`, `marca`, `unidade_medida` | Global | Tenant direto | nome/sigla únicos por organização | 2 |
| `produto`, `servico` | Global | Tenant direto | códigos/nomes e referências locais ao tenant | 2 |
| `produto_fornecedor` | Vínculo global | Filho tenant direto | produto e fornecedor no mesmo tenant | 2 |
| estoque embutido em `produto.quantidade` | Saldo mutável | Razão de estoque tenant | todo delta possui origem e local | 2/4 |
| `patio` | Misto; tenant opcional | Tenant direto | eliminar novos registros sem owner | 3 |
| `veiculo`, `tarifa`, `mensalista` | Global | Tenant direto | cliente/pátio/veículo coerentes por tenant | 3 |
| `movimentacao` | Global | Tenant direto | pátio, veículo, tarifa e mensalista no tenant | 3 |
| `colaborador` | Global de pátio | Tenant direto | vínculo laboral canônico via Membership | 3 |
| `ordem_compra`, itens, `recebimento` | Inexistentes | Novos agregados tenant | ADR e máquina de estados antes do DDL | 4 |
| `nota_entrada`, `item_nota_entrada` | Global | Raiz/filho tenant | fornecedor, produto e condição do tenant | 4 |
| `conta_pagar` | Global | Tenant direto | origem fiscal/ordem no mesmo tenant | 4/6 |
| venda administrativa e itens | Inexistentes como agregado | Novo agregado tenant | contrato antes do DDL | 5 |
| `nota_saida`, `item_nota_saida` | Global | Raiz/filho tenant | cliente, produto e condição do tenant | 5 |
| `ordem_servico` e itens | Inexistentes | Novo agregado tenant | ADR e máquina de estados antes do DDL | 5 |
| `nota_servico` | Global | Tenant direto | cliente, serviço e origem no mesmo tenant | 5 |
| `conta_receber` | Global | Tenant direto | origem comercial/fiscal no mesmo tenant | 5/6 |
| `contas_pagar_avulsa` | Global | Tenant direto | fornecedor/forma no tenant quando presentes | 6 |
| relatórios legados | Consultas globais | Projeções tenant/evento | nenhuma agregação sem filtro de owner | 6 |
| eventos, inventário, credenciais e acesso | Tenant direto | Preservar/integrar | FKs compostas já existentes | transversal |
| `comprador_pedido` | Owner derivado do pedido | Filho tenant derivado | PK/FK com pedido é suficiente; consultas partem do agregado | transversal |
| `item_pedido` | Owner do pedido, referências escalares sem FKs | Filho tenant com integridade reforçada | adicionar owner carrier ou FKs equivalentes para produto/alocação/lote | transversal |

### 7.3 Estratégia física

Cada conjunto será migrado em quatro passos separados quando necessário:

1. coluna opcional, FK simples e índice por organização;
2. adaptação da aplicação para exigir tenant em novos registros;
3. ferramenta/lote explícito de backfill com relatório de conflitos;
4. FK composta, unicidade tenant-aware e `NOT NULL` após reconciliação completa.

Índices únicos globais antigos somente serão removidos quando o índice equivalente
por organização estiver ativo e o modo compatível não depender deles. Rollback é
lógico: desativar uso do novo contrato preservando colunas e dados já escritos.

## 8. Diagrama de classes técnico

```mermaid
classDiagram
    class UsuarioAutenticado {
      +Long userId
      +Long organizacaoId
      +Long membershipId
      +boolean possuiOrganizacaoAtiva()
    }
    class RbacGuard {
      +hasPermissionInActiveContext(permission, authentication) boolean
    }
    class TenantOwnedEntity {
      +Long organizacaoId
      +Long version
    }
    class ControllerEmpresarial
    class ApplicationServiceEmpresarial
    class RepositoryTenantAware
    class RegistroAuditoria

    UsuarioAutenticado --> RbacGuard : contexto
    ControllerEmpresarial --> UsuarioAutenticado : deriva tenant
    ControllerEmpresarial --> RbacGuard : autoriza
    ControllerEmpresarial --> ApplicationServiceEmpresarial
    ApplicationServiceEmpresarial --> RepositoryTenantAware
    ApplicationServiceEmpresarial --> RegistroAuditoria
    RepositoryTenantAware --> TenantOwnedEntity
```

`TenantOwnedEntity` representa o contrato conceitual. A implementação deve manter
campos explícitos nas entidades e não introduzir filtro Hibernate global que possa
ser esquecido, desabilitado ou ocultar queries administrativas de migração.

## 9. Diagramas de sequência

### 9.1 Consulta contextual

```mermaid
sequenceDiagram
    actor U as Usuário
    participant F as JwtAuthFilter
    participant C as Controller
    participant G as RbacGuard
    participant S as Service
    participant R as Repository
    participant DB as PostgreSQL
    U->>F: JWT contextual
    F->>F: valida Membership, versão e organização
    F->>C: UsuarioAutenticado
    C->>G: permissão no contexto ativo
    G-->>C: autorizado
    C->>S: listar(organizacaoId, filtros)
    S->>R: consulta tenant-aware
    R->>DB: WHERE organizacao_id = ?
    DB-->>U: dados somente do tenant
```

### 9.2 Comando com referência empresarial

```mermaid
sequenceDiagram
    actor U as Usuário
    participant C as Controller
    participant S as Service
    participant R as Repositories
    participant DB as PostgreSQL
    participant A as Auditoria
    U->>C: payload sem organizationId
    C->>S: criar(principal.organizacaoId, request)
    S->>R: buscar referências por id + organização
    R-->>S: referências válidas ou vazio
    S->>DB: INSERT com organizacao_id
    DB->>DB: valida FK composta e unicidade
    S->>A: ator, tenant, antes/depois
    S-->>U: DTO + Location/ETag
```

## 10. Requisitos não funcionais

### 10.1 Segurança

- isolamento horizontal obrigatório em todas as operações empresariais;
- 401 para contexto JWT inválido, 403 para permissão ausente e 404 para recurso
  ausente ou fora do tenant;
- tenant nunca controlado pelo cliente;
- constraints de banco como segunda linha de defesa;
- nenhuma credencial ou token em log, seed ou auditoria.

### 10.2 Performance

- índices iniciados por `organizacao_id` para filtros dominantes;
- paginação em coleções; keyset para feeds volumosos;
- validação de referências em lote em processos com múltiplos itens;
- evitar N+1 em documentos e relatórios.

### 10.3 Disponibilidade e migração

- migrations aditivas e compatíveis com rolling deployment quando aplicável;
- nenhuma alteração de migration aplicada;
- backfill reiniciável, auditável e sem efeitos parciais;
- ausência de owner não pode derrubar a aplicação nem aparecer no tenant errado.

### 10.4 Usabilidade

- shell único, sem esconder capacidades permanentes;
- estados de carregamento, vazio, erro, conflito e acesso negado distintos;
- fluxo móvel adequado para operação e desktop para gestão.

### 10.5 Escalabilidade

- modelo coluna por organização no banco compartilhado;
- consultas previsíveis e índices tenant-first;
- sem banco ou schema por tenant nesta etapa.

### 10.6 Backup e recuperação

- backup inclui ownership, auditoria e lotes de migração;
- restauração deve preservar integridade das FKs compostas;
- rollback de aplicação não remove colunas ou dados já migrados.

### 10.7 Auditoria e LGPD

- mutações materiais registram ator, organização, motivo, trace ID e estados;
- acesso administrativo a dados históricos sem owner é excepcional e auditado;
- relatórios e exportações respeitam tenant e minimização de dados pessoais.

### 10.8 Verificação obrigatória por módulo

Cada recorte migrado possui teste de API tenant com segurança real, teste de
constraint PostgreSQL e teste de upgrade de schema. A matriz mínima comprova:

- anônimo 401, token global 403 e papel insuficiente 403;
- tenant B recebe 404 para IDs de A e sua listagem não contém A;
- body/header não alteram o contexto;
- referência A → pai B falha na aplicação e na FK específica;
- chave duplicada em A falha, enquanto a mesma chave em A e B é permitida;
- linha histórica null permanece intacta e invisível a A/B;
- auditoria registra organização, ator, recurso e trace sem segredo;
- Flyway migra banco limpo e upgrade V37 → latest; Hibernate `validate` inicia.

Helpers de JWT/RBAC devem usar os endpoints reais de Membership e seleção de
organização. Assertions de banco verificam SQLSTATE e nome da constraint, não
apenas uma `DataIntegrityViolationException` genérica.

## Plano de refatoração executável

1. **Onda 0:** consolidar ownership, RBAC, estratégia de migration e testes-base.
2. **Onda 1:** formas/condições de pagamento; clientes; fornecedores; RH e logística.
3. **Onda 2:** catálogo de conveniência e razão de estoque.
4. **Onda 3:** operação de estacionamento com origens evento, rotativo e mensalista.
5. **Onda 4:** ADR de Ordem de Compra; ordem, recebimento, nota e contas a pagar.
6. **Onda 5:** venda administrativa; ADR de Ordem de Serviço; notas e contas a receber.
7. **Onda 6:** baixas, conciliação, despesas, relatórios e fechamento por evento.
8. **Onda 7:** consolidação de frontend, permissões e E2E ponta a ponta.

Cada história deve entregar verticalmente migration, entidade, repository,
service, RBAC, controller, OpenAPI, frontend, auditoria, testes e `brain`. Nenhuma
onda autoriza transmissão fiscal, pagamento real ou integração externa sem as
dependências registradas em [[bloqueios-externos]].

### 10.9 Estado de execução

Em 2026-08-03, a V38 concluiu a primeira fatia da Onda 1: formas, condições e
parcelas de pagamento agora possuem ownership aditivo, unicidade normalizada por
organização, RBAC contextual e proteção de FK composta dentro do agregado. O
backfill e o `NOT NULL` permanecem condicionados ao mapeamento explícito dos
dados históricos. Consumers fiscais e financeiros serão endurecidos nas ondas
próprias, sem remover o catálogo já migrado.
