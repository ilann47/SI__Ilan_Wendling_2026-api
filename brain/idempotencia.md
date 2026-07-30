> Links: [[core]] · [[eventos]] · [[organizacoes]]

# Idempotência

## Objetivo

Garantir que a repetição de um comando crítico produza um único efeito e devolva
o resultado original, sem aceitar a mesma chave para intenções diferentes.

## Contexto

A fundação entra com UC701 e é deliberadamente genérica. O primeiro consumidor
é [[eventos]]; outros comandos só aderem em suas histórias.

## Fluxo (camadas da arquitetura)

```text
tenant + ator + operação + chave
  -> pg_advisory_xact_lock
  -> consulta request_hash
  -> replay, conflito ou execução
  -> registro do recurso na mesma transação
```

## Endpoints (se houver)

Não possui endpoint próprio. `POST /api/v1/events` exige `Idempotency-Key`.

## Estrutura de Dados (DTOs, Entidades)

`requisicao_idempotente` conserva organização, ator, operação, chave, SHA-256 do
payload canônico e referência lógica ao resultado. A unicidade é garantida no banco.

## Integrações externas (se houver)

Nenhuma; utiliza lock transacional e persistência PostgreSQL.

## Tratamento de Erros

Chave ausente/inválida retorna `400`; mesma chave com hash diferente retorna
`409`; falha da operação reverte também o registro idempotente.

## Testes (curl ou equivalente)

Testes repetem UC701 com a mesma chave, verificam o mesmo ID e um único evento,
e rejeitam payload diferente.

## Decisões Técnicas

- O escopo inclui tenant, identidade e operação.
- Lock advisory serializa concorrência antes da consulta e da criação.
- O hash usa dados semanticamente normalizados, não os bytes brutos do JSON.
- Não há cache nem expiração automática nesta primeira fundação.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[eventos]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-30 | Implementa fundação PostgreSQL usada pela criação de Evento. |
