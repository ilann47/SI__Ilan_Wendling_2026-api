> Links: [[core]] · [[estoque]] · [[conveniencia]] · [[fornecedores]] · [[pagamentos-cadastrais]] · [[rbac]]

# Fiscal e Financeiro de Entrada

## Objetivo
Registrar notas de entrada/saída por organização, movimentos no razão e títulos financeiros.

## Contexto
A V55 migra `nota_entrada`, `item_nota_entrada` e `conta_pagar` de forma aditiva.
A V63 permite vincular a Nota de Entrada a um recebimento de compra já estocado.
A V77 inclui o modelo na chave única da nota e a Nota de Entrada vinculada passa
a validar fornecedor, número, série e modelo contra a compra de origem.

## Fluxo (camadas da arquitetura)
O JWT define a organização. A criação valida pais do tenant. A confirmação
serializa a nota, registra `RECEBIMENTO` por item e gera parcelas na mesma transação.

## Endpoints (se houver)
- `/api/notas-entrada`: consulta, criação e transições.
- `GET /api/notas-entrada/{id}/detalhes`: visão tenant-aware com documento,
  referência da ordem, recebimento, razão de estoque e contas a pagar.
- `/api/contas-pagar`: consulta, lançamento, baixa e cancelamento.
- `/api/contas-receber`: consulta, lançamento, baixa e cancelamento.
- `GET /api/contas-{pagar|receber}/resumo`: totais SQL do conjunto filtrado completo,
  não da página: `quantidade`, `valorOriginal`, `valorTotal`, `valorBaixado`, `saldo`.
- Listagem e resumo compartilham `search`, `fornecedorId`/`clienteId`, `situacao`,
  `dataVencimentoDe`, `dataVencimentoAte`, `origem` e os vínculos `notaEntradaId`,
  `notaSaidaId`, `notaServicoId`, `vendaAdministrativaId`. `search` procura nome,
  documento, número de nota, observação e ID exato; caracteres de LIKE são escapados.
- `GET /api/contas-{pagar|receber}/{id}/baixas`: histórico interno append-only.
- `POST /api/contas-{pagar|receber}/{id}/baixa`: recebe `{valor,data?}` e
  `Idempotency-Key`; a mesma chave/corpo recupera o título sem repetir a baixa.
  Reutilizar a chave com outro valor/data retorna 409. Header ausente permanece
  compatível com clientes legados, mas não permite recuperação segura de retries.

## Estrutura de Dados (DTOs, Entidades)
Nota e Conta carregam owner e versão; Item carrega owner. Nota tenant exige local.

As respostas de conta incluem `saldo` e `origem {tipo,id,numero,url}`. Recebíveis
também expõem `vendaAdministrativaId`. A V69 adiciona `baixa_financeira` com valor,
data efetiva, ator, instante do registro, chave idempotente e hash do comando.
O acumulado preexistente é preservado como `saldoAnterior=true`; não são inventados
pagamentos individuais, responsáveis nem datas ausentes.

## Integrações externas (se houver)
Não há transmissão fiscal ou pagamento externo; a integração é com [[estoque]].
[[importacao-xml]] prepara e confirma a nota interna sem alegar autorização fiscal. A etapa de transmissão NF-e/NFS-e está bloqueada: não há adapter nem configuração de homologação implementados no módulo atual. Exige escolher provedor, confirmar contrato/cobertura, fornecer acesso/certificado e parâmetros fiscais da empresa/município. Respostas inconclusivas deverão ser consultadas antes de reenviar, sem testes em produção.

## Tratamento de Erros
Cross-tenant retorna 404; duplicidade/transição inválida 409; sem permissão 403.
Nota de entrada repete a validação de unicidade por organização, fornecedor,
número, série e modelo na aplicação e no PostgreSQL. Pela regra acadêmica
vigente, fornecedor/número/série/modelo também identificam previamente a compra;
a nota vinculada não pode divergir desses dados.
No faturamento legado (`organizacaoId=null`), as origens de estacionamento, venda e serviço consultam exclusivamente `organizacao_id IS NULL`. O caminho contextual mantém a organização explícita; ausência de tenant nunca significa consolidar todas as empresas.

## Testes (curl ou equivalente)
V1→V55, Hibernate validate, legado, isolamento, razão e títulos são cobertos.
As listagens de Nota de Entrada e Nota de Saída têm regressão com
`hibernate.query.fail_on_pagination_over_collection_fetch=true`: a primeira
consulta pagina somente os IDs e a segunda carrega o grafo completo da página.

## Decisões Técnicas
- Tenant usa razão V54; `produto.quantidade` permanece histórico.
- Nota vinculada ao recebimento V63 gera Conta a Pagar sem duplicar estoque.
- O detalhe fiscal é uma projeção de leitura separada: notas diretas consultam
  movimentos por origem; notas vinculadas recuperam os movimentos imutáveis do
  recebimento pelos respectivos IDs.
- Relatórios consolidados selecionam contas e documentos pela organização ativa.
- Página e resumo usam os mesmos predicados de `FinanceiroConsultaService`,
  com agregação no PostgreSQL e ordenação determinística. O saldo exclui títulos
  cancelados; valores original/total/baixado representam todos os títulos filtrados.
- Baixa e cancelamento bloqueiam o título com `PESSIMISTIC_WRITE`. O registro de
  baixa, o acumulado e a auditoria pertencem à mesma transação. Constraints de
  unicidade e trigger de ownership/imutabilidade protegem o histórico no banco.
- Título com qualquer valor baixado não pode ser cancelado sem um fluxo explícito
  de compensação (fora deste escopo). Repetir cancelamento já concluído é inofensivo.
- O total manual é validado contra original menos desconto mais juros/multa;
  baixas são positivas, limitadas ao saldo e a duas casas decimais.
- Cancelamento fiscal deve obter os IDs dos títulos e bloqueá-los em ordem crescente
  antes de consultar saldo; não deve reutilizar entidades lidas antes do lock.
- As listas fiscais paginadas não aplicam `Pageable` diretamente sobre o
  `EntityGraph` das coleções. O serviço pagina IDs no PostgreSQL, busca os detalhes
  em lote e recompõe a ordem original, preservando o contrato HTTP sem paginação
  em memória.

## Módulos relacionados
[[estoque]], [[conveniencia]], [[fornecedores]], [[pagamentos-cadastrais]], [[rbac]].

## Histórico (data + ação)
| Data | Ação |
|---|---|
| 2026-09-29 | Remove paginação em memória das listas de notas de entrada e saída com paginação de IDs, carga detalhada em lote e teste que falha ao reintroduzir collection fetch paginado. |
| 2026-09-29 | Mantém a compra demonstrativa vinculada à Nota de Entrada pela mesma chave fiscal e cobre a idempotência do seed em teste de integração. |
| 2026-09-29 | V77 inclui modelo na unicidade e alinha a chave fiscal da Nota de Entrada à compra vinculada. |
| 2026-09-25 | Reforça por teste o bloqueio de nota duplicada por número, série e fornecedor e mantém dados fiscais separados da intenção comercial da compra. |
| 2026-08-04 | Nota de entrada e conta a pagar migradas na V55. |
| 2026-08-04 | Nota de saída e conta a receber migradas na V56. |
| 2026-08-04 | Nota de serviço e faturamento a receber migrados na V57. |
| 2026-08-04 | Despesa Avulsa, baixa e cancelamento migrados na V62. |
| 2026-08-04 | Relatórios gerenciais passam a consolidar operação, fiscal, financeiro e estoque por tenant. |
| 2026-08-14 | Nota de Entrada recebe vínculo opcional ao recebimento de compra e separa efeito físico do financeiro. |
| 2026-08-14 | Nota de Saída pode documentar Venda Administrativa sem duplicar estoque ou recebível. |
| 2026-08-14 | Nota de Serviço passa a faturar uma única [[ordens-servico|Ordem de Serviço]] concluída. |
| 2026-09-11 | Adiciona consulta integrada da Nota de Entrada com compra, recebimento, estoque e contas a pagar. |
| 2026-09-12 | V69 adiciona histórico imutável e idempotência de baixas; financeiro recebe filtros reais, resumo global filtrado e origem/saldo. 25 testes de API/tenant/concorrência e 4 testes de regras passaram; Flyway V69 e Hibernate validate aprovados no banco de teste. |
| 2026-09-12 | Integra geração de notas a partir de vendas/OS e [[importacao-xml]] (V70/V71); mantém separados documento interno e autorização externa indisponível. |
| 2026-09-12 | Regressão detectou soma indevida de NFS tenant no relatório legado; restringidas três consultas de faturamento ao escopo legado e adicionada regressão entre empresas. |
