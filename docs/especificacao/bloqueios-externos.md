> Links: [[README]] · [[16-casos-de-uso-plataforma]] · [[17-apis-enterprise]] · [[19-equipamentos-integracoes]] · [[20-roadmap-produto]] · [[21-status-implementacao]] · [[22-unificacao-multitenant]] · [[relatorio-conformidade]]

# Registro Central de Bloqueios

## Finalidade

Este documento é a fonte operacional para capacidades que não podem ser
concluídas com segurança usando apenas o código e a infraestrutura atuais. Ele
não substitui o roadmap: detalha a condição objetiva que impede cada retomada.
Nenhum item bloqueado deve aparecer como ação executável no frontend.

## Matriz

| ID | História/UC | Status | Tipo de bloqueio | Dependência | Parte concluída | Parte pendente | Condição para retomada | Impacto |
|---|---|---|---|---|---|---|---|---|
| BLK-001 | UC710/UC714 — pagamento e reembolso | `BLOQUEADA_EXTERNAMENTE` | Provider e credenciais | Gateway escolhido; credenciais sandbox; Pix/cartão aceitos; assinatura e autenticação de webhook | Pedido, hold, confirmação manual conciliável e cancelamento compensatório preservam valores e direitos | Intenção, autorização/captura, webhook, reembolso total/parcial, retry e conciliação | Provider e contrato aprovados; ambiente sandbox funcional; segredo/certificado de webhook fornecido por canal seguro | Impede pagamento eletrônico, restituição real e fechamento financeiro |
| BLK-002 | UC713/UC714 — política comercial | `BLOQUEADA_POR_DECISAO_DE_NEGOCIO` | Política e alçada | Cancelamento, prazos, multas, créditos, reembolso parcial, aprovação e chargeback | Pedido confirmado pode terminar em `EM_REEMBOLSO`; credencial/QR são inutilizados e inventário é compensado | Elegibilidade, cálculo, aprovação e desfecho financeiro | Política versionada aprovada, inclusive pagamento aprovado após expiração do inventário | Impede oferecer reembolso ou prometer prazo/valor ao usuário |
| BLK-003 | UC716 — operação offline | `BLOQUEADA_POR_INFRAESTRUTURA` | Edge e segurança de dispositivo | Modelo de dispositivo; manifesto assinado; criptografia; relógio; expiração; armazenamento e sincronização | QR online, decisão canônica, idempotência e trilha de tentativas existem | Decisão local, sequência, pacote, múltiplos dispositivos e reconciliação de conflitos | Arquitetura edge verificável, identidade/mTLS, formato assinado e política fail-open/fail-closed aprovados | Operação depende de conectividade com a API central |
| BLK-004 | UC720/UC721 — fechamento financeiro e DRE | `BLOQUEADA_POR_DECISAO_DE_NEGOCIO` | Fontes contábeis | Centros de custo, competência, repasses, split, taxas, impostos e critérios de conciliação | Encerramento operacional seguro produz ressalva `FONTES_FINANCEIRAS_PENDENTES` | Conciliação, aprovação, fechamento imutável, DRE e finalização | Pagamentos, reembolsos, caixa e fontes contábeis canônicas disponíveis; regras de rateio aprovadas | Evento não pode ser finalizado financeiramente nem ter DRE confiável |
| BLK-005 | UC722 — fiscal externo | `BLOQUEADA_EXTERNAMENTE` | Provider, certificado e legislação | Escopo NF-e/NFC-e/NFS-e, certificado, ambiente, município/SEFAZ e contingência | Documentos fiscais legados funcionam como registros internos | Transmissão, protocolo, rejeição, cancelamento e armazenamento fiscal seguro | Provedor e ambiente escolhidos; certificado e responsabilidade fiscal disponibilizados com segurança | Nenhum documento interno equivale a autorização fiscal externa |
| BLK-006 | Cancelamento integral do evento | `BLOQUEADA_POR_DECISAO_DE_NEGOCIO` | Política e compensações | Estados canceláveis, política, comunicação, pedidos, credenciais, reembolso e inventário | Cancelamento individual de pedido é compensatório e idempotente | Orquestração do evento, impactos e retomada/reversão | Política de cancelamento de evento e matriz de compensações aprovadas | Não existe ação segura de cancelamento global no frontend |
| BLK-007 | Override/admissão manual | `BLOQUEADA_POR_DECISAO_DE_NEGOCIO` | Alçada e ABAC | Aprovador, motivo, evidência, turno, evento, pátio, faixa e limites de exceção | Toda decisão automática é auditada; Supervisor e permissões operacionais existem | Comando de override, vínculo com decisão recusada e responsabilidade contextual | Contrato de aprovação/ABAC e motivos canônicos definidos | Recusas não podem ser convertidas manualmente em autorização |
| BLK-008 | Reemissão, desbloqueio e troca de veículo | `BLOQUEADA_POR_DECISAO_DE_NEGOCIO` | Política de credencial | Origem do bloqueio, alçada, vínculo substituta/substituída, unicidade e política de placa | Emissão, QR e bloqueio operacional/compensatório estão concluídos | Desbloqueio seguro, reemissão e atualização de placa | Estados e alçadas aprovados; distinção entre fraude/cancelamento/bloqueio operacional e migração de unicidade definida | Frontend oferece somente emissão, QR e bloqueio reais |
| BLK-009 | UC729 — escalas | `BLOQUEADA_POR_DECISAO_DE_NEGOCIO` | Identidade laboral e contrato | Vínculo usuário–funcionário–colaborador; função, qualificação, turno, substituição e aceite | Identidades legadas e RBAC organizacional existem separadamente | Agregado de escala, máquina de estados, APIs e escopo operacional | Modelo canônico de identidade laboral e contratos de publicação/execução aprovados | ABAC por evento/pátio/faixa/turno não pode ser aplicado |
| BLK-010 | UC730 — integrações externas | `BLOQUEADA_EXTERNAMENTE` | Contrato e homologação | Integração real escolhida, autenticação, credenciais, rate limits, schemas e SLA | Outbox transacional preserva fatos internos | Inbox, adapters/ACL, entrega, retry, quarentena e replay | Selecionar uma integração concreta e fornecer documentação e ambiente de homologação | Nenhum parceiro, equipamento ou webhook externo pode ser ativado |
| BLK-011 | Equipamentos e barreiras | `BLOQUEADA_POR_INFRAESTRUTURA` | Hardware e identidade | Fabricante/SDK, cancela/leitor/LPR/RFID, site controller, telemetria e política de falha | API responde explicitamente `barrierCommandRequested=false` | Provisionamento, comando físico, confirmação e contingência | Equipamento alvo, protocolo, ambiente de teste e política operacional aprovados | Check-in/check-out registram o acesso, mas não acionam barreira física |
| BLK-012 | Quota por canal e ocupação por setor/categoria | `BLOQUEADA_POR_DECISAO_DE_NEGOCIO` | Modelo comercial/físico | Catálogo canônico de canais; roteamento produto–setor–vaga | Quota total, capacidade por pátio e ocupação da alocação são concorrentes e seguras | Limites por canal e projeção física granular | Regras de canal e vínculo de roteamento aprovados com contratos de leitura | Frontend não pode exibir granularidade que o banco não registra |
| BLK-013 | Abertura agendada de vendas | `BLOQUEADA_POR_INFRAESTRUTURA` | Scheduler confiável | Persistência, cancelamento/reagendamento, eleição multi-instância e recuperação | Abertura imediata versionada está concluída | Execução futura exatamente uma vez e observável | Contrato do agendamento e estratégia transacional multi-instância definidos | Frontend aceita somente abertura imediata |
| BLK-014 | Cutover dos dados históricos multi-tenant | `BLOQUEADA_POR_DECISAO_DE_DADOS` | Mapa explícito de ownership | Organização proprietária de cada grafo V1/V6/V7; política para conflitos e registros órfãos | Inventário, estratégia nullable e ordem de migration estão definidos | Backfill, validação de constraints e aplicação final de `NOT NULL` | Mapa registro/grafo → organização aprovado e relatório de conflitos sem ambiguidades | Não impede adicionar colunas, adaptar código ou migrar novas escritas; impede somente backfill definitivo e cutover |

## Regras de produto enquanto houver bloqueio

1. Confirmação manual permanece `PENDENTE_CONCILIACAO`; não é pagamento de gateway.
2. Nenhum token, segredo ou certificado é armazenado neste repositório.
3. Frontend pode explicar o bloqueio, mas não oferece formulário ou botão ativo.
4. Retomadas exigem atualização desta matriz, do [[21-status-implementacao]] e
   dos testes de contrato antes de expor a funcionalidade.

## Evidências principais

- `Pagamento` AS-IS contém apenas cadastros; não há captura transacional.
- `EventoService` rejeita abertura futura de vendas até existir scheduler.
- `AcessoService` e `CheckoutService` não enviam comando físico de barreira.
- `CredencialAcesso` não distingue a origem do bloqueio nem modela sucessora.
- `TentativaAcesso` registra evento/pátio/faixa, mas não há atribuição ABAC de turno.

## Histórico

| Data | Ação |
|---|---|
| 2026-08-02 | Centraliza bloqueios externos, de infraestrutura e de decisão de negócio da fase de eventos. |
| 2026-08-03 | Registra que somente o cutover histórico depende do mapa explícito de ownership; a migração incremental pode prosseguir. |
