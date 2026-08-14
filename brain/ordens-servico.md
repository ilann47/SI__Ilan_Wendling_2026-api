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

## Decisoes Tecnicas

- A ordem nao gera titulo financeiro na conclusao; a emissao da Nota de Servico e a origem contabil do recebivel.
- Uma ordem concluida admite no maximo uma Nota de Servico.
- Numero e unico dentro da organizacao; owner e imutavel no PostgreSQL.

## Modulos relacionados

[[clientes]], [[conveniencia]], [[fiscal-financeiro]], [[auditoria]], [[idempotencia]], [[outbox]] e [[rbac]].

## Historico

| Data | Acao |
|---|---|
| 2026-08-14 | Implementa ordem tenant-aware, execucao, conclusao e faturamento vinculado na V65. |
