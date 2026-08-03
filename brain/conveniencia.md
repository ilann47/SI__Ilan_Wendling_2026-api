> Links: [[core]] · [[unificacao-multitenant]] · [[organizacoes]] · [[membership]] · [[rbac]] · [[auditoria]] · [[testes-multitenancy]]

# Conveniência

## Objetivo

Administrar o catálogo-base de categorias, marcas e unidades de medida com
ownership organizacional explícito e preparar a migração incremental de Produto.

## Contexto

A V50 migra `categoria`, `marca` e `unidade_medida`. O `produto` legado continua
global nesta história e somente pode referenciar pais históricos sem owner.

## Fluxo (camadas da arquitetura)

```text
JWT contextual + catalog:read/manage
  -> controllers preservados /api/categorias, /api/marcas, /api/unidades-medida
  -> services(organizacaoId, comando/filtro)
  -> repositories por id + organizacaoId
  -> uniques normalizadas e owner imutável no PostgreSQL
  -> auditoria CONFIGURACAO
```

## Endpoints (se houver)

- `GET /api/categorias[/{id}]`, `/api/marcas[/{id}]` e
  `/api/unidades-medida[/{id}]`: `catalog:read`.
- `POST/PUT/DELETE` nas mesmas raízes: `catalog:manage`.
- DELETE preserva a inativação lógica existente.

## Estrutura de Dados (DTOs, Entidades)

- As três entidades carregam `organizacao_id` imutável e nullable somente para histórico.
- Categoria, Marca e Unidade de Medida têm nome normalizado único por organização.
- Unidade de Medida também tem sigla normalizada única por organização.
- Os três agregados usam `@Version`; respostas individuais expõem `version` e `ETag`.
- DTOs rejeitam `organizationId` e qualquer campo desconhecido.

## Integrações externas (se houver)

Não há integração externa neste recorte. Produto é um consumer interno temporário.

## Tratamento de Erros

- `401`: autenticação ausente ou contexto inválido.
- `403`: token global ou Membership sem permissão.
- `404`: recurso inexistente ou de outro tenant.
- `400`: payload inválido ou tentativa de controlar tenant.
- `409`: nome/sigla normalizado repetido na organização.
- `428`: `If-Match` ausente em alteração/inativação.
- `412`: versão esperada diverge do agregado persistido.

## Testes (curl ou equivalente)

`CatalogoConvenienciaTenantApiTest` cobre RBAC, isolamento, contrato fechado,
histórico e auditoria. `CatalogoConvenienciaTenantConstraintTest` cobre uniques,
FKs e guards. Os testes de migration executam V49→V50 e preflights atômicos.

## Decisões Técnicas

- Nenhum dado histórico recebe owner automaticamente; UC732 fará reconciliação.
- Produto global resolve apenas Categoria, Marca e Unidade sem owner, também por trigger.
- Reverse guards impedem promover um pai histórico ainda referenciado por Produto.
- V50 e V51 formam uma unidade não publicável isoladamente.
- O rollout backend deve aplicar V50/V51 e a futura V52 de Produto de forma coordenada
  antes de habilitar o frontend tenant-aware, evitando referências incompatíveis.
- A API preserva rotas e DTOs de resposta; `organizationId` não é exposto.

## Módulos relacionados

- [[rbac]]
- [[auditoria]]
- [[unificacao-multitenant]]
- [[testes-multitenancy]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-08-03 | V50 migra Categoria, Marca e Unidade de Medida para tenancy contextual. |
| 2026-08-03 | V51 adiciona concorrência HTTP/JPA, unique de nome da Unidade e reverse guards. |
