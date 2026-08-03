> Links: [[core]] · [[unificacao-multitenant]] · [[organizacoes]] · [[membership]] · [[rbac]] · [[auditoria]] · [[pagamentos-cadastrais]] · [[fornecedores]]

# Logística

## Objetivo

Administrar transportadoras da cadeia de abastecimento com ownership explícito,
sem misturar cadastros entre organizações.

## Contexto

A V41 migra `transportadora` como primeira raiz do módulo de Logística. Frota e
o vínculo N:N continuam históricos nesta fatia e somente resolvem
transportadoras sem owner até sua migration própria.

## Fluxo (camadas da arquitetura)

```text
JWT contextual + logistics:read/manage
  -> TransportadoraController
  -> TransportadoraService(organizacaoId, comando)
  -> TransportadoraRepository por id + organizacaoId
  -> PostgreSQL: owner, documento local e condição do mesmo tenant
  -> auditoria transacional
```

## Endpoints (se houver)

- `GET /api/transportadoras` e `GET /api/transportadoras/{id}`: `logistics:read`.
- `POST /api/transportadoras`: `logistics:manage`.
- `PUT /api/transportadoras/{id}`: `logistics:manage`.
- `DELETE /api/transportadoras/{id}`: inativação lógica com `logistics:manage`.

## Estrutura de Dados (DTOs, Entidades)

- `Transportadora.organizacaoId`: owner obtido exclusivamente do principal.
- Documento: normalizado pela aplicação e único dentro da organização.
- `cidade_id`: catálogo global.
- `condicao_pagamento_id`: referência opcional, obrigatoriamente do mesmo tenant.
- DTO não expõe `organizationId` e rejeita campos desconhecidos.

## Integrações externas (se houver)

Não há CT-e, rastreamento ou integração externa implementada. O cadastro é
interno e prepara o vínculo futuro com frota e fornecedores.

## Tratamento de Erros

- `401`: autenticação ausente ou contexto inválido.
- `403`: token global ou Membership sem permissão.
- `404`: transportadora/condição inexistente ou de outro tenant.
- `400`: payload inválido ou tentativa de controlar o tenant.
- `409`: documento duplicado dentro da organização.

## Testes (curl ou equivalente)

`TransportadoraTenantApiTest`, `TransportadoraTenantConstraintTest` e
`TransportadoraMigrationCompatibilityTest` cobrem segregação A/B, RBAC,
payload hostil, auditoria, FKs, unicidade e upgrade V40→V41.

## Decisões Técnicas

- Linhas históricas permanecem com owner nulo, preservadas e invisíveis na API.
- Um trigger transitório impede `NULL -> tenant` na condição de pagamento.
- `Fornecedor` e `TransportadoraVeiculoFrota` continuam resolvendo apenas
  transportadoras históricas até suas migrations, evitando referência cruzada.
- `ADMIN_ORGANIZACAO` recebe `logistics:read/manage`.

## Módulos relacionados

- [[pagamentos-cadastrais]]
- [[fornecedores]]
- [[auditoria]]
- [[testes-multitenancy]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-08-03 | V41 migra Transportadora para tenancy contextual e preserva dados históricos. |
