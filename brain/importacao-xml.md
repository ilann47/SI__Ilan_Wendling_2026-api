> Links: [[core]] · [[compras]] · [[fiscal-financeiro]] · [[estoque]] · [[fornecedores]] · [[auditoria]]

# Importação e conferência de XML de entrada

## Objetivo

Importar o documento do fornecedor uma vez, conciliar os cadastros e conferir a mercadoria antes de confirmar a nota interna, estoque e obrigação financeira. Importar não significa autorizar documento fiscal.

## Contexto

`fiscal/importacao` coordena os serviços já existentes, sem mudar o momento dos lançamentos de compras. Aceita NF-e 4.00 modelo 55 normal, de saída do fornecedor, destinada ao CNPJ da organização ativa e com moeda BRL. Devoluções, complementos, ajustes e itens fora do total não entram neste fluxo. A interpretação de `tpNF` e `finNFe` segue a [NT oficial da NF-e](https://www.nfe.fazenda.gov.br/Portal/exibirArquivo.aspx?conteudo=%2FRFndRUpZJw%3D).

O leitor valida o subconjunto operacional, não todo o XSD, assinatura, protocolo ou situação na SEFAZ. O arquivo original é preservado. Totais com tributos/componentes ainda não representáveis na nota interna ficam com divergência explícita e confirmação bloqueada; nunca ajustar preço artificialmente para fechar o total.

## Fluxo (camadas da arquitetura)

`ImportacaoNotaController` → `ImportacaoNotaService` → `NotaEntradaXmlParser` / repositories → `OrdemCompraService.receber` quando explicitamente solicitado → `NotaEntradaService.criar/confirmar` → [[estoque]] / [[fiscal-financeiro]].

- `IMPORTADA`: XML identificado, sem estoque ou contas.
- `CONCILIADA`: fornecedor, produto, unidade, local e vínculos persistidos. Alterar conciliação apaga a conferência anterior.
- `CONFERIDA`: todos os itens contados exatamente na quantidade conciliada. Divergência física mantém `CONCILIADA`.
- `CONFIRMADA`: nota vinculada e lançamentos efetivados na mesma transação; terminal, sem edição.

Entrada direta: confirmação da nota lança estoque e contas. Com recebimento já registrado: confirmação da nota gera contas sem repetir estoque. Pedido sem recebimento: exige aceite `registrarNovoRecebimento=true` e `purchases:manage`; o recebimento e a confirmação da nota são uma única transação. Se a criação da nota falhar, recebimento, quantidades, estoque e contas são revertidos juntos.

## Endpoints

Base `/api/notas-entrada/importacoes`, sempre com JWT contextual.

| Método/rota | Corpo | Permissão / efeito |
|---|---|---|
| GET base | `page`, `size` (máximo 50) | `fiscal:read`; Page de resumos, ordenação data/ID descendente, sem carregar XML |
| GET `/{id}` | — | `fiscal:read`; detalhe persistido e ETag |
| POST base | bytes XML, `application/xml`, `text/xml` ou `application/octet-stream` | `fiscal:manage`; 201, Location, ETag; no máximo 2 MiB |
| PUT `/{id}/conciliacao` | fornecedorId, localEstoqueId, condicaoPagamentoId?, ordemCompraId?, recebimentoCompraId?, registrarNovoRecebimento?, itens | `fiscal:manage`; referências de compra exigem `purchases:read` |
| POST `/{id}/conferencia` | itens: sequencia, quantidadeConferida | `fiscal:manage`; persiste contagem sem lançar |
| POST `/{id}/confirmacao` | sem corpo | `fiscal:manage`; adicional `purchases:manage` se registrar recebimento |

As três últimas operações exigem `If-Match: "N"`. ETag deriva exclusivamente de `@Version`. Campo opcional `registrarNovoRecebimento` ausente/null significa false. A confirmação repetida de importação já confirmada retorna o mesmo documento, mesmo com versão anterior; não produz novos efeitos.

Itens de conciliação: `sequencia`, `produtoId`, `fatorConversao`, `valorUnitario`, `itemOrdemCompraId?`, `motivoDivergencia?`. O servidor resolve a unidade do cadastro, calcula quantidade com três casas sem perda e exige preço com duas casas que preserve o valor bruto original. Conversão de unidade/fator e divergência do preço negociado exigem motivo. Item de compra deve corresponder ao produto; recebimento existente deve corresponder a todos os itens/quantidades da importação (não há faturamento parcial de um recebimento nesta entrega).

## Estrutura de Dados (DTOs, Entidades)

V70 cria `importacao_nota_entrada` (XML bytea, SHA-256, chave, tenant, ator, versão, estado e vínculos) e `item_importacao_nota_entrada` (conciliação, contagem e motivo). V71 acrescenta o aceite explícito de novo recebimento. FKs compostas preservam tenant e vínculo item–pedido–produto. `(organizacao_id,chave)` é único; identidade/XML e importação confirmada são protegidos no PostgreSQL. `ImportacaoNotaResponse` inclui documento, conciliação e divergências; a listagem usa `ResumoImportacaoResponse` diretamente.

## Integrações externas (se houver)

Nenhuma chamada externa. Sem provedor fiscal, certificado ou homologação configurados para NF-e/NFS-e, a transmissão permanece bloqueada. São necessários escolha de provedor/contrato, ambiente de homologação, acesso e configuração fiscal específica antes de desenvolver o adapter real. Não utilizar produção.

## Tratamento de Erros

- 400: XML inválido/excessivo, DTD/entidades externas, estruturas/precisões não suportadas, campos ou If-Match malformados.
- 428: If-Match ausente; 412: versão divergente durante edição.
- 403: permissão insuficiente; 404: recurso inexistente ou de outro tenant.
- 409: destinatário incompatível, chave existente com conteúdo diferente, transição inválida, divergência física/valor, inconsistência no recebimento ou nota duplicada.
- DTD, entidades externas, XInclude, acesso externo a schemas/DTDs e profundidade excessiva são bloqueados; upload limitado antes de materialização integral.
- Unicidade no banco + advisory lock por tenant/chave serializam importação. Lock pessimista do agregado serializa edição/confirmação; confirmação é atomicamente coordenada com locks existentes de compra/estoque/nota.

## Testes (curl ou equivalente)

`mvnw.cmd -B -Dtest=NotaEntradaXmlParserTest,ImportacaoNotaEntradaTenantApiTest test` — 11 cenários verdes na rodada focal: parser seguro, finalidade/total, repetição e importação simultânea, confirmação concorrente com/sem recebimento, tenant/RBAC, contagem divergente, total não representável e rollback de recebimento quando nota duplicada falha.

Na UI: Notas de Entrada → Importar XML → selecionar documento → conciliar fornecedor/produtos/local → relacionar recebimento existente ou aceitar novo explicitamente → salvar contagem → confirmar. Use somente documentos de teste compatíveis com a organização selecionada. A interface envia os bytes do arquivo original, preservando encoding/hash.

## Decisões Técnicas

Não criar cadastros automaticamente pelo conteúdo não confiável do XML. Nenhuma alteração nos serviços comerciais para mascarar valores. Nenhum gateway/mensageria externa adicionado. Reimportação de bytes diferentes com a mesma chave exige consultar o registro existente, sem sobrescrever o original. Cadastros e vínculos são sempre resolvidos no tenant ativo.

## Módulos relacionados

[[compras]], [[fiscal-financeiro]], [[estoque]], [[fornecedores]], [[conveniencia]], [[auditoria]], [[rbac]].

## Histórico

| Data | Ação |
|---|---|
| 2026-09-12 | Importação persistida, conciliação, contagem, confirmação atômica e testes de concorrência/rollback. V70/V71; frontend integrado sem transmissão fiscal. |
