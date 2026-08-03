> Links: [[README]] · [[16-casos-de-uso-plataforma]] · [[18-permissoes-rbac]] · [[19-equipamentos-integracoes]] · [[20-roadmap-produto]] · [[22-unificacao-multitenant]] · [[relatorio-conformidade]]

# 21. Estado de Implementação e Dependências

## 21.1 Finalidade

Este documento registra o estado verificável dos casos de uso enterprise e
separa bloqueios externos das dependências internas. Uma história somente pode
ser marcada como `CONCLUIDA` quando código, migration, API, segurança, testes,
OpenAPI e documentação aplicáveis estiverem sincronizados.

Estados utilizados:

- `CONCLUIDA`: todos os critérios técnicos e funcionais do recorte aprovado
  foram satisfeitos;
- `EXECUTAVEL`: não existe dependência impeditiva para iniciar o próximo recorte
  vertical;
- `PARCIALMENTE_EXECUTAVEL`: há uma fundação interna útil e testável, mas parte
  do caso de uso depende de capacidade ainda indisponível;
- `BLOQUEADA_EXTERNAMENTE`: depende de provider, segredo, credencial,
  infraestrutura ou decisão comercial externa;
- `BLOQUEADA_POR_DEPENDENCIA_INTERNA`: depende de outro caso de uso ainda não
  implementado.

## 21.2 Matriz de continuidade

| História | Estado | Dependência | Parte concluída | Parte pendente | Condição para retomada |
|---|---|---|---|---|---|
| UC701–UC703 | `CONCLUIDA` | — | Fundação multiempresa do slice enterprise, eventos e pátio tenant-aware | Unificação dos módulos anteriores permanece na Onda 0 | Reabrir o slice somente por defeito; acompanhar retrofit no documento 22 |
| UC704 — Cadastrar setor | `CONCLUIDA` | — | Setor tenant-aware, taxonomia, capacidade serializada, auditoria, outbox e constraints | Coordenadas, alteração e bloqueio pertencem a recortes próprios | Reabrir somente por defeito ou mudança aprovada |
| UC705 — Cadastrar vagas | `CONCLUIDA` | — | Vaga tenant-aware, batch idempotente, capacidade serializada, conflitos parciais, auditoria, outbox e constraints | Coordenadas e mudanças de disponibilidade pertencem a recortes próprios | Reabrir somente por defeito ou mudança aprovada |
| UC706–UC713 | `CONCLUIDA` | — | Alocação multipátio até cancelamento compensatório de pedido, conforme relatório de conformidade | — | Reabrir somente por defeito ou mudança aprovada |
| UC714 — Reembolsar pagamento | `BLOQUEADA_EXTERNAMENTE` | Pagamento capturado real, gateway, credenciais sandbox, assinatura de webhook e política comercial | UC713 preserva `EM_REEMBOLSO`, bloqueia o direito e registra os efeitos compensatórios | Solicitação vinculada a captura, aprovação, execução, retorno, conciliação e restituição real | Provider e contrato escolhidos; credenciais/segredos sandbox disponíveis; alçadas, prazo, multa, crédito e reembolso parcial definidos |
| UC715 — Realizar check-in | `CONCLUIDA` | — | Validação QR sem consumo e check-in QR tenant-aware, tentativa terminal, consumo, presença, sessão de evento, ocupação concorrente, auditoria, outbox e idempotência | Placa/RFID, override, hardware e offline pertencem a contratos próprios | Reabrir somente por defeito ou mudança aprovada |
| UC716 — Check-in offline | `BLOQUEADA_POR_DEPENDENCIA_INTERNA` | UC715 e modelo canônico de decisão | QR seguro e contexto tenant existem | Pacote assinado, edge, sequência, armazenamento local e sincronização | Concluir UC715 e definir infraestrutura edge verificável |
| UC717 — Check-out | `CONCLUIDA` | — | Política explícita, saída QR idempotente, presença encerrada, credencial consumida/reativada e ocupação decrementada | Saída manual, placa/RFID, hardware e offline pertencem a contratos próprios | Reabrir somente por defeito ou mudança aprovada |
| UC718 — Abrir movimentação | `CONCLUIDA` | — | Sessão enterprise com origem `PEDIDO_EVENTO`, snapshots e vínculos tenant-aware aberta no check-in | Origens rotativo/mensalista pertencem à Onda 3 da unificação | Reabrir o agregado somente por defeito; implementar novas origens no contexto próprio |
| UC719 — Encerrar movimentação | `CONCLUIDA` | — | Sessão `PEDIDO_EVENTO` encerrada atomicamente no check-out | Tarifação rotativa tenant-aware pertence à Onda 3 | Preservar o fluxo de evento e integrar as demais origens sem reutilizar sua tarifa |
| UC720 — Fechar evento | `PARCIALMENTE_CONCLUIDA` | Fontes financeiras canônicas para a parte restante | Encerramento operacional versionado, bloqueio de ocupação/presença/sessão aberta e ressalva financeira auditável | Conciliação, aprovação, relatório de fechamento e finalização | Pagamento, reembolso, caixa, fiscal e repasse autênticos disponíveis |
| UC721 — DRE por evento | `BLOQUEADA_POR_DEPENDENCIA_INTERNA` | Fontes financeiras e fechamento | Relatórios legados existem | Linhagem econômica por evento | Disponibilizar fontes financeiras canônicas ou modo preliminar formal |
| UC722 — Documento fiscal | `BLOQUEADA_EXTERNAMENTE` | Provedor fiscal, certificado e contratos regulatórios | Registros fiscais legados permanecem preservados | Transmissão, protocolo, contingência e armazenamento fiscal seguro | Provider/ambiente/certificado e escopo fiscal definidos |
| UC723–UC724 — Compra e venda administrativa | `EXECUTAVEL` | Ondas 1 e 2 da unificação | Fluxos atuais de notas, estoque e financeiro existem | Ownership, razão de estoque, Ordem de Compra e Venda Administrativa | Concluir a Onda 0 e executar a migração aditiva definida no documento 22 |
| UC725 — Venda PDV | `BLOQUEADA_POR_DEPENDENCIA_INTERNA` | Caixa, pagamento transacional e razão de estoque | Cadastros legados de produto existem | Venda, recebimento, caixa, estoque e fiscal conciliáveis | Concluir pagamentos, estoque enterprise e UC728 |
| UC726–UC727 — Mensalista e rotativo | `PARCIALMENTE_EXECUTAVEL` | Onda 3 e políticas próprias | Serviços atuais de mensalista, tarifa e movimentação existem | Ownership, planos versionados, origens e integração financeira | Migrar o grafo operacional após os cadastros empresariais necessários |
| UC728 — Caixa | `BLOQUEADA_POR_DEPENDENCIA_INTERNA` | Pagamentos e PDV | Financeiro legado existe | Sessão de caixa, diferenças e conciliação | Concluir pagamento transacional e preparar PDV |
| UC729 — Escalas | `BLOQUEADA_POR_DEPENDENCIA_INTERNA` | Identidade laboral canônica e contrato de escala | Usuário, funcionário e evento existem isoladamente | Vínculo usuário–funcionário–colaborador, catálogo de função/qualificação, API e máquina de estados executável | Definir o vínculo canônico e os contratos de criação/publicação/execução sem reutilizar o colaborador legado como identidade autenticável |
| UC730 — Integração externa | `BLOQUEADA_EXTERNAMENTE` | Provider/contrato por categoria | Outbox transacional existe | Inbox, adapters, credenciais, retries e quarentena por integração | Escolher uma integração real e fornecer ambiente/credenciais de homologação |

## 21.3 Bloqueio formal da UC714

O código atual não possui pagamento transacional capturado. O pacote
`pagamento` AS-IS mantém somente formas e condições cadastrais. A confirmação
manual de pedido permanece evidência `PENDENTE_CONCILIACAO` e, por decisão
arquitetural, não representa `PagamentoAprovado`, captura ou liquidação de
gateway.

Por isso, enquanto as dependências externas não forem fornecidas:

1. nenhum pagamento capturado fictício será criado;
2. a confirmação manual não será reinterpretada como captura;
3. nenhum webhook sem assinatura será aceito;
4. nenhum reembolso será marcado como executado ou conciliado;
5. o pedido permanecerá em `EM_REEMBOLSO`, com credenciais bloqueadas, até que
   exista um processo financeiro autêntico.

## 21.4 Acesso online concluído e próxima história executável

A UC715 é independente da conclusão financeira da UC714. O check-in opera sobre
uma credencial já emitida e ativa, validando tenant, evento, pátio, janela,
representação e duplicidade. Pedidos em `EM_REEMBOLSO` já têm suas credenciais
bloqueadas pela UC713 e, portanto, serão recusados sem consultar gateway.

Esse recorte foi entregue nas migrations V28/V29 e no módulo `acesso`. A
alocação Evento–Pátio serializa a ocupação; duas credenciais disputando a última
vaga produzem uma autorização e uma recusa determinística. Barreira física e
modo offline permanecem fora do recorte até existirem adapters, identidade de
dispositivo e infraestrutura edge próprios.

As migrations V30/V31 tornam a política de reentrada explícita e implementam a
saída QR online. UC717 encerra presença, atualiza a credencial e decrementa a
ocupação; UC719 encerra a sessão `PEDIDO_EVENTO` no mesmo commit. A política não
possui default comercial: eventos legados permanecem sem política até
configuração explícita e não podem ser publicados nessa condição.

O encerramento operacional da UC720 valida sob locks presença, sessão e ocupação,
transiciona o evento para `OPERACAO_ENCERRADA` e impede novas admissões. A parte
financeira permanece explicitamente pendente, sem simular captura, reembolso,
conciliação ou DRE. A execução corrente inicia a Fundação da unificação
multi-tenant descrita no documento 22; quotas por canal, projeção por setor e
UC729 continuam dependentes de seus contratos específicos.

O endurecimento transversal de segurança restringe `/actuator/metrics` e os
demais endpoints administrativos a JWT válido. Somente `/actuator/health/**` e
`/actuator/info` permanecem públicos para probes sem credencial.

O bloqueio manual de credencial ativa também é executável sem gateway ou
hardware e foi concluído com `credentials:block` restrita ao Supervisor,
revogação transacional do QR e replay sem efeitos duplicados. Desbloqueio e
reemissão permanecem separados porque exigem política de alçada e substituição.

O feed `GET /api/v1/access-attempts` também foi concluído de forma independente:
usa cursor keyset e filtros de evento, decisão e motivo, sempre no tenant do JWT.
A leitura exige `audit:read`, concedida somente a Administrador da Organização e
Gestor enquanto os escopos ABAC operacionais não estiverem implementados. Assim,
`access:operate` não foi ampliada e nenhum segredo QR é retornado.

`POST /api/v1/access-validations` foi concluído sobre o mesmo agregado de
tentativa, agora discriminado como `VALIDACAO`, `CHECKIN` ou `CHECKOUT`. A
validação QR aplica as regras online, registra decisão, auditoria, outbox e
idempotência, mas responde `consumptionPerformed=false` e não altera credencial,
presença, sessão ou ocupação. `access:validate` é operacional e separada de
`audit:read` e `access:operate`.

## 21.5 Auditoria dos recortes remanescentes

Depois das entregas de setor, vaga, leitura própria de pedidos, bloqueio de
credencial, validação sem consumo e feed auditável de acesso, os candidatos
internos restantes foram confrontados novamente com código e baseline. Nenhum
deles possui hoje contrato suficiente para uma nova história vertical segura:

1. quota por canal não possui endpoint, catálogo canônico de canais nem regra
   que relacione o `canal` textual da reserva aos limites do produto;
2. ocupação por setor/categoria não pode ser derivada porque check-in e sessão
   guardam evento/pátio, mas não setor/vaga, e produto/alocação também não roteiam
   a entrada para um setor;
3. desbloqueio precisa impedir reativação de credencial bloqueada por
   cancelamento/fraude e definir alçada; reemissão precisa definir vínculo com a
   substituída e migrar a unicidade por unidade; troca de placa depende da
   política comercial aprovada;
4. abertura agendada de vendas ainda não possui persistência, cancelamento,
   reagendamento nem semântica multi-instância para um scheduler confiável;
5. consoles administrativos enterprise dependem de contratos de consulta de
   eventos, venues, pátios, produtos e alocações que a OpenAPI atual
   deliberadamente não publica.

Implementar os itens enumerados sem seus contratos ainda exigiria inventar regra
de negócio. Essa conclusão não se aplica à migração de ownership dos módulos
empresariais já existentes: ela foi autorizada como fundação transversal e deve
prosseguir pela matriz de [[22-unificacao-multitenant]]. Rotativo e mensalistas
permanecem contextos distintos de Evento, mas fazem parte do produto unificado.

## 21.6 Estado da unificação multi-tenant

A Onda 0 está `EM_EXECUCAO`. A matriz de ownership e a fundação reutilizável de
testes tenant-aware foram concluídas. A V38 migra formas, condições e parcelas de
pagamento: novas escritas recebem o tenant do JWT, nomes são únicos por
organização, referências de parcela usam FKs compostas e as URLs legadas exigem
`payments:read/manage`. Linhas históricas permanecem com owner nulo e invisíveis
às consultas tenant. A V39 mantém a unicidade anterior das linhas sem owner,
fecha o caminho `NULL → tenant` nas parcelas e limita consumers ainda globais a
catálogos históricos. A V40 migra Cliente com `customers:read/manage`, documento
único por organização, condição de pagamento composta e auditoria de privacidade.
Consumers ainda globais resolvem apenas clientes históricos. A V41 migra
Transportadora com `logistics:read/manage`, documento local ao tenant, condição
composta e auditoria de privacidade. Fornecedor e o vínculo de frota ainda
global resolvem somente transportadoras históricas. A V42 migra Fornecedor com
`suppliers:read/manage`, documento local, referências compostas, contatos filhos
preservados e auditoria de privacidade. Consumers de compras, fiscal, financeiro
e conveniência resolvem apenas fornecedores históricos até suas migrations;
nenhum dado histórico foi associado automaticamente. A V43 migra Cargo com nome
normalizado único por organização, `workforce:read/manage`, auditoria RH e
compatibilidade explícita: Funcionário global resolve somente Cargo sem owner.
Nenhum cargo histórico foi associado automaticamente.
