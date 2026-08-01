> Links: [[core]] · [[organizacoes]] · [[venues]] · [[rbac]] · [[auditoria]]

# Instalações

## Objetivo

Descrever a topologia física dos estacionamentos de cada organização por meio
das raízes `Patio` e `Setor` e de seus limites de capacidade.

## Contexto

UC703 cria o pátio e UC704 o divide em setores tenant-aware. Vagas, faixas,
interdições e alterações cadastrais permanecem em histórias posteriores. A
operação rotativa legada continua isolada nos registros sem organização.

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

POST /api/v1/parking-facilities/{parkingFacilityId}/sectors
  -> JWT contextual + facilities:manage
  -> lock pessimista do pátio ativo
  -> código único + soma de capacidades <= capacidade física
  -> SetorRepository -> PostgreSQL V32
  -> auditoria + outbox SetorCriado
  -> 201 + Location + ETag
```

## Endpoints (se houver)

- `POST /api/v1/parking-facilities`: cria um pátio no tenant ativo. O tenant é
  derivado exclusivamente do principal e não é aceito no payload.
- `POST /api/v1/parking-facilities/{parkingFacilityId}/sectors`: cria setor ativo
  com código, nome, capacidade e categorias físicas canônicas.
- `/api/patios`: compatibilidade legada; opera somente registros com
  `organizacao_id IS NULL`.

## Estrutura de Dados (DTOs, Entidades)

`PatioInstalacao` mapeia a tabela compartilhada `patio` com organização imutável,
endereço, cidade, capacidades física/operacional, ativo, versão e timestamps.
`CriarPatioInstalacaoRequest` expõe somente campos controláveis pelo cliente.
`Setor` é agregado próprio com organização/pátio imutáveis, código normalizado,
capacidade, categorias, estado ativo e versão otimista. As categorias são locais
ao contexto de Instalações para não depender do módulo downstream de Inventário.

## Integrações externas (se houver)

Nenhuma. Organização, referência geográfica, RBAC e auditoria são módulos internos.

## Tratamento de Erros

- `400`: payload/capacidade inválida ou campo desconhecido, inclusive tenant.
- `403`: ausência de contexto ou de `facilities:manage`.
- `404`: organização ou cidade ativa não encontrada.
- `409`: nome normalizado duplicado ou violação de integridade.
- `409`: código de setor duplicado ou soma das capacidades acima do pátio.

## Testes (curl ou equivalente)

Os testes cobrem criação, `Location`/`ETag`, permissão contextual, rejeição de
tenant fornecido pelo cliente, capacidade, cidade, unicidade normalizada, auditoria,
constraints diretas no PostgreSQL e isolamento da API legada.
UC704 também cobre autorização, normalização, taxonomia, capacidade agregada,
auditoria, outbox e constraints de código/categoria/capacidade.

## Decisões Técnicas

- Evolução aditiva da tabela legada: linhas existentes permanecem sem tenant.
- Capacidade operacional legada recebe backfill igual à capacidade física.
- Aplicação e banco defendem a hierarquia de capacidade e a unicidade por tenant.
- Dois mapeamentos JPA separam o modelo enterprise do modelo legado durante a migração.
- Nenhum dado legado é associado automaticamente e Venue não é antecipado.
- O lock do pátio serializa criações concorrentes antes de somar capacidades.
- O banco protege ownership composto, código normalizado e valores locais; a
  soma entre setores é uma invariante transacional protegida pelo lock da raiz.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[venues]] — localização administrativa do evento.
- [[inventario]] — bloqueia e compromete o saldo operacional do pátio por janela temporal.
- [[rbac]]
- [[auditoria]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-29 | Implementa criação tenant-aware de pátio e compatibilidade isolada com `/api/patios`. |
| 2026-07-30 | Expõe lock pessimista tenant-aware para serializar alocações concorrentes do pátio. |
| 2026-08-01 | Implementa UC704 com setores tenant-aware, auditoria e outbox. |
