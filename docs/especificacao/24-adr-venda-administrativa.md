> Links: [[README]] · [[22-unificacao-multitenant]] · [[21-status-implementacao]]

# 24. ADR — Venda Administrativa

## Status

Histórica. Implementada em 2026-08-14 pela migration V64, substituída em
2026-11-05 pelo lançamento direto de mercadorias na Nota de Saída avulsa.
A V82 também removeu o esquema do módulo. Este ADR é histórico.

## Contexto

UC724 exige venda fora do PDV com estoque, financeiro e fiscal correlacionados,
sem transformar Nota de Saída em pedido comercial ou repetir efeitos.

## Decisão

`VendaAdministrativa` é o agregado da intenção e execução comercial. Possui os
estados `RASCUNHO`, `CONFIRMADA` e `CANCELADA`. A confirmação é transacional:
baixa os itens no razão e cria contas a receber conforme a condição de pagamento.

A Nota de Saída pode referenciar uma venda confirmada uma única vez. Cliente,
local, produtos, quantidades, valores e total devem coincidir. Nesse vínculo, a
nota não baixa estoque nem cria novos títulos, pois esses efeitos pertencem à
venda. Transmissão fiscal e PDV não fazem parte desta decisão.

## Consequências

Venda, movimentos e títulos compartilham origem rastreável; falha de estoque
reverte a confirmação; documentos fiscais podem ser criados posteriormente sem
duplicidade; cancelamento após confirmação exige fluxo compensatório futuro.
