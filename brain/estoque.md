> Links: [[core]] · [[conveniencia]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]] · [[unificacao-multitenant]] · [[fiscal-financeiro]] · [[vendas-administrativas]]

# Estoque

V96 conclui o corte de saldo, razão, mínimo e custo: o banco de teste guarda
apenas inteiros em milionésimos. Consultas de posição e razão devolvem a
unidade usual sem arredondar. As verificações de cadeia, compensação e
inativação com saldo operam diretamente sobre os inteiros.

Custo médio da entrada nova usa seis casas e congela o cálculo no item e no
movimento. Dados históricos não são reprocessados. Para produtos contextuais,
o saldo físico continua apenas no razão/local; o saldo legado no cadastro
continua protegido em zero.

## Objetivo

Manter o saldo físico tenant-aware por Produto e Local em um razão imutável,
sem permitir saldo negativo ou misturar organizações.

## Contexto

A V54 torna o razão canônico somente para Produto tenant-aware. O campo legado
`produto.quantidade` permanece histórico e é congelado em zero nesse recorte.
Local de Estoque é lógico e pertence diretamente à organização.

## Fluxo (camadas da arquitetura)

O estoque consulta saldo e razão e administra locais. A Nota de Entrada
confirmada recebe mercadorias; a Nota de Saída confirmada baixa mercadorias.
A reversão interna só ocorre pela nota de origem e preserva o razão.
Services validam Produto/Local ativos do mesmo tenant, serializam por Produto
e saldo e registram [[auditoria]] e [[outbox]] na mesma transação.
PUT/DELETE de Local obtêm lock pessimista de escrita antes de conferir versão e
saldo; movimentações mantêm lock pessimista de leitura no Local. Assim recebimento e
inativação concorrentes não deixam saldo em Local inativo.

## Endpoints (se houver)

- `/api/v1/stock-locations`: CRUD lógico com `ETag`/`If-Match`.
- `/api/v1/stock-balances`: saldo paginado por Produto/Local.
- `/api/v1/stock-positions`: posição agregada e filtro abaixo do mínimo.
- `GET /api/v1/stock-movements`: razão paginado e detalhe; sem mutação direta.
- `GET /api/v1/stock-movements/{id}/origin`: descrição e caminho do documento real; sem ID/número/caminho quando falta permissão de origem.
- Os antigos POST de ajuste e compensação avulsa não existem. Reversão usa
  os comandos fiscais documentados em [[fiscal-financeiro]].

## Estrutura de Dados (DTOs, Entidades)

- `LocalEstoque`: nome único normalizado no tenant, ativo e versionado.
- `SaldoEstoque`: uma linha por organização, Produto e Local; quantidade não negativa.
- `MovimentoEstoque`: append-only, tipo, delta, saldo local anterior/posterior,
  saldo total anterior/posterior, custo unitário da operação, custo médio anterior/
  posterior, origem, ator, motivo e vínculo de compensação.
- Tipos: `RECEBIMENTO`, `BAIXA` e `COMPENSACAO` de reversão fiscal.
  `AJUSTE` e compensações avulsas antigas permanecem legíveis no histórico.
- `origem_tipo` + `origem_chave` identificam o documento operacional
  (por exemplo, `NOTA_SAIDA` e `id:sequência`); não são FK física para uma
  tabela fiscal. Já `compensado_id` referencia o movimento original.

## Integrações externas (se houver)

Não há broker nem integração externa. Eventos oficiais usam a outbox PostgreSQL
existente; auditoria e idempotência participam da mesma transação.

## Tratamento de Erros

Sem contexto/permissão retorna `401/403`; histórico/cross-tenant retorna `404`;
If-Match ausente, inválido ou divergente retorna `428/400/412`; saldo insuficiente,
compensação repetida e chave idempotente divergente retornam `409`.
Produto com saldo não zero e Local com qualquer saldo não zero não podem ser
inativados. Concorrência otimista de Local também é traduzida para `412`.

## Testes (curl ou equivalente)

Há testes de constraints PostgreSQL, isolamento/RBAC/API, conteúdo de
auditoria/outbox, reversão fiscal, limiar agregado, cadeia do razão, concorrência
sobre o mesmo saldo, recebimento versus inativação e dois PUTs com o mesmo ETag.
Mesmo um administrador não consegue usar as antigas rotas de mutação.
`CustoMedioPonderadoTest` cobre primeira entrada, ponderação, quantidade fracionada
e entradas inválidas. `EstoqueTenantApiTest` comprova 10 unidades a R$ 20,00 mais
5 a R$ 30,00 resultando em saldo 15 e custo médio R$ 23,33 com snapshot no razão.
Rastreabilidade atual valida ator nominal, Nota de Entrada real do mesmo tenant,
cross-tenant 404 e ausência de consulta/IDs fiscais sem permissão.

## Decisões Técnicas

- Locks consultivos por Produto combinados com lock pessimista do saldo.
- Constraint triggers diferidos validam, no estado final da transação, a cadeia
  iniciada em zero e a igualdade entre último movimento e saldo materializado.
- Movimento append-only e compensação, nunca edição/exclusão.
- A projeção inclui `atorNome` resolvido pelo usuário global associado ao movimento; representa o nome atual da conta, não um snapshot histórico. Nenhum registro de movimento é alterado.
- Origem é resolvida somente ao abrir o detalhe. Nota de entrada/saída exige
  `fiscal:read` e compensação navega ao movimento original.
- Compensação não pode ser autorreferente, compensar outra compensação nem usar
  delta diferente do inverso exato do original.
- Origem única inclui Produto, Local e tipo para suportar documentos multi-item.
- Local só pode ser inativado com todos os saldos zerados.
- Produto tenant-aware só pode ser inativado com posição agregada zerada.
- Eventos normais expõem allowlist completa do movimento; o alerta de mínimo
  contém somente identificação do produto, movimento, total e mínimo.
- Nota de Entrada nova usa o recebimento V79 como origem física e recalcula
  `produto.custo` pelo saldo consolidado de todos os locais. O cálculo ocorre sob
  lock do Produto e grava saldo total/custo médio antes e depois no movimento.
- Nota de Saída avulsa usa a baixa canônica. Bloqueia os produtos em ordem
  estável antes de baixar e lê o custo vigente dentro do lock, sem alterá-lo.
- Na reversão fiscal V81, movimentos da nota são o sufixo intacto do razão por Produto
  no tenant; IDs definem a ordem, sem depender de empates de timestamp.
  Notas de entrada restauram o custo médio anterior pelo snapshot verificado,
  sem modificar movimentos originais. Transferência e PDV permanecem fora
  deste recorte.

## Módulos relacionados

[[conveniencia]] fornece Produto tenant-aware; [[rbac]] autoriza; [[auditoria]],
[[idempotencia]] e [[outbox]] registram os efeitos transacionais. Os documentos
que originam entrada e baixa são descritos em [[fiscal-financeiro]].

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-11-08 | V96 converte os valores já registrados e mantém as proteções do razão sem as colunas duplicadas. |
| 2026-11-08 | Ativa milionésimos no saldo, no razão e no custo médio das novas entradas; mantém a verificação da cadeia. |
| 2026-11-06 | Remove ajuste e compensação avulsa dos comandos públicos; estoque muda por nota confirmada/reversão, sem apagar histórico. Transferência não foi implementada. |
| 2026-11-05 | V82 retira a origem operacional de Pedido de Venda; o razão segue sem alteração de movimentos preexistentes. |
| 2026-11-05 | Pedido de Venda descontinuado: baixas antigas seguem no razão, sem navegação ao pedido ou compensação isolada. |
| 2026-11-05 | Na confirmação da Nota de Entrada, recarrega o produto sob o lock do razão antes de calcular o custo médio; mantém o último preço bruto registrado no documento. |
| 2026-11-05 | Explicita custo da baixa sob lock do produto para saídas avulsas e vendas; documenta origem lógica do movimento e FK física da compensação. |
| 2026-11-05 | Compensação de movimento fiscal passa pelo comando atômico da nota; V81 indexa a sequência do razão por tenant e Produto. |
| 2026-10-30 | Remove origens e vínculos de Ordem/Recebimento de Compra; entradas físicas passam a nascer exclusivamente da Nota de Entrada. |
| 2026-10-23 | V79 registra saldo total e custo médio anterior/posterior nos recebimentos de Nota de Entrada. |
| 2026-08-03 | Fundação tenant-aware de estoque e razão adicionada na V54. |
| 2026-08-03 | Hardenings P1 de concorrência, cadeia do razão, compensação, idempotência e payloads aplicados. |
| 2026-08-14 | Recebimento de Ordem de Compra passa a registrar entrada física idempotente no razão. |
| 2026-08-14 | Confirmação de Venda Administrativa passa a registrar baixa física no razão. |
| 2026-09-12 | Expõe responsável nominal e navegação tenant-aware/RBAC ao documento de origem, preservando imutabilidade e saldo. |
| 2026-09-28 | Resolve notas legadas pelo número somente quando a chave é realmente legada; chaves estruturadas inválidas não disparam consulta nem inferência. |
