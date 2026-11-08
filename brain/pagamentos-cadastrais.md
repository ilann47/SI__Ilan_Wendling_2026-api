> Links: [[core]] · [[unificacao-multitenant]] · [[organizacoes]] · [[membership]] · [[rbac]] · [[testes-multitenancy]]

# Pagamentos Cadastrais

## Objetivo

Manter formas e condições de pagamento reutilizáveis por cada organização, sem
confundi-las com captura, gateway, liquidação ou conciliação financeira.

## Contexto

O pacote `pagamento` contém somente catálogos. A V38 inicia o retrofit do legado
adicionando ownership opcional para preservar linhas históricas e obrigatório
pela aplicação em toda nova escrita contextual.

V85 grava juros, multa, desconto e percentuais de parcela em milionésimos.
Os DTOs aceitam até seis casas, em pontos percentuais; a soma das parcelas
continua exatamente 100. A V99 consolida a persistência no banco local de
teste: prevalece o inteiro salvo; quando ausente, converte-se o percentual
histórico. O conversor JPA grava apenas as colunas `*_micros`, sem colunas
de compatibilidade, e a API não muda de unidade.

## Fluxo (camadas da arquitetura)

```text
JWT contextual + payments:read/manage
  -> FormaPagamentoController ou CondicaoPagamentoController
  -> service(organizacaoId, comando/filtro)
  -> repository por id + organizacaoId
  -> forma/condição/parcela com FK tenant-aware
```

## Endpoints (se houver)

- `GET /api/formas-pagamento` e `GET /api/formas-pagamento/{id}`: `payments:read`.
- `POST|PUT|DELETE /api/formas-pagamento`: `payments:manage`.
- `GET /api/condicoes-pagamento` e `GET /api/condicoes-pagamento/{id}`: `payments:read`.
- `POST|PUT|DELETE /api/condicoes-pagamento`: `payments:manage`.

As URLs foram preservadas. O tenant vem apenas do JWT contextual.

## Estrutura de Dados (DTOs, Entidades)

- `FormaPagamento.organizacaoId`: owner da forma.
- `CondicaoPagamento.organizacaoId`: owner da raiz do agregado.
- `ParcelaCondicaoPagamento.organizacaoId`: reforça as FKs compostas para a
  condição e para a forma opcional.
- DTOs não oferecem campo de organização controlável pelo cliente.

## Integrações externas (se houver)

Nenhuma. Este módulo não representa pagamento transacional.

## Tratamento de Erros

- `401`: token ausente ou inválido.
- `403`: token global ou Membership sem `payments:read/manage`.
- `404`: ID inexistente ou pertencente a outra organização.
- `409`: nome normalizado repetido dentro do tenant ou regra inválida de parcelas.

## Testes (curl ou equivalente)

`PagamentoTenantApiTest` cobre token global, RBAC, isolamento A/B, referência
cruzada, payload hostil e histórico sem owner. `PagamentoTenantConstraintTest`
valida unicidade local e FK composta por SQLSTATE e nome da constraint. Os testes
CRUD anteriores agora usam JWT contextual real.

## Decisões Técnicas

- Mesma forma/condição pode existir em organizações diferentes.
- Editar uma condição atualiza parcelas existentes pelo número, sem reinseri-las
  com a mesma chave. Parcelas retiradas são removidas do cadastro; os cronogramas
  já gravados em notas de mercadoria não são recalculados por essa edição.
- Linhas históricas com `organizacao_id IS NULL` são preservadas e invisíveis às APIs tenant.
- A V38 não faz backfill nem aplica `NOT NULL`.
- A V39 preserva unicidade para linhas históricas, rejeita campos desconhecidos
  e impede parcelas ownerless de apontarem para catálogos tenant.
- `ADMIN_ORGANIZACAO` recebe as duas permissões; demais papéis não recebem acesso implícito.
- Consumers fiscais/financeiros ainda globais são compatibilidade temporária e
  resolvem exclusivamente catálogos históricos sem owner até receberem FKs
  compostas nas respectivas ondas.

## Módulos relacionados

- [[core]]
- [[unificacao-multitenant]]
- [[organizacoes]]
- [[membership]]
- [[rbac]]
- [[testes-multitenancy]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-08-03 | Migra o catálogo de pagamentos para tenancy real na V38. |
| 2026-08-03 | Endurece compatibilidade histórica e ownership de parcelas na V39. |
| 2026-11-05 | Corrige a edição de parcelas preservando sua identidade e rejeita números repetidos; valida que notas já salvas mantêm o cronograma. |
| 2026-11-08 | V99 elimina as colunas de compatibilidade no banco de teste sem recalcular parcelas de documentos. |
