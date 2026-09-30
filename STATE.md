> Links: [[brain/core]] · [[brain/vendas-administrativas]] · [[brain/compras]] · [[brain/ordens-servico]] · [[brain/fiscal-financeiro]]

# Estado do Projeto

**Última atualização:** 2026-09-29
**Fase atual:** Refinamento da Ordem de Compra implementado e validado.

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
- [x] 2. Compras parciais → recebimentos → notas → contas.
- [x] 3. Ordem de serviço → nota → cobrança.
- [x] 4. Financeiro: filtros, totais, origem, saldo e histórico de baixa.
- [x] 5. Estoque rastreável, cadastros contextuais e pendências reais na home.
- [x] 6. XML: importação segura, conciliação e conferência sem efeitos duplicados.
- [x] 7. Verificar integração fiscal de homologação; registrar bloqueios externos concretos (emissão externa bloqueada).
- [x] Regressão, Flyway/Hibernate, build, OpenAPI, navegação desktop/mobile e documentação. Lançamentos completos no navegador demo não executados; efeitos e falhas validados por API no banco de testes e Vitest.

## Atualização da base demo — 2026-09-25

- Seed opt-in executada no banco local com perfis administrador e operador e
  dados representativos para todas as áreas já implementadas.
- Compra demonstrativa agora percorre ordem, recebimento, estoque, nota e conta
  a pagar; venda confirmada liga pedido, nota, conta recebida e baixa.
- Importação XML conciliada foi acrescentada com XML íntegro e hash SHA-256.
- Testes da seed: 4 executados, zero falhas; integração executa a carga duas
  vezes e compara todas as contagens para proteger a idempotência.

## Decisões

- Preservar branch feat/patio-colaborador e contratos administrativos existentes.
- Não commitar/push nesta execução sem autorização nova; Nio2 e logs preexistentes intocados.
- Venda mantém sua origem comercial/física/financeira; vínculo fiscal não relança efeitos.
- Frontend e backend são repositórios distintos; mudanças integradas revisadas separadamente.
- O recorte financeiro está isolado nos seus componentes; testes PostgreSQL serão serializados.

## Próximos passos

1. Conferir as telas locais de pedidos de venda, compras/recebimentos, OS, contas e XML conforme roteiro abaixo. Não há etapa independente de implementação restante neste recorte.
2. Definir provedor, contrato/cobertura e acesso de homologação para NF-e e NFS-e, incluindo certificado e parâmetros fiscais quando exigidos. Só então integrar emissão externa real.
3. Commit/push permanecem pendentes de autorização; alterações estão nos dois worktrees em `feat/patio-colaborador`.

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
- Compras/OS: rodada conjunta 32 testes verdes (25 compras/fiscal + 7 OS), incluindo geração e emissão concorrentes.
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
3. `/app/ordens-compra`: conferir comprado/recebido/pendente, recebimentos e documentos relacionados; recebimento oferece continuidade para a nota, sem obrigá-la.
4. `/app/ordens-servico`: OS concluída permite preparar nota; emissão interna é ação distinta. Nota e títulos possuem links de origem.
5. `/app/contas-pagar` e `/app/contas-receber`: filtros, totais completos, origem, parcelas, saldo e histórico. Baixa apenas registra a movimentação no sistema, não transfere dinheiro.
6. `/app/estoque?tab=razao`: abrir uma movimentação para explicar o saldo e consultar a origem quando disponível.
7. `/app/notas-entrada/importar`: importar, conciliar, conferir e confirmar em contexto persistido. Repetição/concorrência, divergências e rollback têm cobertura automatizada no banco de testes.

## Bloqueios / Dívidas técnicas

- Não há adapter/configuração de homologação de NF-e/NFS-e no módulo fiscal atual; falta provedor/contrato/acesso/certificado e configuração fiscal por empresa/município. Etapa7 externa bloqueada, etapas1–6 independentes implementadas.
- Registro interno de nota não equivale a documento autorizado por provedor fiscal.
- XML não valida todo XSD/assinatura nem SEFAZ; componentes de total não representáveis e operações fora do recorte são explicitamente bloqueados, sem adulterar valores.
- Aviso de chunk MUI de aproximadamente 603 kB permanece; não foi introduzida refatoração ampla fora do recorte para removê-lo. Fixtures de testes ainda produzem avisos não fatais de select sem opções em EventsPage e defaultExpanded de Accordion no teste mobile da home.
