> Links: [[core]] · [[clientes]] · [[conveniencia]] · [[fiscal-financeiro]] · [[auditoria]] · [[idempotencia]] · [[outbox]] · [[rbac]]

# Ordens de Servico

## Objetivo

Controlar a contratacao e a execucao de servicos por organizacao sem confundir a ordem operacional com a Nota de Servico que documenta o faturamento.

## Contexto

A V65 implementa o agregado `OrdemServico`, itens com snapshot do servico e o ciclo `RASCUNHO -> EM_EXECUCAO -> CONCLUIDA`. Cancelamento e permitido antes da conclusao. Estados finais sao terminais.

## Fluxo (camadas da arquitetura)

`OrdemServicoController` valida contexto e versao, `OrdemServicoService` resolve cliente e servicos no tenant, persiste o agregado, registra [[auditoria]] e publica fatos pela [[outbox]]. A [[fiscal-financeiro|Nota de Servico]] pode referenciar uma ordem concluida e gera a Conta a Receber na emissao.

## Endpoints

- `GET/POST /api/v1/service-orders`
- `GET/PUT /api/v1/service-orders/{id}`
- `POST /api/v1/service-orders/{id}/start`
- `POST /api/v1/service-orders/{id}/completion`
- `POST /api/v1/service-orders/{id}/cancellation`
- `GET /api/v1/service-orders/{id}/documents`: nota e recebíveis; seções sem permissão fiscal/financeira retornam `null`.
- `POST /api/v1/service-orders/{id}/service-note`: `Idempotency-Key` obrigatório. Recebe apenas número, modelo/série, data, condição/forma de pagamento e alíquota; cliente e valores vêm da OS. Retorna nota `PENDENTE`, sem cobrança.

A listagem aceita `numero` (trecho literal, sem diferenciar maiúsculas e
minúsculas e sem espaços das extremidades) e `status` (`RASCUNHO`,
`EM_EXECUCAO`, `CONCLUIDA` ou `CANCELADA`). Organização e filtros são aplicados
antes da paginação; o total filtrado não é perdido quando a página está vazia.
`%` e `_` não são curingas. Um estado desconhecido retorna 400.

## Estrutura de Dados (DTOs, Entidades)

- `OrdemServico`: cliente, numero, estado, datas, moeda, totais, versao e itens.
- `ItemOrdemServico`: servico, descricao congelada, quantidade, precos e desconto.
- `NotaServico.ordemServico`: vinculo fiscal opcional e unico.

## Integracoes externas

Nenhuma. A nota permanece registro fiscal interno; transmissao fiscal externa continua bloqueada.

## Tratamento de Erros

Relacionamentos cross-tenant retornam 404. Estado invalido ou ordem ja faturada retorna 409. Ausencia de `If-Match` retorna 428 e versao divergente retorna 412.

## Testes

Os testes cobrem isolamento, idempotencia, concorrencia otimista, transicoes, cancelamento e faturamento unico por Nota de Servico.

`OrdemServicoConsultaTest` verifica normalização e total da página filtrada,
inclusive vazia, e a assinatura legada sem filtros.
`ConsultasComerciaisTenantApiTest` usa API e PostgreSQL para validar número,
situação, ordenação, paginação, isolamento e caracteres literais.

## Decisoes Tecnicas

- A ordem nao gera titulo financeiro na conclusao; a emissao da Nota de Servico e a origem contabil do recebivel.
- Uma ordem concluida admite no maximo uma Nota de Servico.
- Preparação e emissão são etapas distintas. A emissão interna existente continua gerando títulos, sem representar autorização fiscal externa.
- Geração serializa por OS e chave idempotente; a emissão, edição, exclusão e cancelamento bloqueiam a nota. Cancelamento bloqueia títulos em ordem de ID antes de verificar recebimentos.
- Numero e unico dentro da organizacao; owner e imutavel no PostgreSQL.
- O overload `listar(organizacaoId, pageable)` é preservado. A consulta filtrada
  mantém paginação de IDs e carregamento posterior dos relacionamentos com
  `EntityGraph`, sem paginar a coleção de itens em memória.

## Modulos relacionados

[[clientes]], [[conveniencia]], [[fiscal-financeiro]], [[auditoria]], [[idempotencia]], [[outbox]] e [[rbac]].

## Historico

| Data | Acao |
|---|---|
| 2026-09-12 | Conecta documentos/recebíveis à OS, preparação server-side idempotente e exclusão mútua das transições fiscais. |
| 2026-09-12 | Acrescenta filtros reais de número/situação antes da paginação, total preservado e testes de contrato de consulta com compatibilidade legada. |
| 2026-08-14 | Implementa ordem tenant-aware, execucao, conclusao e faturamento vinculado na V65. |
