> Links: [[brain/core]] · [[brain/vendas-administrativas]] · [[brain/estoque]] · [[brain/ordens-servico]] · [[brain/fiscal-financeiro]]

# Estado do Projeto

**Última atualização:** 2026-11-08
**Fase atual:** estoque de mercadorias movimentado por documento fiscal.

## Corte operacional da plataforma — 2026-11-08

- V98 converte a Ordem de Serviço, o preço padrão de catálogo e a Nota de
  Serviço (inclusive ISS, itens e parcelas salvos) sem refazer cálculos.
  OS concluída prepara a nota, a emissão cria o título e a baixa recebe
  seis casas. Número, série e modelo seguem VARCHAR.
- V99 migra despesas avulsas, condições/parcelas, RH, limites de crédito,
  capacidade da frota, tarifas, mensalidades e valores do pátio.
  V100 migra catálogo, custos por fornecedor e preços/totais de eventos.
- V100 aplicada somente no banco fiscal local de teste. O esquema público
  não contém colunas `NUMERIC` fracionárias. XML, auditoria e contratos
  históricos de integração permanecem no formato original; contagens
  discretas e identificadores não foram escalados.
- A suíte de integração tem cenário da OS à quitação, com precisão do
  ISS e dos snapshots, rejeição de sete casas e baixa idempotente.
- Validação: 916 testes Maven na suíte ampla; 13 testes de serviço e JAR
  revalidados após corrigir a resposta das transições com sessão fechada.
  Frontend: 439 testes, typecheck, lint e build.
- Chromium local :5176 / :8089: OS #357, Nota de Serviço #326 e título
  #943 da organização sintética #8979. Total 2,000002, ISS 0,102469 e duas
  baixas de 0,000001 e 2,000001, sem duplicação e com saldo zero.
  SELECT confirmou 113 colunas `NUMERIC` de escala zero, nenhuma
  fracionária, nove identificadores fiscais VARCHAR e inteiros nos snapshots.

## Nota de Saída e Contas a Receber em escala zero — 2026-11-08

- V97, restrita ao mesmo banco fiscal local de teste, converte 22 campos de
  cabeçalho, itens, parcelas e títulos, sem colunas decimais em paralelo.
  A migração preserva valores salvos, identidade VARCHAR, situação, revisão,
  datas e baixas históricas; não recalcula notas.
- Notas novas calculam rateios e cronograma em milionésimos; a baixa a receber,
  o saldo, o resumo e a interface mantêm as seis casas nas unidades usuais.
- QA em Chromium no frontend :5176 e backend :8089, organização de teste #6331:
  Nota de Saída #526 totalizou 2,000002, retirou 1,000001 do estoque e gerou
  o título #760. Baixas 0,000001 e 2,000001 quitaram o saldo; repetição não
  duplicou a primeira baixa e a sétima casa foi rejeitada. SELECT confirmou
  total inteiro 2000002, saldo zero e baixas 1 e 2000001.
- Verificação: suíte Maven completa com 908 testes; Maven verify direcionado
  com 28 testes e JAR. Frontend com typecheck, lint, 430 testes e build.
  Nenhum outro banco foi migrado.

## Corte de persistência para escala zero — 2026-11-08

- V96 elimina os campos decimais duplicados da Nota de Entrada manual/XML,
  estoque (incluindo quantidade, mínimo e custo do produto), contas a pagar
  e tabela de baixas. Os campos migrados existem apenas como `NUMERIC(p,0)`.
- A migração usa o inteiro já gravado ou converte o valor salvo multiplicando
  por um milhão; não dispara confirmação nem reprocessa notas, custos,
  parcelas ou baixas. A API continua devolvendo valores em unidades usuais.
- Verificação final: Maven verify com 173 testes aprovados e JAR gerado.
  O ensaio de migração verifica os 46 campos de escala zero e o histórico.
  O teste integrado cobre confirmação, estoque, resumo, baixa e quitação.
- QA ao vivo em Chromium via Playwright no frontend :5176 / backend :8089:
  organização sintética QA-V96-MW7B2X62, nota e título #615. Estoque de
  1,000001; título de 2,000002; baixas de 0,000001 e 2,000001. A repetição
  da primeira baixa não duplicou o registro, sete casas foram rejeitadas e
  a quitação terminou com saldo zero. Inteiros conferidos por SELECT na V96.
- Local de migração separado (`db/test-cutover`) e execução limitada ao
  PostgreSQL local `estacionamento_kaneko_fiscal_20261105_test`. O artefato
  novo requer o esquema V96 e não foi iniciado contra o banco do serviço
  normal. Qualquer promoção para outro ambiente fica fora desta etapa.

## Precisão decimal — 2026-11-08

- V92–V95 ativam seis casas para Nota de Entrada manual/XML, saldo, razão,
  custo de aquisição e contas a pagar. Novas gravações em milionésimos
  (`NUMERIC(p,0)`); leitura prefere o inteiro e recorre ao decimal histórico.
  Os decimais também são escritos para manter consultas SQL e restrições
  existentes. Não há backfill, recálculo ou atualização de notas históricas.
- A regressão de ponta a ponta cobre quantidade e percentual de seis casas,
  parcelas, saldo, razão, baixa parcial e quitação, com assertivas SQL dos
  inteiros. O ensaio V87 → V95 verifica valores e identidade da nota antiga.
- Verificação final: 60 testes direcionados e build Maven aprovados, incluindo
  as duas comparações de escala corrigidas após a execução ampla de 906 testes.
  Frontend: 428 testes, typecheck, lint e build aprovados.
- Aplicação da migração restrita ao banco `estacionamento_kaneko_fiscal_20261105_test`.
  O corte dos campos decimais de compatibilidade e os demais módulos seguem
  separados. Não operar versões de escrita antigas e novas sobre a mesma base.

## Precisão decimal — 2026-11-07

- V88/V89 preparam os campos inteiros para Nota de Entrada, itens, parcelas,
  estoque e contas a pagar, sem preenchimento de notas antigas. V90/V91
  habilitam seis casas na baixa a pagar e no acumulado, com escrita também
  nas colunas inteiras. As colunas decimais continuam para compatibilidade.
- Integração no PostgreSQL de testes: a Nota de Entrada gerou título de
  50,00; baixa de 0,000001 retornou saldo 49,999999 e gravou 1 em ambas as
  colunas inteiras. Ensaio V87 → V91 preservou cabeçalho, itens, título e
  baixa históricos. Foram 44 testes direcionados sem falhas.
- Suítes completas: 905 testes e build no backend; 426 testes, typecheck,
  lint e build no frontend. V88–V91 aplicadas apenas no banco de testes
  nesta sessão; não houve reset nem atualização da base do serviço.
- A revisão não liberou o corte amplo de tipos, restrições e triggers.
  A Nota de Entrada mantém a validação anterior de quantidade/valor, para
  não aceitar precisão que o saldo e o título ainda perderiam.

- V85–V87: novas escritas em campos inteiros de escala zero para condições e
  parcelas de pagamento, preço de venda e estoque mínimo do produto, lote de
  preço, reserva e pedido de estacionamento. O conversor JPA mantém BigDecimal
  na aplicação. Em registros legados sem valor novo, a leitura usa o valor
  original; não há recálculo histórico.
- Corrigido o HTTP 500 da Nota de Entrada: o SQL do Hibernate não usa mais uma
  expressão com percentual de desconto sem qualificador em consultas com join.
  Os testes de leitura, prévia e confirmação passaram com PostgreSQL real.
- Ainda pendentes: o corte integral de notas, razão/saldo de estoque e
  financeiro e o restante dos campos dos outros módulos. Não foi autorizada
  pela revisão a mudança automática ampla de tipos e constraints.
- Verificação final desta etapa: Maven verify com 902 testes, sem falhas,
  incluindo V83 → V87 em esquema descartável. Frontend: 425 testes, lint e
  build aprovados. Nenhum banco foi apagado ou recriado; apenas os esquemas
  descartáveis criados pelos testes foram limpos.

- V84 aditiva no código: cinco espelhos inteiros no cabeçalho da Nota de Serviço.
  As colunas originais seguem como fonte de leitura e escrita. A identidade da
  nota permanece VARCHAR e os snapshots históricos não são alterados.
- A mudança global automática foi bloqueada por risco de locks e rollback
  insuficiente. Nenhum dado da aplicação foi excluído. Quantidades, demais
  percentuais e a troca da persistência ainda estão em aberto; ver
  [[brain/fiscal-financeiro]].
- Validação: 27 testes passaram no Maven verify contra PostgreSQL de testes,
  incluindo V83 → V84, Nota de Serviço, Ordem de Serviço e seed. JAR gerado.
  A V84 não foi aplicada à base usada pelo serviço local nesta sessão.

## Nota de Serviço pela Ordem de Serviço — 2026-11-06

- A OS concluída prepara nota pendente com serviços, valores e parcelas para
  conferência; a emissão interna gera as contas da revisão conferida, sem
  transmissão fiscal ou impacto de estoque. Notas históricas não são recalculadas.
- A nota avulsa também aceita múltiplos serviços. A listagem consulta filtros
  reais e o detalhe preserva vínculo com OS, contas e motivo de cancelamento.
- PostgreSQL de testes: 22 testes direcionados passaram no Maven verify,
  incluindo alteração posterior da condição, emissão única e cancelamento.
  Ambiente local: Flyway aplicou a V83 e o novo detalhe consta no OpenAPI.

## Estoque sem ajuste manual — 2026-11-06

- Retirados o ajuste manual e a compensação avulsa da API e da tela de estoque.
  A confirmação da Nota de Entrada recebe mercadoria; a Nota de Saída baixa.
  A reversão permanece no documento de origem, com seus bloqueios.
- Posição, razão, cadastro de locais e histórico foram preservados. Não houve
  migration ou alteração de dados existentes. O seed novo só cria movimentos
  de entrada e saída ligados às notas de demonstração.
- Transferência entre depósitos não foi incluída; quando implementada, deverá
  mover quantidades entre locais sem alterar saldo total, custo ou financeiro.
- Verificação: 57 testes no Maven verify e 55 testes direcionados no frontend,
  além de lint, build e checagem do diff. Os dois antigos comandos retornam
  404 mesmo para um administrador e não alteram saldo nem histórico.

## Ajustes do modelo de prática profissional — 2026-11-06

- A emissão futura é bloqueada na Nota de Entrada manual e no XML, inclusive
  na confirmação de rascunho antigo; o editor sinaliza o campo e desabilita
  dias futuros no calendário. Emissão e chegada no mesmo dia são aceitas.
- Requisitos, casos de uso, telas, DER, dicionário, UML e sequências do recorte
  fiscal/financeiro passaram a distinguir a baseline do fluxo vigente.
  Ordem de Compra, Pedido de Venda e edição direta de título fiscal continuam
  fora do fluxo. Nenhuma migration ou alteração de dados de desenvolvimento.
- Verificação: 60 testes direcionados de backend com PostgreSQL isolado;
  415 testes web (73 arquivos), typecheck, lint e build. Frontend e API locais
  respondem após a reinicialização, e a matriz de rastreabilidade foi checada.
- Pendente: registrar capturas atuais do editor, conferência, detalhe e baixa.
  A sessão observada no navegador estava na tela de login; a descrição das
  telas se baseia no frontend e não substitui a prova visual.

## Resposta fiscal e financeira — 2026-11-06

- A resposta de baixa e cancelamento de Contas a Pagar e a Receber e de
  confirmação e cancelamento da Nota de Saída é montada dentro da transação.
  Antes, uma associação ainda não carregada podia causar HTTP 500 depois
  de a operação ter sido gravada. Contrato HTTP, locks, idempotência e schema
  permanecem os mesmos.
- Os testes de regressão desabilitam a sessão JPA na camada web e executam
  as requisições sem uma transação de teste envolvendo a requisição.
- Verificação: 81 testes direcionados passaram no PostgreSQL isolado.
  No serviço local reiniciado, duas baixas existentes foram repetidas pelo
  proxy do frontend com a carga e a chave originais. Ambas retornaram
  HTTP 200 com saldo zero, origem fiscal e uma única baixa no histórico.
  A confirmação visual no navegador não foi reexecutada nesta rodada.

## Pedido de Venda descontinuado — 2026-11-05

- Novas operações de mercadoria começam na Nota de Saída avulsa. Rotas de
  Pedido de Venda, geração a partir de pedido e pendência de venda sem nota
  foram retiradas. A API não expõe nem aceita o campo de vínculo antigo.
- A V82 remove as duas tabelas de Pedido de Venda, vínculos em Nota de Saída e
  Conta a Receber, e permissões exclusivas; não recria o banco. A migração
  interrompe a aplicação se encontrar vínculos ou efeitos operacionais legados,
  para não deixar estoque e financeiro inconsistentes.
  Pedidos de eventos e ordens de serviço não mudam.
- Verificação da V82: 62 testes focados no backend com PostgreSQL isolado
  (incluindo upgrade e rollback), 414 testes frontend, typecheck, lint e build.
  No banco local, V82 aplicada, sem tabelas, colunas ou permissões antigas;
  foram preservadas 2 notas de saída, 2 contas a receber e 7 movimentos.
  Na API local, a rota antiga retorna 404; Notas de Saída, detalhe,
  Contas a Receber e pedidos de eventos retornam 200 com sessão de QA.
- A retirada não alterou a resposta de baixa financeira; a correção foi
  feita separadamente em 2026-11-06.

## Notas de mercadoria — V80/V81 (2026-11-05)

- Nota de Entrada permanece a única origem de novas compras: revisão com itens
  e parcelas congeladas, confirmação integral, custo médio e contas a pagar.
- Na Saída avulsa a revisão também congela parcelas. A identidade contextual
  é organização emitente + número + série + modelo; o cliente é destinatário.
  A confirmação baixa estoque e gera recebíveis; se vinculada à Venda,
  consulta os lançamentos originais sem duplicá-los.
- A reversão interna exige versão, motivo e permissões fiscal, estoque e
  financeiro. Preserva a nota, os títulos e o razão; recusa baixa financeira,
  movimento posterior incompatível e saída vinculada. Não emite devolução fiscal
  nem estorna pagamentos reais.
- O DER e as classes têm recorte vigente separado dos diagramas históricos;
  o brain registra contratos e regras. As migrações são incrementais, não reprocessam
  notas confirmadas e não pressupõem apagar a base. Colisões preexistentes
  devem ser identificadas para aplicar a nova unicidade.
- Validação: 86 testes direcionados de backend com PostgreSQL isolado; 425
  testes web na suíte completa, 13 revalidados após o ajuste final de navegação,
  além de typecheck, lint e build. V80/V81 aplicadas no banco de desenvolvimento
  existente. No navegador: login, organização ativa, listagens e formulários
  de entrada e saída, e bloqueio ao salvar saída incompleta. Confirmação e
  reversão exercitadas no banco de testes, sem criar notas no tenant dev.

### QA de navegador — 2026-11-05

- Organização descartável `Kaneko QA Fiscal 6214003`: entrada 10 → 20 com
  custo médio R$ 10 → R$ 12 e conta a pagar de R$ 140; saída 20 → 16 com
  custo R$ 12 e conta a receber de R$ 80. Links abriram os detalhes do razão
  e dos títulos. PostgreSQL confirmou um movimento por saída confirmada.
- Reversão sem baixa passou em Entrada e Saída, compensando o saldo e
  cancelando o título. Bloqueios de duplicidade, desconto acima do item,
  saldo insuficiente, baixa registrada e movimento posterior exercitados.
  Confirmação em duas abas não duplicou saldo ou título.
- A baixa em Contas a Pagar e Receber gravou o efeito, mas devolveu HTTP 500
  com `LazyInitializationException` (fornecedor/cliente fora da sessão).
  Repetir a mesma tentativa não duplicou a baixa, mas voltou a falhar na
  resposta. A seção de 2026-11-06 registra a correção no backend.

## Remoção de Ordem/Recebimento de Compra — 2026-10-30

- Removidos controllers, services, entidades, DTOs, repositories, endpoints,
  permissões específicas, migrations e seeds do módulo de compras.
- Nota de Entrada manual/XML não aceita mais vínculos históricos de ordem ou
  recebimento; sua confirmação é a única origem de entrada física e financeira.
- Bases locais `estacionamento_kaneko` e `estacionamento_kaneko_test` foram
  recriadas sem preservar dados do módulo removido.
- Instalação limpa aplicou 71 migrations e os 15 testes críticos de Nota de
  Entrada/OpenAPI passaram sem falhas.
- Frontend removeu rotas, telas, atalhos, histórico anterior e contratos de
  Ordem/Recebimento; nenhum commit ou push foi realizado.

## Custo médio ponderado da Nota de Entrada — 2026-10-23

- A prévia da Nota calcula por item o saldo/custo atuais, custo da entrada e custo
  médio projetado; linhas repetidas do mesmo Produto são projetadas em sequência.
- A confirmação recalcula sob lock, atualiza `produto.custo` e persiste snapshots
  de saldo/custo no item e no movimento. `produto.valorCompra` conserva o último
  custo unitário bruto de aquisição.
- Regularizações de entradas já movimentadas não alteram estoque nem custo médio.
- A V79 é aditiva e usa o `produto.custo` vigente como base para saldos anteriores,
  sem reprocessar ou reprecificar fatos históricos.
- A suíte focal aprovou 27 testes (calculadora, estoque, fluxo único da Nota e
  migração), sem falhas. A suíte integral executou 898 testes: 887 passaram e
  11 falharam em cenários antigos de QR/credencial porque as massas fixam a
  vigência do evento em 10–11/10/2026 e a data corrente já é 23/10/2026.
- Publicação continua fora do escopo desta sessão; nenhum commit ou push foi feito.

## Fechamento da revisão transversal — 2026-09-30

- A revisão ponta a ponta foi concluída nos módulos compartilhados, compras,
  fiscal, financeiro, estoque, vendas, serviços, eventos, acesso e
  administração. A Ordem de Compra permaneceu congelada após sua validação de
  regressão, conforme o escopo definido para esta etapa.
- Modais e filtros reutilizam o padrão responsivo comum; formulários confirmam
  descarte de alterações e campos numéricos impedem valores negativos ou
  formatos monetários inconsistentes antes do envio.
- Eventos, administração, estoque e listagens críticas foram conferidos em
  desktop e mobile. A navegação não apresentou rolagem horizontal no documento,
  e o detalhe de compra preservou o andamento baseado em dados reais.
- A linguagem do bloqueio de credencial de evento passou a ser operacional:
  não expõe `ETag`, payload, status HTTP ou outro jargão técnico ao usuário.
- A paginação fiscal no backend deixou de combinar paginação SQL com coleção
  carregada por `join fetch`, eliminando resultados instáveis sem alterar o
  contrato HTTP.
- Backend: `mvnw.cmd test` aprovou **887 testes em 05:59**, com zero falhas,
  erros ou skips e `BUILD SUCCESS`; Flyway validou o schema público em V77.
- Frontend: Vitest aprovou **373 testes em 70 arquivos**. Os três testes que
  excederam o timeout durante uma execução simultânea com o backend passaram
  isolados (**22/22**) e na repetição integral serial. Typecheck, lint e build
  de produção também passaram.
- Os commits `097eb6f` (backend) e `a348504`, `e7f72be`, `28f4190`, `8606ce1`,
  `8911b07` e `699574e` (frontend) foram enviados à branch
  `feat/patio-colaborador` nos respectivos repositórios.
- Permanece bloqueada somente a homologação fiscal externa, que depende de
  provedor, certificado e configuração fiscal. O usuário demo não possui a
  permissão de bloqueio de credenciais; o RBAC foi respeitado e o formulário
  autorizado permanece coberto por teste automatizado.

## Ordem dos custos e totais da compra — 2026-09-29

- O formulário agora apresenta `Itens → Custos da ordem → Totais dos itens →
  Observações`, para que frete, seguro e outras despesas apareçam antes do
  resultado que elas alteram.
- O resumo financeiro foi separado do editor de itens sem alterar o payload;
  campos exclusivamente visuais continuam fora da requisição ao backend.
- Um teste de regressão verifica a ordem real dos blocos no diálogo. Backend,
  contratos e dados não foram alterados; commit e push seguem pendentes de
  autorização.
- Teste focado da compra (6 casos), componentes compartilhados do formulário
  (9 casos), typecheck, lint e build passaram. O navegador confirmou a sequência
  visual no modal real sem salvar o cadastro de validação.

## Navegação e andamento contextual de compras — 2026-09-29

- A listagem de compras perdeu a faixa genérica `Ordem → Receber → Nota →
  Pagar` e os três atalhos que repetiam a mesma navegação.
- O andamento agora aparece somente no detalhe de uma compra e deriva da
  situação da ordem e dos recebimentos, notas e contas vinculados de verdade.
- Estados cancelados ou rejeitados interrompem as etapas posteriores; relações
  sem acesso não são apresentadas como concluídas.
- A home resume o fluxo do módulo Estoque como `Ordem → Recebimento → Nota →
  Conta`, sem adicionar novos botões.
- Backend, contratos e dados não foram alterados. Commit e push continuam
  pendentes de autorização.
- Verificação concluída: 15 testes focados, typecheck, lint, build e regressão
  integral de **366 testes em 67 arquivos**. O navegador confirmou a listagem
  limpa, o andamento real da compra demo e o resumo do fluxo na home.

## Compra fiscal e recebimento integral — 2026-09-29

- O detalhe da compra usa drawer amplo e divide dados, entrega, itens e totais
  em blocos responsivos. A antiga tabela de nove colunas foi substituída por
  cards de item, removendo a rolagem horizontal e os textos espremidos.
- O desconto por item agora é validado imediatamente contra `quantidade × valor
  unitário`; a interface mostra o limite monetário, bloqueia o salvamento e o
  backend mantém a mesma regra como barreira autoritativa.
- Linhas de itens reservam altura uniforme para mensagens de validação e alinham
  campos pelo topo, sem deslocar um único campo quando aparece um erro.
- A criação da compra passa a começar por fornecedor, número, série e modelo da
  nota. Os demais campos permanecem bloqueados até a chave estar completa e a
  chave fica imutável após a inclusão do primeiro produto.
- O número técnico legado da ordem é gerado pelo servidor e não é solicitado ao
  usuário. PostgreSQL e service impedem duplicidade da chave fiscal no tenant.
- Não há novo recebimento parcial: uma entrega aprovada entra integralmente no
  estoque ou é rejeitada integralmente, com motivo, sem movimento físico.
- A Nota de Entrada vinculada precisa repetir a chave da compra; a V77 inclui o
  modelo na unicidade da nota.
- O seed demonstrativo foi alinhado à chave fiscal obrigatória e continua
  idempotente tanto em uma base nova quanto em uma base local preexistente.
- V75–V77, Flyway e Hibernate validate foram aprovados no PostgreSQL de testes.
  A regressão anterior concluiu com **886 testes backend**; após a correção do
  limite de desconto, a suíte frontend concluiu com **361 testes**;
  OpenAPI, JAR, typecheck, lint e build de produção também estão verdes.
- O navegador local confirmou que a chave incompleta bloqueia o formulário, a
  chave completa libera os demais campos e o primeiro produto trava fornecedor,
  número, série e modelo, sem persistir a ordem usada na verificação.
- Nenhum commit ou push foi realizado; mudanças locais anteriores e
  `Nio2WebServerCustomizer.java` permanecem preservados.

## Cadastro contextual de local de estoque — 2026-09-29

- Os itens dos seletores agora possuem ação de edição quando o perfil tem
  permissão de atualização. Fornecedores, produtos e demais referências
  reutilizam o formulário oficial; recursos versionados mantêm `ETag`/`If-Match`.
- A edição abre sobre o seletor e retorna à lista sem perder os dados já
  preenchidos na Ordem de Compra. O navegador confirmou o fornecedor demo com
  seus dados atuais, sem salvar alterações.
- Typecheck, lint, build, 27 testes focados e a regressão integral de **363
  testes em 66 arquivos** passaram.
- O seletor “Local previsto de entrega” agora oferece “Cadastrar local de
  estoque” para usuários com `stock:manage`, seguindo o mesmo padrão contextual
  de clientes, fornecedores e demais referências.
- O cadastro abre em modal empilhado, reutiliza os campos reais de estoque,
  preserva toda a Ordem de Compra em preenchimento e seleciona o novo local após
  a criação. Recursos com tela operacional própria não ganham rota CRUD duplicada.
- Teste de integração do componente cobre criação, seleção automática e
  preservação de número, itens e observação. O navegador local confirmou o botão
  e o modal “Novo local de estoque” sem persistir dados de demonstração.
- Typecheck, lint e build passaram. A suíte completa passou com **359 testes em
  66 arquivos**; a execução paralela teve um timeout por contenção em um teste de
  compra que passou isoladamente e na repetição integral serial.
- Nenhum endpoint ou arquivo do backend foi alterado por este ajuste. Commit e
  push continuam pendentes de autorização.

## Ordem de Compra comercial e logística — 2026-09-29

- A Ordem de Compra registra comprador autenticado, condição de pagamento,
  transportadora, local planejado de entrega, responsabilidade do frete,
  referência do fornecedor e observação interna, preservando os contratos e
  dados existentes.
- Condição de pagamento e transportadora ativas são herdadas do fornecedor e
  agora aparecem preenchidas assim que o fornecedor é selecionado; podem ser
  substituídas por referências válidas da mesma organização. Se o frontend
  omitir os campos, o backend mantém a mesma herança como garantia. O local
  planejado é sugerido ao abrir o recebimento.
- Desconto geral foi eliminado como regra de negócio: somente itens aceitam
  desconto. A aplicação rejeita valores no cabeçalho e a V74 normaliza o legado
  e adiciona constraint PostgreSQL para manter o campo em zero.
- A interface chama `dataEmissao` de “Data da ordem” sem quebrar o JSON legado.
  Modelo, série, número e datas fiscais continuam exclusivamente na Nota de
  Entrada, onde a duplicidade por fornecedor é validada.
- Testes focados aprovados: backend **16 testes** (ordem + seed) e frontend **7
  testes** (contrato + tela). A regressão completa aprovou **884 testes** no
  backend e **358 testes em 66 arquivos** no frontend; Flyway V74, Hibernate
  validate, OpenAPI, JAR, typecheck, lint e build foram validados.
- Conferência no navegador local validou a listagem, o formulário, o calendário
  acima do modal e o detalhe da ordem demo. A tela não apresenta desconto geral
  e exibe os novos dados comerciais e logísticos; nenhum registro foi alterado.
- Nenhum commit ou push foi realizado; alterações locais preexistentes e
  `Nio2WebServerCustomizer.java` permanecem preservados.

## Padronização de modais — 2026-09-29

- Os diálogos do frontend foram centralizados em `AppDialog`, com título e
  descrição acessíveis, fechamento visível, tamanhos semânticos, bloqueio de
  descarte durante processamento e rolagem restrita ao conteúdo.
- O tema deixou de forçar todos os diálogos a 100% da largura e de deslocar o
  backdrop abaixo do cabeçalho; confirmações usam `alertdialog` e priorizam a
  ação menos destrutiva.
- Formulários compartilhados, seletores, busca, filtros, documentos, financeiro,
  compras e estoque passaram a usar o mesmo contrato visual. No celular, os
  formulários extensos ocupam corretamente todo o viewport com ações acessíveis.
- Verificação no navegador confirmou o modal de Ordem de Compra centralizado no
  desktop, fullscreen em 390 × 844, calendário acima do modal e backdrop cobrindo
  todo o aplicativo.
- Frontend: lint e typecheck aprovados; suíte completa com **358 testes em 66
  arquivos** aprovada. Após o último ajuste responsivo, os **14 testes focados**
  de tema, `AppDialog` e formulário compartilhado passaram novamente.
- Nenhum endpoint ou arquivo do backend foi alterado por esta entrega. Não houve
  commit nem push; todas as alterações locais preexistentes foram preservadas.

## Revisão transversal — 2026-09-28

- A validação real no navegador percorreu as **45 rotas** do menu, abriu os
  **22 formulários genéricos** e os fluxos especializados de compra, venda,
  serviço, fiscal, financeiro, estoque, eventos, pátio e administração.
- Formulários compartilhados agora normalizam entradas monetárias, bloqueiam
  valores negativos, mantêm calendários acima dos modais, permitem rolagem em
  telas menores e confirmam o descarte de qualquer edição não salva.
- O fluxo compra → recebimento → nota → conta mantém itens, rateio e custo final;
  venda/serviço preservam documentos, títulos e movimentos relacionados sem
  duplicar efeitos. Busca global e razão abrem as origens exatas quando há API.
- Listagens, detalhes e cartões usam rótulos operacionais e situações traduzidas;
  o catálogo de produtos deixou de apresentar quantidade obsoleta como estoque.
- Eventos exibem alocação, produto, preço e disponibilidade reais. Administração,
  acesso e bloqueios mantêm RBAC e mensagens honestas sobre APIs ausentes.
- Desktop e viewport móvel foram verificados. Em navegação limpa, o console do
  navegador terminou sem erros ou avisos.
- Backend: `mvnw.cmd test` — **879 testes**, zero falhas/erros/skips e
  `BUILD SUCCESS`; **72 migrations** validadas, schema V73 e Hibernate iniciando
  contra PostgreSQL 17.10.
- Frontend: **354 testes em 65 arquivos**, typecheck, lint e build aprovados.
  Permanece somente o aviso de build conhecido do chunk MUI acima de 500 kB.
- `git diff --check` passou nos dois repositórios. Nenhum commit ou push foi
  realizado; alterações locais anteriores e `Nio2WebServerCustomizer.java`
  permaneceram preservados.

## Ajuste de Ordem de Compra — 2026-09-25

- V72/V73 adicionam seguro, outras despesas, rateio por item, custo final e
  código histórico do produto no fornecedor.
- Recebimentos passam a registrar no estoque o custo unitário final da ordem.
- Frontend reorganiza itens/despesas/observação, mostra totais e custos e corrige
  calendário, entrada monetária, rolagem responsiva e descarte acidental.
- Naquele estágio, identidade e datas fiscais permaneciam na Nota de Entrada;
  a regra acadêmica atual acima substitui essa decisão para a chave fiscal.

## Validação do ajuste de Ordem de Compra — 2026-09-28

- Backend: `mvnw.cmd -B verify` — **873 testes**, zero falhas/erros/skips e
  `BUILD SUCCESS`; Flyway validou o schema em V73 e Hibernate iniciou com
  validação de mapeamentos.
- Seed completa corrigida para persistir frete rateado, custo total e custo
  unitário final; execução idempotente aprovada no teste de integração.
- Frontend: typecheck, lint e build com exit code 0; Vitest — **328 testes em
  60 arquivos**, todos verdes. Os 6 testes focados de rateio, descarte e campo
  monetário também passaram após o último ajuste responsivo.
- `git diff --check` passou nos dois repositórios. Nenhum commit ou push foi
  realizado nesta entrega.

## Checklist da execução

- [x] Confirmar escopo do anexo ded8022e e estado dos dois repositórios.
- [x] Confirmar efeitos atuais: venda confirma estoque/recebível; recebimento confirma estoque; nota de compra gera obrigação; nota de serviço gera cobrança na emissão interna.
- [x] 1. Pedido de venda → nota → recebíveis, incluindo repetição/concorrência e UI.
- [x] 2. Nota de Entrada direta → estoque → contas a pagar.
- [x] 3. Ordem de serviço → nota → cobrança.
- [x] 4. Financeiro: filtros, totais, origem, saldo e histórico de baixa.
- [x] 5. Estoque rastreável, cadastros contextuais e pendências reais na home.
- [x] 6. XML: importação segura, conciliação e conferência sem efeitos duplicados.
- [x] 7. Verificar integração fiscal de homologação; registrar bloqueios externos concretos (emissão externa bloqueada).
- [x] Regressão, Flyway/Hibernate, build, OpenAPI, navegação desktop/mobile e documentação. Lançamentos completos no navegador demo não executados; efeitos e falhas validados por API no banco de testes e Vitest.

## Atualização da base demo — 2026-09-25

- Seed opt-in executada no banco local com perfis administrador e operador e
  dados representativos para todas as áreas já implementadas.
- Entrada demonstrativa foi removida do ambiente local; a base de desenvolvimento
  parte vazia e a Nota de Entrada é o único fluxo de entrada de mercadorias.
- Importação XML conciliada foi acrescentada com XML íntegro e hash SHA-256.
- Testes da seed: 4 executados, zero falhas; integração executa a carga duas
  vezes e compara todas as contagens para proteger a idempotência.

## Decisões

- Preservar branch feat/patio-colaborador e contratos administrativos existentes.
- Os commits autorizados desta revisão foram publicados; Nio2 e logs locais
  preexistentes continuaram intocados.
- Venda mantém sua origem comercial/física/financeira; vínculo fiscal não relança efeitos.
- Frontend e backend são repositórios distintos; mudanças integradas revisadas separadamente.
- O recorte financeiro está isolado nos seus componentes; testes PostgreSQL serão serializados.
- Preservar o contrato paginado atual até uma migração coordenada: o aviso de
  serialização de `PageImpl` é dívida conhecida, não motivo para quebrar os
  consumidores existentes durante esta revisão.

## Próximos passos

1. Definir provedor, contrato/cobertura e acesso de homologação para NF-e e
   NFS-e, incluindo certificado e parâmetros fiscais quando exigidos. Só então
   integrar emissão externa real.
2. Planejar uma versão de contrato estável para respostas paginadas antes de
   substituir a serialização atual de `PageImpl`.
3. Validar o bloqueio de credenciais com um usuário de homologação que possua a
   permissão correspondente, sem elevar artificialmente o perfil demo.

## Validação final — 2026-09-12

- Backend: `mvnw.cmd -B verify`, Java 25.0.2, PostgreSQL de testes — **871 testes, zero falhas, erros ou skips; BUILD SUCCESS**. Evidência: `target/operational-full-verify-final.log`.
- Flyway validou as migrations existentes e o schema em V71; V69, V70 e V71 foram aplicadas no ambiente de desenvolvimento/teste durante a implementação. Hibernate iniciou com `ddl-auto: validate`. Nenhuma migration já aplicada foi reescrita.
- OpenAPI: **2 testes verdes**, incluindo contratos operacionais novos e schema binário do upload XML.
- Frontend: typecheck, lint e build com exit code 0; Vitest **325 testes em 59 arquivos, todos verdes**. Evidências: `.tmp-check/full-typecheck-final-clean.log`, `full-lint-final-clean.log`, `full-build-final-clean.log` e `full-vitest-final-clean.log`, dentro do frontend.
- A primeira regressão completa identificou vazamento de valores de notas de tenants no relatório legado sem organização. As três consultas sem tenant agora restringem `organizacao_id IS NULL`; teste legado preservado e regressão de isolamento adicionada (8 testes focais verdes antes da rodada completa).
- Verificação de navegador detectou HTML inválido nos cards mobile; `ListingCards` corrigido com dois testes de regressão. Mocks incompletos foram corrigidos apenas nos testes; a suíte final não faz requisições HTTP inesperadas nem emite aviso de nesting inválido.
- `git diff --check` passou nos dois repositórios; canvases JSON válidos (backend 37 nós/116 arestas; frontend 23 nós/54 arestas). Nenhum arquivo staged, commit ou push; HEADs preservados: backend `2c60c25`, frontend `14afdd5`.
- Logs novos desta execução ficaram em `target/` ou `.tmp-check/`; logs locais preexistentes e `Nio2WebServerCustomizer.java` foram preservados.

## Cobertura focal e conferência de interface

- Vendas: `target/sales-concurrency.log` — 6 integrações verdes de confirmação/notas concorrentes, RBAC e filtros no PostgreSQL, além da cobertura API anterior.
- Nota de Entrada/OS: cobertura focal preservada para os fluxos fiscal, estoque,
  financeiro e ordens de serviço, sem o módulo removido de compras/recebimentos.
- Financeiro/estoque/home: V69 + Hibernate validate aprovados; último lote 20 testes verdes. Frontend 19 testes focais/typecheck/lint/build verdes.
- XML: `target/xml-final-green.log` — 11 testes verdes (6 APIs + 5 parser), incluindo confirmação concorrente com/sem recebimento, rollback por nota duplicada e permissões. V70/V71 aplicadas sem editar migration já aplicada.
- OpenAPI: teste novo detectou ausência do schema de upload binário; contrato corrigido e incluído na regressão completa.
- UI local: login/seleção explícita, home com pendências reais, link exato à compra, detalhe/recebimento sem confirmar dados, listagem mobile, venda com itens e efeitos e importações vazias consultadas. Validação mutante completa automatizada no banco de testes, não em dados demo do usuário.
- Consulta XML durante recompilação local foi recuperada; GET em 8086 e via proxy 5173 retornaram 200. Chrome connector expirou; navegador interno local funcionou.
- UI adicional: OS concluída exibe nota interna e parcela; link abriu exatamente a conta4, com origem, composição do saldo e histórico. Diálogo de baixa explica que não efetua transferência bancária; fechado sem registrar.
- UI estoque: razão mostra produto, local, quantidade, responsável, saldo anterior/posterior e origem; origem ausente em movimento demo legado é informada, não inferida. Ajuste compensatório não foi executado nos dados demo.
- Desktop e viewport mobile conferidos; viewport restaurado e navegador deixado na home. Importação XML completa até a confirmação e baixas financeiras mutantes foram validadas automaticamente, não executadas sobre o demo do usuário.

## Roteiro curto para conferir

1. Home `/app`: abrir uma pendência e verificar o documento exato.
2. `/app/vendas-administrativas`: abrir pedido, conferir itens/efeitos e a seção nota/estoque/recebíveis. Pedido confirmado permite preparar a nota sem redigitar os itens.
3. `/app/notas-entrada`: criar, conferir e confirmar a entrada diretamente, sem ordem ou recebimento intermediário.
4. `/app/ordens-servico`: OS concluída permite preparar nota; emissão interna é ação distinta. Nota e títulos possuem links de origem.
5. `/app/contas-pagar` e `/app/contas-receber`: filtros, totais completos, origem, parcelas, saldo e histórico. Baixa apenas registra a movimentação no sistema, não transfere dinheiro.
6. `/app/estoque?tab=razao`: abrir uma movimentação para explicar o saldo e consultar a origem quando disponível.
7. `/app/notas-entrada/importar`: importar, conciliar, conferir e confirmar em contexto persistido. Repetição/concorrência, divergências e rollback têm cobertura automatizada no banco de testes.

## Bloqueios / Dívidas técnicas

- Não há adapter/configuração de homologação de NF-e/NFS-e no módulo fiscal atual; falta provedor/contrato/acesso/certificado e configuração fiscal por empresa/município. Etapa7 externa bloqueada, etapas1–6 independentes implementadas.
- Registro interno de nota não equivale a documento autorizado por provedor fiscal.
- XML não valida todo XSD/assinatura nem SEFAZ; componentes de total não representáveis e operações fora do recorte são explicitamente bloqueados, sem adulterar valores.
- Aviso de chunk MUI de aproximadamente 603 kB permanece; não foi introduzida refatoração ampla fora do recorte para removê-lo. Fixtures de testes ainda produzem avisos não fatais de select sem opções em EventsPage e defaultExpanded de Accordion no teste mobile da home.

## Remoção definitiva de compras — 2026-10-30

- Ordem de Compra, itens de ordem, Recebimento de Compra e seus vínculos
  históricos foram removidos do backend, migrations, testes e dados locais.
- O banco local foi recriado sem seed: existe somente o usuário bootstrap;
  organizações, notas, estoque e contas permanecem vazios.
- Verificação final: compilação limpa; 15 testes focais de Nota de Entrada/API
  verdes; as tabelas removidas não existem no PostgreSQL local.
