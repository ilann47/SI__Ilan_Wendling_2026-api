> Links: [[organizacoes]] · [[membership]] · [[rbac]] · [[auditoria]] · [[instalacoes]] · [[venues]] · [[eventos]] · [[inventario]] · [[produtos-estacionamento]] · [[reservas-inventario]] · [[pedidos]] · [[credenciais]] · [[acesso]] · [[idempotencia]] · [[outbox]] · [[bloqueios]] · [[dados-demo]] · [[unificacao-multitenant]] · [[testes-multitenancy]] · [[pagamentos-cadastrais]] · [[clientes]] · [[logistica]] · [[fornecedores]] · [[rh]] · [[conveniencia]] · [[estoque]] · [[compras]] · [[vendas-administrativas]] · [[ordens-servico]]

# Core do Estacionamento Kaneko

## Fonte oficial

A arquitetura normativa permanece em [docs/especificacao](../docs/especificacao/README.md), especialmente nos documentos 11 a 22. O estado de execução e seus bloqueios são mantidos em [[../docs/especificacao/21-status-implementacao|21-status-implementacao]]. O `brain` registra somente o estado implementado e as relações entre módulos; ele não redefine a arquitetura aprovada.

## Arquitetura

- Monólito modular em Spring Boot 4 e Java 25.
- Organização package-by-feature.
- PostgreSQL 17 com migrations Flyway incrementais.
- Hibernate configurado com `ddl-auto=validate`.
- APIs TARGET sob `/api/v1`, preservando as rotas legadas `/api`.
- Problem Details e `X-Trace-Id` como contrato de erros e correlação.

## Infraestrutura

- Banco relacional: PostgreSQL.
- Persistência: Spring Data JPA/Hibernate.
- Segurança atual: JWT HS256, RBAC contextual nas APIs `/api/v1` implementadas e perfis legados nas APIs `/api`.
- Documentação de API: springdoc OpenAPI.
- Testes de integração contra banco PostgreSQL separado.
- Actuator expõe publicamente apenas `health` e `info`; `metrics` e demais
  endpoints administrativos exigem JWT válido quando a segurança está ativa.

## Padrões globais

- Controllers chamam services, nunca repositories.
- A transação começa no application service.
- Cada módulo possui suas entidades, repositories, DTOs e endpoints.
- Regras críticas possuem defesa na aplicação e constraints no PostgreSQL.
- Funcionalidades enterprise são introduzidas incrementalmente e sem quebrar APIs legadas.

## Módulos implementados na evolução enterprise

- [[organizacoes]] — fundação do tenant empresarial.
- [[membership]] — vínculo da identidade global, descoberta e contexto de tenant.
- [[rbac]] — permissões efetivas por papel, Membership e escopo organizacional.
- [[auditoria]] — trilha transacional e imutável das mutações enterprise.
- [[instalacoes]] — topologia física tenant-aware e limites de capacidade.
- [[venues]] — unidades operacionais que localizam eventos no tempo e espaço.
- [[eventos]] — planejamento e ciclo de vida do agregado de evento.
- [[inventario]] — alocação temporal da capacidade dos pátios aos eventos.
- [[produtos-estacionamento]] — ofertas, quotas e lotes de preço comerciais.
- [[disponibilidade]] — projeção read-only do catálogo vendável vigente.
- [[reservas-inventario]] — hold temporal e atômico da capacidade vendável.
- [[pedidos]] — intenção comercial com snapshots, total e expiração.
- [[credenciais]] — direito por unidade e representação QR opaca versionada.
- [[acesso]] — decisão QR online, presença, sessão e ocupação de evento.
- [[idempotencia]] — execução única de comandos críticos por tenant e identidade.
- [[outbox]] — fatos de domínio persistidos atomicamente para publicação confiável.
- [[bloqueios]] — condições externas e decisões pendentes que limitam a entrega.
- [[dados-demo]] — cenário local opt-in criado pelos serviços reais do domínio.
- [[unificacao-multitenant]] — retrofit de ownership dos módulos empresariais permanentes.
- [[testes-multitenancy]] — fixtures de JWT contextual e asserções de integridade PostgreSQL.
- [[pagamentos-cadastrais]] — formas, condições e parcelas isoladas por organização.
- [[clientes]] — cadastro de clientes com privacidade e ownership organizacional.
- [[logistica]] — transportadoras, veículos de carga e vínculos de frota isolados por organização.
- [[fornecedores]] — fornecedores, contatos e referências comerciais do tenant.
- [[rh]] — cargos e funcionários tenant-aware com compatibilidade histórica controlada.
- [[conveniencia]] — categorias, marcas e unidades de medida isoladas por organização.
- [[compras]] — ordens e recebimentos físicos tenant-aware integrados ao estoque e fiscal.
- [[vendas-administrativas]] — venda comercial tenant-aware integrada ao estoque e recebíveis.

## Histórico

| Data | Ação |
|---|---|
| 2026-07-29 | Inicialização do brain e registro da História 1 de Organizações. |
| 2026-07-29 | Registro da História 2 de Membership e JWT contextual. |
| 2026-07-29 | Registro da História 3 de RBAC contextual. |
| 2026-07-29 | Registro da História 4 de auditoria append-only. |
| 2026-07-29 | Registro da História 5 de Instalações e criação de pátio tenant-aware. |
| 2026-07-29 | Registro da História 6 de Venue. |
| 2026-07-30 | Registro da História 7 de Evento, idempotência e outbox transacional. |
| 2026-07-30 | Registro da História 8 de alocação Evento–Pátio e proteção contra sobrealocação. |
| 2026-07-30 | Registro de UC707 com Produto de Estacionamento e quota comercial. |
| 2026-07-30 | Registro de UC708 com lotes de preço temporais e determinísticos. |
| 2026-07-30 | Registro da publicação transacional de produto com auditoria e outbox. |
| 2026-07-30 | Registro de UC702 com publicação versionada e checklist de prontidão. |
| 2026-07-30 | Registro da consulta de disponibilidade sem garantia de hold. |
| 2026-07-30 | Registro da abertura imediata e versionada de vendas do evento. |
| 2026-07-30 | Registro de UC709 com reserva atômica, expiração e liberação de saldo. |
| 2026-07-30 | Registro de UC710 até pedido aguardando pagamento e expiração. |
| 2026-07-30 | Registro da confirmação manual conciliável de pedido e inventário. |
| 2026-07-30 | Registro de UC711 com credencial ativa por unidade do pedido. |
| 2026-07-30 | Registro de UC712 com QR opaco, hash persistido e concorrência por ETag. |
| 2026-07-30 | Registro de UC713 com cancelamento compensatório e reembolso pendente. |
| 2026-08-01 | UC714 registrada como bloqueada externamente; UC715 confirmada como executável e independente do gateway. |
| 2026-08-01 | UC715/UC718 implementadas com check-in QR online e ocupação concorrente. |
| 2026-08-01 | UC717/UC719 implementadas com política explícita, check-out QR e encerramento da sessão. |
| 2026-08-01 | UC720 recebe encerramento operacional seguro; fechamento financeiro permanece pendente. |
| 2026-08-01 | Credencial recebe bloqueio operacional idempotente restrito ao Supervisor. |
| 2026-08-01 | Auditoria final consolida dependências que bloqueiam os recortes restantes da fase de eventos. |
| 2026-08-01 | Restringe métricas e endpoints administrativos do Actuator a clientes autenticados. |
| 2026-08-01 | Expõe o feed tenant-aware de tentativas de acesso sob `audit:read`. |
| 2026-08-01 | Distingue validação, check-in e check-out no agregado de tentativa. |
| 2026-08-02 | Centraliza bloqueios e condições objetivas de retomada da fase de eventos. |
| 2026-08-03 | Adiciona seed local idempotente do fluxo de eventos, desativado por padrão. |
| 2026-08-03 | Inicia Onda 0 da unificação multi-tenant dos módulos empresariais. |
| 2026-08-14 | Conclui a Onda 4 com Ordem de Compra e recebimentos na V63. |
| 2026-08-14 | Implementa Venda Administrativa da Onda 5 na V64. |
| 2026-08-03 | Adiciona a fundação reutilizável de testes tenant-aware e constraints PostgreSQL. |
| 2026-08-03 | Migra formas e condições de pagamento para ownership e RBAC contextual. |
| 2026-08-03 | Migra clientes e sua referência de condição de pagamento para tenancy real. |
| 2026-08-03 | Migra transportadoras e sua condição de pagamento para tenancy real. |
| 2026-08-03 | Migra fornecedores, contatos derivados e referências comerciais para tenancy real. |
| 2026-08-03 | Inicia a Onda 2 com o catálogo-base de conveniência tenant-aware na V50. |
| 2026-08-03 | Migra Cargo para ownership organizacional e RBAC contextual de RH. |
| 2026-08-03 | Migra Funcionário para ownership organizacional e preserva Usuário global. |
| 2026-08-03 | Valida o grafo legado de Fornecedor e bloqueia mudança direta de owner na V47. |
| 2026-08-03 | Migra Veículo de Frota e vínculo N:N para tenancy real na V48. |
| 2026-08-03 | Fecha owner imutável de Transportadora na V49. |
| 2026-08-03 | Torna imutável no PostgreSQL o ownership de Cargo e Funcionário. |
| 2026-08-03 | Completa o catálogo de conveniência tenant-aware com Produto e Serviço na V52. |
| 2026-08-03 | Migra ProdutoFornecedor para tenancy contextual e concorrência otimista na V53. |
| 2026-08-03 | Introduz Local, Saldo e Razão tenant-aware de Estoque na V54. |
| 2026-08-04 | Integra Nota de Entrada, recebimento e Conta a Pagar na V55. |
| 2026-08-04 | Integra Nota de Saída, baixa e Conta a Receber na V56. |
| 2026-08-04 | Integra Nota de Serviço e faturamento na V57. |
| 2026-08-04 | Inicia a operação tradicional tenant-aware com Veículo na V58. |
| 2026-08-04 | Migra Tarifa para tenancy contextual na V59. |
| 2026-08-04 | Migra Mensalista e seu faturamento na V60. |
| 2026-08-04 | Conclui o núcleo da operação tradicional tenant-aware na V61. |
| 2026-08-04 | Migra Despesa Avulsa e baixa financeira na V62. |
| 2026-08-04 | Consolida relatórios gerenciais tenant-aware sem nova migration. |
| 2026-08-04 | Completa auditoria das transições da movimentação tradicional. |
| 2026-08-04 | Amplia o seed local idempotente para cobrir perfis e todas as áreas funcionais. |
| 2026-08-14 | Implementa Ordem de Serviço tenant-aware e faturamento único pela Nota de Serviço na V65. |
