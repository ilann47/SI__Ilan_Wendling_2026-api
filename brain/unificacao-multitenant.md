> Links: [[core]] · [[organizacoes]] · [[membership]] · [[rbac]] · [[auditoria]] · [[instalacoes]] · [[eventos]] · [[dados-demo]] · [[bloqueios]] · [[testes-multitenancy]] · [[pagamentos-cadastrais]] · [[clientes]] · [[logistica]] · [[fornecedores]]

# Unificação Multi-Tenant

## Objetivo

Migrar os módulos empresariais anteriores para a fronteira de Organização já
utilizada pelo fluxo de eventos, preservando funcionalidades, dados e contratos.

## Contexto

O AS-IS possui entidades de V1/V6/V7 globais e módulos enterprise tenant-aware.
Os módulos anteriores são permanentes; “legado” descreve apenas a ausência atual
de ownership e RBAC contextual. A especificação normativa está em
[[../docs/especificacao/22-unificacao-multitenant]].

## Fluxo (camadas da arquitetura)

```text
JWT contextual
  -> controller + permissão atômica
  -> service(organizacaoId, comando)
  -> referências por id + organizacaoId
  -> repository tenant-aware
  -> constraints/FKs compostas PostgreSQL
  -> auditoria/outbox quando aplicável
  -> DTO sem organizationId controlável
```

## Endpoints (se houver)

As rotas `/api/*` existentes são preservadas durante a transição, mas cada rota
migrada passa a exigir contexto organizacional e o mesmo service tenant-aware.
Não há fallback global. Novas rotas `/api/v1` somente surgem quando o contrato
precisa evoluir de forma incompatível e documentada.

## Estrutura de Dados (DTOs, Entidades)

- Globais: `Usuario`, `Pais`, `Estado`, `Cidade`, `Permissao` e `Papel`.
- Tenant direto: raízes empresariais, documentos, contas e movimentações.
- Filho tenant: owner derivado do pai; carrega `organizacao_id` quando relaciona
  múltiplos pais e precisa de FK composta.
- Compatibilidade histórica: owner opcional somente enquanto o backfill explícito
  não foi executado; novas escritas nunca usam null.

## Integrações externas (se houver)

Nenhuma na Fundação. Gateway, transmissão fiscal, hardware e offline permanecem
nos bloqueios próprios e não impedem o retrofit de ownership.

## Tratamento de Erros

- `401`: token/contexto que deixou de ser válido.
- `403`: contexto ativo sem permissão do domínio.
- `404`: recurso inexistente ou pertencente a outro tenant.
- `400`: payload tenta controlar `organizationId` ou viola contrato fechado.
- `409`: unicidade local, estado ou integridade de negócio.
- `412/428`: versão divergente ou ausente quando o agregado exigir ETag.

## Testes (curl ou equivalente)

Cada módulo deve provar token global 403, permissão insuficiente 403, A não vê B,
payload não troca tenant, mesma chave permitida em organizações diferentes, FK
cross-tenant rejeitada, histórico null preservado/invisível e auditoria correta.
Flyway deve ser testado em banco limpo e em upgrade V37 → latest; Hibernate
`validate` permanece obrigatório.

## Decisões Técnicas

- Coluna por tenant no banco compartilhado.
- Sem filtro Hibernate global; ownership fica explícito em métodos e queries.
- Sem associação automática de dados históricos.
- Sem remoção de menus como substituto de autorização.
- `Usuario.funcionario_id` é compatibilidade; vínculo laboral TARGET usa Membership.
- Ordem de Compra e Ordem de Serviço exigem contrato/ADR próprios.
- `item_pedido` precisa de hardening porque suas referências empresariais não
  possuem hoje FKs tenant-aware.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[membership]]
- [[rbac]]
- [[auditoria]]
- [[instalacoes]]
- [[eventos]]
- [[dados-demo]]
- [[bloqueios]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-08-03 | Consolida a Onda 0, ownership AS-IS/TARGET e estratégia de migração aditiva. |
| 2026-08-03 | Cria suporte de teste para login, Membership, contexto JWT e constraints nomeadas. |
| 2026-08-03 | Conclui a primeira fatia vertical com formas, condições e parcelas tenant-aware. |
| 2026-08-03 | Migra Cliente como primeira raiz de pessoas da Onda 1. |
| 2026-08-03 | Migra Transportadora antes de Fornecedor para fechar a dependência logística. |
| 2026-08-03 | Migra Fornecedor e fecha condition/transportadora com FKs compostas. |
