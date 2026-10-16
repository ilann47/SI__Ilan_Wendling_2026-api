> Links: [[core]] · [[unificacao-multitenant]] · [[membership]] · [[rbac]] · [[organizacoes]]

# Testes Multi-Tenancy

## Objetivo

Padronizar a prova de isolamento organizacional, autenticação contextual e
integridade referencial em cada fatia vertical da unificação.

## Contexto

Os testes anteriores repetiam a criação de usuário, organização, Membership e
troca de JWT. A Onda 0 centraliza somente a infraestrutura reutilizável; os
testes de comportamento continuam pertencendo ao módulo que protegem.

## Fluxo (camadas da arquitetura)

```text
TenantTestSupport
  -> POST /api/auth/login
  -> POST /api/v1/organizations/{id}/memberships
  -> POST /api/v1/me/active-organization
  -> JWT contextual real
  -> endpoint do módulo sob teste
```

## Endpoints (se houver)

O suporte consome os endpoints reais de login, criação de Membership, seleção de
organização e atribuição de papel. Ele não publica endpoints.

## Estrutura de Dados (DTOs, Entidades)

- `TenantFixture`: organização, usuário, Membership, token global e contextual.
- `TenantApiIntegrationTest`: composição de Spring Boot, MockMvc, transação e segurança ativa.
- `TenantTestSupport`: criação dos atores e contextos usados pelos módulos.
- `PostgresConstraintAssertions`: valida SQLSTATE e nome exato da constraint.

## Integrações externas (se houver)

PostgreSQL local de testes, com Flyway e Hibernate `validate` executados pelo
contexto Spring Boot.

## Tratamento de Erros

O suporte falha imediatamente quando login, Membership, seleção ou papel não
retornam o status esperado. Violações de banco são aceitas apenas quando o
SQLSTATE e a constraint correspondem ao cenário.

## Testes (curl ou equivalente)

Executar `./mvnw -Dtest=TenantApiIntegrationTestTest,PostgresConstraintAssertionsTest test`
contra uma base PostgreSQL isolada. Cada retrofit deve acrescentar cenários de
token global, `403`, isolamento A/B, payload hostil e FK cruzada.

## Decisões Técnicas

- Composição por annotation e helper injetável, sem classe-base obrigatória.
- Autenticação e troca de contexto passam pelos controllers reais.
- Organizações e usuários de fixture são criados pelos services do domínio.
- CNPJ de teste válido e login único evitam colisões sem limpar bases compartilhadas.
- A sequência da fixture recebe um prefixo derivado de UUID por execução,
  reduzindo colisões com registros persistidos por testes de concorrência de
  execuções anteriores, sem apagar o banco de testes.
- Asserções de constraint não aceitam um `DataIntegrityViolationException` genérico.

## Módulos relacionados

- [[core]]
- [[unificacao-multitenant]]
- [[membership]]
- [[rbac]]
- [[organizacoes]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-08-03 | Introduz a fundação de testes tenant-aware da Onda 0. |
| 2026-09-12 | Registra o prefixo por execução das fixtures na consolidação dos testes de compras e fiscal. |
