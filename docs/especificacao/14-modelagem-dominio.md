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
| `Organizacao` | TARGET; tenant e proprietário dos dados | documento, razão social, nome, moeda, fuso, plano, configurações | 1:N unidades, usuários, pátios, eventos | `PROVISIONANDO/ATIVA/SUSPENSA/ENCERRADA`; documento único; fuso/moeda obrigatórios | provisionamento, suspensão, mudança de plano e configuração |
| `UnidadeOperacional` | TARGET; unidade/venue administrativo | nome, tipo, endereço, contatos, timezone | N:1 organização; 1:N pátios | ativa/inativa; endereço e organização válidos | alterações de local e status |
| `Usuario` | AS-IS/EVOLUÇÃO; identidade autenticável | nome, login, hash, email, status, MFA | N:1 organização; N:N atribuições; 0:1 funcionário | convidado/ativo/bloqueado/inativo; login único no tenant | login, falhas, bloqueios, redefinições e concessões |
| `Papel` | TARGET; conjunto nomeado de permissões | código, nome, permissões, tipo | N:N atribuições | código imutável; permissões conhecidas | alterações versionadas |
| `AtribuicaoAcesso` | TARGET; papel em escopo e vigência | usuarioId, papelId, tipoEscopo, escopoId, início/fim | N:1 usuário/papel | escopo pertence ao tenant; vigência coerente | concedente, revogador e motivo |
| `SessaoAutenticacao` | TARGET; ciclo da sessão | jti, client, device, emitidaEm, expiraEm, revogadaEm | N:1 usuário | token rotacionável e revogável | IP reduzido/mascarado, dispositivo e revogação |
| `RegistroAuditoria` | TARGET; trilha imutável | ação, recurso, antes/depois redigidos, ator, origem | referência lógica a qualquer agregado | append-only; retenção por classe | é o próprio histórico auditável |
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
| `Patio` | AS-IS/EVOLUÇÃO; instalação física | nome, endereço, capacidade física, observação | N:1 organização/unidade; 1:N setores/alocações | ativo/interditado; capacidade > 0 | capacidade e interdições versionadas |
| `Setor` | TARGET; divisão operacional | código, nome, capacidade, categorias, coordenadas | N:1 pátio; 1:N vagas/faixas | capacidade ≤ pátio; código único no pátio | alterações e bloqueios |
| `Vaga` | TARGET; unidade individual opcional | código, tipo, acessibilidade, posição | N:1 setor | livre/bloqueada/manutenção; código único | mudanças de disponibilidade |
| `FaixaAcesso` | TARGET; via física de entrada/saída | código, direção, modo de falha | N:1 pátio/setor; N:N equipamentos | entrada/saída/bidirecional; ativa | abertura manual e mudança de política |
| `Interdicao` | TARGET; reduz capacidade | alvo, período, quantidade/motivo | pátio/setor/vaga | período e quantidade válidos | autor, aprovação e encerramento |
| `Evento` | TARGET; ciclo comercial/operacional | nome, venue, início/fim, janelas, políticas, capacidade esperada | N:1 organização; N:N pátios por alocação | máquina completa de [[15-maquinas-de-estados]] | publicação, alterações materiais, cancelamento e fechamento |
| `ParticipanteOrganizacional` | TARGET; operador/promotor no evento | organizationId, papel, regras de repasse | N:1 evento | organização ativa; papel permitido | vigência e aprovador |
| `AlocacaoPatioEvento` | TARGET; capacidade temporal comprometida | eventoId, patioId, período, capacidade operacional/vendável/reservada | N:1 evento/pátio | vendável + reservada ≤ operacional ≤ física; sem conflito | alterações de capacidade e override |
| `PoliticaEvento` | TARGET; regras versionadas | reentrada, tolerância, cancelamento, cut-off, categorias | 1:1/N:1 evento | versão imutável após uso | snapshot referenciado por credenciais/pedidos |
| `PortaoLogicoEvento` | TARGET; roteamento de acesso | nome, produtos aceitos, faixas, janela | N:1 evento; N:N faixas | pelo menos uma faixa; regras não conflitantes | configuração publicada por versão |

## 14.4 Comercial, inventário, pedidos e pagamentos

| Entidade | Situação e responsabilidade | Atributos principais | Relacionamentos | Estados/validações | Histórico e auditoria |
|---|---|---|---|---|---|
| `ProdutoEstacionamento` | TARGET; oferta de direito | nome, categoria, alocação, janela, benefícios, restrições | N:1 evento/alocação; 1:N lotes/quotas | rascunho/publicado/suspenso/encerrado | publicação e mudanças versionadas |
| `LotePreco` | TARGET; preço temporal/quantitativo | nome, preço, moeda, início/fim, quantidade, prioridade | N:1 produto | preço ≥ 0; período coerente; sobreposição determinística | snapshot usado no item |
| `QuotaCanal` | TARGET; inventário por canal | canal, quantidade, período | N:1 produto/alocação | soma de quotas ≤ vendável | transferência de quota auditada |
| `ReservaInventario` | TARGET; hold temporário | produto, quantidade, canal, expiraEm, idempotencyKey | N:1 produto; 0:1 pedido | `MANTIDA/CONFIRMADA/EXPIRADA/LIBERADA`; transição atômica | origem, expiração e confirmação |
| `Pedido` | TARGET; contrato comercial | número, comprador, canal, moeda, subtotal, desconto, taxa, total | 1:N itens/pagamentos/credenciais | estados definidos; total derivado; pago imutável | todas as transições e snapshots |
| `ItemPedido` | TARGET; linha comercial | produtoId, descrição, quantidade, preço, desconto, total | N:1 pedido; N:1 reserva | total derivado; snapshot obrigatório | preservado mesmo se produto mudar |
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
| `CredencialAcesso` | TARGET; direito independente do meio | origem, direito, validade, uso, pátios, evento, veículo | pedido/contrato/cortesia; 1:N representações | pendente/ativa/dentro/consumida/bloqueada/expirada/substituída | emissão, entrega, bloqueio, uso e substituição |
| `RepresentacaoQR` | TARGET; representação escaneável | tokenHash, versão, emitidoEm, expiraEm | N:1 credencial | token imprevisível; nunca persistir segredo em claro quando evitável | geração e revogação |
| `PlacaAutorizada` | TARGET; placa como representação | placa normalizada, confiança exigida | N:1 credencial | período e quantidade de alterações | todas as trocas de placa |
| `TagRFID` | TARGET; identificador físico | uidTokenizado, emissor, status | N:1 credencial | único e revogável | emissão/substituição |
| `TentativaAcesso` | TARGET; toda decisão de portão | direção, instante, faixa, credencial/placa, decisão, motivo | 0:1 credencial; N:1 evento/pátio | autorizada/recusada/pendente-supervisor | ator/dispositivo/evidência sempre |
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
