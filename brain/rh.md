> Links: [[core]] · [[unificacao-multitenant]] · [[organizacoes]] · [[membership]] · [[rbac]] · [[auditoria]] · [[testes-multitenancy]]

# RH

## Objetivo

Manter a estrutura organizacional de trabalho com ownership explícito, iniciando
por Cargo sem antecipar a migração de Funcionário ou do vínculo laboral TARGET.

## Contexto

A V43 torna `Cargo` tenant-aware. Linhas anteriores permanecem sem owner para
reconciliação futura; novos cargos são sempre vinculados ao tenant do JWT.
`Funcionario` continua global nesta fatia e só pode referenciar cargo histórico.

## Fluxo (camadas da arquitetura)

```text
JWT contextual + workforce:read/manage
  -> CargoController
  -> CargoService(organizacaoId, comando/filtro)
  -> CargoRepository por id + organizacaoId
  -> cargo com ownership e unicidade local
  -> auditoria RH transacional
```

## Endpoints (se houver)

- `GET /api/cargos` e `GET /api/cargos/{id}`: `workforce:read`.
- `POST /api/cargos`: `workforce:manage`.
- `PUT /api/cargos/{id}`: `workforce:manage`.
- `DELETE /api/cargos/{id}`: inativação sob `workforce:manage`.

## Estrutura de Dados (DTOs, Entidades)

- `Cargo.organizacaoId`: owner imutável, nullable apenas para histórico.
- Nome normalizado é único dentro do tenant; histórico conserva unique separado.
- `CargoRequest` rejeita `organizationId` e qualquer campo desconhecido.
- `Funcionario` ainda não recebe owner; sua referência usa somente Cargo sem owner.

## Integrações externas (se houver)

Nenhuma.

## Tratamento de Erros

- `401`: token ausente ou inválido.
- `403`: contexto global ou sem `workforce:read/manage`.
- `404`: cargo inexistente ou de outro tenant.
- `400`: payload inválido ou campo desconhecido.
- `409`: nome normalizado repetido no mesmo tenant.

## Testes (curl ou equivalente)

`CargoTenantApiTest` cobre segurança, segregação A/B em GET/PUT/DELETE, payload
fechado, histórico invisível, compatibilidade de Funcionário e auditoria RH.
`CargoTenantConstraintTest` cobre FK de organização, uniques nomeadas e proteção
do vínculo global. `CargoMigrationCompatibilityTest` executa V42→V43 preservando
Cargo e Funcionário históricos.

## Decisões Técnicas

- Somente `ADMIN_ORGANIZACAO` recebe `workforce:read/manage` nesta etapa.
- Cargo histórico não é associado automaticamente a organização.
- O trigger de compatibilidade é temporário até a migração de Funcionário.
- Vínculo `Usuario`–`Funcionario` não é alterado nesta história.

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
