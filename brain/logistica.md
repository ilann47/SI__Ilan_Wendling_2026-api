> Links: [[core]] · [[unificacao-multitenant]] · [[organizacoes]] · [[membership]] · [[rbac]] · [[auditoria]] · [[pagamentos-cadastrais]] · [[fornecedores]]

# Logística

## Objetivo

Administrar transportadoras, veículos de carga e composição de frota com
ownership explícito, sem misturar cadastros entre organizações.

## Contexto

A V41 migra Transportadora. A V48 migra `veiculo_frota` e o vínculo N:N
`transportadora_veiculo_frota`, preservando sem owner todo o histórico.

## Fluxo (camadas da arquitetura)

```text
JWT contextual + logistics:read/manage
  -> controllers de Transportadora, VeiculoFrota e vínculo
  -> services(organizacaoId, comando/filtro)
  -> repositories por id + organizacaoId
  -> FKs compostas das duas pontas da frota
  -> auditoria transacional
```

## Endpoints (se houver)

- `GET /api/transportadoras[/{id}]`, `/api/veiculos-frota[/{id}]` e
  `/api/transportadora-veiculos[/{id}]`: `logistics:read`.
- `POST/PUT/DELETE` nas mesmas raízes: `logistics:manage`.
- DELETE preserva a inativação lógica existente.

## Estrutura de Dados (DTOs, Entidades)

- `Transportadora`, `VeiculoFrota` e `TransportadoraVeiculoFrota` carregam owner.
- Placa usa `trim + uppercase` e unique parcial por tenant/histórico.
- O vínculo exige Transportadora e Veículo do mesmo tenant por FKs compostas.
- DTOs rejeitam `organizationId` e qualquer campo desconhecido.
- Owner de Veículo e vínculo é imutável; backfill isolado do parent é bloqueado.
- V49 torna também o owner da Transportadora incondicionalmente imutável.

## Integrações externas (se houver)

Não há CT-e, rastreamento ou integração externa implementada.

## Tratamento de Erros

- `401`: autenticação ausente ou contexto inválido.
- `403`: token global ou Membership sem permissão.
- `404`: raiz ou referência inexistente/de outro tenant.
- `400`: payload inválido ou tentativa de controlar tenant.
- `409`: documento, placa ou par repetido dentro da organização.

## Testes (curl ou equivalente)

`FrotaLogisticaTenantApiTest` cobre RBAC, isolamento A/B, referências cruzadas,
payload fechado, histórico e auditoria. `FrotaLogisticaTenantConstraintTest`
cobre uniques, FKs e guards de owner. `FrotaLogisticaMigrationCompatibilityTest`
executa V47→V48 preservando o grafo histórico.

## Decisões Técnicas

- Histórico permanece sem owner, preservado e invisível na API contextual.
- Nenhum backfill automático; UC732 migrará o grafo completo atomicamente.
- O rollout V48/V49 exige drenagem dos binários anteriores, sem overlap.
- Veículo usa retenção `PRIVACIDADE` sem placa/observação no snapshot.
- Vínculo usa retenção `CONFIGURACAO` com IDs das pontas e atividade.
- `ADMIN_ORGANIZACAO` já possui `logistics:read/manage` desde V41.

## Módulos relacionados

- [[pagamentos-cadastrais]]
- [[fornecedores]]
- [[auditoria]]
- [[testes-multitenancy]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-08-03 | V41 migra Transportadora para tenancy contextual e preserva dados históricos. |
| 2026-08-03 | V48 migra Veículo de Frota e vínculo N:N com integridade tenant-aware. |
| 2026-08-03 | V49 torna o owner de Transportadora incondicionalmente imutável. |
