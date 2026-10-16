> Links: [[README]] · [[12-dominio-ddd]] · [[13-event-storming]] · [[15-maquinas-de-estados]] · [[07-dicionario-de-dados]]

# 14. Modelagem Completa do Domínio

## 14.1 Convenções de modelagem

- **AS-IS:** entidade persistente existente no projeto Java.
- **EVOLUÇÃO:** conceito existente que receberá semântica ou relacionamento adicional.
- **TARGET:** entidade da arquitetura-alvo ainda não implementada.
- Atributos listados são atributos de domínio mínimos; detalhes físicos do modelo atual permanecem em [[07-dicionario-de-dados]].
- Todo agregado empresarial TARGET possui, ainda que omitidos das tabelas: `id`, `organizationId`, `version`, `createdAt`, `createdBy`, `updatedAt`, `updatedBy`.
- Toda transição sensível registra `actorId`, `actorType`, `source`, `deviceId`, `reason`, `traceId`, instante e versão anterior/nova.
- Histórico não é sobrescrito. Mudanças materiais geram eventos, versões ou registros de transição.

## 14.2 Organização, identidade e cadastros

| Entidade | Situação e responsabilidade | Atributos principais | Relacionamentos | Estados/validações | Histórico e auditoria |
|---|---|---|---|---|---|
| `Organizacao` | AS-IS/EVOLUÇÃO; tenant e proprietário dos dados | documento, razão social, nome, moeda, fuso, região, plano | 1:N memberships; futuramente unidades, pátios e eventos | `PROVISIONANDO/ATIVA/SUSPENSA/ENCERRADA`; documento único; fuso/moeda obrigatórios | timestamps, versão otimista e registro imutável de criação/alteração |
| `UnidadeOperacional` | AS-IS/EVOLUÇÃO; venue administrativo e local de evento | nome, tipo fixo `VENUE`, endereço, cidade, timezone | N:1 organização; vínculo com evento entra na História 7; vínculo com pátios permanece evolução | nasce ativa; endereço, cidade ativa, organização ativa e fuso IANA válidos; nome normalizado único no tenant | criação auditada e versão otimista; contatos e alteração de status permanecem futuras |
| `Usuario` | AS-IS/EVOLUÇÃO; identidade autenticável global | nome, login, hash, email, ativo, perfil legado | N:N organizações por `MembroOrganizacao`; futuramente N:N atribuições e 0:1 funcionário | ativo/inativo; login global único; MFA e estados ampliados futuros | inativação lógica; sessões e concessões detalhadas futuras |
| `MembroOrganizacao` | AS-IS/EVOLUÇÃO; vínculo temporal entre identidade global e tenant | organizacaoId, usuarioId, status, origem, entrouEm, suspensoEm, encerradoEm, version | N:1 organização; N:1 usuário | `ATIVO/SUSPENSO/ENCERRADO`; no máximo um vínculo corrente por par; encerrado é terminal | histórico preservado; versão invalida JWT contextual anterior |
| `Papel` | AS-IS/EVOLUÇÃO; conjunto nomeado de permissões | código, nome, sistema | N:N permissões; N:N memberships por atribuição | código único; catálogo inicial de administração organizacional | novos papéis/alterações futuras serão auditados |
| `AtribuicaoAcesso` | AS-IS/EVOLUÇÃO; papel em escopo | membershipId, papelId, organizationId, tipoEscopo, escopoId, concessão, vigência, revogação, version | N:1 Membership/papel | escopo pertence ao tenant; uma atribuição não revogada por combinação | revogação preserva registro e motivo, inclusive antes do início da vigência |
| `SessaoAutenticacao` | TARGET; ciclo da sessão | jti, client, device, emitidaEm, expiraEm, revogadaEm | N:1 usuário | token rotacionável e revogável | IP reduzido/mascarado, dispositivo e revogação |
| `RegistroAuditoria` | AS-IS/EVOLUÇÃO; trilha imutável | tenant, ação, recurso, antes/depois redigidos, versões, ator, origem, IP, dispositivo, trace, motivo, classe de retenção | referência lógica a qualquer agregado; ator humano referencia usuário | append-only protegido no PostgreSQL; retenção classificada, prazos futuros | é o próprio histórico auditável; instrumenta Organização, Membership e RBAC |
| `Pais` | AS-IS; referência geográfica | nome, sigla, código, moeda, DDI | 1:N estados | nome único; ativo | timestamps e operador futuro |
| `Estado` | AS-IS; divisão geográfica | nome, UF | N:1 país; 1:N cidades | `(país, UF)` único | timestamps |
| `Cidade` | AS-IS; município | nome, código IBGE | N:1 estado | `(estado, nome)` único | timestamps |
| `Cliente` | AS-IS/EVOLUÇÃO; parte comercial | tipo, documento, nome, contatos, endereço, limite | N:1 cidade/condição; N:N veículos | documento normalizado/único; ativo | versão cadastral e consentimentos |
| `Fornecedor` | AS-IS; parte de compras | tipo, documento, nome, contatos, limite | cidade, condição, transportadora, contatos | documento único; principal único por tipo | alterações e inativação |
| `FornecedorEmail` | AS-IS; contato filho | email, tipo, principal | N:1 fornecedor | formato válido; um principal | auditoria pelo agregado fornecedor |
| `FornecedorTelefone` | AS-IS; contato filho | telefone, tipo, principal | N:1 fornecedor | um principal | auditoria pelo agregado fornecedor |
| `Veiculo` | AS-IS/EVOLUÇÃO; veículo operacional | placa, modelo, cor, categoria, observação | 0:N vínculos com cliente; N sessões/credenciais | placa normalizada/única no tenant; ativo/bloqueado | mudanças de placa preservam snapshot histórico |
| `VinculoClienteVeiculo` | TARGET; propriedade/uso temporal | clienteId, veiculoId, início, fim, tipo | N:1 cliente/veículo | período coerente; sobreposição conforme política | quem vinculou/desvinculou |
| `BloqueioVeiculo` | TARGET; restrição de acesso | veiculoId/placa, motivo, início/fim, severidade | N:1 veículo ou placa | vigência; aprovador conforme severidade | criação, renovação e retirada |

## 14.3 Instalações e eventos

| Entidade | Situação e responsabilidade | Atributos principais | Relacionamentos | Estados/validações | Histórico e auditoria |
|---|---|---|---|---|---|
| `Patio` | AS-IS/EVOLUÇÃO; raiz de instalação física tenant-aware | nome, endereço, cidade, capacidade física, capacidade operacional, observação | N:1 organização; unidade/venue e setores permanecem evoluções | ativo; física > 0; `0 ≤ operacional ≤ física`; nome normalizado único por organização | criação auditada e versão otimista; interdições permanecem futuras |
| `Setor` | AS-IS/EVOLUÇÃO; divisão operacional | código, nome, capacidade, categorias; coordenadas permanecem TARGET | N:1 pátio tenant-aware; 1:N vagas/faixas futuras | ativo; soma das capacidades ≤ pátio sob lock; código normalizado único no pátio; categorias defendidas no banco | criação auditada/versionada e `SetorCriado` na outbox; alterações e bloqueios permanecem TARGET |
| `Vaga` | AS-IS/EVOLUÇÃO; unidade individual opcional | código, tipo, acessibilidade e posição textual opcional; coordenadas permanecem TARGET | N:1 setor no mesmo tenant | nasce `LIVRE`; tipos defendidos; código normalizado único no setor; batch não excede capacidade e aceita somente categoria do setor | criação auditada/versionada e `VagaCriada` na outbox; bloqueio/manutenção permanecem TARGET |
| `FaixaAcesso` | TARGET; via física de entrada/saída | código, direção, modo de falha | N:1 pátio/setor; N:N equipamentos | entrada/saída/bidirecional; ativa | abertura manual e mudança de política |
| `Interdicao` | TARGET; reduz capacidade | alvo, período, quantidade/motivo | pátio/setor/vaga | período e quantidade válidos | autor, aprovação e encerramento |
| `Evento` | AS-IS/EVOLUÇÃO; ciclo comercial/operacional iniciado | nome, venue, início/fim, fuso, externalId opcional | N:1 organização; N:1 venue; N:N pátios entra por alocação | nasce `RASCUNHO`; publicação exige versão atual, alocação vendável e produto publicado, transitando por `CONFIGURADO` até `PUBLICADO`; abertura imediata transita para `VENDAS_ABERTAS`; demais estados permanecem futuros | criação, publicação e abertura auditadas/versionadas; `EventoCriado`, `EventoConfigurado`, `EventoPublicado` e `VendasDoEventoAbertas` persistidos em outbox |
| `ParticipanteOrganizacional` | TARGET; operador/promotor no evento | organizationId, papel, regras de repasse | N:1 evento | organização ativa; papel permitido | vigência e aprovador |
| `AlocacaoPatioEvento` | AS-IS/EVOLUÇÃO; capacidade temporal comprometida | eventoId, patioId, período, capacidade operacional/vendável/reservada | N:1 evento/pátio, sempre no mesmo tenant | `vendável + reservada ≤ operacional ≤ capacidade operacional do pátio`; soma simultânea não sobrealoca o pátio; evento configurável e pátio ativo | criação idempotente e auditada com outbox; alteração de capacidades usa `If-Match`; override e capacidade consumida permanecem futuras |
| `PoliticaEvento` | TARGET; regras versionadas | reentrada, tolerância, cancelamento, cut-off, categorias | 1:1/N:1 evento | versão imutável após uso | snapshot referenciado por credenciais/pedidos |
| `PortaoLogicoEvento` | TARGET; roteamento de acesso | nome, produtos aceitos, faixas, janela | N:1 evento; N:N faixas | pelo menos uma faixa; regras não conflitantes | configuração publicada por versão |

## 14.4 Comercial, inventário, pedidos e pagamentos

| Entidade | Situação e responsabilidade | Atributos principais | Relacionamentos | Estados/validações | Histórico e auditoria |
|---|---|---|---|---|---|
| `ProdutoEstacionamento` | AS-IS/EVOLUÇÃO; oferta de direito e autoridade do saldo | nome, categoria, direito, alocação, janela, quota, reservado, vendido, bloqueado, benefícios e restrições | N:1 evento/alocação no mesmo tenant; 1:N lotes/holds | nasce `RASCUNHO`; soma de quotas ≤ vendável; publica com lote; `reservado + vendido + bloqueado ≤ quota` | criação/publicação auditadas; lock e versão serializam saldo; constraints defendem contadores |
| `LotePreco` | AS-IS/EVOLUÇÃO; preço temporal/quantitativo | nome, preço, moeda, início/fim, quantidade, prioridade | N:1 produto no mesmo tenant | preço ≥ 0; moeda da organização; quantidade ≤ quota; período coerente; sobreposição na mesma prioridade é proibida | criação idempotente, auditada e versionada; janela determina agendamento; snapshot permanece futuro |
| `QuotaCanal` | TARGET; inventário por canal | canal, quantidade, período | N:1 produto/alocação | soma de quotas ≤ vendável | transferência de quota auditada |
| `ReservaInventario` | AS-IS/EVOLUÇÃO; hold temporário | produto, quantidade, canal, referência do comprador, expiraEm, idempotencyKey, snapshot de lote/preço/moeda | N:1 produto; 0:1 pedido | `MANTIDA/EXPIRADA/LIBERADA/CONFIRMADA/CANCELADA`; confirmação converte reservado em vendido; cancelamento compensa reservado ou vendido uma única vez; transições atômicas | criação, expiração, liberação, confirmação e cancelamento auditados com outbox |
| `Pedido` | AS-IS/EVOLUÇÃO; contrato comercial | número, comprador, canal, moeda, subtotal, desconto, taxa, total, expiraEm | 1:N itens; 0:1 confirmação manual; 0:1 cancelamento; pagamentos/credenciais | `CRIADO/AGUARDANDO_PAGAMENTO/CONFIRMADO/ATENDIDO/EXPIRADO/CANCELADO/EM_REEMBOLSO`; total derivado; confirmado imutável; cancelamento único | criação, espera, expiração, confirmação manual e cancelamento com snapshots |
| `CancelamentoPedido` | AS-IS; processo compensatório | pedido, ator, motivo, estado resultante, reembolsoNecessario, quantidadeLiberada, credenciaisBloqueadas | 1:1 pedido no tenant | único por pedido; resultado imutável; replay por chave idempotente | motivo, ator e efeitos preservados para UC714 e conciliação |
| `ItemPedido` | AS-IS/EVOLUÇÃO; linha comercial | produtoId, alocaçãoId, pátioId, loteId, descrição, categoria, direito, janela, quantidade, preço, total | N:1 pedido; snapshot da reserva; 1:N credenciais | total derivado; snapshot obrigatório | preservado mesmo se produto/alocação mudar |
| `ConfirmacaoManualPedido` | AS-IS; exceção conciliável | pedido, ator, meio, referênciaEvidência, motivo, statusConciliação | 1:1 pedido no tenant | única por pedido; nasce `PENDENTE_CONCILIACAO`; não equivale a gateway | evidência e ator preservados para conciliação |
| `DescontoPedido` | TARGET; benefício aplicado | código, tipo, valor, origem, justificativa | N:1 pedido/item | limites e autorização | concessor e regra versionada |
| `IntencaoPagamento` | TARGET; ciclo transacional | provider, método, valor, moeda, externalRef, idempotencyKey | N:1 pedido; 1:N tentativas/capturas | estados; referência externa única | payloads redigidos e callbacks |
| `TentativaPagamento` | TARGET; chamada individual | número, iniciadaEm, concluídaEm, resultado, código | N:1 intenção | append-only; sem PAN | request/response redigidos e latência |
| `CapturaPagamento` | TARGET; valor efetivamente capturado | valor, externalRef, capturadaEm | N:1 intenção | soma ≤ autorizado | origem e conciliação |
| `Reembolso` | TARGET; restituição | valor, motivo, solicitado/aprovado, externalRef | N:1 pagamento | solicitado/processando/aprovado/recusado; soma ≤ capturado | solicitante e aprovador distintos conforme limite |
| `Chargeback` | TARGET; contestação | valor, razão, prazo, evidências | N:1 pagamento | aberto/contestado/ganho/perdido | evidência e decisões |
| `FormaPagamento` | AS-IS; cadastro de modalidade | nome, descrição, ativo | parcelas/contas | nome único | inativação |
| `CondicaoPagamento` | AS-IS; regra cadastral de parcelamento | parcelas, juros, multa, desconto | 1:N parcelas | percentuais e quantidade válidos | alterações administrativas |
| `ParcelaCondicaoPagamento` | AS-IS; definição de parcela | número, dias, percentual, forma | N:1 condição | números únicos e soma conforme regra | pelo agregado condição |

## 14.5 Credenciais, acesso e operação

| Entidade | Situação e responsabilidade | Atributos principais | Relacionamentos | Estados/validações | Histórico e auditoria |
|---|---|---|---|---|---|
| `CredencialAcesso` | AS-IS/EVOLUÇÃO; direito independente do meio | origem, direito, validade, unidade, pátio, evento, veículo opcional, código público, qrVersao | N:1 pedido/item; 1:N representações | `PENDENTE → ATIVA` implementado; demais estados TARGET; uma por unidade | emissão e geração QR auditadas; código público não é token autorizador |
| `RepresentacaoQR` | AS-IS/EVOLUÇÃO; representação escaneável | tokenHash, número da versão, emitidoEm, expiraEm, revogadaEm | N:1 credencial tenant-aware | token opaco de 256 bits; somente hash SHA-256 persistido; uma representação não revogada por credencial | geração auditada; revogação continua TARGET |
| `PlacaAutorizada` | TARGET; placa como representação | placa normalizada, confiança exigida | N:1 credencial | período e quantidade de alterações | todas as trocas de placa |
| `TagRFID` | TARGET; identificador físico | uidTokenizado, emissor, status | N:1 credencial | único e revogável | emissão/substituição |
| `TentativaAcesso` | AS-IS/EVOLUÇÃO; toda decisão de acesso | operação, direção, instante, faixa, credencial/placa, decisão, motivo | 0:1 credencial; N:1 evento/pátio | `VALIDACAO/CHECKIN/CHECKOUT`; autorizada/recusada implementadas; validação autorizada não registra consumo/ocupação | ator e contexto sempre; dispositivo/evidência permanecem TARGET |
| `OverrideAcesso` | TARGET; exceção autorizada | tentativaId, motivo, aprovador, validade | 1:1 tentativa | permissão e motivo; limite temporal | registro imutável |
| `Presenca` | TARGET; estado dentro/fora | credencialId, checkin, checkout, reentradas | N:1 credencial/evento/pátio | uma presença incompatível aberta | cada entrada/saída e conflito |
| `Movimentacao` / `SessaoEstacionamento` | AS-IS/EVOLUÇÃO; permanência física | veículo, pátio, evento, origemDireito, entrada, saída, valor, status | veículo, credencial, pedido, mensalista/tarifa | aberta/encerrada/cancelada; pátio obrigatório no target | snapshots e ajustes, nunca reabrir encerrada |
| `AjusteOperacional` | TARGET; correção compensatória | sessãoId, tipo, antes/depois, motivo | N:1 sessão | supervisor e justificativa | imutável |
| `TarifaRotativa` | AS-IS/EVOLUÇÃO; preço de permanência | vigência, pátio, categoria, regras e limites | N:1 pátio | versão/vigência; não usar “último ID” no target | snapshot na sessão |

## 14.6 Mensalistas, conveniência, estoque e logística

| Entidade | Situação e responsabilidade | Atributos principais | Relacionamentos | Estados/validações | Histórico e auditoria |
|---|---|---|---|---|---|
| `PlanoAssinatura` | TARGET; produto recorrente | nome, preço, periodicidade, pátios, horários, limites | 1:N contratos | ativo/inativo; regra versionada | versões de plano |
| `ContratoMensalista` | AS-IS/EVOLUÇÃO; vínculo recorrente | cliente, plano, vigência, valor, status, inadimplência | 1:N veículos/cobranças/credenciais | rascunho/ativo/suspenso/cancelado/expirado | transições, renovação e motivo |
| `VeiculoAutorizadoContrato` | TARGET; veículo do contrato | veículoId, início/fim | N:1 contrato | limite do plano e vigência | inclusão/remoção |
| `Produto` | AS-IS; item de conveniência | nome, marca, categoria, unidade, compra, custo, venda, quantidade mínima | fornecedores, itens e movimentos | valores não negativos conforme regra | alterações e custos |
| `Categoria` | AS-IS; classificação | nome, descrição | 1:N produtos | nome único | timestamps |
| `Marca` | AS-IS; marca comercial | nome | 1:N produtos | nome único | timestamps |
| `UnidadeMedida` | AS-IS; unidade | nome, sigla | 1:N produtos | sigla/nome únicos | timestamps |
| `Servico` | AS-IS; serviço vendável | nome, descrição, valor | notas/PDV | ativo e valor válido | alterações |
| `MovimentoEstoque` | TARGET; razão de estoque | produto, local, tipo, quantidade, custo, origem | N:1 produto/local | append-only; compensação em vez de edição | ator, origem e documento |
| `LocalEstoque` | TARGET; saldo por localização | nome, pátio/unidade | 1:N movimentos/saldos | ativo; único no tenant | inventários e bloqueios |
| `VendaPDV` | TARGET; venda rápida | caixa, cliente opcional, totais, status | itens, pagamentos, fiscal | aberta/paga/cancelada; caixa aberto | operador, terminal e estornos |
| `ItemVendaPDV` | TARGET; item da venda | produto/serviço, quantidade, preço, total | N:1 venda | snapshot e saldo | preservado após mudança de catálogo |
| `ProdutoFornecedor` | AS-IS; relação comercial | produto, fornecedor, código, custo | N:1 ambos | par único | custo e vigência futuros |
| `Transportadora` | AS-IS; transportador | documento, nome, contatos | frota, fornecedores | documento único; ativa | alterações |
| `VeiculoFrota` | AS-IS; veículo de carga | placa, modelo, marca, ano, capacidade | N:N transportadoras | placa única | vínculos e inativação |
| `TransportadoraVeiculoFrota` | AS-IS; associação | transportadoraId, veiculoId | N:1 ambos | par único | timestamps |
| `Entrega` | TARGET; recebimento/logística | fornecedor, transportadora, veículo, janela, status | compra/local | planejada/em-rota/recebida/cancelada | check-in e recebimento |

## 14.7 Fiscal, financeiro, RH e equipamentos

| Entidade | Situação e responsabilidade | Atributos principais | Relacionamentos | Estados/validações | Histórico e auditoria |
|---|---|---|---|---|---|
| `NotaEntrada` | AS-IS/EVOLUÇÃO; documento de compra | fornecedor, emissão, valores, condição, situação | 1:N itens; contas | pendente/confirmada/cancelada; confirmada imutável | confirmação e efeitos |
| `ItemNotaEntrada` | AS-IS; linha de compra | produto, quantidade, valor, rateios | N:1 nota | totais derivados | snapshot |
| `NotaSaida` | AS-IS/EVOLUÇÃO; documento de venda | cliente, emissão, valores, condição, situação | 1:N itens; contas | pendente/confirmada/cancelada | confirmação e baixa |
| `ItemNotaSaida` | AS-IS; linha de venda | produto, quantidade, valor | N:1 nota | saldo suficiente | snapshot |
| `NotaServico` | AS-IS/EVOLUÇÃO; serviço/futuro NFS-e | cliente, serviço/origem, ISS, total, situação | movimentação/mensalista/serviço | pendente/emitida/cancelada/rejeitada target | transmissões e cancelamento |
| `DocumentoFiscal` | TARGET; abstração externa | tipo, emitente, número, série, chave, ambiente, XML/ref | origem comercial | solicitado/processando/autorizado/rejeitado/cancelado | todos os protocolos |
| `ContaPagar` | AS-IS/EVOLUÇÃO; obrigação | fornecedor, origem, parcela, valores, vencimento | nota/condição/baixas | pendente/parcial/paga/cancelada | baixas e ajustes |
| `ContaReceber` | AS-IS/EVOLUÇÃO; direito | cliente, pedido/nota/contrato, valores, vencimento | origem/baixas | pendente/parcial/recebida/cancelada | baixas e conciliação |
| `ContaPagarAvulsa` | AS-IS; despesa sem nota estruturada | descrição, fornecedor opcional, valores, datas | forma de pagamento | estados de conta | baixas |
| `BaixaFinanceira` | TARGET; lançamento de liquidação | conta, valor, data, meio, origem | N:1 conta | append-only; ≤ saldo | operador e conciliação |
| `Conciliacao` | TARGET; conferência entre fontes | período, origem, totais, divergências | pagamentos/contas/caixa | pendente/com-divergências/conciliada/aprovada | executor/aprovador |
| `Repasse` | TARGET; obrigação com parceiro | beneficiário, regra, base, taxas, líquido | evento/pedidos | calculado/aprovado/pago/cancelado | regra versionada |
| `CentroCusto` | TARGET; dimensão gerencial | código, nome, hierarquia | eventos/despesas/receitas | ativo e código único | alterações |
| `Cargo` | AS-IS; função laboral | nome, salário, carga, exigeCNH | 1:N funcionários | valores válidos | alterações |
| `Funcionario` | AS-IS/EVOLUÇÃO; vínculo RH | dados pessoais, cargo, cidade, situação | usuário opcional; escalas | ativo/inativo/afastado target | mudanças laborais restritas |
| `EscalaOperacional` | TARGET; equipe de evento/pátio | evento, período, status | 1:N turnos/alocações | rascunho/publicada/em-execução/encerrada | publicação e alterações tardias |
| `TurnoOperacional` | TARGET; jornada alocada | funcionário, função, local, início/fim | N:1 escala | sem sobreposição; qualificação | presença e substituição |
| `Colaborador` | AS-IS/EVOLUÇÃO; convite de atuação no pátio | pátio, nome, email, perfil, status | deve relacionar usuário e não duplicar RH | convidado/ativo/inativo | convite, aceite e revogação |
| `Equipamento` | TARGET; identidade do dispositivo | tipo, serial, modelo, capacidades, firmware, estado | instalação/gateway | registrado/online/offline/manutenção/desativado | provisionamento e saúde |
| `InstalacaoEquipamento` | TARGET; vínculo físico temporal | equipamento, pátio/setor/faixa, início/fim | N:1 equipamento/local | uma instalação ativa incompatível | instalador e mudanças |
| `ComandoEquipamento` | TARGET; comando canônico | capacidade, parâmetros, prazo, correlação, prioridade | N:1 equipamento/gateway | solicitado/aceito/despachado/executado/falhou/expirado | tentativas e resposta física |
| `SiteController` | TARGET; edge local | identidade, versão, site, última sincronização | 1:N equipamentos/pacotes | online/offline/degradado | atualizações e pacotes |
| `PacotePoliticaOffline` | TARGET; dados mínimos assinados | versão, válidoDe/Até, hash, regras, credenciais | N:1 site/evento | assinatura e validade obrigatórias | distribuição e aceite |
| `EventoDispositivo` | TARGET; fato canônico do hardware | tipo, deviceSequence, payload normalizado, instante | equipamento/faixa | deduplicação por origem/sequência | payload bruto segregado quando permitido |
| `ConexaoIntegracao` | TARGET; configuração externa | tipo, provider, escopos, status, segredoRef | tenant | ativa/suspensa; segredos externos | rotação e teste |
| `EntregaWebhook` | TARGET; entrega confiável | destino, evento, tentativa, status, próximaTentativa | conexão/evento outbox | pendente/enviando/entregue/falhou/quarentena | cada tentativa |

## 14.8 Value Objects transversais

| Value Object | Componentes | Regras |
|---|---|---|
| `Dinheiro` | valor decimal, moeda | moeda obrigatória, escala definida, arredondamento explícito |
| `Periodo` | início, fim, fuso de negócio | início < fim; instantes persistidos em UTC |
| `Placa` | país/padrão, valor normalizado | normalização determinística; validação configurável |
| `Documento` | tipo, país, valor normalizado | checksum quando aplicável; proteção de exibição |
| `Quantidade` | valor, unidade | não negativa salvo movimento de compensação explícito |
| `Capacidade` | física, operacional, vendável, reservada | hierarquia de limites obrigatória |
| `ChaveIdempotencia` | chave, owner, operação, hash do payload | mesma chave/payload repete resultado; payload diferente conflita |
| `SnapshotPreco` | descrição, valor, moeda, tributos, regra, versão | imutável após pedido |
| `DireitoAcesso` | evento/modalidade, pátios, janelas, categoria, usos | ao menos um escopo e validade |
| `DecisaoAcesso` | decisão, código, mensagem, retryable | código estável; explicação operacional |

## 14.9 Serviços de domínio essenciais

- `ValidadorProntidaoEvento` — verifica o conjunto necessário para publicação/operação.
- `DisponibilidadeInventario` — garante os limites de capacidade em concorrência.
- `SelecionadorPreco` — resolve lote/regra e cria snapshot.
- `PoliticaCancelamento` — determina elegibilidade, multa e restituição.
- `EmissorCredencial` — materializa direito e revoga representações anteriores.
- `PoliticaAutorizacaoAcesso` — decide sem conhecer fabricante.
- `ResolvedorConflitoOffline` — classifica e encaminha duplicidades.
- `CalculadoraTarifaRotativa` — preserva/evolui `CalculoTarifa` atual.
- `ConciliadorFinanceiro` — compara pedido, gateway, caixa e conta.
- `DeterminadorObrigacaoFiscal` — seleciona documento e momento fiscal.
- `OrquestradorDispositivos` — traduz capacidades canônicas em portas de integração.

## 14.10 Regra de preservação do legado

As 36 entidades JPA atuais continuam válidas como modelo AS-IS. A evolução pode especializar conceitos, adicionar referências e introduzir novos agregados, mas não deve eliminar cadastros ou processos existentes sem migração de dados, compatibilidade de API, decisão de produto e plano de descontinuação aprovados.

## 14.11 Ownership obrigatório na plataforma unificada

A preservação do AS-IS não autoriza ownership global permanente. `Usuario`,
`Pais`, `Estado`, `Cidade`, `Permissao` e `Papel` permanecem globais. Os demais
agregados empresariais recebem organização direta ou derivação inequívoca de um
pai tenant-aware. Filhos que relacionam dois agregados — itens fiscais, parcelas
com forma de pagamento e vínculos N:N — carregam owner suficiente para FKs
compostas e rejeição de relações cross-tenant.

`Usuario.funcionario_id` é compatibilidade histórica incompatível com uma
identidade global que pode participar de múltiplas organizações. O vínculo
laboral TARGET é contextual à Membership. `patio.organizacao_id` deixa de ser
opcional após reconciliação. Ordem de Compra e Ordem de Serviço são agregados
TARGET novos e não podem ser substituídos conceitualmente por Nota de Entrada ou
Nota de Serviço. A matriz física e a ordem de cutover estão em
[[22-unificacao-multitenant]].
