> Links: [[README]] · [[11-visao-produto-enterprise]] · [[12-dominio-ddd]] · [[19-equipamentos-integracoes]] · [[22-unificacao-multitenant]] · [[10-requisitos-nao-funcionais]]

# 20. Roadmap do Produto e Evolução Arquitetural

## 20.1 Princípios do roadmap

1. O roadmap amplia a especificação definitiva; não reduz a visão do produto.
2. Nenhuma fase remove os módulos atuais.
3. Eventos são o primeiro domínio de expansão; rotativo, mensalistas e módulos
   empresariais permanecem bounded contexts próprios e não são descartados.
4. Fundação transversal é entregue antes ou junto das capacidades que dela dependem.
5. Uma fase só termina quando os critérios operacionais e arquiteturais forem comprovados, não apenas quando houver telas.
6. Mudanças são incrementais, com compatibilidade e migração; não há reescrita big bang.

### 20.1.1 Diretriz transversal de unificação

A evolução tenant-aware dos módulos empresariais é parte da Fundação transversal
e pode avançar em paralelo às capacidades de evento que não dependam dela. A
ordem das fases continua expressando dependências de produto, mas não autoriza
ocultar, remover ou manter globalmente Fiscal, Financeiro, Conveniência, Pessoas,
Fornecedores, Logística, RH ou Operação de Pátio. A matriz e o procedimento de
migração vinculantes estão em [[22-unificacao-multitenant]].

## 20.2 Fase 0 — Fundação transversal

**Objetivos:**

- organização/tenant obrigatório;
- relacionamento explícito entre usuário, funcionário e colaborador;
- RBAC por permissão e escopo;
- auditoria de ator/origem/motivo;
- API `/api/v1` e compatibilidade legada;
- idempotência e concorrência otimista;
- outbox/inbox e process managers;
- fuso, moeda e identificadores globais;
- observabilidade e métricas de negócio;
- padrões de teste de contrato e isolamento.

**Dependências:** código atual, inventário de dados e decisão de implantação.  
**Riscos:** vazamento entre tenants, migração de dados sem owner, duplicação de identidades, tentativa de mudar todos os módulos simultaneamente.  
**Complexidade:** muito alta.  
**Impacto:** habilita todas as fases.  
**Critério de saída:** testes provam isolamento horizontal/vertical; comandos críticos são idempotentes; eventos são publicados de forma confiável; APIs atuais continuam operáveis.

## 20.3 Fase 1 — Eventos

**Objetivos funcionais completos:**

- evento, venue, agenda e políticas;
- alocação multipátio e capacidade operacional/vendável/reservada;
- setores, produtos, categorias e lotes de preço;
- quotas por canal, VIP, staff, PCD e cortesia;
- reserva temporária e inventário atômico;
- pedido, comprador, cancelamento e expiração;
- pagamento transacional, Pix/cartão, webhook, reembolso e conciliação;
- credencial, QR, placa opcional, bloqueio e reemissão;
- check-in/check-out online e offline;
- antifraude, duplicidade, override e evidências;
- ocupação e dashboard em tempo real;
- escalas e responsabilidades operacionais;
- encerramento, conciliação, repasse e DRE do evento;
- APIs de parceiros e primeira integração brasileira de ticketing.

**Dependências:** Fase 0, pátio atual, clientes/veículos e financeiro básico.  
**Riscos:** sobrevenda concorrente, pico concentrado, QR duplicado, conectividade, pagamento aprovado sem credencial, credencial usada após cancelamento.  
**Complexidade:** muito alta.  
**Impacto:** estabelece o posicionamento event-first e primeiro ciclo ponta a ponta.  
**Critério de saída:** evento real executado da configuração ao fechamento; operação online/offline; reconciliação individual por pedido → pagamento → credencial → acesso → movimentação; nenhum controle crítico paralelo em planilha.

### Bloqueios formais da fase

As dependências que impedem o critério integral de saída são mantidas no
[[bloqueios-externos|Registro Central de Bloqueios]]. Histórias independentes
continuam executáveis, mas pagamento/reembolso, offline, fechamento financeiro,
fiscal externo, equipamentos e integrações não podem ser declarados concluídos
antes das respectivas condições de retomada.

## 20.4 Fase 2 — Rotativo

**Objetivos:** tarifas por pátio, categoria, calendário, faixa e permanência; tolerância; teto diário; tickets; placa; caixa; validação/desconto; ticket perdido; reserva cotidiana; pagamento na saída; estorno e comprovante.

**Dependências:** pátio, acesso, pagamentos e caixa estabilizados.  
**Riscos:** reutilizar preço de evento como tarifa; cálculo não reproduzível; relógio/fuso; saída sem pagamento conciliado.  
**Complexidade:** alta.  
**Impacto:** amplia o mercado para operação permanente.  
**Critério de saída:** qualquer cobrança é reproduzível com snapshot da regra; sessões, pagamentos e caixa fecham sem ambiguidade.

## 20.5 Fase 3 — Mensalistas

**Objetivos:** planos versionados, múltiplos veículos, limites de simultaneidade, pátios/horários, renovação, recorrência, inadimplência, portal, credenciais persistentes, suspensão e histórico.

**Dependências:** pagamentos, credenciais, financeiro e rotinas de acesso.  
**Riscos:** conceder acesso fora da vigência; cobrança duplicada por competência; conflito entre plano e contrato; migração do mensalista atual.  
**Complexidade:** alta.  
**Impacto:** receita recorrente e retenção.  
**Critério de saída:** cobrança e acesso são reconciliáveis por contrato/competência; regras vigentes não dependem de atualização manual de status.

## 20.6 Fase 4 — Conveniência

**Objetivos:** PDV, carrinho, comanda, pagamentos mistos, caixa, venda offline controlada, razão de estoque, locais, ajustes, compras, custo, inventário e integração com evento.

**Dependências:** pagamentos, caixa, produtos e fornecedores atuais.  
**Riscos:** saldo mutável sem razão, vendas offline conflitantes, cancelamento sem compensação, pressão no caminho crítico do evento.  
**Complexidade:** alta.  
**Impacto:** aumenta receita por visitante e aproveita a base ERP.  
**Critério de saída:** toda alteração de estoque possui movimento/origem; PDV e caixa conciliam; cancelamentos compensam efeitos.

## 20.7 Fase 5 — Fiscal

**Objetivos:** motor de obrigação, NF-e/NFC-e/NFS-e conforme escopo, adapters de provedores, certificado, numeração, transmissão, rejeição, contingência, cancelamento, armazenamento e consulta.

**Dependências:** pedidos, PDV, estoque e financeiro.  
**Riscos:** legislação variável, municípios heterogêneos, indisponibilidade, certificados e falsa equivalência entre registro interno e documento autorizado.  
**Complexidade:** muito alta.  
**Impacto:** conformidade comercial brasileira.  
**Critério de saída:** documento possui ciclo completo e protocolo; rejeição/contingência são operáveis; autorizado não é editável.

## 20.8 Fase 6 — Financeiro

**Objetivos:** baixas como lançamentos, conciliação de gateway/caixa, split, repasses, centros de custo, competência, DRE, tesouraria, fluxo de caixa, integração contábil e fechamento por organização/evento.

**Dependências:** todas as origens de receita/despesa e fiscal.  
**Riscos:** duplicar receita entre pedido, pagamento e conta; diferenças de regime/competência; repasse calculado com regra atual em vendas históricas.  
**Complexidade:** muito alta.  
**Impacto:** transforma a plataforma em ERP operacional integrado.  
**Critério de saída:** todo lançamento tem origem; toda divergência é visível; DRE é reproduzível; regra de repasse é versionada.

## 20.9 Fase 7 — Equipamentos

**Objetivos:** device management, site controller, adapters, cancelas, LPR, QR, RFID, sensores, totens, POS, painéis, impressoras, semáforos, câmeras, telemetria, comandos e contingência edge.

**Dependências:** controle de acesso estável, contratos canônicos, segurança de dispositivos.  
**Riscos:** segurança física, SDK legado, latência, versões de firmware, operação local e dependência de fabricante.  
**Complexidade:** muito alta.  
**Impacto:** automação, velocidade e escala.  
**Critério de saída:** novo fabricante entra por adapter; falhas são observáveis; fail-open/fail-closed é testado; evento continua durante perda de nuvem.

## 20.10 Fase 8 — BI

**Objetivos:** plataforma analítica, catálogo de métricas, ocupação, funil, no-show, curva de chegada, receita/vaga, recusas, filas, equipe, equipamento, conciliação e benchmarking interno.

**Dependências:** eventos confiáveis, qualidade, lineage e histórico.  
**Riscos:** KPI sem definição, consulta pesada no OLTP, atraso invisível e exposição cross-tenant.  
**Complexidade:** alta.  
**Impacto:** decisão operacional e comercial.  
**Critério de saída:** cada indicador tem definição, owner, fonte, versão e latência; relatórios pesados não prejudicam check-in.

## 20.11 Fase 9 — Marketplace

**Objetivos:** catálogo público, busca geográfica, disponibilidade, reserva multicanal, distribuição, white label, comissão, reputação, parceiros, season passes, campanhas e atendimento.

**Dependências:** inventário, pedidos, pagamentos, multiempresa, APIs e conciliação maduros.  
**Riscos:** overbooking entre canais, chargeback, diferença de preço, qualidade do fornecedor e responsabilidade operacional.  
**Complexidade:** muito alta.  
**Impacto:** aquisição de demanda e efeito de rede.  
**Critério de saída:** canais disputam o mesmo inventário sem exceder capacidade; comissão e cancelamento conciliam.

## 20.12 Fase 10 — Inteligência Artificial

**Objetivos:** previsão de demanda e chegada, preço recomendado, detecção de fraude, previsão de lotação, escala sugerida, manutenção preditiva, roteamento e assistente operacional.

**Dependências:** BI maduro, governança, consentimento, dados históricos e métricas de qualidade.  
**Riscos:** viés, explicabilidade, drift, automação indevida e recomendação que viola invariante.  
**Complexidade:** muito alta.  
**Impacto:** diferenciação e eficiência sustentável.  
**Critério de saída:** modelos monitorados, explicáveis, reversíveis e submetidos às invariantes; nenhuma IA autoriza acesso ou movimenta dinheiro fora das policies.

## 20.13 Requisitos não funcionais enterprise

Os valores são baselines a validar por teste e contrato.

### Segurança e LGPD

- TLS em trânsito e criptografia em repouso.
- Segredos em gerenciador externo; nunca em logs ou payloads de auditoria.
- MFA/SSO para privilégios; identidade própria para integração e dispositivo.
- Placa, imagem e localização tratadas como dados pessoais, com finalidade e retenção.
- PAN de cartão nunca armazenado; tokenização para reduzir escopo PCI.
- Trilhas imutáveis de preço, cortesia, acesso, pagamento, reembolso e fechamento.
- Rate limit e proteção contra replay por tenant/identidade.
- Exportação, correção, anonimização e eliminação conforme base legal e retenção.

### Disponibilidade e continuidade

| Capacidade | Baseline |
|---|---|
| Validação/check-in na janela de evento | 99,95% mensal SaaS |
| APIs administrativas | 99,9% mensal |
| Edge | pelo menos 12 horas sem nuvem, conforme pacote/política |
| RPO transacional SaaS | até 5 minutos |
| RTO crítico SaaS | até 30 minutos |
| Backup | criptografado e restauração periodicamente comprovada |

Falha do control plane não pode interromper evento já provisionado no data plane/edge.

### Performance

- decisão online de acesso: p95 ≤ 300 ms, excluído equipamento;
- decisão edge: p95 ≤ 150 ms;
- APIs transacionais: p95 ≤ 500 ms;
- dashboard operacional: p95 ≤ 2 s com atraso da projeção indicado;
- nenhuma listagem ilimitada;
- testes simulam pico concentrado, rajada de webhooks e reconexão de dispositivos;
- BI não compete com o caminho crítico de acesso.

### Observabilidade

- logs estruturados com `traceId`, `organizationId`, `eventId`, `aggregateId` e `deviceId`, redigidos;
- métricas técnicas e de negócio;
- tracing distribuído;
- SLOs e error budgets;
- alertas com runbooks;
- correlação pedido → pagamento → credencial → acesso → equipamento;
- auditoria separada de logs operacionais.

### Dados e qualidade

- instantes em UTC e fuso explícito para regras;
- dinheiro com moeda e precisão;
- eventos/APIs versionados e com testes de contrato;
- migrações compatíveis com implantação gradual;
- nenhum relógio local não sincronizado decide validade sem política;
- acessibilidade WCAG para interfaces públicas e operacionais;
- retenção e lineage por classe de dado.

## 20.14 Plano de evolução arquitetural

### Banco e dados

1. Introduzir organização e ownership sem tornar filtros opcionais.
2. Migrar registros existentes para uma organização inicial controlada.
3. Tornar unicidades relevantes compostas por tenant.
4. Adicionar versão otimista aos agregados sensíveis.
5. Introduzir auditoria, idempotência, outbox e inbox.
6. Tornar pátio obrigatório nos novos fluxos, saneando legado.
7. Separar capacidade física, operacional, vendável e ocupação.
8. Introduzir razão de estoque e baixas financeiras compensatórias.

### Backend

1. Declarar módulos e contratos internos.
2. Impedir repository/entity cross-context em código novo.
3. Encapsular comportamento atual por facades de aplicação.
4. Isolar cálculo tarifário, inventário, autorização e conciliação como serviços de domínio.
5. Introduzir process managers para fluxos intercontexto.
6. Criar ACL para qualquer integração.
7. Extrair serviços somente após métricas comprovarem necessidade.

### Frontend

1. Resolver organização e escopo após autenticação.
2. Navegação e ações derivadas de permissões efetivas, sem confiar apenas na ocultação visual.
3. Criar consoles distintos: configuração, venda, acesso, supervisor, caixa, financeiro e auditoria.
4. Tornar a operação de acesso mobile-first e tolerante a conexão.
5. Exibir estado, motivo de recusa, atraso de projeção e pendências de sincronização.

### APIs

1. Adicionar `/api/v1` e erros estáveis.
2. Preservar `/api/**` por facade durante transição.
3. Exigir idempotência em comandos críticos.
4. Adotar ETag/versão em agregados concorrentes.
5. Publicar OpenAPI e schemas de eventos versionados.
6. Medir uso antes de deprecar contratos.

### Operação

1. Definir runbooks de venda, check-in, offline, lotação, falha de equipamento, reembolso e fechamento.
2. Treinar papéis conforme matriz de alçadas.
3. Ensaiar picos e perda de conectividade antes de cada evento relevante.
4. Executar reconciliação e retrospectiva após eventos.

## 20.15 Decisões rejeitadas

| Alternativa | Motivo da rejeição atual |
|---|---|
| Microserviços desde o início | aumenta operação e consistência distribuída sem evidência de necessidade |
| Um único agregado “Evento” contendo tudo | inviabiliza concorrência, crescimento e autonomia dos ciclos |
| Usar `Movimentacao` como pedido/pagamento/credencial | mistura permanência física com direitos e receita |
| Tenant apenas por filtro ORM | risco de vazamento e jobs/integrações fora do filtro |
| Integrar fabricante diretamente no service | acoplamento, testes difíceis e contaminação do domínio |
| Confiar em consulta de disponibilidade antes da compra | não evita corrida pela última vaga |
| Operação offline como cópia do banco central | exposição, conflito e ausência de política de decisão limitada |

## 20.16 Resultado arquitetural esperado

Ao concluir o roadmap, o Kaneko será uma plataforma de mobilidade e ERP operacional que preserva seus módulos administrativos atuais, domina eventos de ponta a ponta e suporta novas modalidades sem compartilhar indevidamente regras, dados ou integrações entre contextos.
