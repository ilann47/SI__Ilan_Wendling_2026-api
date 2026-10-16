> Links: [[core]] · [[organizacoes]] · [[eventos]] · [[inventario]] · [[produtos-estacionamento]] · [[reservas-inventario]] · [[rbac]]

# Disponibilidade

## Objetivo

Projetar o catálogo de estacionamento vigente de um evento publicado sem
confundir consulta com reserva de inventário.

## Contexto

O saldo projetado subtrai os contadores reservado, vendido e bloqueado do menor
valor entre quota e quantidade do lote vigente. [[reservas-inventario]] atualiza
o reservado atomicamente; a leitura continua sem proteger a última vaga.

## Fluxo (camadas da arquitetura)

```text
GET /api/v1/events/{eventoId}/availability?category=VIP
  -> JWT com tenant ativo
  -> DisponibilidadeEventoService (read-only)
  -> evento PUBLICADO no tenant
  -> projeção direta de produtos PUBLICADO
  -> consulta única de lotes vigentes por todos os produtos
  -> maior prioridade numérica; menor ID desempata
  -> max(0, min(quota, quantidade do lote) - comprometido)
  -> 200 + asOf + guaranteesHold=false + itens
```

## Endpoints (se houver)

- `GET /api/v1/events/{eventoId}/availability`: aceita `category` opcional.
  Filtro por canal permanece TARGET até `QuotaCanal` existir.

## Estrutura de Dados (DTOs, Entidades)

`DisponibilidadeEventoResponse` contém evento, instante UTC da projeção, total,
indicador de ausência de hold e itens. Cada item contém produto, alocação,
categoria/direito, janela de acesso, quantidade, lote e preço vigentes.

Nenhuma entidade ou tabela foi criada; a projeção lê `Evento`,
`ProdutoEstacionamento` e `LotePreco`.

## Integrações externas (se houver)

Não há. A consulta não publica outbox nem escreve auditoria.

## Tratamento de Erros

- `400`: categoria inválida.
- `403`: JWT sem contexto organizacional ativo.
- `404`: evento inexistente ou de outro tenant.
- `409`: evento ainda não publicado.

## Testes (curl ou equivalente)

Os testes cobrem tenant ativo, evento não publicado, recurso invisível, filtro
de categoria, lote vigente, prioridade determinística, saldo e OpenAPI.

## Decisões Técnicas

- A consulta usa duas queries independentemente da quantidade de produtos:
  projeção de produtos e lotes vigentes em lote, evitando N+1 e coleções EAGER.
- Maior número significa maior prioridade; o ID crescente estabiliza o desempate.
- Produto sem lote vigente não aparece como disponível.
- `guaranteesHold=false` é parte do contrato para impedir uso incorreto da
  leitura como confirmação de compra.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[eventos]]
- [[inventario]]
- [[produtos-estacionamento]]
- [[rbac]]
- [[reservas-inventario]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-30 | Implementa projeção tenant-aware de disponibilidade da primeira entrega. |
| 2026-07-30 | Passa a reduzir o saldo pelos contadores de inventário de UC709. |
