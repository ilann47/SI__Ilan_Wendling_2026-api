> Links: [[README]] · [[13-event-storming]] · [[14-modelagem-dominio]] · [[16-casos-de-uso-plataforma]]

# 15. Máquinas de Estados

## 15.1 Regras gerais

1. Estado não é alterado por CRUD genérico. Cada transição é um comando explícito.
2. Estado terminal não retorna a um estado anterior; correções geram ajuste compensatório.
3. Transição registra ator, origem, motivo quando exigido, versão e eventos resultantes.
4. Processamento repetido com a mesma chave deve devolver o resultado original.
5. Transição concorrente usa versão do agregado; versão obsoleta é rejeitada.

## 15.2 Evento

**Situação implementada:** o evento entra em `RASCUNHO`; `PATCH /events/{id}`
define `ENTRADA_UNICA` ou `REENTRADA_PERMITIDA` com `If-Match`. O comando
explícito de publicação exige essa política e executa `RASCUNHO → CONFIGURADO → PUBLICADO`
somente com alocação vendável e produto publicado. Comandos também versionados
executam `PUBLICADO → VENDAS_ABERTAS → VENDAS_ENCERRADAS → EM_OPERACAO`, com
auditoria e outbox em cada etapa. Agendamento, suspensão e transições posteriores
continuam TARGET.

```mermaid
stateDiagram-v2
  [*] --> RASCUNHO
  RASCUNHO --> CONFIGURADO: configurar
  CONFIGURADO --> PUBLICADO: publicar
  PUBLICADO --> VENDAS_ABERTAS: abrir vendas
  VENDAS_ABERTAS --> VENDAS_ENCERRADAS: encerrar vendas
  VENDAS_ENCERRADAS --> EM_OPERACAO: iniciar operação
  EM_OPERACAO --> OPERACAO_ENCERRADA: encerrar operação
  OPERACAO_ENCERRADA --> EM_CONCILIACAO: iniciar conciliação
  EM_CONCILIACAO --> FINALIZADO: aprovar fechamento
  PUBLICADO --> SUSPENSO: suspender
  VENDAS_ABERTAS --> SUSPENSO: suspender
  SUSPENSO --> PUBLICADO: retomar sem vendas
  SUSPENSO --> VENDAS_ABERTAS: retomar vendas
  RASCUNHO --> CANCELADO: cancelar
  CONFIGURADO --> CANCELADO: cancelar
  PUBLICADO --> CANCELADO: cancelar
  VENDAS_ABERTAS --> CANCELADO: cancelar e compensar
  FINALIZADO --> [*]
  CANCELADO --> [*]
```

| Transição | Quem | Regras | Efeitos colaterais |
|---|---|---|---|
| Rascunho → Configurado | Gestor/Organizador | local, datas, políticas e responsáveis completos | `EventoConfigurado` |
| Configurado → Publicado | Gestor | pátios, capacidade e produtos válidos | snapshot de publicação; `EventoPublicado` |
| Publicado → Vendas abertas | Gestor/agenda | janela comercial | disponibiliza ofertas |
| Vendas abertas → Encerradas | Gestor/agenda | cut-off ou motivo | impede novas reservas |
| Vendas encerradas → Em operação | Supervisor | checklist operacional e contingência | ativa dashboards e dispositivos |
| Em operação → Operação encerrada | Supervisor | entradas encerradas, exceções listadas | fecha admissões normais |
| Operação encerrada → Em conciliação | Financeiro/Gestor | consolida fontes | cria fechamento pendente |
| Em conciliação → Finalizado | Financeiro + aprovador | divergências resolvidas/aceitas | imutabilidade, DRE e retenção |
| Estados pré-operação → Cancelado | Gestor | motivo e política | bloqueios, notificações, cancelamentos e reembolsos |

## 15.3 Reserva de inventário

**Situação implementada:** UC709 cria em `MANTIDA`, libera explicitamente para
`LIBERADA` e expira para `EXPIRADA` por job ou verificação transacional. Somente
`MANTIDA` consome o contador reservado. `CONFIRMADA` permanece para a história
de pedido/pagamento.

```mermaid
stateDiagram-v2
  [*] --> MANTIDA
  MANTIDA --> CONFIRMADA: confirmar antes da expiração
  MANTIDA --> EXPIRADA: atingir prazo
  MANTIDA --> LIBERADA: cancelar/desistir
  CONFIRMADA --> [*]
  EXPIRADA --> [*]
  LIBERADA --> [*]
```

- Confirmação e expiração competem atomicamente.
- Somente `MANTIDA` consome saldo reservado.
- `CONFIRMADA` converte reservado em vendido.
- `EXPIRADA` e `LIBERADA` devolvem saldo uma única vez.
- Autor: sistema, cliente, caixa ou integração conforme origem.

## 15.4 Pedido

**Situação implementada:** a criação percorre `CRIADO → AGUARDANDO_PAGAMENTO`
na mesma transação, sem expor o estado intermediário ao cliente. Um job interno
materializa `EXPIRADO` quando o prazo herdado do hold termina. UC713 implementa
o cancelamento próprio: `CRIADO/AGUARDANDO_PAGAMENTO → CANCELADO` ou
`CONFIRMADO/ATENDIDO → EM_REEMBOLSO`. As transições financeiras posteriores
continuam TARGET.

```mermaid
stateDiagram-v2
  [*] --> CRIADO
  CRIADO --> AGUARDANDO_PAGAMENTO: solicitar pagamento
  CRIADO --> CONFIRMADO: cortesia autorizada
  AGUARDANDO_PAGAMENTO --> CONFIRMADO: pagamento aprovado e inventário confirmado
  AGUARDANDO_PAGAMENTO --> CONFIRMADO: confirmação manual conciliável
  CRIADO --> EXPIRADO: prazo
  AGUARDANDO_PAGAMENTO --> EXPIRADO: prazo
  CONFIRMADO --> ATENDIDO: credencial emitida
  ATENDIDO --> CONCLUIDO: prestação encerrada
  CRIADO --> CANCELADO: cancelar
  AGUARDANDO_PAGAMENTO --> CANCELADO: cancelar
  CONFIRMADO --> EM_REEMBOLSO: cancelar elegível
  ATENDIDO --> EM_REEMBOLSO: cancelar antes do uso permitido
  EM_REEMBOLSO --> REEMBOLSADO: restituição concluída
  EM_REEMBOLSO --> CANCELADO_COM_PENDENCIA: falha/contestação
```

| Estado | Mutabilidade | Quem pode avançar | Efeito principal |
|---|---|---|---|
| Criado | itens ainda controlados pela reserva | Cliente/Caixa/Integração | total e snapshots definidos |
| Aguardando pagamento | preço e itens congelados | Payment process manager | inicia tentativas |
| Confirmado | imutável comercialmente | Policy de compra | solicita credencial |
| Atendido | credencial entregue | Credenciais | aguarda uso/conclusão |
| Em reembolso | sem novo uso | Cliente/Financeiro conforme política | bloqueia credencial e restitui |
| Concluído/Reembolsado/Expirado/Cancelado | terminal | sistema | preserva histórico |

## 15.5 Pagamento

```mermaid
stateDiagram-v2
  [*] --> CRIADO
  CRIADO --> PROCESSANDO: enviar ao provedor
  PROCESSANDO --> AUTORIZADO: autorizar
  PROCESSANDO --> RECUSADO: recusar
  PROCESSANDO --> PENDENTE_EXTERNO: processamento assíncrono
  PENDENTE_EXTERNO --> AUTORIZADO: webhook aprovado
  PENDENTE_EXTERNO --> RECUSADO: webhook recusado/expiração
  AUTORIZADO --> CAPTURADO: capturar
  AUTORIZADO --> CANCELADO: cancelar autorização
  CAPTURADO --> LIQUIDADO: conciliar
  CAPTURADO --> REEMBOLSO_PARCIAL: restituir parte
  CAPTURADO --> REEMBOLSADO: restituir total
  LIQUIDADO --> REEMBOLSO_PARCIAL: restituir parte
  LIQUIDADO --> REEMBOLSADO: restituir total
  CAPTURADO --> CHARGEBACK: contestação
  LIQUIDADO --> CHARGEBACK: contestação
```

- Gateway/policy altera estados técnicos; operador não marca aprovação arbitrariamente.
- Confirmação manual exige permissão extraordinária, evidência e posterior conciliação.
- **AS-IS:** a confirmação manual versionada confirma o hold e o pedido na mesma
  transação e registra `PENDENTE_CONCILIACAO`; não altera a máquina de pagamento
  nem fabrica uma aprovação de provedor.
- Captura total ou parcial nunca supera autorização.
- Reembolso acumulado nunca supera captura.
- Webhook duplicado não repete evento de negócio.

## 15.6 Credencial

```mermaid
stateDiagram-v2
  [*] --> PENDENTE
  PENDENTE --> ATIVA: emitir
  ATIVA --> DENTRO: check-in
  ATIVA --> BLOQUEADA: bloquear
  ATIVA --> EXPIRADA: expirar
  ATIVA --> SUBSTITUIDA: reemitir
  DENTRO --> CONSUMIDA: checkout sem reentrada
  DENTRO --> ATIVA: checkout com reentrada
  DENTRO --> BLOQUEADA: incidente
  BLOQUEADA --> ATIVA: desbloqueio autorizado
  SUBSTITUIDA --> [*]
  CONSUMIDA --> [*]
  EXPIRADA --> [*]
```

Regras:

- emissão: sistema após direito elegível; **AS-IS**, `PENDENTE → ATIVA` ocorre no
  mesmo caso de uso e uma credencial é criada por unidade do pedido;
- representação QR: **AS-IS**, somente credencial `ATIVA` com meio QR e validade
  futura pode incrementar `qrVersao`; a criação exige a versão esperada e o
  PostgreSQL admite somente uma representação ainda não revogada;
- bloqueio/desbloqueio: supervisor, atendimento autorizado ou policy antifraude;
- substituição: atendimento, revogando a representação anterior no mesmo commit;
- entrada/saída: controle de acesso;
- `DENTRO` descreve o direito, enquanto `Presenca` guarda o histórico físico detalhado.
- O código público AS-IS é somente identificador do agregado. Não substitui QR,
  placa validada ou RFID e nunca autoriza entrada isoladamente.

## 15.7 Tentativa de acesso

```mermaid
stateDiagram-v2
  [*] --> RECEBIDA
  RECEBIDA --> AVALIANDO: carregar políticas
  AVALIANDO --> AUTORIZADA: regras satisfeitas
  AVALIANDO --> RECUSADA: regra impeditiva
  AVALIANDO --> AGUARDANDO_SUPERVISOR: exceção permitida
  AGUARDANDO_SUPERVISOR --> AUTORIZADA_POR_OVERRIDE: aprovar
  AGUARDANDO_SUPERVISOR --> RECUSADA: negar/expirar
  AUTORIZADA --> [*]
  AUTORIZADA_POR_OVERRIDE --> [*]
  RECUSADA --> [*]
```

Toda decisão terminal contém código estável e motivo. Override exige usuário distinto do operador quando a política determinar.

**Situação implementada:** check-in e check-out QR online persistem
`RECEBIDA → AVALIANDO`
e uma decisão terminal `AUTORIZADA` ou `RECUSADA`. As recusas usam códigos
estáveis para representação inválida, credencial inativa, janela, estado do
evento, pátio, duplicidade e lotação. A autorização consome a credencial,
materializa presença e sessão, e incrementa a ocupação no mesmo commit. A saída
encerra presença e sessão, decrementa a ocupação e executa `DENTRO → CONSUMIDA`
ou `DENTRO → ATIVA` conforme a política publicada. Override,
placa, RFID, leitura sem consumo e sincronização offline continuam TARGET.

## 15.8 Movimentação/Sessão de estacionamento

```mermaid
stateDiagram-v2
  [*] --> ABERTA
  ABERTA --> ENCERRADA: registrar saída
  ABERTA --> CANCELADA: entrada indevida
  ENCERRADA --> [*]
  CANCELADA --> [*]
```

- **AS-IS:** `ABERTO → FECHADO` e `ABERTO → CANCELADO` já são protegidos.
- **TARGET:** pátio e origem do direito são obrigatórios; encerrada não reabre; ajuste posterior é entidade compensatória.
- Evento: valor normalmente é preço pré-pago, não cálculo de permanência.
- Rotativo: valor pode ser calculado no encerramento por snapshot tarifário.

**Situação implementada:** `SessaoEstacionamento` enterprise é aberta como efeito
da autorização QR, com organização, evento, pátio, credencial, pedido, item,
presença, tentativa e origem `PEDIDO_EVENTO`. A tabela e a API legadas de
`Movimentacao` permanecem inalteradas. UC717/UC719 encerram a sessão enterprise
no mesmo commit do check-out, sem cálculo tarifário para evento pré-pago.

## 15.9 Contrato mensalista

```mermaid
stateDiagram-v2
  [*] --> RASCUNHO
  RASCUNHO --> ATIVO: ativar contrato
  ATIVO --> SUSPENSO: inadimplência/bloqueio
  SUSPENSO --> ATIVO: regularizar
  ATIVO --> EXPIRADO: fim sem renovação
  ATIVO --> CANCELADO: cancelar
  SUSPENSO --> CANCELADO: cancelar
  EXPIRADO --> RENOVADO: nova vigência
  RENOVADO --> ATIVO: ativar renovação
```

Somente `ATIVO` concede direito. A vigência efetiva deve ser conferida além do estado. Suspensão preserva contrato e acessos passados.

## 15.10 Documento fiscal

```mermaid
stateDiagram-v2
  [*] --> RASCUNHO
  RASCUNHO --> VALIDADO: validar dados
  VALIDADO --> SOLICITADO: transmitir
  SOLICITADO --> PROCESSANDO: provedor aceitou
  PROCESSANDO --> AUTORIZADO: protocolo autorizado
  PROCESSANDO --> REJEITADO: rejeição
  REJEITADO --> VALIDADO: corrigir
  AUTORIZADO --> CANCELAMENTO_SOLICITADO: solicitar cancelamento
  CANCELAMENTO_SOLICITADO --> CANCELADO: autorizado
  CANCELAMENTO_SOLICITADO --> AUTORIZADO: cancelamento rejeitado
```

Fiscal tributário transmite/corrige; cancelamento exige permissão e justificativa. Documento autorizado não é editado.

## 15.11 Conta a receber e conta a pagar

```mermaid
stateDiagram-v2
  [*] --> PENDENTE
  PENDENTE --> PARCIAL: baixa menor que saldo
  PENDENTE --> LIQUIDADA: baixa total
  PARCIAL --> PARCIAL: nova baixa parcial
  PARCIAL --> LIQUIDADA: completar saldo
  PENDENTE --> CANCELADA: cancelar elegível
  PARCIAL --> CANCELADA: cancelar saldo com compensação
  LIQUIDADA --> ESTORNADA: estorno compensatório
```

- Caixa/Financeiro registra baixa conforme escopo.
- Financeiro aprova cancelamento/estorno.
- Baixa acumulada não ultrapassa total.
- A implementação atual usa `PAGA` para pagar e `RECEBIDA` para receber; `LIQUIDADA` é o termo conceitual comum.

## 15.12 Caixa/turno

```mermaid
stateDiagram-v2
  [*] --> ABERTO
  ABERTO --> EM_FECHAMENTO: solicitar fechamento
  EM_FECHAMENTO --> COM_DIVERGENCIA: diferença acima da tolerância
  EM_FECHAMENTO --> FECHADO: valores conferidos
  COM_DIVERGENCIA --> APROVADO_COM_DIVERGENCIA: supervisor aprovar
  APROVADO_COM_DIVERGENCIA --> FECHADO: concluir
```

O operador fecha o próprio turno, mas não aprova divergência acima do limite. Venda retroativa após `FECHADO` é proibida.

## 15.13 Comando de equipamento

```mermaid
stateDiagram-v2
  [*] --> SOLICITADO
  SOLICITADO --> ACEITO: validar capacidade
  SOLICITADO --> REJEITADO: incompatível/sem autorização
  ACEITO --> DESPACHADO: enviar ao edge
  DESPACHADO --> CONFIRMADO_GATEWAY: gateway recebeu
  CONFIRMADO_GATEWAY --> EXECUTADO: estado físico confirmado
  DESPACHADO --> FALHOU: erro
  CONFIRMADO_GATEWAY --> FALHOU: execução falhou
  ACEITO --> EXPIRADO: prazo
  DESPACHADO --> EXPIRADO: prazo
```

“Despachado” não significa “executado”. A confirmação física é separada e correlacionada.

## 15.14 Fechamento do evento

```mermaid
stateDiagram-v2
  [*] --> PENDENTE
  PENDENTE --> APURANDO: iniciar
  APURANDO --> COM_DIVERGENCIAS: inconsistências
  APURANDO --> CONCILIADO: sem divergências
  COM_DIVERGENCIAS --> APURANDO: ajustar/reprocessar
  COM_DIVERGENCIAS --> CONCILIADO_COM_RESSALVA: aprovar ressalva
  CONCILIADO --> APROVADO: segunda aprovação quando exigida
  CONCILIADO_COM_RESSALVA --> APROVADO: aprovar
  APROVADO --> FECHADO: finalizar
```

Fontes obrigatórias: inventário, pedidos, pagamentos, reembolsos, credenciais, acessos, movimentações, caixas, documentos fiscais, contas e repasses.

## 15.15 Membership de organização

```mermaid
stateDiagram-v2
  [*] --> ATIVO: vínculo administrativo
  ATIVO --> SUSPENSO: suspender
  SUSPENSO --> ATIVO: reativar
  ATIVO --> ENCERRADO: encerrar
  SUSPENSO --> ENCERRADO: encerrar
  ENCERRADO --> [*]
```

`ENCERRADO` é terminal: um vínculo posterior cria outro registro e preserva o
histórico. Suspensão, reativação e encerramento incrementam `@Version`, de modo
que qualquer JWT contextual emitido com a versão anterior deixa de autenticar.
O estado `PENDENTE` somente será introduzido quando convite, aceite e expiração
forem implementados.
