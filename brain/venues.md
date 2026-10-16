> Links: [[core]] · [[organizacoes]] · [[instalacoes]] · [[eventos]] · [[rbac]] · [[auditoria]]

# Venues

## Objetivo

Representar a unidade operacional que recebe eventos, com localização e fuso
próprios dentro de uma organização.

## Contexto

A História 6 implementa o contrato mínimo aprovado de criação. O tipo é fixado
pelo servidor como `VENUE`. Outros tipos de unidade, contatos e mutações não têm
contrato suficiente na baseline e não foram antecipados.

## Fluxo (camadas da arquitetura)

```text
POST /api/v1/venues
  -> JWT contextual + organizations:admin
  -> VenueService
  -> organização/cidade ativas + nome único + fuso IANA
  -> VenueRepository -> PostgreSQL V14
  -> AuditoriaService (UNIDADE_CRIADA)
  -> 201 + Location + ETag
```

## Endpoints (se houver)

- `POST /api/v1/venues`: cria Venue ativo na organização autenticada.
- Nenhum endpoint de consulta ou alteração foi antecipado nesta história.

## Estrutura de Dados (DTOs, Entidades)

`Venue` mapeia `unidade_operacional`: organização imutável, nome, tipo `VENUE`,
endereço, cidade, fuso IANA, ativo, versão e timestamps. O request não recebe
organização, tipo, estado ou versão.

## Integrações externas (se houver)

Nenhuma. Organização, geografia, RBAC e auditoria são dependências internas.

## Tratamento de Erros

- `400`: payload, campo desconhecido ou fuso inválido.
- `403`: contexto ausente ou sem `organizations:admin`.
- `404`: organização ou cidade ativa inexistente no caso de uso.
- `409`: nome normalizado duplicado ou constraint violada.

## Testes (curl ou equivalente)

Os testes cobrem criação, tenant server-side, permissão, fuso IANA, cidade ativa,
unicidade, tipo fixo, auditoria, constraints PostgreSQL e OpenAPI.

## Decisões Técnicas

- Venue pertence ao BC de Organizações e usa `organizations:admin` nesta etapa.
- `VENUE` é o único tipo aprovado e é protegido por check no banco.
- Nome é único por organização após `trim` e normalização case-insensitive.
- O fuso é persistido no identificador canônico retornado por `ZoneId`.
- Contatos e vínculo Venue–Pátio aguardam contratos explícitos.
- A FK composta `(venue_id, organizacao_id)` impede que [[eventos]] referencie venue externo ao tenant.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[instalacoes]]
- [[rbac]]
- [[auditoria]]
- [[eventos]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-29 | Implementa criação tenant-aware de Venue como unidade operacional. |
| 2026-07-30 | Evento passa a referenciar Venue com isolamento também no PostgreSQL. |
