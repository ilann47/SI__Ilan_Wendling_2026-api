> Links: [[core]] · [[organizacoes]] · [[venues]] · [[rbac]] · [[auditoria]]

# Instalações

## Objetivo

Descrever a topologia física dos estacionamentos de cada organização por meio
das raízes `Patio` e `Setor` e de seus limites de capacidade.

## Contexto

UC703 cria o pátio, UC704 o divide em setores tenant-aware e UC705 permite
individualizar vagas quando necessário. A gestão permite consultar e editar os
cadastros existentes e mudar a situação de vagas individuais; interdições de
faixas/pátios permanecem fora deste fluxo. A
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

POST /api/v1/sectors/{sectorId}/spaces:batch
  -> JWT contextual + facilities:manage
  -> lock pessimista do setor ativo
  -> leitura em lote por códigos normalizados
  -> idempotência natural + capacidade + compatibilidade de categoria
  -> VagaRepository -> PostgreSQL V33
  -> auditoria + outbox VagaCriada por nova vaga
  -> 201 ou 207 Multi-Status
```

## Endpoints (se houver)

- `GET /api/v1/parking-facilities`: opções de pátios ativos (`id`, `name`).
  Requer `facilities:manage`, `inventory:read` ou `inventory:manage`.
- `GET /api/v1/parking-facilities/{id}/availability`: disponibilidade de pátio
  ativo no tenant. Requer simultaneamente `facilities:manage` e `inventory:read`.
  Recebe `startsAt` e `endsAt` com deslocamento ISO 8601 e retorna
  `operationalCapacity`, `availableCapacity` (mínimo no período) e `segments`.
  Sem `includeTimeline=true`, `segments` é vazio. Com a opção, lista faixas
  contíguas com início, fim e vagas disponíveis em janelas de até 45 dias.
  A consulta não reserva nem bloqueia vagas; não utiliza cache HTTP. O
  vínculo da alocação continua sujeito à validação transacional do saldo.
- `GET /api/v1/sectors`: setores ativos de pátios ativos, com nome e código do
  setor e nome do pátio. Filtro opcional `parkingFacilityId`; requer `facilities:manage`.
- `GET /api/v1/installation-cities`: municípios ativos para cadastros de
  instalações. Requer contexto e `facilities:manage` ou `organizations:admin`;
  não altera as permissões da rota legada nem permite escrita.
- As consultas aceitam `name` (trecho do nome, sem distinção de caixa/acento),
  `id` exato e `page`/`size` (padrão 0/20, máximo 100 por página). Retornam
  `content` e metadados de paginação, em ordem de nome e ID. Pátios e setores
  usam a organização do JWT, nunca parâmetros do cliente para definir o escopo.

- `POST /api/v1/parking-facilities`: cria um pátio no tenant ativo. O tenant é
  derivado exclusivamente do principal e não é aceito no payload.
- `POST /api/v1/parking-facilities/{parkingFacilityId}/sectors`: cria setor ativo
  com código, nome, capacidade e categorias físicas canônicas.
- `POST /api/v1/sectors/{sectorId}/spaces:batch`: importa de uma a 500 vagas,
  preservando a ordem e informando criação, replay ou conflito por item.
- `POST /api/v1/sectors/{sectorId}/spaces`: cadastra uma vaga em setor ativo
  da organização, com `facilities:manage`. Recebe `code`, `type`, `accessible`
  e `position` opcional, por exemplo
  `{"code":"A-01","type":"COMUM","accessible":false}`. Retorna `201`, `Location`,
  `ETag` e o detalhe da vaga em situação `LIVRE`. Retorna `409` para código
  repetido, categoria não aceita ou capacidade atingida, sem alterar registros.
- `GET /api/v1/spaces`: lista vagas da organização por trecho de `code` (até 40
  caracteres, sem distinguir maiúsculas de minúsculas), `sectorId` opcional e
  `page`/`size` (0/20 por padrão). Ordena por código e ID. Inclui situação,
  acessibilidade, posição e nomes de setor e pátio. Requer `facilities:manage`.
- `GET /api/v1/spaces/{id}`: retorna os dados da vaga, as categorias permitidas
  no setor e `ETag`. `PUT` no mesmo endereço exige `If-Match` e aceita somente
  `code`, `type`, `accessible` e `position`. Ambos exigem `facilities:manage`;
  um ID de outra organização responde 404.
- `PATCH /api/v1/spaces/{id}/status`: muda apenas a situação da vaga.
  Requer `facilities:manage` e `If-Match` com a versão da linha ou do detalhe.
  Aceita `{"status":"BLOQUEADA"}`, `MANUTENCAO` ou `LIVRE` e retorna
  `200` com detalhe e `ETag`. Repetir a situação com a versão atual não
  altera a versão nem cria outra auditoria.
- `GET /api/v1/parking-facilities/management` e `GET /api/v1/sectors/management`:
  busca paginada com `name`, `page` e `size`, incluindo inativos. Retornam
  situação, capacidades e, no setor, código e nome do pátio. O catálogo de
  gestão de setores também aceita `id` exato para exibir o filtro selecionado.
- `GET` e `PUT` em `/api/v1/parking-facilities/{id}` e `/api/v1/sectors/{id}`:
  leem/atualizam somente registros do tenant, com `facilities:manage`.
  Retornam o cadastro e `ETag`; `PUT` exige `If-Match` e usa os campos do cadastro.
  Detalhes incluem o nome da cidade/pátio vinculado. Não alteram situação nem vínculos.
- `/api/patios`: compatibilidade legada; opera somente registros com
  `organizacao_id IS NULL`.

## Estrutura de Dados (DTOs, Entidades)

`PatioInstalacao` mapeia a tabela compartilhada `patio` com organização imutável,
endereço, cidade, capacidades física/operacional, ativo, versão e timestamps.
`CriarPatioInstalacaoRequest` expõe somente campos controláveis pelo cliente.
`Setor` é agregado próprio com organização/pátio imutáveis, código normalizado,
capacidade, categorias, estado ativo e versão otimista. As categorias são locais
ao contexto de Instalações para não depender do módulo downstream de Inventário.
`Vaga` contém código normalizado, tipo local, acessibilidade, posição opcional,
estado `LIVRE`, `BLOQUEADA` ou `MANUTENCAO` e versão. O estado inicial é `LIVRE`.
`ImportarVagasResponse` resume os resultados e detalha
cada item sem alterar uma vaga existente divergente.

## Integrações externas (se houver)

Nenhuma. Organização, referência geográfica, RBAC e auditoria são módulos internos.

## Tratamento de Erros

Busca por nome translitera acentos minúsculos e maiúsculos no SQL. O resultado
não depende de `lower()` reduzir letras como Á em bancos configurados com
locale C; `area` encontra `Área Coberta` sem mudar o nome armazenado.

- `400`: payload/capacidade inválida ou campo desconhecido, inclusive tenant.
- `403`: ausência de contexto ou de `facilities:manage`.
- `404`: organização ou cidade ativa não encontrada.
- No calendário, intervalo inválido ou janela detalhada maior que 45 dias
  resulta em `400`; pátio inativo ou fora do tenant ativo resulta em `404`.
- `409`: nome normalizado duplicado ou violação de integridade.
- `409`: código de setor duplicado ou soma das capacidades acima do pátio.
- `409`: redução de capacidade abaixo dos setores, alocações vigentes/futuras
  ou vagas cadastradas; remoção de categoria em uso.
- `409`: código duplicado no setor da vaga ou categoria não aceita por ele.
- `412` / `428`: versão desatualizada / ausência de `If-Match` na edição.
- `207`: batch parcialmente aplicado por replay, código divergente, categoria
  incompatível ou capacidade esgotada.

## Testes (curl ou equivalente)

Os testes cobrem criação, `Location`/`ETag`, permissão contextual, rejeição de
tenant fornecido pelo cliente, capacidade, cidade, unicidade normalizada, auditoria,
constraints diretas no PostgreSQL e isolamento da API legada.
UC704 também cobre autorização, normalização, taxonomia, capacidade agregada,
auditoria, outbox e constraints de código/categoria/capacidade.
UC705 cobre replay equivalente, conflito parcial, lotação do setor, categoria,
tenant, RBAC, contrato fechado e constraints de vaga.
A gestão é coberta em `InstalacoesPatioTest` com listagem de inativos, atualização
versionada e proteção da capacidade física.
A disponibilidade é coberta em `AlocacaoPatioEventoControllerTest` (saldo
por faixa, isolamento, permissões e limites) e em `CapacidadeTemporalTest`
(fronteiras simultâneas e recorte de janela).
Para vagas, cobre importação seguida de busca e edição, filtros combinados,
versão antiga, código duplicado, categoria incompatível, permissões e isolamento.
O cadastro individual também é testado junto com a importação: código, replay
e capacidade são compartilhados entre os dois caminhos.

## Decisões Técnicas

- Evolução aditiva da tabela legada: linhas existentes permanecem sem tenant.
- Capacidade operacional legada recebe backfill igual à capacidade física.
- Aplicação e banco defendem a hierarquia de capacidade e a unicidade por tenant.
- Dois mapeamentos JPA separam o modelo enterprise do modelo legado durante a migração.
- Nenhum dado legado é associado automaticamente e Venue não é antecipado.
- O lock do pátio serializa criações concorrentes antes de somar capacidades.
- A edição de setor obtém lock do pátio antes do setor; a edição de pátio
  usa o mesmo lock de alocação de eventos. As duas registram auditoria de configuração.
- O banco protege ownership composto, código normalizado e valores locais; a
  soma entre setores é uma invariante transacional protegida pelo lock da raiz.
- O batch normaliza todos os itens antes da primeira escrita e busca códigos em
  lote; o lock do setor serializa o limite sem uma query por item.
- Cadastro individual e importação compartilham lock, persistência, auditoria
  e evento `VagaCriada`. A rota individual rejeita duplicidade em vez de tratar
  como replay, comportamento específico da importação.
- A edição de vaga usa o mesmo lock do setor e valida unicidade e categoria,
  sem alterar vínculo ou situação. Mudanças geram auditoria `VAGA_ATUALIZADA`.
- A situação usa lock da vaga e gera `VAGA_SITUACAO_ALTERADA` com antes/depois.
  Não muda capacidade cadastrada, cotas ou reservas de [[inventario]], que
  são controladas por alocação de pátio, sem vínculo com vagas individuais.
- A validação de categorias rejeita elementos nulos por iteração, preservando
  compatibilidade com conjuntos imutáveis (`Set.of`) usados pelos serviços.
- Posição é um identificador textual opcional. Geometria e coordenadas aguardam
  contrato de layout explícito.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[venues]] — localização administrativa do evento.
- [[inventario]] — bloqueia e compromete o saldo operacional do pátio por janela temporal.
- [[rbac]]
- [[auditoria]]

## Histórico (data + ação)

Consulta paginada de pátios, setores e cidades para seletores adicionada em 2026-11-08.

| Data | Ação |
|---|---|
| 2026-11-22 | Consulta de vagas por pátio e período para o calendário de eventos, sem reservar vagas. |
| 2026-11-19 | Corrige busca de nomes iniciados com acento maiúsculo em PostgreSQL locale C, sem alterar dados. |
| 2026-11-08 | Permite bloquear, colocar em manutenção e liberar vaga individual, com auditoria e versão. |
| 2026-11-08 | Adiciona cadastro individual de vagas sem alterar a importação em lote. |
| 2026-11-08 | Adiciona listagem paginada e edição auditada de vagas, com busca por código e filtro por setor. |
| 2026-11-08 | Lista e edita pátios e setores por organização, protegendo capacidade, vagas e versão. |
| 2026-07-29 | Implementa criação tenant-aware de pátio e compatibilidade isolada com `/api/patios`. |
| 2026-07-30 | Expõe lock pessimista tenant-aware para serializar alocações concorrentes do pátio. |
| 2026-08-01 | Implementa UC704 com setores tenant-aware, auditoria e outbox. |
| 2026-08-01 | Implementa UC705 com importação idempotente de vagas em lote. |
| 2026-08-03 | Corrige a validação de categorias para aceitar conjuntos imutáveis sem lançar `NullPointerException`. |
