# ADR — Ordem de Servico

**Status:** aceito e implementado
**Data:** 2026-08-14

## Contexto

A unificacao multi-tenant exige uma Ordem de Servico propria, distinta do cadastro de servicos e da Nota de Servico. O agregado precisa preservar intencao, execucao, valores e rastreabilidade antes do faturamento.

## Decisao

`OrdemServico` e raiz tenant-aware com itens que referenciam `Servico` e preservam descricao, quantidade e preco. O fluxo e:

```text
RASCUNHO -> EM_EXECUCAO -> CONCLUIDA
    |              |
    +-> CANCELADA <-+
```

Somente rascunho pode ser editado. Conclusao e cancelamento sao terminais. Toda mutacao exige permissao contextual e, exceto a criacao idempotente, `If-Match` derivado de `@Version`.

A conclusao nao gera Conta a Receber. Uma Nota de Servico interna pode vincular uma unica ordem concluida, com o mesmo tenant, cliente e valor. A emissao da nota continua sendo a origem do recebivel, evitando dupla contabilizacao.

## Consequencias

- A ordem mantem ciclo operacional independente do documento fiscal.
- O PostgreSQL protege ownership, relacionamentos cross-tenant, estados, totais e unicidade do faturamento.
- Gateway fiscal externo permanece fora deste recorte.
- A API fica em `/api/v1/service-orders`; as APIs legadas de servico e Nota de Servico permanecem compativeis.
