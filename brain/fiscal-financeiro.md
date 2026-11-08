> Links: [[core]] · [[estoque]] · [[importacao-xml]] · [[pagamentos-cadastrais]] · [[vendas-administrativas]]

# Notas de Entrada, de Saída e financeiro

Regressão corrigida: a Nota de Entrada usa os percentuais do pagamento através de um conversor JPA, sem SQL ambíguo no join.

## Precisão decimal: plano de migração

V96, restrita ao PostgreSQL de teste, elimina os campos decimais duplicados
da Nota de Entrada, XML, estoque, contas a pagar e histórico compartilhado de
baixas. O banco guarda somente `_micros` (escala zero) nos campos migrados.
Registros anteriores usam o inteiro já existente ou a conversão exata do
valor salvo, sem refazer totais, custos, parcelas ou baixas. Resumos e
pendências devolvem o valor usual pela API.

A V97, no mesmo banco de teste, substitui 22 colunas decimais da Nota de
Saída, dos itens, das parcelas e de Contas a Receber por colunas inteiras
`NUMERIC(p,0)`. Número, série e modelo seguem `VARCHAR`. A migração converte o
valor salvo, sem refazer os cálculos nem alterar o histórico de baixas.
Novas notas calculam valores e rateios em seis casas, e confirmam com o
cronograma salvo. Títulos a receber e suas baixas aceitam até seis casas;
resumo e pendências convertem os milionésimos para as unidades usuais da API.

V98 substitui os espelhos da Nota de Serviço por inteiros canônicos, converte
os números dos snapshots JSON de itens/parcelas e preserva sequência,
identidade, valores e revisão histórica. Notas novas calculam ISS e parcelas
com seis casas. V99 aplica a mesma escala às despesas avulsas, às condições
de pagamento e aos valores de tarifas/mensalidades; V100 completa o catálogo
e os pedidos de eventos. Esses cortes também são exclusivos do banco de teste.

### Histórico da transição (V88–V95)

V88–V91, validadas no banco de testes: as novas colunas de Nota de Entrada,
itens, cronograma, estoque e título são aditivas e não têm backfill. O comando
`POST /api/contas-pagar/{id}/baixa` aceita seis casas positivas, conserva o
saldo e grava `baixa_financeira.valor_micros` e
`conta_pagar.valor_pago_micros` (1 = 0,000001), mantendo também o decimal
exato para os leitores atuais. A API retorna valores nas unidades usuais.
As contas a receber e as despesas avulsas não mudaram de validação.
V92–V95 completam a ativação no fluxo de entrada: cabeçalho, itens, descontos,
rateio por maiores restos, parcelas, snapshots de custo médio, estoque e título.
Valores de entrada aceitam seis casas sem arredondamento silencioso. Resultados
de multiplicação e divisão usam HALF_UP na sexta casa; rateio conserva a soma
inteira. Número, série e modelo permanecem VARCHAR. Entrada por XML preserva
o bruto declarado do fornecedor (em centavos) e mantém quantidade, preço
conciliado, rateio e baixa com seis casas.

As colunas `_micros` são canônicas para novas gravações no JPA; as decimais
continuam para consultas SQL e validações. O fallback preserva os valores
históricos onde o inteiro está ausente. Para operar, não misturar versões
antigas e novas gravando simultaneamente. Contas a Receber e Despesas Avulsas
não fazem parte desta etapa.

Etapa histórica: a V84 adicionava cinco colunas geradas, somente no cabeçalho da Nota de
Serviço. São inteiros de escala fixa (`NUMERIC(p,0)`) sincronizados pelo banco.
As colunas originais ainda são a fonte da aplicação; a API não passa a aceitar
seis casas nesta etapa. Nenhum total ou snapshot é recalculado.

- Usar BigDecimal no domínio. Converter na persistência com
  `movePointRight(6)` e escala zero exata; ler com `movePointLeft(6)`.
  R$ 120 vira 120000000; 1,125 unidade vira 1125000; 5,25% vira 5250000.
  Calcular a capacidade de cada coluna preservando os dígitos inteiros atuais.
  Número, série e modelo são identificadores VARCHAR, sem conversão.
- Criar armazenamento paralelo com lista explícita de colunas, preencher por
  multiplicação exata, comparar ida/volta registro por registro e impedir a
  troca se houver divergência ou overflow. Não recalcular totais, parcelas,
  impostos ou custos históricos. Preservar XML e histórico de auditoria.
- Antes de trocar a leitura, cobrir mapeamentos ORM, SQL nativo, relatórios,
  seeds, validações e funções/triggers que dependem da escala. Tratar também
  os valores estruturados de itens e parcelas gravados em JSONB. A API
  continua em unidades de negócio; o fator de armazenamento não chega à UI.
- Ajustar validação, inputs e cálculos de notas, estoque, financeiro e demais
  módulos conjuntamente. Para novos registros, arredondar apenas nos pontos
  explícitos de cálculo e reconciliar a soma de rateios e parcelas. No corte,
  sincronizar escritas concorrentes e ensaiar retorno à leitura anterior sem
  perder registros. Manter as colunas originais até confirmar a reconciliação.

Verificação: `EscalaDecimalMigrationTest`. Cria um esquema descartável em V83
e migra até V87. Confere a nota anterior, os espelhos da V84, a preservação dos
snapshots e dos nove campos VARCHAR nas três espécies de nota. Também verifica
que a V85 não muda os percentuais já gravados em condições legadas.
O teste isolado da ida e volta com BigDecimal não implica aceitação de seis
casas nos formulários ou DTOs existentes.

## Nota de Serviço a partir da OS

- `POST /api/v1/service-orders/{id}/service-note` mantém idempotência e
  prepara a nota com cliente, itens, descontos e totais da OS concluída.
  A nota avulsa aceita `itens[]` com serviço, quantidade (até três casas),
  valor unitário e desconto; o servidor calcula o total. Não movimenta estoque.
- A V83 acrescenta snapshots de itens e parcelas e dados de cancelamento
  sem reprocessar notas antigas. Salvar ou editar congela o cronograma.
  Uma condição sem parcelas mantém o vencimento único na emissão; parcelas
  configuradas são rateadas em centavos pelo maior resto, sem lançar zero.
  Rascunho anterior à V83 precisa ser salvo novamente; notas já emitidas
  mantêm títulos reais e composição antiga, sem inventar linhas.
- `GET /api/notas-servico` filtra por `numero` literal, `clienteId` e
  `situacao` antes de paginar, por organização. `GET /{id}/detalhes` exige
  `fiscal:read` e devolve nota/itens/cronograma; os títulos ficam nulos
  sem `finance:read`.
- No contexto organizacional, editar, emitir, cancelar e excluir exigem
  `If-Match` da revisão. Emitir só aceita pendente com itens, total positivo,
  cronograma salvo e data não futura; cria as mesmas parcelas sob lock e
  na mesma transação. Versão ausente retorna 428; divergente, 412; regra de
  negócio incompatível, 409. Data futura também é recusada ao salvar.
- `POST /{id}/cancelamento` exige `{"motivo":"Cobrança duplicada"}` (até 500
  caracteres). Sem recebimento, cancela os títulos e preserva nota, motivo
  e data; com recebimento, recusa. Só rascunhos podem ser excluídos.
  Não há autorização de NFS-e, estorno externo ou transmissão fiscal.

## Fluxo PARK-0003

NotaEntrada e o documento único de novas entradas. POST cria PENDENTE sem estoque
ou titulos. PUT edita somente PENDENTE com If-Match e recalcula os dados no servidor.
Confirmar exige revisao esperada no contexto tenant, bloqueia a Nota e efetiva
estoque/custo/titulos/auditoria atomicamente. Repeticao de confirmacao retorna409:
o cliente recupera o mesmo ID por GET, sem criar outra Nota ou outro intento.

Cancelamento de pendente nao tem efeito fisico/financeiro; confirmada e imutavel
e nao pode ser cancelada/excluida pelo CRUD. Há reversão interna explícita conforme
as regras abaixo; devolução fiscal e pagamento bancário não são emitidos.

## Reversão interna de nota confirmada

O razão de estoque não oferece compensação avulsa nem ajuste manual. A
reversão que altera estoque e títulos só é executada pela nota de origem.

- POST `/api/notas-entrada/{id}/reversao` e `/api/notas-saida/{id}/reversao`
  recebem `{ "motivo": "Conferência operacional incorreta" }` (até 500 caracteres)
  e `If-Match: "<versão>"`. Exigem organização ativa e as permissões
  `fiscal:manage`, `stock:manage` e `finance:manage` simultaneamente.
- Sob lock da nota, verifica títulos vinculados com o mesmo lock de baixa.
  Qualquer baixa parcial/integral ou total dos títulos divergente bloqueia a
  reversão. Sem baixas, títulos originais passam para CANCELADA sem exclusão.
- Estoque exige um movimento original com snapshots por item, cadeia intacta
  no produto, ausência de compensação prévia e saldos/custo atual conciliados.
  Cria compensação com vínculo ao movimento original, inverte linhas repetidas
  na ordem inversa e restaura custo médio anterior na Entrada. O último preço
  bruto histórico de compra não é reescrito. A Saída não altera o custo médio.
- Nota revertida retorna CANCELADA com `revertidaEm`, `motivoReversao` e novo ETag;
  não pode ser editada, excluída ou revertida novamente. Em caso de timeout,
  consulte o mesmo ID antes de qualquer nova tentativa.
- Tudo ocorre em uma transação com auditoria. V81 é aditiva, sem reprocessar
  documentos antigos. Falhas: 403 sem acesso, 404 fora do tenant, 428 sem
  If-Match, 412 para versão divergente, 400 para motivo/versão inválidos
  e 409 para estado, financeiro ou estoque incompatíveis.

## HTTP — Nota de Entrada

Base /api/notas-entrada, JWT contextual; fiscal:read nas consultas e fiscal:manage
nas mutacoes e previas. GET base aceita numero (contido), situacao e fornecedorId;
IDs sao paginados no banco antes de carregar itens. POST aceita Idempotency-Key;
mesma chave/carga recupera ID, outra carga409. GET/POST/PUT devolvem ETag/versao.
PUT /{id} e POST /{id}/previa exigem If-Match. POST /{id}/confirmacao exige If-Match
no contexto ativo; 428 ausente, 412 revisao velha, 409 estado ja efetivado.
Cancelamento/exclusao preservam compatibilidade e validam If-Match se enviado.

Request preserva os dados do documento, transportadoraId e observacaoInterna.
Sem condicao/transportadora explicitas, herda referencia ativa do fornecedor.
Emissão posterior ao dia corrente é rejeitada com 409 na criação, edição, prévia
e confirmação da Entrada. Se informada, a chegada deve ser igual ou posterior
à emissão. O serviço compartilha a regra com a [[importacao-xml]].
Uma Nota tem um local efetivo e confirma integralmente. Unidade/codigo fornecedor
sao snapshots por item; referencias/cadastros aprovados nao foram remodelados.

POST /previa e POST /{id}/previa retornam {nota,parcelas,valorCustoTotal,
valorObrigacao,diferencaDescontoGeral,custosMedios}, sem
persistir ou lancar. `custosMedios` informa por item saldo/custo atuais, quantidade
e custo da entrada, resultado ponderado, se altera estoque e se e projecao.
Itens retornam unidadeMedida/codigoProdutoFornecedor/custoTotal, alem dos rateios
e custoPrecoFinal existentes. Parcelas incluem numeroParcela/totalParcelas/valor/
dataVencimento/formaPagamentoId/formaPagamentoNome. O cronograma efetivo e congelado
com a revisao da Nota; altera-lo requer editar a pendente e revisar nova versao.
POST /{id}/previa compara a composicao recebida com a revisao salva: mudancas nao
salvas retornam409; sem mudancas retorna exatamente o snapshot persistido, mesmo
com alteracao posterior da condicao. Nova composicao deve passar por PUT primeiro.
Pendente anterior a V78 sem cronograma definido exige PUT explicito, mesmo com
composicao igual, antes de POST /{id}/previa ou confirmacao (409 com orientacao
para salvar). Esse PUT congela parcelas e gera nova versao; nao ha backfill
financeiro em lote nem alteracao de notas confirmadas antigas.

## Cálculos e efeitos — Nota de Entrada

Quantidade aceita3 casas e dinheiro2, sem perda de precisao no ingresso. Total
bruto por linha e HALF_UP(q*unitario,2). Percentual isolado calcula desconto;
valor e percentual contraditorios sao recusados. Total liquido do item nao pode
ser negativo. Desconto geral CONTINUA permitido: reduz a obrigacao; conforme UC401,
o custo e a composicao dos itens+frete+seguro+outras, sem descontar o geral. A previa
explicita essa diferenca. O custo medio ponderado usa o saldo consolidado atual e
o custo unitario final da entrada; documentos anteriores nao sao reprecificados.

RateioMonetario distribui centavos por maiores restos e sequencia estavel, sem
residual negativo. Despesas usam valor liquido ou quantidade se a base monetaria
for zero. CustoTotal do item conserva composicao; custo unitario arredonda2casas.
Parcelas usam o mesmo alocador, mantem soma exata e omitem valores zero; numeracao
e totalParcelas correspondem apenas as obrigacoes efetivas. Total0 gera[] e nenhum
titulo pendente impossivel de pagar. Sem detalhes de condicao, uma obrigacao a vista
na emissao, sem inventar dias/percentuais. Forma e vencimento ficam na previsao.

Nota nova preserva o ultimo custo de aquisicao em `produto.valorCompra`, calcula e
atualiza o custo medio ponderado em `produto.custo` (sem aumentar preco de venda),
e registra RECEBIMENTO por item com origem NOTA_ENTRADA/id:sequencia. A formula e
`(saldoAnterior*custoAnterior + quantidadeEntrada*custoEntrada) / saldoPosterior`,
arredondada HALF_UP em 2 casas.
Tenant usa saldo/razao por local; produto.quantidade legado continua zero.
Locks por produto ordenados reduzem inversao de locks entre notas; locks de
saldo/local e razão append-only continuam canônicos.

V79 adiciona snapshots de saldo/custo anterior e posterior em item_nota_entrada
e movimento_estoque. Para saldo preexistente sem historico V79, o ponto de partida
e o `produto.custo` vigente; nao ha replay arbitrario de movimentos antigos.

## Detalhe, histórico e limites — Nota de Entrada

GET /{id}/detalhes retorna nota, contasPagar, movimentosEstoque e previa. Seções
sem permissão ficam null, não uma coleção vazia.
Razao inclui compensacoes vinculadas aos movimentos originais. Previa do detalhe
financeiro exige finance:read; previa de comando exige fiscal:manage.
Contas a pagar mantem baixa interna idempotente/historico V69 e cancelamento so
sem qualquer baixa. Historico pago nao e apagado ou inventado.

Em Contas a Pagar e a Receber, a baixa (inclusive a repetição idempotente) e
o cancelamento montam a resposta dentro da mesma transação da operação. Assim,
fornecedor ou cliente e documento de origem podem ser lidos antes de encerrar
a sessão JPA, sem alterar o formato da resposta nem o lock do título.

V78 adiciona snapshots, cronograma e índice normalizado.
Nao modifica V1-V77, nao relanca documentos existentes, nao aplica custos novos
a itens antigos e recusa upgrade com colisao semantica de identidade sem apagar
dados. Testes de upgrade e fluxo sao executados somente pelo Global no banco
exclusivo sintetico; logs e aceite de execucao pertencem a iniciativa do HUB.

## Fluxo e contrato V80 — Nota de Saída

O número da nota pertence ao emitente, não ao destinatário: no tenant a chave
é organização + número + série + modelo normalizados. O cliente continua
obrigatório e pertence à mesma organização. O legado sem organização mantém a
chave número + série + cliente. V80 não renumera notas nem oculta colisões;
uma colisão preexistente deve ser tratada explicitamente para aplicar a migração.

| Operação | Comportamento |
|---|---|
| 2026-11-06 | Aplica RN003 do modelo acadêmico: bloqueia emissão futura na Entrada manual e XML, incluindo confirmação de rascunho preexistente. |
| 2026-11-06 | V83: itens, conferência versionada e parcelas salvas na Nota de Serviço, consulta financeira vinculada e cancelamento com motivo. |
| 2026-11-07 | Ensaio de conversão aditiva para milionésimos; banco da aplicação permanece inalterado. |
| 2026-11-07 | V84 no código: espelhos inteiros no cabeçalho da Nota de Serviço, sem mudar a fonte da aplicação. |
| 2026-11-07 | V88–V91: expansão sem backfill e baixa de Conta a Pagar de seis casas comprovada no PostgreSQL de testes. |
| GET `/api/notas-saida` | Lista paginada por organização; aceita `numero` (trecho) e `situacao`. Exige `fiscal:read`. |
| POST `/api/notas-saida` | Cria nota `PENDENTE`, sem estoque ou títulos; exige `fiscal:manage`. Campos desconhecidos retornam 400. `Idempotency-Key` opcional com organização ativa: a mesma carga recupera o registro; carga divergente retorna 409. |
| PUT `/api/notas-saida/{id}` | Edita apenas a nota pendente, com a mesma estrutura de criação e `If-Match`; exige `fiscal:manage`. Revalida e congela o novo cronograma. |
| POST `/api/notas-saida/{id}/confirmacao` | Efetiva a revisão pendente. No tenant, exige `If-Match` e `fiscal:manage`; confirmação legada sem organização preserva a compatibilidade sem cabeçalho. |
| POST `/api/notas-saida/{id}/cancelamento` e DELETE `/api/notas-saida/{id}` | Não desfazem efeitos confirmados nem apagam histórico revertido. No tenant, exigem `If-Match` e `fiscal:manage`. |
| GET `/api/notas-saida/{id}/detalhes` | Com `fiscal:read`, retorna `nota`, `parcelas`, `contasReceber` e `movimentosEstoque`. Parcelas e contas são `null` sem `finance:read`; movimentos são `null` sem `stock:read`. Lista vazia significa consulta autorizada sem registros. |

A resposta da nota expõe `version`; GET individual, POST de criação, PUT e
confirmação e cancelamento retornam `ETag`. Confirmação e cancelamento montam
a resposta dentro da transação para não retornar erro após gravar a operação.
Ausência de `If-Match` obrigatório: 428; versão
divergente: 412; estado incompatível ou identidade duplicada: 409. Uma saída
avulsa contextual anterior à V80 ainda pendente, sem cronograma definido, exige
salvar a revisão por PUT, mesmo sem mudar a composição. Não se inventam parcelas
para notas antigas confirmadas.

### Cálculos e origem dos efeitos

- Quantidade aceita até três casas, dinheiro até duas, sem arredondar o valor
  digitado para encaixá-lo. Bruto é quantidade × valor unitário arredondado
  para centavos; desconto do item pode ser valor ou percentual. Ambos devem
  concordar quando preenchidos. Despesas rateadas e parcelas fecham em
  centavos pelo `RateioMonetario`; desconto geral não pode deixar total negativo.
- Na avulsa, a revisão congela parcelas efetivas: número, valor, vencimento e
  referência/nome da forma de pagamento. Editar o cadastro da condição depois
  não altera essa revisão. Sem detalhes de condição, há um vencimento na data
  de emissão; total zero não gera título. A confirmação usa as parcelas salvas,
  baixa o local informado e registra o custo vigente sob lock do produto,
  sem recalcular o custo médio por ser saída.
- A V82 remove os vínculos do antigo Pedido de Venda em Nota de Saída e Conta
  a Receber. A Nota de Saída é a única origem de baixa e cobrança de mercadoria.

## Testes

`NotaEntradaFluxoUnicoTenantApiTest` valida previa, confirmacao, snapshot do item
e custo final do Produto. `EstoqueTenantApiTest` valida o snapshot do movimento;
`CustoMedioPonderadoTest` cobre a aritmetica isolada.

`NotaSaidaTenantApiTest` cobre identidade do emitente, cronograma congelado,
versão, valores e custo no momento da baixa. `ReversaoNotaTenantApiTest` exercita
reversão integral, itens repetidos, bloqueio de baixa parcial, movimento
posterior e nota vinculada, além da precondição de versão.
Sem sessão JPA na camada web, o teste também cobre baixa parcial e total,
repetição da mesma chave, conflito por carga diferente e cancelamentos de
títulos fiscais, além de confirmação e cancelamento da Nota de Saída.

## Historico

| Data | Acao |
|---|---|
| 2026-11-08 | V96 remove os campos decimais duplicados; confirma entrada, consulta estoque e quita título com escala de milionésimos no banco de teste. |
| 2026-11-08 | V97 testa Nota de Saída, estoque, resumo, baixa parcial idempotente e quitação; preserva notas, parcelas e recebimentos históricos. |
| 2026-11-08 | V98 migra Ordem/Nota de Serviço, ISS e snapshots sem recálculo; V99–V100 removem colunas decimais de compatibilidade dos demais fluxos. |
| 2026-11-08 | Valida nota nova em seis casas até a quitação e preserva valores, parcelas e identidade histórica na migração V87 → V95. |
| 2026-11-06 | Monta a resposta de baixas e cancelamentos financeiros e de confirmação e cancelamento de Nota de Saída antes de encerrar a transação. |
| 2026-11-05 | V80 documenta chave de saída do emitente, cronograma congelado, edição versionada e consulta dos lançamentos com RBAC. |
| 2026-11-05 | V81 adiciona reversão interna transacional de notas confirmadas avulsas e preserva título, razão e auditoria. |
| 2026-11-05 | V82 remove o esquema e o contrato residual do Pedido de Venda; a conferência consulta somente os efeitos da própria nota. |
| 2026-11-05 | Novas vendas de mercadoria são lançadas pela Nota de Saída avulsa; pedidos e novos vínculos removidos, efeitos históricos preservados. |
| 2026-10-30 | Remove do contrato de prévia o marcador legado de estoque já movimentado; toda entrada nova nasce e é efetivada pela própria Nota. |
| 2026-10-30 | Remove Ordem/Recebimento de Compra; Nota de Entrada passa a ser o único fluxo de entrada. |
| 2026-10-23 | V79 aplica custo medio ponderado por item na Nota nova e preserva a memoria do calculo. |
