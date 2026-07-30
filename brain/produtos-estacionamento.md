> Links: [[core]] · [[organizacoes]] · [[eventos]] · [[inventario]] · [[disponibilidade]] · [[reservas-inventario]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Produtos de Estacionamento

## Objetivo

Materializar e publicar a oferta vendável de estacionamento para evento,
comprometendo uma quota da capacidade comercial e configurando seu preço.

## Contexto

UC707 define `ProdutoEstacionamento` como a oferta de direito. “Oferta” não é
uma entidade adicional: criar ambas duplicaria responsabilidade e contrariaria
o DDD oficial. O produto nasce em `RASCUNHO`; UC708 configura lotes antes do
comando explícito de publicação.

## Fluxo (camadas da arquitetura)

```text
POST /api/v1/events/{eventoId}/parking-products + Idempotency-Key
  -> JWT contextual + pricing:manage
  -> ProdutoEstacionamentoService
  -> evento configurável + alocação tenant-aware com lock pessimista
  -> janela + nome + soma das quotas
  -> ProdutoEstacionamentoRepository -> PostgreSQL V17
  -> AuditoriaService + resultado idempotente
  -> 201 + Location + ETag

POST /api/v1/parking-products/{produtoId}/publication + If-Match
  -> JWT contextual + pricing:manage
  -> ProdutoEstacionamentoService
  -> produto tenant-aware em RASCUNHO com lock pessimista
  -> versão esperada + ao menos um lote de preço
  -> transição para PUBLICADO
  -> AuditoriaService + OutboxService na mesma transação
  -> 200 + ETag

POST /api/v1/parking-products/{produtoId}/price-tiers + Idempotency-Key
  -> JWT contextual + pricing:manage
  -> LotePrecoService
  -> produto tenant-aware em RASCUNHO com lock pessimista
  -> moeda + quota + nome + período/prioridade sem ambiguidade
  -> LotePrecoRepository -> PostgreSQL V18
  -> AuditoriaService + resultado idempotente
  -> 201 + Location + ETag
```

## Endpoints (se houver)

- `POST /api/v1/events/{eventoId}/parking-products`: cria produto em rascunho.
- `POST /api/v1/parking-products/{produtoId}/price-tiers`: cria lote de preço.
- `POST /api/v1/parking-products/{produtoId}/publication`: publica produto.

## Estrutura de Dados (DTOs, Entidades)

`ProdutoEstacionamento` possui organização, evento, alocação, nome, categoria,
direito, janela de acesso, quota, contadores reservado/vendido/bloqueado,
estado, benefícios/restrições normalizados, versão e timestamps. O FK composto
impede cruzamento de tenant ou evento.

`LotePreco` possui produto, organização, nome, preço decimal, moeda, janela
comercial, quantidade, prioridade, versão e timestamps. A janela define a
ativação/agendamento; não existe estado adicional de lote na baseline.

## Integrações externas (se houver)

Não há chamada externa. A publicação grava `ProdutoEstacionamentoPublicado` na
outbox PostgreSQL, sem antecipar dispatcher ou broker.

## Tratamento de Erros

- `400`: chave/payload/janela/código/campo controlado inválido.
- `403`: contexto sem `pricing:manage`.
- `404`: evento ou alocação invisível no tenant/evento.
- `409`: idempotência divergente, nome duplicado, quota excedida ou período
  ambíguo na mesma prioridade; publicação sem lote ou transição inválida.
- `428`: publicação sem `If-Match`.
- `412`: versão esperada divergente ou conflito otimista.

## Testes (curl ou equivalente)

Há testes de domínio, API, RBAC, tenancy, idempotência, quota cumulativa,
preço gratuito, moeda, sobreposição determinística, publicação, concorrência,
auditoria, outbox, OpenAPI e constraints PostgreSQL.

## Decisões Técnicas

- A quota é serializada pelo lock da alocação e somada no banco antes da criação.
- Benefícios e restrições usam tabelas e códigos normalizados, não JSON genérico.
- O direito inicial é `ESTACIONAMENTO_EVENTO`; políticas de uso/reentrada ficam
  no agregado aprovado de política/credencial.
- A categoria física de setor permanece sem validação enquanto setores não
  integrarem a entrega; o catálogo de categorias aprovado é validado no banco.
- O lote usa a moeda da organização e quantidade limitada à quota do produto.
- O lock pessimista do produto serializa criações; a aplicação e trigger
  PostgreSQL rejeitam sobreposição na mesma prioridade.
- Sobreposição com prioridades diferentes é válida e fornece seleção
  determinística; a janela comercial representa o agendamento.
- Publicação é comando explícito, requer lote e usa exclusivamente `@Version`
  como precondição e origem do `ETag`.
- Auditoria e outbox participam da mesma transação da mudança para `PUBLICADO`.
- [[reservas-inventario]] bloqueia o produto e altera seu contador reservado;
  a versão JPA e constraints defendem o saldo contra concorrência e corrupção.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[eventos]]
- [[inventario]]
- [[rbac]]
- [[auditoria]]
- [[idempotencia]]
- [[outbox]]
- [[disponibilidade]]
- [[reservas-inventario]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-30 | Implementa UC707 com criação idempotente de produto em rascunho. |
| 2026-07-30 | Implementa UC708 com lote de preço temporal e quantitativo. |
| 2026-07-30 | Implementa publicação versionada do produto com auditoria e outbox. |
| 2026-07-30 | Fornece produto e lote vigentes à projeção de [[disponibilidade]]. |
| 2026-07-30 | Passa a manter contadores protegidos para UC709. |
