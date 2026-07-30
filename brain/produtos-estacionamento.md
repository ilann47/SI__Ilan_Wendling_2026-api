> Links: [[core]] · [[organizacoes]] · [[eventos]] · [[inventario]] · [[rbac]] · [[auditoria]] · [[idempotencia]]

# Produtos de Estacionamento

## Objetivo

Materializar a oferta vendável de estacionamento para evento e comprometer uma
quota da capacidade comercial de uma alocação, sem introduzir preço ou venda.

## Contexto

UC707 define `ProdutoEstacionamento` como a oferta de direito. “Oferta” não é
uma entidade adicional: criar ambas duplicaria responsabilidade e contrariaria
o DDD oficial. O produto nasce em `RASCUNHO`; publicação é comando posterior.

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
```

## Endpoints (se houver)

- `POST /api/v1/events/{eventoId}/parking-products`: cria produto em rascunho.

## Estrutura de Dados (DTOs, Entidades)

`ProdutoEstacionamento` possui organização, evento, alocação, nome, categoria,
direito, janela de acesso, quota, estado, benefícios/restrições normalizados,
versão e timestamps. O FK composto impede cruzamento de tenant ou evento.

## Integrações externas (se houver)

Não há. A criação em rascunho não publica outbox; o fato aprovado é o da
publicação futura.

## Tratamento de Erros

- `400`: chave/payload/janela/código/campo controlado inválido.
- `403`: contexto sem `pricing:manage`.
- `404`: evento ou alocação invisível no tenant/evento.
- `409`: idempotência divergente, nome duplicado ou quota excedida.

## Testes (curl ou equivalente)

Há testes de domínio, API, RBAC, tenancy, idempotência, quota cumulativa,
auditoria, OpenAPI e constraints PostgreSQL.

## Decisões Técnicas

- A quota é serializada pelo lock da alocação e somada no banco antes da criação.
- Benefícios e restrições usam tabelas e códigos normalizados, não JSON genérico.
- O direito inicial é `ESTACIONAMENTO_EVENTO`; políticas de uso/reentrada ficam
  no agregado aprovado de política/credencial.
- A categoria física de setor permanece sem validação enquanto setores não
  integrarem a entrega; o catálogo de categorias aprovado é validado no banco.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[eventos]]
- [[inventario]]
- [[rbac]]
- [[auditoria]]
- [[idempotencia]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-30 | Implementa UC707 com criação idempotente de produto em rascunho. |
