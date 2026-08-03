> Links: [[core]] · [[unificacao-multitenant]] · [[organizacoes]] · [[membership]] · [[rbac]] · [[auditoria]] · [[pagamentos-cadastrais]] · [[logistica]]

# Fornecedores

## Objetivo

Administrar pessoas físicas ou jurídicas que abastecem a operação e a
conveniência, com contatos, condição comercial e transportadora padrão isolados
pela organização ativa.

## Contexto

A V42 migra `fornecedor` como raiz tenant direta. `fornecedor_email` e
`fornecedor_telefone` continuam filhos com ownership derivado inequivocamente da
raiz, sem duplicar `organizacao_id`.

## Fluxo (camadas da arquitetura)

```text
JWT contextual + suppliers:read/manage
  -> FornecedorController
  -> FornecedorService(organizacaoId, comando)
  -> referências de condição/transportadora no mesmo tenant
  -> FornecedorRepository por id + organizacaoId
  -> constraints e trigger PostgreSQL
  -> auditoria de privacidade
```

## Endpoints (se houver)

- `GET /api/fornecedores` e `GET /api/fornecedores/{id}`: `suppliers:read`.
- `POST /api/fornecedores`: `suppliers:manage`.
- `PUT /api/fornecedores/{id}`: `suppliers:manage`.
- `DELETE /api/fornecedores/{id}`: inativação lógica com `suppliers:manage`.

## Estrutura de Dados (DTOs, Entidades)

- `Fornecedor.organizacaoId`: owner derivado do JWT contextual.
- Documento: normalizado e único dentro da organização.
- Condição de pagamento e transportadora: referências opcionais do mesmo tenant.
- Cidade: catálogo global.
- E-mails e telefones: filhos do agregado; no máximo um principal em cada coleção.
- DTO fechado contra campos desconhecidos e sem controle externo do tenant.

## Integrações externas (se houver)

Não há integração externa. O agregado é referência para compras, documentos e
contas internas, que permanecem restritos a fornecedores históricos até suas
migrations próprias.

## Tratamento de Erros

- `401`: autenticação ausente ou contexto inválido.
- `403`: token global ou Membership sem permissão.
- `404`: fornecedor, condição ou transportadora de outro tenant/inexistente.
- `400`: payload inválido ou campo desconhecido.
- `409`: documento duplicado ou múltiplos contatos principais.

## Testes (curl ou equivalente)

`FornecedorTenantApiTest`, `FornecedorTenantConstraintTest` e
`FornecedorMigrationCompatibilityTest` cobrem A/B, RBAC, referências cruzadas,
payload hostil, auditoria sem contatos, constraints e upgrade V41→V42.

## Decisões Técnicas

- Dados históricos permanecem sem owner, preservados e invisíveis na API tenant.
- Trigger transitório fecha `NULL -> tenant` para condição e transportadora.
- Nota de Entrada, contas a pagar e Produto–Fornecedor resolvem somente
  fornecedores históricos até migrarem seus próprios agregados.
- A auditoria omite documento, endereço, e-mail e telefone dos snapshots.
- `ADMIN_ORGANIZACAO` recebe `suppliers:read/manage`.

## Módulos relacionados

- [[pagamentos-cadastrais]]
- [[logistica]]
- [[auditoria]]
- [[testes-multitenancy]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-08-03 | V42 migra Fornecedor e preserva o grafo histórico de contatos e referências. |
