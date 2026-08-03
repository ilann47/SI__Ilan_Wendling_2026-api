> Links: [[core]] · [[unificacao-multitenant]] · [[organizacoes]] · [[membership]] · [[rbac]] · [[auditoria]] · [[testes-multitenancy]]

# RH

## Objetivo

Manter cargos e funcionários com ownership organizacional explícito, preservando
o histórico sem owner e sem antecipar o vínculo laboral canônico da Membership.

## Contexto

A V43 torna `Cargo` tenant-aware, a V45 aplica a mesma fronteira a `Funcionario`
e a V46 torna fisicamente imutável o owner de ambos os agregados.
Novas escritas derivam o owner exclusivamente do JWT contextual. Linhas históricas
continuam sem owner e não aparecem na API. `Usuario` permanece identidade global.

## Fluxo (camadas da arquitetura)

```text
JWT contextual + workforce:read/manage
  -> CargoController / FuncionarioController
  -> service(organizacaoId, comando/filtro)
  -> repository por id + organizacaoId
  -> FK composta Funcionario–Cargo do mesmo tenant
  -> auditoria RH transacional com snapshot mínimo
```

## Endpoints (se houver)

- `GET /api/cargos[/{id}]`: `workforce:read`.
- `POST/PUT/DELETE /api/cargos[/{id}]`: `workforce:manage`.
- `GET /api/funcionarios[/{id}]`: `workforce:read`.
- `POST/PUT/DELETE /api/funcionarios[/{id}]`: `workforce:manage`.

## Estrutura de Dados (DTOs, Entidades)

- `Cargo.organizacaoId` e `Funcionario.organizacaoId`: owners imutáveis, nullable
  apenas para o histórico.
- Nome de Cargo e CPF normalizado de Funcionário são únicos dentro do tenant;
  o conjunto histórico mantém uniques parciais separados.
- Funcionário exige Cargo do mesmo owner por FK composta e trigger de defesa.
- `CargoRequest` e `FuncionarioRequest` rejeitam `organizationId` e desconhecidos.
- `Usuario.funcionario_id` só pode apontar para Funcionário histórico sem owner.
- `condicao_pagamento` não pertence ao modelo de Funcionário e não foi adicionada.

## Integrações externas (se houver)

Nenhuma.

## Tratamento de Erros

- `401`: token ausente ou inválido.
- `403`: contexto global ou sem `workforce:read/manage`.
- `404`: recurso/referência inexistente ou de outro tenant.
- `400`: payload inválido ou campo desconhecido.
- `409`: nome de Cargo ou CPF normalizado repetido no mesmo tenant.

## Testes (curl ou equivalente)

`FuncionarioTenantApiTest` cobre segurança, segregação A/B, payload fechado,
Cargo cross-tenant, histórico invisível, consumer global e auditoria sem PII.
`FuncionarioTenantConstraintTest` cobre uniques/FKs/triggers nomeados e bloqueio
de qualquer troca ou backfill direto de owner. `FuncionarioMigrationCompatibilityTest`
executa V44→V45 preservando o grafo histórico Cargo–Funcionário–Usuário;
`FuncionarioHardeningMigrationCompatibilityTest` prova que a V46 recusa grafo
preexistente inconsistente. `FuncionarioControllerTest` caracteriza o
CRUD preservado sob JWT contextual.

## Decisões Técnicas

- Somente `ADMIN_ORGANIZACAO` possui `workforce:read/manage` nesta etapa.
- Nenhum Cargo ou Funcionário histórico recebe tenant automaticamente.
- Backfill direto fica bloqueado pela V46; UC732 deverá substituir os guards em
  migration administrativa, auditável e validada como uma unidade transacional.
- O snapshot de auditoria de Funcionário contém somente `cargoId` e `ativo`.
- Usuário continua global; o vínculo laboral TARGET será feito por Membership.
- Não existe relação de domínio entre Funcionário e Condição de Pagamento.

## Módulos relacionados

- [[unificacao-multitenant]]
- [[organizacoes]]
- [[membership]]
- [[rbac]]
- [[auditoria]]
- [[testes-multitenancy]]

## Histórico

| Data | Ação |
|---|---|
| 2026-08-03 | Migra Cargo para tenancy real na V43 e preserva compatibilidade de Funcionário. |
| 2026-08-03 | Migra Funcionário na V45 e restringe o vínculo de Usuário ao histórico sem owner. |
| 2026-08-03 | Endurece na V46 o preflight do grafo e a imutabilidade física dos owners de RH. |
