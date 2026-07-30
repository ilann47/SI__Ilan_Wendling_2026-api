> Links: [[core]] · [[organizacoes]] · [[membership]] · [[eventos]] · [[pedidos]]

# RBAC Contextual

## Objetivo

Autorizar operações tenant-aware pela combinação de Membership ativa, papel,
permissão e escopo, sem confiar no perfil legado ou em IDs enviados pelo cliente.

## Contexto

A implementação atual usa exclusivamente o escopo `ORGANIZACAO`. O papel de
sistema `ADMIN_ORGANIZACAO` reúne `organizations:admin`, `users:invite`,
`roles:grant`, `roles:revoke`, `facilities:manage`, `events:create` e as demais
permissões de gestão já migradas. `orders:create` integra Administrador, Gestor
e Organizador; escopo por evento permanece futuro.

## Fluxo (camadas da arquitetura)

```text
JwtAuthFilter -> Membership/Organizacao válidas -> RbacService -> permissões efetivas
Controller @PreAuthorize -> RbacGuard -> organização do principal + permissão no banco
RbacController -> RbacService -> AtribuicaoAcessoRepository -> PostgreSQL V10
```

## Endpoints (se houver)

| Método | Rota | Comportamento |
|---|---|---|
| GET | `/api/v1/me/permissions` | Permissões efetivas; exige token com tenant ativo. |
| POST | `/api/v1/organizations/{organizationId}/memberships/{membershipId}/role-assignments` | Concede papel conhecido no mesmo tenant. |
| DELETE | `/api/v1/organizations/{organizationId}/memberships/{membershipId}/role-assignments/{assignmentId}` | Revoga com motivo e `If-Match`. |

## Estrutura de Dados (DTOs, Entidades)

`Permissao` e `Papel` formam o catálogo. `AtribuicaoAcesso` liga papel e Membership
ao escopo organizacional, possui vigência, preserva revogação e usa `@Version`.

## Integrações externas (se houver)

Nenhuma. As permissões são resolvidas no PostgreSQL em cada requisição contextual.

## Tratamento de Erros

`401` para contexto inválido, `403` para ausência de permissão/escopo, `404` para
recurso invisível, `409` para duplicidade/lockout, `412` para versão divergente e
`428` para revogação sem `If-Match`.

## Testes (curl ou equivalente)

Há testes de bootstrap, isolamento do administrador global e entre tenants,
concessão/revogação imediata ou futura, alteração contextual de organização e
proteção do último administrador.

## Decisões Técnicas

- Permissões não são gravadas no JWT; revogações têm efeito na próxima requisição.
- `ROLE_ADMIN` global provisiona organização e somente a primeira Membership.
- O último administrador ativo não pode ser suspenso, encerrado ou revogado.
- Alterações que afetam esse invariante são serializadas por lock pessimista da organização.
- Recursos de outra organização são indistinguíveis de recursos inexistentes (`404`).
- Escopos de pátio/evento não são antecipados; criar um agregado novo usa escopo organizacional.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[membership]]
- [[auditoria]] — registra concessões e revogações com vigência, versões e motivo.
- [[instalacoes]] — criação de pátio exige `facilities:manage` no contexto ativo.
- [[eventos]] — criação exige `events:create`; Gestor e Organizador integram o catálogo.
- [[inventario]] — alocações exigem `inventory:manage`, concedida a Administrador da Organização e Gestor.
- [[produtos-estacionamento]] — criação exige `pricing:manage`, concedida também ao Organizador.
- [[pedidos]] — criação e leitura própria exigem `orders:create`, concedida a
  Administrador, Gestor e Organizador; `orders:manual-confirm` é restrita a
  Administrador e Gestor.

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-29 | Implementa RBAC contextual organizacional e remove administração global excessiva. |
| 2026-07-29 | Concessões e revogações passam a integrar a trilha transacional de auditoria. |
| 2026-07-29 | Catálogo recebe `facilities:manage` para o módulo de Instalações. |
| 2026-07-30 | Catálogo recebe Gestor, Organizador e `events:create`. |
| 2026-07-30 | Catálogo recebe `inventory:manage` para Administrador da Organização e Gestor. |
| 2026-07-30 | Catálogo recebe `pricing:manage` para Administrador, Gestor e Organizador. |
| 2026-07-30 | Catálogo recebe `orders:create` para Administrador, Gestor e Organizador. |
