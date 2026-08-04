> Links: [[README]] · [[16-casos-de-uso-plataforma]] · [[18-permissoes-rbac]] · [[19-equipamentos-integracoes]] · [[20-roadmap-produto]] · [[22-unificacao-multitenant]] · [[relatorio-conformidade]]

# 21. Estado de Implementação e Dependências

> Atualização 2026-08-04: a V55 tornou Nota de Entrada e Conta a Pagar
> tenant-aware e conectou a confirmação ao razão de estoque V54.

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

A V44 complementa Fornecedor sem alterar a V42: normaliza documento também na
unicidade do PostgreSQL, impede que Nota de Entrada, contas a pagar e
Produto–Fornecedor globais apontem para fornecedor tenant-aware, bloqueia
backfill parcial do grafo e fecha campos desconhecidos nos contatos. A auditoria
deixa de persistir nome e limite de crédito nos snapshots. O rollout desta etapa
é operacionalmente **sem overlap**: todas as instâncias antigas devem ser
drenadas antes de habilitar escrita tenant-aware, pois a versão antiga não conhece
o filtro de organização.

A V45 migra Funcionário para ownership organizacional, CPF normalizado único por
tenant, Cargo do mesmo owner, RBAC `workforce:read/manage` e auditoria RH com
snapshot mínimo. Funcionários históricos permanecem sem owner; `UsuarioService`
e constraints temporárias permitem ao Usuário global somente esse histórico.
`condicao_pagamento` não integra o modelo de Funcionário e não foi adicionada.

A V46 complementa a fronteira de RH sem alterar migrations anteriores: recusa no
upgrade qualquer grafo Cargo–Funcionário com owners divergentes e torna fisicamente
imutável `organizacao_id` de Cargo e Funcionário. O backfill histórico permanece
bloqueado até UC732 oferecer operação administrativa explícita, auditável e
transacional; nenhuma associação automática é realizada.

A V47 complementa Fornecedor sem alterar V42/V44: antes de instalar o guard de
owner, aborta o upgrade se algum dos quatro consumers globais já apontar para
fornecedor tenant-aware ou se condição de pagamento/transportadora tiver owner
divergente. `organizacao_id` de Fornecedor torna-se fisicamente imutável, inclusive
no caminho `NULL -> tenant`; a reconciliação permanece reservada à UC732. O rollout
continua coordenado e sem overlap de binários antigos.

A V48 conclui a frota logística tenant-aware. `veiculo_frota` e
`transportadora_veiculo_frota` recebem owner imutável, placa/par únicos por
organização e FKs compostas para as duas pontas. As rotas preservadas usam
`logistics:read/manage`, auditam mutações e mantêm o histórico sem owner invisível.

A V49 torna `transportadora.organizacao_id` incondicionalmente imutável, inclusive
nos caminhos `NULL → tenant`, tenant A → B e tenant → `NULL`. Os guards específicos
da V48 permanecem instalados. O rollout V48/V49 exige drenagem total dos binários
anteriores; reconciliação e backfill continuam reservados à UC732.

A V50 inicia a Onda 2 com Categoria, Marca e Unidade de Medida tenant-aware. As
rotas existentes derivam owner do JWT e exigem `catalog:read/manage`; nomes e
siglas são únicos de forma normalizada por organização, mutações usam auditoria
`CONFIGURACAO` e o histórico sem owner permanece invisível. Produto continua
global e, temporariamente, somente aceita esses três pais também históricos.
Por isso, V50 e a próxima fatia de Produto devem ser implantadas de forma
coordenada antes de habilitar o frontend tenant-aware do catálogo.

A V51 endurece a V50 sem modificá-la: adiciona `@Version`, `ETag` e `If-Match`
obrigatório às três raízes, torna também o nome de Unidade único de forma
normalizada e instala reverse guards contra backfill de pais referenciados por
Produto. V50/V51 não são publicáveis isoladamente e o frontend permanece
desativado até Produto ser migrado na V52.

A V52 migra Produto e Serviço para ownership organizacional, versionamento
otimista e RBAC `catalog:read/manage`. Produto exige Marca, Unidade e Categoria
opcional do mesmo tenant; código de barras e nome de Serviço possuem unicidade
normalizada por organização e conjunto histórico separado. Notas de entrada e
saída, Nota de Serviço e relatório de estoque mínimo continuam
globais e resolvem somente registros históricos. `produto.quantidade` permanece
compatibilidade legada, sem ledger novo. V50/V51/V52/V53 compõem um único rollout.
Marca, Unidade e Categoria precisam estar ativas na criação ou alteração de
Produto. O saldo legado usa `@Version`: confirmações fiscais concorrentes falham
com `409` e retry explícito, preservando o rollback atômico de nota, estoque e
títulos.

A V53 migra ProdutoFornecedor para o contexto organizacional. Owner e pontas do
par são imutáveis e FKs compostas garantem Produto, Fornecedor e vínculo no mesmo
tenant. A unicidade histórica do par é preservada; POST sobre par inativo reativa
a mesma linha e uma disputa concorrente resulta em `201/409`. GET usa
`catalog:read`; mutações usam `catalog:manage`, auditoria `CONFIGURACAO`, `ETag`
e `If-Match` em PUT/DELETE. Registros históricos continuam invisíveis à API
contextual. V50/V51/V52/V53 formam um rollout backend coordenado.

A V54 introduz Local de Estoque, Saldo por Produto/Local e Movimento append-only
como fundação tenant-aware. O razão é canônico para produtos da organização;
`produto.quantidade` fica congelado em zero nesse recorte e permanece somente
como compatibilidade histórica. Ajuste e compensação são idempotentes, auditados
e publicados pela outbox. Locks por Produto/saldo impedem quantidade negativa e
o evento de limiar ocorre apenas no cruzamento agregado. V50 a V54 compõem o
rollout backend coordenado do catálogo e estoque tenant-aware.

O hardening da V54 acrescenta constraint triggers diferidos que, no fim de cada
transação, exigem razão iniciado em zero, continuidade por ID e igualdade do
último saldo posterior com `saldo_estoque.quantidade`. Compensações são validadas
antes do insert; Produto com saldo não zero não pode ser inativado. PUT/DELETE de
Local serializam contra movimentações, idempotência manual inclui o ator e os
payloads de outbox/auditoria usam allowlist explícita. Esses guards preservam a
atualização conjunta Saldo+Movimento do service e rejeitam escrita SQL parcial.
