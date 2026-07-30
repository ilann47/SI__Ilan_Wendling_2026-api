> Links: [[core]] · [[organizacoes]] · [[rbac]] · [[auditoria]]

# Instalações

## Objetivo

Descrever a topologia física dos estacionamentos de cada organização, começando
pela raiz `Patio` e pelos limites de capacidade física e operacional.

## Contexto

A História 5 implementa somente UC703: criação de pátio. Venue, setores, vagas,
faixas, interdições e alterações cadastrais permanecem em histórias posteriores.
A operação rotativa legada continua isolada nos registros sem organização.

## Fluxo (camadas da arquitetura)

```text
POST /api/v1/parking-facilities
  -> JWT contextual + facilities:manage
  -> InstalacoesService
  -> organização e cidade ativas + invariantes de capacidade/unicidade
  -> PatioInstalacaoRepository
  -> PostgreSQL (patio)
  -> AuditoriaService (PATIO_CRIADO)
  -> 201 + Location + ETag
```

## Endpoints (se houver)

- `POST /api/v1/parking-facilities`: cria um pátio no tenant ativo. O tenant é
  derivado exclusivamente do principal e não é aceito no payload.
- `/api/patios`: compatibilidade legada; opera somente registros com
  `organizacao_id IS NULL`.

## Estrutura de Dados (DTOs, Entidades)

`PatioInstalacao` mapeia a tabela compartilhada `patio` com organização imutável,
endereço, cidade, capacidades física/operacional, ativo, versão e timestamps.
`CriarPatioInstalacaoRequest` expõe somente campos controláveis pelo cliente.

## Integrações externas (se houver)

Nenhuma. Organização, referência geográfica, RBAC e auditoria são módulos internos.

## Tratamento de Erros

- `400`: payload/capacidade inválida ou campo desconhecido, inclusive tenant.
- `403`: ausência de contexto ou de `facilities:manage`.
- `404`: organização ou cidade ativa não encontrada.
- `409`: nome normalizado duplicado ou violação de integridade.

## Testes (curl ou equivalente)

Os testes cobrem criação, `Location`/`ETag`, permissão contextual, rejeição de
tenant fornecido pelo cliente, capacidade, cidade, unicidade normalizada, auditoria,
constraints diretas no PostgreSQL e isolamento da API legada.

## Decisões Técnicas

- Evolução aditiva da tabela legada: linhas existentes permanecem sem tenant.
- Capacidade operacional legada recebe backfill igual à capacidade física.
- Aplicação e banco defendem a hierarquia de capacidade e a unicidade por tenant.
- Dois mapeamentos JPA separam o modelo enterprise do modelo legado durante a migração.
- Nenhum dado legado é associado automaticamente e Venue não é antecipado.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[rbac]]
- [[auditoria]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-29 | Implementa criação tenant-aware de pátio e compatibilidade isolada com `/api/patios`. |
