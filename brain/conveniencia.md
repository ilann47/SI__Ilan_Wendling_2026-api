> Links: [[core]] · [[unificacao-multitenant]] · [[organizacoes]] · [[membership]] · [[rbac]] · [[auditoria]] · [[testes-multitenancy]]

# Conveniência

## Objetivo

Administrar categorias, marcas, unidades de medida, produtos e serviços com
ownership organizacional explícito.

## Contexto

A V50/V51 migra e endurece `categoria`, `marca` e `unidade_medida`; a V52 acrescenta
`produto` e `servico`; a V53 migra o vínculo `produto_fornecedor`. Registros
anteriores continuam sem owner.

## Fluxo (camadas da arquitetura)

```text
JWT contextual + catalog:read/manage
  -> controllers preservados /api/categorias, /api/marcas, /api/unidades-medida,
     /api/produtos, /api/servicos e /api/produto-fornecedores
  -> services(organizacaoId, comando/filtro)
  -> repositories por id + organizacaoId
  -> uniques normalizadas e owner imutável no PostgreSQL
  -> auditoria CONFIGURACAO
```

## Endpoints (se houver)

- `GET /api/categorias[/{id}]`, `/api/marcas[/{id}]` e
  `/api/unidades-medida[/{id}]`: `catalog:read`.
- `POST/PUT/DELETE` nas mesmas raízes: `catalog:manage`.
- `GET/POST/PUT/DELETE /api/produtos` e `/api/servicos` seguem as mesmas
  permissões e semântica de concorrência.
- `GET /api/produto-fornecedores` aceita filtro opcional `produtoId`; consulta e
  busca exigem `catalog:read`, enquanto criação, alteração e inativação exigem
  `catalog:manage`.
- DELETE preserva a inativação lógica existente.

## Estrutura de Dados (DTOs, Entidades)

- As três entidades carregam `organizacao_id` imutável e nullable somente para histórico.
- Categoria, Marca e Unidade de Medida têm nome normalizado único por organização.
- Unidade de Medida também tem sigla normalizada única por organização.
- Os três agregados usam `@Version`; respostas individuais expõem `version` e `ETag`.
- DTOs rejeitam `organizationId` e qualquer campo desconhecido.
- Produto exige Marca e Unidade do mesmo tenant; Categoria é opcional e deve ter
  o mesmo owner quando informada. Os três pais precisam estar ativos.
- Código de barras de Produto e nome de Serviço são normalizados e únicos por tenant.
- `produto.quantidade` permanece somente como compatibilidade histórica; a V52 não
  cria ledger nem permite controlar esse saldo pelo payload contextual.
- ProdutoFornecedor possui owner e identidade Produto–Fornecedor imutáveis,
  `@Version` e FKs compostas para pontas do mesmo tenant. Produto e Fornecedor
  precisam estar ativos para criação/reativação. POST para par inativo reativa a
  mesma linha; o par continua globalmente único.

## Integrações externas (se houver)

Não há integração externa neste recorte. Notas fiscais e o relatório de estoque
mínimo continuam globais e resolvem somente Produto/Serviço
históricos, tanto no service quanto por guards no PostgreSQL.

## Tratamento de Erros

- `401`: autenticação ausente ou contexto inválido.
- `403`: token global ou Membership sem permissão.
- `404`: recurso inexistente ou de outro tenant.
- `400`: payload inválido ou tentativa de controlar tenant.
- `409`: nome/sigla/par repetido, parent inativo ou perdedor de POST concorrente.
- `428`: `If-Match` ausente em alteração/inativação.
- `412`: versão esperada diverge do agregado persistido.

## Testes (curl ou equivalente)

`CatalogoConvenienciaTenantApiTest` e `ProdutoServicoTenantApiTest` cobrem RBAC,
isolamento, contrato fechado, ETag e auditoria. Os testes de constraint cobrem
uniques, FKs same-owner e consumers globais. `ProdutoServicoMigrationTest` cobre
V51→V52, preflight atômico e preservação dos registros históricos.
`ProdutoFornecedorTenantApiTest`, `ProdutoFornecedorMigrationTest`,
`ProdutoFornecedorTenantConstraintTest` e `ProdutoFornecedorConcorrenciaTest`
cobrem a V53, inclusive reativação da mesma linha e concorrência 201/409.

## Decisões Técnicas

- Nenhum dado histórico recebe owner automaticamente; UC732 fará reconciliação.
- Produto tenant-aware resolve apenas Categoria, Marca e Unidade do mesmo owner.
- Reverse guards impedem promover um pai histórico ainda referenciado por Produto.
- V50, V51, V52 e V53 formam uma unidade de rollout backend coordenada.
- Consumers ainda globais usam somente registros históricos; migrá-los exige uma
  história própria, sem backfill implícito.
- Confirmações fiscais fazem flush explícito da alteração versionada do Produto.
  Concorrência otimista retorna `409` com retry explícito e reverte integralmente
  estoque, nota e títulos da transação perdedora.
- A API preserva rotas e DTOs de resposta; `organizationId` não é exposto.
- A UI do vínculo usa `catalog:*`, mas precisa também de `suppliers:read` para
  carregar e exibir o seletor de fornecedores.

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
| 2026-08-03 | V52 migra Produto e Serviço, protege parents same-owner e consumers globais. |
| 2026-08-03 | Revisão V52 valida parents ativos, snapshots completos e concorrência fiscal. |
| 2026-08-03 | V53 migra ProdutoFornecedor, preserva unicidade do par e reativa a mesma linha. |
