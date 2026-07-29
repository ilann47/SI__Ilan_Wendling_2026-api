> Links: [[core]]

# Organizações

## Objetivo

Representar o tenant empresarial proprietário dos dados da plataforma. Nesta história, o módulo entrega o cadastro e o lifecycle interno de `Organizacao`; a resolução do tenant autenticado será adicionada somente com Membership.

## Contexto

O agregado nasce em `PROVISIONANDO` e conclui o mesmo caso de uso em `ATIVA`, sem expor o estado intermediário. `ENCERRADA` é terminal. Nenhum dado legado é atribuído automaticamente e `Usuario` permanece uma identidade global.

## Fluxo (camadas da arquitetura)

```text
OrganizacaoController
  -> OrganizacaoService (@Transactional)
    -> Organizacao (agregado)
      -> OrganizacaoRepository
        -> PostgreSQL / Flyway V8
```

## Endpoints

| Método | Rota | Comportamento |
|---|---|---|
| POST | `/api/v1/organizations` | Provisiona organização e retorna `201`, `Location` e `ETag`. |
| GET | `/api/v1/organizations/{id}` | Consulta por ID e retorna `ETag`. |
| PATCH | `/api/v1/organizations/{id}` | Atualiza somente campos permitidos; exige `If-Match`. |

## Estrutura de Dados (DTOs, Entidades)

`Organizacao` contém documento imutável normalizado, razão social, nome fantasia, moeda ISO 4217, fuso IANA, região ISO 3166-2, código de plano, status, versão otimista e timestamps.

Estados: `PROVISIONANDO`, `ATIVA`, `SUSPENSA`, `ENCERRADA`.

`configuracoes` não foi criado: não existe configuração concreta nesta história que justifique um JSON genérico.

## Integrações externas (se houver)

Não existem integrações externas nesta história.

## Tratamento de Erros

- `400`: payload, valor ou `If-Match` malformado.
- `404`: organização inexistente; futuramente também será usado para recurso fora do tenant visível.
- `409`: documento duplicado ou estado incompatível.
- `412`: versão esperada divergente ou conflito otimista.
- `428`: ausência de `If-Match` no PATCH.

## Testes (curl ou equivalente)

Testes automatizados cobrem domínio, API, constraints PostgreSQL, segurança e OpenAPI.

## Decisões Técnicas

- ID interno `Long`, compatível com o legado.
- `@Version` é a única origem do `ETag`.
- Documento é normalizado na aplicação e protegido no banco por CHECK e índice único funcional.
- Região significa subdivisão operacional/regulatória ISO 3166-2, como `BR-PR`.
- Moeda é armazenada em maiúsculas após validação ISO 4217.
- A proteção temporária usa `ROLE_ADMIN` até o RBAC contextual da História 3.
- Não há Membership, auditoria, outbox ou ownership de pátio antecipados.

## Módulos relacionados

- [[core]]
- Membership — próxima história, ainda não implementada.
- Instalações — receberá ownership da organização em sua etapa própria.

## Histórico

| Data | Ação |
|---|---|
| 2026-07-29 | Implementação inicial do agregado, migration V8 e três endpoints `/api/v1`. |
