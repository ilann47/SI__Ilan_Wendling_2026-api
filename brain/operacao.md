> Links: [[core]] · [[unificacao-multitenant]] · [[clientes]] · [[rbac]] · [[auditoria]] · [[testes-multitenancy]]

# Operação

## Objetivo

Manter os cadastros e fluxos operacionais do estacionamento isolados pela organização ativa.

## Contexto

As V58 a V61 migram `Veiculo`, `Tarifa`, `Mensalista` e `Movimentacao` para o contexto organizacional.

## Fluxo (camadas da arquitetura)

`VeiculoController` valida o JWT contextual e o RBAC, `VeiculoService` aplica normalização e isolamento, `VeiculoRepository` consulta pelo owner e o PostgreSQL protege unicidade e referências do mesmo tenant.

## Endpoints

- `GET /api/veiculos[/{id}]`: requer `operations:read`.
- `POST/PUT/DELETE /api/veiculos[/{id}]`: requer `operations:manage`.
- `GET /api/tarifas[/{id}]`: requer `operations:read`.
- `POST/PUT/DELETE /api/tarifas[/{id}]`: requer `operations:manage`.
- `GET /api/mensalistas[/{id}]`: requer `operations:read`.
- Mutações e geração de mensalidade exigem `operations:manage`; o faturamento também exige `finance:manage`.

## Estrutura de Dados (DTOs, Entidades)

`Veiculo` possui `organizacaoId` nullable apenas para compatibilidade histórica e `version` otimista. `VeiculoResponse` expõe ambos; `VeiculoRequest` não aceita owner.

## Integrações externas

Não há integração externa neste recorte.

## Tratamento de Erros

Recurso ou Cliente fora do tenant retorna `404`; placa duplicada no mesmo tenant retorna `409`; ausência de autenticação retorna `401` e contexto/permissão insuficiente retorna `403`.

## Testes

`VeiculoTenantApiTest` cobre contexto obrigatório, normalização, duplicidade entre tenants, segregação, referência cruzada e auditoria. A suíte legada de veículo, mensalista e movimentação preserva compatibilidade.

## Decisões Técnicas

- Placa é normalizada e única por organização no service e no banco.
- Cliente vinculado deve possuir o mesmo owner.
- Mensalista e Movimentação continuam aceitando somente veículos históricos até suas migrations próprias.
- Ownership é imutável no PostgreSQL.

## Módulos relacionados

[[clientes]] · [[rbac]] · [[auditoria]] · [[unificacao-multitenant]]

## Histórico

| Data | Ação |
|---|---|
| 2026-08-04 | V58 migra Veículo e o frontend correspondente para tenancy contextual. |
| 2026-08-04 | V59 migra Tarifa preservando descrições repetidas permitidas pelo legado. |
| 2026-08-04 | V60 migra Mensalista e a geração tenant-aware de Conta a Receber. |
| 2026-08-04 | V61 migra entrada, saída, cancelamento e ocupação por pátio. |
| 2026-08-04 | Relatórios de pátio, faturamento e mensalistas passam a respeitar a organização ativa. |
| 2026-08-04 | Entrada, saída e cancelamento passam a registrar trilha operacional tenant-aware. |
