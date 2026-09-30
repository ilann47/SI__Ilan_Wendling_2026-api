> Links: [[core]] · [[fornecedores]] · [[conveniencia]] · [[estoque]] · [[fiscal-financeiro]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Compras

## Objetivo

Controlar ordens de compra e seus recebimentos físicos por organização, ligando
Fornecedor, Produto, Local de Estoque, razão de movimentos e Nota de Entrada sem
duplicar efeitos de estoque.

## Contexto

A V63 implementa a Onda 4 da unificação multi-tenant. A ordem nasce em
`RASCUNHO`, pode ser editada, aprovada ou cancelada. Conforme a regra acadêmica
vigente, a entrega aprovada é confirmada integralmente ou rejeitada integralmente;
novos recebimentos parciais não são aceitos. O recebimento é append-only e
antecede opcionalmente a confirmação da Nota de Entrada.

As V72/V73 acrescentam a composição do custo negociado: seguro, outras
despesas, rateios por item, custo final e o código do produto no fornecedor. A
V74 completa os dados comerciais e logísticos da ordem e elimina o desconto
geral, mantendo desconto exclusivamente por item.

As V75/V76 registram fornecedor, número, série e modelo como chave fiscal da
compra, mantêm um número interno automático apenas para compatibilidade e
introduzem o estado terminal `REJEITADA`. A V77 inclui o modelo na unicidade da
Nota de Entrada, alinhando as duas defesas contra duplicidade.

## Fluxo (camadas da arquitetura)

`OrdemCompraController` valida JWT contextual, RBAC, `If-Match` e idempotência.
`OrdemCompraService` resolve as referências do tenant, serializa a ordem,
persiste o recebimento, registra movimentos `RECEBIMENTO` e grava auditoria e
outbox na mesma transação. Uma Nota de Entrada vinculada valida fornecedor,
local, produtos e quantidades do recebimento; sua confirmação gera o financeiro,
mas não movimenta o estoque novamente.

## Endpoints (se houver)

- `GET/POST /api/v1/purchase-orders`
- `GET/PUT /api/v1/purchase-orders/{id}`
- `POST /api/v1/purchase-orders/{id}/approval`
- `POST /api/v1/purchase-orders/{id}/cancellation`
- `POST /api/v1/purchase-orders/{id}/receipt-rejection`
- `GET/POST /api/v1/purchase-orders/{id}/receipts`
- `GET /api/v1/purchase-orders/{id}/documents`: recebimentos, notas, contas a pagar e razão; cada seção exige sua própria permissão de leitura.
- A listagem aceita `numero` (busca parcial sem diferenciar caixa) e `status` (lista), com paginação e total filtrados no PostgreSQL.

## Estrutura de Dados (DTOs, Entidades)

- `OrdemCompra`: agregado versionado, chave fiscal formada por fornecedor,
  número, série e modelo, comprador, condição de
  pagamento, transportadora, local planejado de entrega, responsabilidade do
  frete, referência do fornecedor, observações, moeda, subtotal, frete, seguro,
  outras despesas, total e estado. A coluna legada de desconto geral permanece
  por compatibilidade estrutural, obrigatoriamente com valor zero.
- `ItemOrdemCompra`: produto, código histórico no fornecedor, quantidade
  pedida/recebida, valores, rateios de despesas e custo final.
- `RecebimentoCompra`: entrega idempotente vinculada a Local de Estoque.
- `ItemRecebimentoCompra`: quantidade recebida por item da ordem.
- `NotaEntrada.recebimentoCompra`: vínculo fiscal opcional e único.

## Integrações externas (se houver)

Não há integração externa. Estoque, fiscal, financeiro, auditoria, idempotência e
outbox são módulos internos do monólito.

## Tratamento de Erros

Ausência de tenant ou permissão retorna `401/403`; recurso histórico,
cross-tenant ou pai inválido retorna `404`; `If-Match` ausente ou divergente
retorna `428/412`; transição, quantidade ou vínculo incompatível retorna `409`.

## Testes (curl ou equivalente)

Os testes cobrem ciclo completo, edição com preservação do item, versão
concorrente, bloqueio de recebimento parcial, recebimento/rejeição integral,
idempotência, isolamento, integração
fiscal sem estoque duplicado, despesas rateadas com fechamento exato dos centavos,
custo aplicado ao recebimento, dados comerciais, isolamento das referências,
seed, V1→V74 e Hibernate validate.

## Decisões Técnicas

- A ordem é a fonte da intenção comercial; o recebimento é a fonte do estoque.
- Recebimentos não são editados ou excluídos; novos recebimentos exigem todos os
  itens e exatamente todo o saldo. Divergência física rejeita a entrega inteira,
  sem movimentar estoque; correções posteriores usam o razão de estoque.
- A Nota de Entrada vinculada é a fonte financeira/fiscal e não repete o estoque.
- Frete, seguro e outras despesas são rateados proporcionalmente ao valor líquido
  dos itens. Se todos os valores forem zero, a quantidade é usada como base. O
  último item absorve a diferença de arredondamento para fechar cada despesa.
- O custo unitário recebido considera valor líquido do item e despesas rateadas.
  Desconto é permitido somente no item; aplicação, validação e constraint da V74
  rejeitam qualquer desconto geral diferente de zero.
- Condição de pagamento e transportadora ativas são herdadas do fornecedor
  quando não há escolha explícita. Referências informadas são sempre validadas
  no tenant e o comprador é capturado do usuário autenticado.
- `dataEmissao` permanece como nome legado do campo no contrato para preservar
  compatibilidade, mas representa a data da ordem. A regra acadêmica exige
  fornecedor, número, série e modelo antes dos demais dados; após o primeiro
  produto essa chave fica imutável. A Nota de Entrada vinculada deve repetir a
  mesma chave e sua unicidade também inclui o modelo.
- `numero` continua existindo como identificador técnico legado, gerado pelo
  servidor e oculto do formulário; o usuário não informa número de ordem.
- O código do produto no fornecedor é copiado do vínculo Produto–Fornecedor ao
  salvar o rascunho, preservando o contexto comercial histórico.
- Paginação de ordens busca primeiro IDs e depois o grafo detalhado, evitando
  paginação em memória sobre coleção.
- ETag deriva exclusivamente de `@Version` e mutações exigem `If-Match`.
- O detalhe integrado valida a compra no tenant antes de consultar vínculos. `notas`, `contasPagar` e `movimentosEstoque` retornam `null` sem `fiscal:read`, `finance:read` ou `stock:read`, respectivamente; `[]` indica consulta autorizada sem registros.
- O recebimento é bloqueado ao vincular uma nota, inclusive no endpoint legado. A unicidade persistente da V63 permanece a última defesa contra duas notas para a mesma entrega.
- `POST /api/notas-entrada` aceita `Idempotency-Key` opcional, mantida pelo wizard; a mesma chave/carga recupera o documento, carga diferente retorna 409. A confirmação continua transacional: nota vinculada não soma estoque novamente e gera títulos uma única vez.
- Cancelar/excluir nota tenant-aware usa o mesmo bloqueio de confirmação, impedindo mudanças concorrentes em documento já efetivado.
- A listagem administrativa abre um detalhe próprio da ordem com itens,
  quantidades pedida/recebida/pendente, valores e resumo financeiro; o histórico
  permanece separado por representar entregas físicas já registradas.

## Módulos relacionados

[[fornecedores]], [[conveniencia]], [[estoque]], [[fiscal-financeiro]], [[rbac]],
[[auditoria]], [[idempotencia]] e [[outbox]].

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-09-29 | Adiciona regressão explícita para impedir desconto de item superior a quantidade × valor unitário; a API responde conflito antes de persistir a ordem. |
| 2026-09-29 | Alinha o seed demonstrativo à chave fiscal obrigatória e valida o fluxo completo com 886 testes backend, 360 testes frontend e smoke no navegador. |
| 2026-09-29 | V75–V77 aplicam a chave fiscal na compra, travam sua alteração após os itens, removem recebimento parcial do fluxo novo e adicionam aceitação ou rejeição integral. |
| 2026-09-29 | V74 adiciona comprador, condição de pagamento, transportadora, local planejado, responsabilidade do frete e referências comerciais; proíbe desconto geral na aplicação e no PostgreSQL. |
| 2026-09-25 | V72/V73 adicionam seguro, outras despesas, rateio, custo final e código do produto no fornecedor; recebimento usa o custo final calculado. |
| 2026-09-12 | Conecta documentos e títulos à compra, protege consultas por permissão, estabiliza idempotência de notas e torna filtros de listagem efetivos no servidor. |
| 2026-08-14 | V63 implementa Ordem de Compra, recebimento e integração fiscal/financeira sem estoque duplicado. |
| 2026-08-17 | Frontend passa a expor os itens comprados e o resumo financeiro no detalhe de cada ordem. |
| 2026-09-28 | Valida no navegador a cadeia ordem → recebimento → nota → conta e preserva o custo rateado da compra até a entrada no estoque. |
