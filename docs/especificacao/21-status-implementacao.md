> Links: [[README]] · [[16-casos-de-uso-plataforma]] · [[18-permissoes-rbac]] · [[19-equipamentos-integracoes]] · [[20-roadmap-produto]] · [[relatorio-conformidade]]

# 21. Estado de Implementação e Dependências

## 21.1 Finalidade

Este documento registra o estado verificável dos casos de uso enterprise e
separa bloqueios externos das dependências internas. Uma história somente pode
ser marcada como `CONCLUIDA` quando código, migration, API, segurança, testes,
OpenAPI e documentação aplicáveis estiverem sincronizados.

Estados utilizados:

- `CONCLUIDA`: todos os critérios técnicos e funcionais do recorte aprovado
  foram satisfeitos;
- `EXECUTAVEL`: não existe dependência impeditiva para iniciar o próximo recorte
  vertical;
- `PARCIALMENTE_EXECUTAVEL`: há uma fundação interna útil e testável, mas parte
  do caso de uso depende de capacidade ainda indisponível;
- `BLOQUEADA_EXTERNAMENTE`: depende de provider, segredo, credencial,
  infraestrutura ou decisão comercial externa;
- `BLOQUEADA_POR_DEPENDENCIA_INTERNA`: depende de outro caso de uso ainda não
  implementado.

## 21.2 Matriz de continuidade

| História | Estado | Dependência | Parte concluída | Parte pendente | Condição para retomada |
|---|---|---|---|---|---|
| UC701–UC703 | `CONCLUIDA` | — | Fundação multiempresa, eventos e pátio tenant-aware | — | Reabrir somente por defeito ou mudança aprovada |
| UC704 — Cadastrar setor | `CONCLUIDA` | — | Setor tenant-aware, taxonomia, capacidade serializada, auditoria, outbox e constraints | Coordenadas, alteração e bloqueio pertencem a recortes próprios | Reabrir somente por defeito ou mudança aprovada |
| UC705 — Cadastrar vagas | `CONCLUIDA` | — | Vaga tenant-aware, batch idempotente, capacidade serializada, conflitos parciais, auditoria, outbox e constraints | Coordenadas e mudanças de disponibilidade pertencem a recortes próprios | Reabrir somente por defeito ou mudança aprovada |
| UC706–UC713 | `CONCLUIDA` | — | Alocação multipátio até cancelamento compensatório de pedido, conforme relatório de conformidade | — | Reabrir somente por defeito ou mudança aprovada |
| UC714 — Reembolsar pagamento | `BLOQUEADA_EXTERNAMENTE` | Pagamento capturado real, gateway, credenciais sandbox, assinatura de webhook e política comercial | UC713 preserva `EM_REEMBOLSO`, bloqueia o direito e registra os efeitos compensatórios | Solicitação vinculada a captura, aprovação, execução, retorno, conciliação e restituição real | Provider e contrato escolhidos; credenciais/segredos sandbox disponíveis; alçadas, prazo, multa, crédito e reembolso parcial definidos |
| UC715 — Realizar check-in | `CONCLUIDA` | — | QR online tenant-aware, tentativa terminal, consumo, presença, sessão de evento, ocupação concorrente, auditoria, outbox e idempotência | Placa/RFID, override, hardware e offline pertencem a contratos próprios | Reabrir somente por defeito ou mudança aprovada |
| UC716 — Check-in offline | `BLOQUEADA_POR_DEPENDENCIA_INTERNA` | UC715 e modelo canônico de decisão | QR seguro e contexto tenant existem | Pacote assinado, edge, sequência, armazenamento local e sincronização | Concluir UC715 e definir infraestrutura edge verificável |
| UC717 — Check-out | `CONCLUIDA` | — | Política explícita, saída QR idempotente, presença encerrada, credencial consumida/reativada e ocupação decrementada | Saída manual, placa/RFID, hardware e offline pertencem a contratos próprios | Reabrir somente por defeito ou mudança aprovada |
| UC718 — Abrir movimentação | `CONCLUIDA` | — | Sessão enterprise com origem `PEDIDO_EVENTO`, snapshots e vínculos tenant-aware aberta no check-in | Origens rotativo/mensalista permanecem fora da fase de eventos | Reabrir somente por defeito ou nova origem aprovada |
| UC719 — Encerrar movimentação | `CONCLUIDA` | — | Sessão `PEDIDO_EVENTO` encerrada atomicamente no check-out, sem alterar o legado | Tarifação rotativa permanece fora da fase exclusiva de eventos | Reabrir somente por defeito ou nova origem aprovada |
| UC720 — Fechar evento | `PARCIALMENTE_CONCLUIDA` | Fontes financeiras canônicas para a parte restante | Encerramento operacional versionado, bloqueio de ocupação/presença/sessão aberta e ressalva financeira auditável | Conciliação, aprovação, relatório de fechamento e finalização | Pagamento, reembolso, caixa, fiscal e repasse autênticos disponíveis |
| UC721 — DRE por evento | `BLOQUEADA_POR_DEPENDENCIA_INTERNA` | Fontes financeiras e fechamento | Relatórios legados existem | Linhagem econômica por evento | Disponibilizar fontes financeiras canônicas ou modo preliminar formal |
| UC722 — Documento fiscal | `BLOQUEADA_EXTERNAMENTE` | Provedor fiscal, certificado e contratos regulatórios | Registros fiscais legados permanecem preservados | Transmissão, protocolo, contingência e armazenamento fiscal seguro | Provider/ambiente/certificado e escopo fiscal definidos |
| UC723–UC724 — Compra e venda administrativa | `PARCIALMENTE_EXECUTAVEL` | Evolução tenant-aware de estoque e financeiro | Fluxos legados de notas e estoque existem | Agregados enterprise, razão de estoque e isolamento organizacional | Concluir a fase de eventos e planejar migração aditiva dos módulos legados |
| UC725 — Venda PDV | `BLOQUEADA_POR_DEPENDENCIA_INTERNA` | Caixa, pagamento transacional e razão de estoque | Cadastros legados de produto existem | Venda, recebimento, caixa, estoque e fiscal conciliáveis | Concluir pagamentos, estoque enterprise e UC728 |
| UC726–UC727 — Mensalista e rotativo | `BLOQUEADA_POR_DEPENDENCIA_INTERNA` | Fase de eventos e políticas próprias | APIs legadas permanecem preservadas | Evolução enterprise completa | Retomar após o critério de saída da fase exclusiva de eventos |
| UC728 — Caixa | `BLOQUEADA_POR_DEPENDENCIA_INTERNA` | Pagamentos e PDV | Financeiro legado existe | Sessão de caixa, diferenças e conciliação | Concluir pagamento transacional e preparar PDV |
| UC729 — Escalas | `PARCIALMENTE_EXECUTAVEL` | Contexto organizacional e eventos | Usuário/funcionário/evento existem | Escala tenant-aware, vigência e autorização contextual | Pode ser iniciada após o núcleo operacional da fase de eventos |
| UC730 — Integração externa | `BLOQUEADA_EXTERNAMENTE` | Provider/contrato por categoria | Outbox transacional existe | Inbox, adapters, credenciais, retries e quarentena por integração | Escolher uma integração real e fornecer ambiente/credenciais de homologação |

## 21.3 Bloqueio formal da UC714

O código atual não possui pagamento transacional capturado. O pacote
`pagamento` AS-IS mantém somente formas e condições cadastrais. A confirmação
manual de pedido permanece evidência `PENDENTE_CONCILIACAO` e, por decisão
arquitetural, não representa `PagamentoAprovado`, captura ou liquidação de
gateway.

Por isso, enquanto as dependências externas não forem fornecidas:

1. nenhum pagamento capturado fictício será criado;
2. a confirmação manual não será reinterpretada como captura;
3. nenhum webhook sem assinatura será aceito;
4. nenhum reembolso será marcado como executado ou conciliado;
5. o pedido permanecerá em `EM_REEMBOLSO`, com credenciais bloqueadas, até que
   exista um processo financeiro autêntico.

## 21.4 Acesso online concluído e próxima história executável

A UC715 é independente da conclusão financeira da UC714. O check-in opera sobre
uma credencial já emitida e ativa, validando tenant, evento, pátio, janela,
representação e duplicidade. Pedidos em `EM_REEMBOLSO` já têm suas credenciais
bloqueadas pela UC713 e, portanto, serão recusados sem consultar gateway.

Esse recorte foi entregue nas migrations V28/V29 e no módulo `acesso`. A
alocação Evento–Pátio serializa a ocupação; duas credenciais disputando a última
vaga produzem uma autorização e uma recusa determinística. Barreira física e
modo offline permanecem fora do recorte até existirem adapters, identidade de
dispositivo e infraestrutura edge próprios.

As migrations V30/V31 tornam a política de reentrada explícita e implementam a
saída QR online. UC717 encerra presença, atualiza a credencial e decrementa a
ocupação; UC719 encerra a sessão `PEDIDO_EVENTO` no mesmo commit. A política não
possui default comercial: eventos legados permanecem sem política até
configuração explícita e não podem ser publicados nessa condição.

O encerramento operacional da UC720 valida sob locks presença, sessão e ocupação,
transiciona o evento para `OPERACAO_ENCERRADA` e impede novas admissões. A parte
financeira permanece explicitamente pendente, sem simular captura, reembolso,
conciliação ou DRE. Com UC704/UC705 concluídas, os próximos candidatos internos
da fase são quota por canal, projeção de ocupação e UC729; cada um será validado
contra seu contrato antes de implementação, sem antecipar integração externa.

O endurecimento transversal de segurança restringe `/actuator/metrics` e os
demais endpoints administrativos a JWT válido. Somente `/actuator/health/**` e
`/actuator/info` permanecem públicos para probes sem credencial.
