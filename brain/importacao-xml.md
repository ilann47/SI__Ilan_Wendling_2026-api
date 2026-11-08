> Links: [[core]] · [[fiscal-financeiro]] · [[estoque]] · [[fornecedores]] · [[auditoria]]

# Importação e conferência de XML de entrada

## Objetivo

Importar o documento do fornecedor uma vez, conciliar os cadastros e conferir a mercadoria antes de confirmar a nota interna, estoque e obrigação financeira. Importar não significa autorizar documento fiscal.

## Contexto

`fiscal/importacao` coordena os serviços já existentes, sem mudar o momento dos lançamentos de compras. Aceita NF-e 4.00 modelo 55 normal, de saída do fornecedor, destinada ao CNPJ da organização ativa e com moeda BRL. Devoluções, complementos, ajustes e itens fora do total não entram neste fluxo. A interpretação de `tpNF` e `finNFe` segue a [NT oficial da NF-e](https://www.nfe.fazenda.gov.br/Portal/exibirArquivo.aspx?conteudo=%2FRFndRUpZJw%3D).

O leitor valida o subconjunto operacional, não todo o XSD, assinatura, protocolo ou situação na SEFAZ. O arquivo original é preservado. Totais com tributos/componentes ainda não representáveis na nota interna ficam com divergência explícita e confirmação bloqueada; nunca ajustar preço artificialmente para fechar o total.

Uma nova importação com emissão futura é recusada antes de persistir o XML.
Conferência e confirmação usam a mesma regra canônica da Nota de Entrada.
Conciliação e conferência usam até seis casas para fator, quantidade e preço;
na V96 os campos conciliados e o cronograma têm apenas inteiros escalados.
O bruto e o total do XML não são
recalculados com uma precisão diferente da declarada pelo fornecedor.

## Fluxo (camadas da arquitetura)

`ImportacaoNotaController` → `ImportacaoNotaService` → `NotaEntradaXmlParser` /
repositories → `NotaEntradaService.criar/confirmar` → [[estoque]] / [[fiscal-financeiro]].

- `IMPORTADA`: XML identificado, sem estoque ou contas.
- `CONCILIADA`: fornecedor, produto, unidade, local e vínculos persistidos. Alterar conciliação apaga a conferência anterior.
- `CONFERIDA`: todos os itens contados exatamente na quantidade conciliada. Divergência física mantém `CONCILIADA`.
- `CONFIRMADA`: nota vinculada e lançamentos efetivados na mesma transação; terminal, sem edição.

Confirma a mesma Nota canônica e gera estoque/contas uma vez. Não existe vínculo
com Ordem ou Recebimento de Compra, nem parâmetros históricos. Falha de
confirmação reverte todos os efeitos da transação.

GET /api/notas-entrada/importacoes/{id}/previa exige fiscal:manage e status
CONFERIDA (ou CONFIRMADA para consultar o resumo da Nota persistida). O cronograma
e congelado pela conferencia salva e reutilizado na previa/confirmacao, mesmo
se o cadastro da condicao mudar depois. Conciliar/refazer conferencia invalida e
substitui a previsao com nova versao. Conferencia antiga sem snapshot precisa ser
refeita antes de confirmar. Retorna o
mesmo PreviaNotaEntradaResponse do manual; usa emissao do XML e chegada da
conferencia salva, sem datas inventadas pelo cliente. IMPORTADA/CONCILIADA ou
componentes nao representaveis retornam409 sem efeitos. Previa e confirmacao
compartilham o montador de NotaEntradaRequest e o calculo canonico.

## Endpoints

Base `/api/notas-entrada/importacoes`, sempre com JWT contextual.

| Método/rota | Corpo | Permissão / efeito |
|---|---|---|
| GET base | `page`, `size` (máximo 50) | `fiscal:read`; Page de resumos, ordenação data/ID descendente, sem carregar XML |
| GET `/{id}` | — | `fiscal:read`; detalhe persistido e ETag |
| POST base | bytes XML, `application/xml`, `text/xml` ou `application/octet-stream` | `fiscal:manage`; 201, Location, ETag; no máximo 2 MiB |
| PUT `/{id}/conciliacao` | fornecedorId, localEstoqueId, condicaoPagamentoId?, itens | `fiscal:manage` |
| POST `/{id}/conferencia` | itens: sequencia, quantidadeConferida | `fiscal:manage`; persiste contagem sem lançar |
| GET `/{id}/previa` | sem corpo | `fiscal:manage`; resumo canonico e cronograma congelado apos conferencia, sem lancamentos |
| POST `/{id}/confirmacao` | sem corpo | `fiscal:manage`; gera Nota, estoque e contas uma única vez |

Conciliação, conferência e confirmação exigem `If-Match: "N"`. ETag deriva
exclusivamente de `@Version`. A confirmação repetida de importação já confirmada
retorna o mesmo documento, mesmo com versão anterior; não produz novos efeitos.

Itens de conciliação: `sequencia`, `produtoId`, `fatorConversao`, `valorUnitario`
e `motivoDivergencia?`. O servidor resolve a unidade do cadastro, calcula
quantidade com três casas sem perda e exige preço com duas casas que preserve o
valor bruto original. Conversão de unidade/fator e divergência exigem motivo.

## Estrutura de Dados (DTOs, Entidades)

V70 cria `importacao_nota_entrada` (XML bytea, SHA-256, chave, tenant, ator,
versão e estado) e `item_importacao_nota_entrada` (conciliação, contagem e motivo).
`(organizacao_id,chave)` é único; identidade/XML e importação confirmada são
protegidos no PostgreSQL. `ImportacaoNotaResponse` inclui documento, conciliação
e divergências; a listagem usa `ResumoImportacaoResponse` diretamente.

## Integrações externas (se houver)

Nenhuma chamada externa. Sem provedor fiscal, certificado ou homologação configurados para NF-e/NFS-e, a transmissão permanece bloqueada. São necessários escolha de provedor/contrato, ambiente de homologação, acesso e configuração fiscal específica antes de desenvolver o adapter real. Não utilizar produção.

## Tratamento de Erros

- 400: XML inválido/excessivo, DTD/entidades externas, estruturas/precisões não suportadas, campos ou If-Match malformados.
- 428: If-Match ausente; 412: versão divergente durante edição.
- 403: permissão insuficiente; 404: recurso inexistente ou de outro tenant.
- 409: destinatário incompatível, chave existente com conteúdo diferente, transição inválida, divergência física/valor ou nota duplicada.
- DTD, entidades externas, XInclude, acesso externo a schemas/DTDs e profundidade excessiva são bloqueados; upload limitado antes de materialização integral.
- Unicidade no banco + advisory lock por tenant/chave serializam importação. Lock pessimista do agregado serializa edição/confirmação; confirmação é atomicamente coordenada com os locks de estoque e nota.

## Testes (curl ou equivalente)

As regressões atuais cobrem Nota direta, concorrência, tenant/RBAC, rollback e
cronograma congelado após conferência.

Na UI: Notas de Entrada → Importar XML → selecionar documento → conciliar
fornecedor/produtos/local → salvar contagem → revisar a prévia autoritativa de
custos e parcelas → confirmar. Use somente documentos de teste compatíveis com a
organização selecionada. A interface envia os bytes do arquivo original,
preservando encoding/hash.

## Decisões Técnicas

Não criar cadastros automaticamente pelo conteúdo não confiável do XML. Nenhuma alteração nos serviços comerciais para mascarar valores. Nenhum gateway/mensageria externa adicionado. Reimportação de bytes diferentes com a mesma chave exige consultar o registro existente, sem sobrescrever o original. Cadastros e vínculos são sempre resolvidos no tenant ativo.

## Módulos relacionados

[[fiscal-financeiro]], [[estoque]], [[fornecedores]], [[conveniencia]], [[auditoria]], [[rbac]].

## Histórico

| Data | Ação |
|---|---|
| 2026-11-08 | V96 conserva o arquivo original e os valores confirmados, removendo as colunas decimais duplicadas. |
| 2026-11-08 | V95 grava conciliação e parcelas em milionésimos, preservando o XML e seu total fiscal. |
| 2026-11-06 | Rejeita nova importação com emissão futura antes de persistir o arquivo; preserva o retorno idempotente de importação já existente. |
| 2026-10-30 | Remove parâmetros e vínculos de Ordem/Recebimento; confirmação cria somente a Nota direta. |
| 2026-09-12 | Importação persistida, conciliação, contagem, confirmação atômica e testes de concorrência/rollback. V70/V71; frontend integrado sem transmissão fiscal. |
