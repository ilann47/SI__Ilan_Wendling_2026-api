> Links: [[README]] · [[05-casos-de-uso]] · [[14-modelagem-dominio]] · [[15-maquinas-de-estados]] · [[17-apis-enterprise]]

# 16. Casos de Uso da Plataforma Enterprise

## 16.1 Regras de negócio transversais

- **RN701:** toda operação empresarial ocorre em uma organização autenticada e autorizada.
- **RN702:** recurso de outra organização é invisível e não pode ser referenciado.
- **RN703:** comando não naturalmente idempotente exige chave de idempotência.
- **RN704:** transição sensível registra ator, origem, versão, instante, correlação e motivo quando aplicável.
- **RN705:** alterações concorrentes em agregado versionado são rejeitadas.
- **RN706:** instantes são persistidos em UTC e interpretados no fuso do venue/evento.
- **RN707:** valores monetários incluem moeda e regra de arredondamento.
- **RN708:** integrações externas não são chamadas dentro da transação do agregado.
- **RN709:** efeitos entre contextos são propagados por outbox e consumidos de forma idempotente.
- **RN710:** nenhuma exceção manual elimina a trilha da decisão automática original.

## UC701 — Criar evento

**Situação implementada:** disponível em `POST /api/v1/events`, exigindo
`Idempotency-Key`; o replay de payload equivalente devolve o mesmo agregado e o
reuso conflitante retorna `409`. A origem por integração preserva `externalId`,
embora autenticação técnica dedicada permaneça futura.

**Objetivo:** iniciar o planejamento de um evento.  
**Atores:** Gestor, Organizador.  
**Pré-condições:** organização ativa; venue visível; permissão `events:create`.  
**Ativação:** ator informa nome, venue, período e fuso.

**Fluxo principal:**

1. O sistema valida tenant, venue, datas e duplicidade da chave idempotente.
2. Cria o evento em `RASCUNHO` com versão inicial.
3. Registra o ator e publica `EventoCriado`.
4. Retorna o evento e o checklist de configuração.

**Alternativos:** evento pode ser criado a partir de integração externa, preservando `externalId`.  
**Exceções:** E1 datas inválidas; E2 venue externo ao tenant; E3 chave idempotente reutilizada com payload diferente.  
**Pós-condição:** evento em rascunho, ainda não vendável.

## UC702 — Configurar e publicar evento

**Situação implementada:** `POST /api/v1/events/{id}/publication` exige
confirmação, `If-Match`, `events:publish`, alocação com capacidade vendável e
produto publicado. A primeira entrega usa o Gestor autenticado como responsável
pela publicação. A abertura de vendas permanece um comando separado:
`POST /api/v1/events/{id}/sales-opening` exige `If-Match` e executa imediatamente
`PUBLICADO → VENDAS_ABERTAS`; instante futuro é recusado até existir scheduler.
Publicação e abertura de vendas não habilitam acesso.

**Objetivo:** tornar o evento oficialmente disponível.  
**Atores:** Organizador configura; Gestor publica.  
**Pré-condições:** evento `RASCUNHO/CONFIGURADO`; alocação, produtos, responsáveis e políticas completos.

**Fluxo principal:**

1. O organizador define janelas de venda, acesso, operação e dispersão.
2. Define políticas de reentrada, cancelamento, veículos e contingência.
3. O sistema executa o checklist de prontidão.
4. O gestor confirma a publicação usando a versão atual.
5. O sistema congela uma versão da configuração e publica `EventoPublicado`.
6. Em comando posterior, o gestor abre vendas e o sistema publica
   `VendasDoEventoAbertas`.

**Alternativos:** publicação pode ser agendada.  
**Exceções:** E1 configuração incompleta; E2 capacidade inexistente; E3 produto não publicado; E4 versão concorrente.  
**Pós-condição:** evento `PUBLICADO`, apto a abrir vendas.

## UC703 — Criar pátio

**Objetivo:** cadastrar uma instalação física.  
**Atores:** Administrador da organização, Gestor.  
**Pré-condições:** organização/unidade ativa; permissão `facilities:manage`.

**Fluxo principal:** valida nome, endereço e capacidade física; cria pátio ativo; registra `PatioCriado`.  
**Alternativos:** pátio pode iniciar inativo para implantação.  
**Exceções:** E1 capacidade menor que um; E2 cidade inválida; E3 nome duplicado segundo política.  
**Pós-condição:** pátio disponível para setores, equipamentos e futuras alocações.

## UC704 — Cadastrar setor

**Objetivo:** dividir o pátio em zonas operacionais.  
**Atores:** Gestor.  
**Pré-condições:** pátio ativo.

**Fluxo principal:** informa código, nome, capacidade e categorias; sistema valida soma/limites; cria setor e publica `SetorCriado`.  
**Alternativos:** setor sem vagas individualizadas.  
**Exceções:** E1 código duplicado; E2 capacidade acima do pátio; E3 categoria inválida.  
**Pós-condição:** setor utilizável em alocações e roteamento.

## UC705 — Cadastrar vagas

**Objetivo:** individualizar vagas quando necessário.  
**Atores:** Gestor.  
**Pré-condições:** setor ativo e compatível.

**Fluxo principal:** informa códigos, tipo, acessibilidade e posição; sistema valida unicidade/capacidade; cria vagas.  
**Alternativos:** importação em lote idempotente.  
**Exceções:** E1 código duplicado; E2 quantidade excedente; E3 layout inválido.  
**Pós-condição:** vagas passam a integrar o mapa físico, sem criar inventário comercial automaticamente.

## UC706 — Alocar pátio ao evento

**Objetivo:** comprometer capacidade operacional em um período.  
**Atores:** Gestor.  
**Pré-condições:** evento configurável; pátio ativo; períodos conhecidos.

**Fluxo principal:** seleciona pátio, período e capacidades; sistema detecta conflitos; cria alocação; publica `CapacidadeAlocadaAoEvento`.  
**Alternativos:** reserva por setor ou capacidade compartilhada.  
**Exceções:** E1 conflito temporal; E2 limite físico; E3 redução abaixo do já vendido.  
**Pós-condição:** capacidade-base disponível para produtos.

## UC707 — Criar produto de estacionamento

**Objetivo:** definir a oferta vendável.  
**Atores:** Gestor, Organizador.  
**Pré-condições:** evento e alocação existentes.

**Fluxo principal:** informa categoria, pátio/setor, janela, benefícios, restrições e quota; sistema valida; cria em rascunho; ator publica.  
**Alternativos:** produtos comuns, VIP, PCD, staff, ônibus, van e cortesia.  
**Exceções:** E1 quota excedente; E2 janela incompatível; E3 categoria física não suportada.  
**Pós-condição:** produto publicado apto a receber lotes.

## UC708 — Criar lote de preço

**Objetivo:** definir preço e janela comercial.  
**Atores:** Gestor, Organizador dentro do escopo.  
**Pré-condições:** produto existente; permissão de preço.

**Fluxo principal:** informa moeda, preço, quantidade, período e prioridade; sistema valida sobreposição; publica o lote ou agenda ativação.  
**Alternativos:** lote gratuito para quota controlada.  
**Exceções:** E1 valor negativo; E2 sobreposição ambígua; E3 quantidade acima da quota.  
**Pós-condição:** preço selecionável com snapshot em futuras reservas.

## UC709 — Reservar vaga/inventário

**Situação implementada:** `POST /api/v1/inventory-holds` exige tenant ativo,
`inventory:hold` e `Idempotency-Key`. O serviço bloqueia o produto, exige evento
em `VENDAS_ABERTAS`, seleciona o lote vigente de maior prioridade, incrementa o
saldo reservado e persiste hold, auditoria e `InventarioReservado` atomicamente.
O prazo padrão provisório é 15 minutos, configurável. Consulta, liberação e
expiração estão disponíveis; confirmação aguarda UC710.

**Objetivo:** garantir temporariamente capacidade durante a compra.  
**Atores:** Cliente, Caixa, Integração.  
**Pré-condições:** vendas abertas, produto vendável e quantidade disponível.

**Fluxo principal:**

1. Recebe produto, quantidade, canal e chave idempotente.
2. Seleciona preço vigente.
3. Executa decremento atômico da disponibilidade e cria hold com expiração.
4. Publica `InventarioReservado` e retorna prazo e snapshot de preço.

**Alternativos:** lista de espera quando esgotado.  
**Exceções:** E1 esgotado; E2 venda encerrada; E3 limite por comprador; E4 conflito idempotente.  
**Pós-condição:** capacidade reservada, ainda não vendida.

## UC710 — Comprar estacionamento

**Situação implementada parcialmente:** `POST /api/v1/orders` cria um pedido
idempotente para uma reserva `MANTIDA` do próprio ator e do tenant ativo. O
serviço valida o canal, congela os snapshots comerciais, calcula o total, grava
`PedidoCriado` e avança para `AGUARDANDO_PAGAMENTO` com
`PedidoAguardandoPagamento`. `GET /api/v1/orders/{id}` respeita ownership. Um
job expira pedidos vencidos e publica `PedidoExpirado`.
`POST /api/v1/orders/{id}/manual-confirmation` implementa o caminho alternativo
de pagamento manual: exige Admin/Gestor, `If-Match`, `Idempotency-Key`, meio,
referência de evidência e motivo; confirma inventário e pedido atomicamente e
registra pendência de conciliação. A credencial já pode ser emitida por UC711;
iniciar pagamento por gateway permanece dependente da integração própria.

**Objetivo:** criar pedido e concluir o compromisso comercial.  
**Atores:** Cliente, Caixa, Parceiro.  
**Pré-condições:** reserva mantida; comprador e canal válidos.

**Fluxo principal:** cria pedido com snapshots; calcula total; inicia pagamento; recebe aprovação; confirma inventário e pedido; solicita credencial.  
**Alternativos:** cortesia autorizada; pagamento manual sujeito à conciliação.  
**Exceções:** E1 reserva expirada; E2 pagamento recusado; E3 aprovação com valor divergente; E4 confirmação de inventário conflitante.  
**Pós-condição:** pedido confirmado e processo de credencial iniciado, ou pedido pendente/expirado sem perda de rastreabilidade.

## UC711 — Emitir credencial

**Situação implementada:** `POST /api/v1/orders/{id}/credentials` exige
`credentials:issue`, tenant, ownership e `Idempotency-Key`. Sob lock do pedido,
emite a próxima unidade ainda não atendida como credencial `ATIVA`, congelando
evento, pátio, direito e validade. A última unidade transiciona o pedido para
`ATENDIDO`. `GET /api/v1/credentials/{id}` respeita tenant e proprietário. O
código público não é meio autorizador; a representação segura é criada em UC712.

**Objetivo:** materializar o direito adquirido.  
**Atores:** Process manager; atendimento em reemissão.  
**Pré-condições:** pedido confirmado, cortesia aprovada ou contrato ativo.

**Fluxo principal:** resolve direito; gera token seguro; cria credencial ativa; associa escopos; publica `CredencialEmitida`.  
**Alternativos:** credencial de placa, RFID ou híbrida.  
**Exceções:** E1 origem inelegível; E2 credencial ativa já existente; E3 política incompatível.  
**Pós-condição:** credencial ativa e auditável.

## UC712 — Gerar QR Code

**Situação implementada:** `POST /api/v1/credentials/{id}/qr-code` exige
`credentials:issue`, tenant, ownership e `If-Match`. Sob lock pessimista da
credencial, valida estado `ATIVA`, meio preferido `QR`, validade e versão; gera
32 bytes com CSPRNG, devolve o token opaco somente na resposta `201`, persiste
apenas SHA-256 e publica `QRCodeGerado` sem token ou hash. A versão da credencial
é incrementada e a constraint parcial impede duas representações ativas.

**Objetivo:** criar representação escaneável da credencial.  
**Atores:** Sistema.  
**Pré-condições:** credencial ativa e meio QR permitido.

**Fluxo principal:** gera representação não previsível; associa versão/validade; persiste hash; publica `QRCodeGerado`; solicita entrega.  
**Alternativos:** QR rotativo quando política exigir.  
**Exceções:** E1 credencial bloqueada/expirada; E2 falha de geração; E3 reemissão concorrente.  
**Pós-condição:** QR utilizável sem expor dados pessoais desnecessários.

## UC713 — Cancelar pedido

**Objetivo:** encerrar pedido conforme política.  
**Atores:** Cliente, Gestor, Atendimento.  
**Pré-condições:** pedido cancelável; identidade autorizada.

**Fluxo principal:** calcula elegibilidade; registra motivo; cancela pedido; bloqueia credencial; libera inventário uma vez; solicita reembolso quando necessário.  
**Alternativos:** cancelamento com multa ou crédito.  
**Exceções:** E1 prazo expirado; E2 credencial já consumida; E3 pedido terminal.  
**Pós-condição:** pedido cancelado/em reembolso e direito inutilizado.

## UC714 — Reembolsar pagamento

**Objetivo:** restituir valor capturado.  
**Atores:** Cliente solicita; Financeiro aprova; gateway executa.  
**Pré-condições:** pagamento capturado; saldo reembolsável.

**Fluxo principal:** valida política/limite; cria reembolso; obtém aprovação quando necessária; envia ao gateway; processa retorno/webhook; ajusta financeiro.  
**Alternativos:** reembolso parcial.  
**Exceções:** E1 valor acima do saldo; E2 gateway indisponível (permanece pendente); E3 retorno divergente.  
**Pós-condição:** reembolso aprovado, recusado ou em processamento, sempre conciliável.

## UC715 — Realizar check-in

**Objetivo:** autorizar e registrar entrada.  
**Atores:** Fiscal de acesso, Operador, Dispositivo.  
**Pré-condições:** evento em operação; faixa e identidade autorizadas.

**Fluxo principal:** lê QR/placa/RFID; cria tentativa; valida tenant, evento, janela, pátio, produto, credencial, uso e veículo; consome direito; cria presença; abre movimentação; atualiza ocupação; solicita abertura da barreira.  
**Alternativos:** busca manual; override de supervisor; processamento offline.  
**Exceções:** E1 inválida; E2 duplicada; E3 pátio incorreto; E4 fora da janela; E5 bloqueada; E6 lotado.  
**Pós-condição:** entrada registrada ou recusa auditada com motivo.

## UC716 — Realizar check-in offline

**Objetivo:** manter entrada durante perda de conectividade.  
**Atores:** Dispositivo/Operador.  
**Pré-condições:** pacote assinado, válido e previamente aceito pelo edge.

**Fluxo principal:** valida localmente; registra sequência, versão da política, instante e decisão; executa ação permitida; armazena evento criptografado; sincroniza ao reconectar.  
**Alternativos:** decisão de supervisor quando o cache não contém a credencial.  
**Exceções:** E1 pacote expirado; E2 relógio não confiável; E3 armazenamento local cheio; E4 conflito detectado na sincronização.  
**Pós-condição:** decisão preservada e posteriormente reconciliada.

## UC717 — Realizar check-out

**Objetivo:** registrar saída e concluir presença.  
**Atores:** Operador, Dispositivo.  
**Pré-condições:** presença/movimentação aberta ou exceção tratável.

**Fluxo principal:** identifica direito/veículo; valida direção; encerra presença e movimentação; aplica política de reentrada; atualiza ocupação; solicita barreira.  
**Alternativos:** saída manual sem leitura, com motivo.  
**Exceções:** E1 presença inexistente; E2 saída duplicada; E3 equipamento falhou.  
**Pós-condição:** veículo fora, sessão encerrada e direito consumido ou reativado.

## UC718 — Abrir movimentação

**Objetivo:** iniciar a permanência física.  
**Atores:** Policy pós-check-in; Operador no fluxo legado autorizado.  
**Pré-condições:** autorização/override; veículo e pátio ativos; nenhuma sessão incompatível.

**Fluxo principal:** captura snapshots e origem do direito; registra entrada e estado aberto; publica `MovimentacaoAberta`.  
**Alternativos:** entrada rotativa ou mensalista futura.  
**Exceções:** E1 veículo já no pátio; E2 pátio lotado/inativo; E3 origem inválida.  
**Pós-condição:** sessão aberta e ocupação incrementável.

## UC719 — Encerrar movimentação

**Objetivo:** finalizar permanência e valor aplicável.  
**Atores:** Policy pós-checkout; Operador.  
**Pré-condições:** movimentação aberta.

**Fluxo principal:** registra saída; calcula permanência; aplica regra da modalidade; grava valor/snapshot; fecha; publica `MovimentacaoEncerrada`.  
**Alternativos:** evento pré-pago, mensalista sem cobrança no ato, rotativo tarifado.  
**Exceções:** E1 já encerrada; E2 regra tarifária ausente no rotativo; E3 horário inconsistente.  
**Pós-condição:** sessão terminal; correções posteriores somente por ajuste.

## UC720 — Fechar evento

**Objetivo:** consolidar operação e resultado.  
**Atores:** Supervisor encerra operação; Financeiro concilia; Gestor aprova.  
**Pré-condições:** entradas encerradas; fontes disponíveis ou ressalva formal.

**Fluxo principal:** consolida inventário, pedidos, pagamentos, credenciais, acessos, sessões, caixas, fiscal e repasses; detecta divergências; resolve/aprova; fecha e finaliza evento.  
**Alternativos:** fechamento com ressalva e tarefas pendentes.  
**Exceções:** E1 sessões críticas abertas; E2 caixa não fechado; E3 divergência sem aprovação; E4 pagamento não conciliado.  
**Pós-condição:** fechamento imutável e relatório versionado.

## UC721 — Gerar DRE por evento

**Objetivo:** demonstrar resultado econômico.  
**Atores:** Financeiro, Gestor, Auditor.  
**Pré-condições:** fontes classificadas e período definido.

**Fluxo principal:** consolida receita bruta, descontos, reembolsos, taxas, impostos, custos diretos, equipe, logística e repasses; calcula margem; registra versão e linhagem.  
**Alternativos:** DRE preliminar antes do fechamento.  
**Exceções:** E1 fonte incompleta; E2 centro de custo ausente; E3 moeda incompatível.  
**Pós-condição:** demonstrativo reproduzível, sem alterar origens.

## UC722 — Gerar documento fiscal

**Objetivo:** emitir o documento correspondente à operação.  
**Atores:** Fiscal tributário; sistema conforme policy.  
**Pré-condições:** origem elegível, emitente e dados fiscais válidos.

**Fluxo principal:** determina obrigação; cria rascunho; valida; transmite por adapter; processa protocolo; marca autorizado ou rejeitado; armazena representação segura.  
**Alternativos:** contingência e emissão posterior.  
**Exceções:** E1 dados incompletos; E2 rejeição; E3 provedor indisponível; E4 duplicidade.  
**Pós-condição:** documento em estado rastreável e vinculado à origem.

## UC723 — Registrar compra

**Objetivo:** reconhecer aquisição, estoque e obrigação.  
**Atores:** Compras/Financeiro.  
**Pré-condições:** fornecedor/produtos ativos e documento pendente.

**Fluxo principal:** cria nota/compra; valida itens e rateios; confirma; gera movimentos de estoque, atualiza custo por política e cria contas a pagar.  
**Alternativos:** compra sem documento fiscal completo em recebimento provisório.  
**Exceções:** E1 duplicidade fiscal; E2 rateio inconsistente; E3 período fechado.  
**Pós-condição:** estoque e financeiro rastreiam a mesma origem.

## UC724 — Registrar venda administrativa

**Objetivo:** reconhecer venda fora do PDV rápido.  
**Atores:** Comercial/Financeiro.  
**Pré-condições:** cliente/produtos ativos e estoque suficiente.

**Fluxo principal:** cria nota/pedido; valida saldo; confirma; baixa estoque; gera conta a receber; solicita fiscal quando aplicável.  
**Alternativos:** serviço sem estoque.  
**Exceções:** E1 saldo insuficiente; E2 limite/regra comercial; E3 duplicidade.  
**Pós-condição:** venda, estoque, financeiro e fiscal correlacionados.

## UC725 — Venda PDV

**Objetivo:** realizar venda rápida em evento/pátio.  
**Atores:** Caixa.  
**Pré-condições:** caixa aberto, terminal autorizado, itens disponíveis.

**Fluxo principal:** cria carrinho; adiciona itens; calcula total; recebe pagamento; confirma venda; gera movimentos de estoque; comprovante e obrigação fiscal.  
**Alternativos:** múltiplos meios, comanda, venda offline limitada.  
**Exceções:** E1 estoque; E2 pagamento recusado; E3 caixa divergente; E4 sincronização conflitante.  
**Pós-condição:** venda concluída ou cancelada sem efeitos parciais invisíveis.

## UC726 — Gerenciar mensalista

**Objetivo:** criar, ativar, suspender, renovar e cancelar contrato.  
**Atores:** Gestor, Financeiro, Cliente no portal limitado.  
**Pré-condições:** plano, cliente e veículos elegíveis.

**Fluxo principal:** cria contrato; valida vigência/limites; ativa; emite direito; gera cobranças por competência; renova ou encerra conforme comandos.  
**Alternativos:** suspensão automática por inadimplência.  
**Exceções:** E1 contrato ativo conflitante; E2 vigência; E3 limite de veículos; E4 cobrança duplicada.  
**Pós-condição:** contrato e direitos coerentes. O fluxo atual existente permanece documentado em [[05-casos-de-uso/estacionamento-patio]].

## UC727 — Operar estacionamento rotativo

**Objetivo:** registrar entrada, permanência, cobrança e saída avulsa.  
**Atores:** Operador, Caixa, Dispositivo.  
**Pré-condições:** modalidade habilitada; tarifa versionada; pátio/caixa disponíveis.

**Fluxo principal:** abre sessão; congela regra tarifária; na saída calcula tolerância, frações e teto; recebe/associa pagamento; encerra e libera capacidade.  
**Alternativos:** ticket, placa, QR, ticket perdido e validação.  
**Exceções:** E1 lotação; E2 tarifa ausente; E3 pagamento recusado; E4 ticket perdido.  
**Pós-condição:** sessão, pagamento e caixa conciliáveis. Esta fase permanece TODO durante o foco exclusivo em eventos.

## UC728 — Controlar caixa e fechamento de turno

**Objetivo:** controlar valores sob responsabilidade do caixa.  
**Atores:** Caixa, Supervisor.  
**Pré-condições:** operador e terminal escalados; nenhum caixa incompatível aberto.

**Fluxo principal:** abre com fundo; registra recebimentos, sangrias e suprimentos; solicita fechamento; sistema apura; operador informa contagem; supervisor resolve divergências; fecha.  
**Alternativos:** troca de operador e fechamento emergencial.  
**Exceções:** E1 caixa já aberto; E2 diferença acima da alçada; E3 transação pendente; E4 tentativa de venda após fechamento.  
**Pós-condição:** caixa fechado e disponível para conciliação.

## UC729 — Planejar e executar escalas

**Objetivo:** dimensionar e alocar equipe.  
**Atores:** RH, Gestor, Supervisor.  
**Pré-condições:** evento/pátio, funções e colaboradores ativos.

**Fluxo principal:** cria escala; define turnos/funções/locais; valida conflito e qualificação; publica; registra início, substituições e encerramento.  
**Alternativos:** convocação e aceite pelo colaborador.  
**Exceções:** E1 sobreposição; E2 colaborador inativo; E3 permissão incompatível; E4 ausência sem substituto.  
**Pós-condição:** equipe e responsabilidade de cada operação conhecidas.

## UC730 — Integrar sistema externo

**Objetivo:** conectar ticketing, pagamento, fiscal, equipamento ou parceiro.  
**Atores:** Administrador, Integração técnica.  
**Pré-condições:** conexão provisionada, escopos e segredos válidos.

**Fluxo principal:** recebe/envia contrato versionado; autentica; registra inbox/outbox; traduz por ACL; executa comando idempotente; responde/agenda entrega; monitora.  
**Alternativos:** reconciliação em lote.  
**Exceções:** E1 assinatura; E2 replay; E3 rate limit; E4 indisponibilidade; E5 payload incompatível; E6 quarentena.  
**Pós-condição:** efeito aplicado uma vez ou falha operacionalmente visível.

## 16.2 Rastreabilidade com casos existentes

Os casos de uso AS-IS não são substituídos. Permanecem normativos para o comportamento implementado:

- geografia/RH/usuários: [[05-casos-de-uso/cadastros-geografia-rh]];
- pessoas/pagamentos cadastrais: [[05-casos-de-uso/cadastros-pagamento-pessoas]];
- conveniência: [[05-casos-de-uso/cadastros-conveniencia]];
- pátio, mensalista e rotativo atual: [[05-casos-de-uso/estacionamento-patio]];
- fiscal: [[05-casos-de-uso/processos-fiscais]];
- financeiro: [[05-casos-de-uso/processos-financeiros]];
- logística: [[05-casos-de-uso/logistica]].

Quando houver divergência, o código atual define o AS-IS e este documento define o comportamento TARGET a ser alcançado por roadmap explícito.
