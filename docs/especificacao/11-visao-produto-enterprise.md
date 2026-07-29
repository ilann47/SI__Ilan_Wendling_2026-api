> Links: [[README]] · [[01-introducao]] · [[12-dominio-ddd]] · [[20-roadmap-produto]]

# 11. Visão de Produto Enterprise — Estacionamento Kaneko

**Status:** arquitetura-alvo normativa (`TO-BE`).  
**Base existente preservada:** plataforma Java/Spring Boot, frontend React e os módulos de pátio, mensalistas, conveniência, fiscal, financeiro, RH e logística já implementados.  
**Regra de leitura:** uma capacidade descrita neste documento somente está implementada quando também constar como existente em [[relatorio-conformidade]].

## 11.1 Propósito

O Estacionamento Kaneko é uma plataforma de orquestração operacional, comercial e administrativa para empresas que exploram estacionamentos permanentes ou temporários. Seu propósito é controlar todo o ciclo de vida de uma vaga e da receita associada: planejamento da oferta, comercialização, autorização de acesso, ocupação física, cobrança, atendimento de exceções, conciliação e inteligência gerencial.

O foco inicial do produto é a operação de estacionamento para eventos, na qual milhares de veículos podem chegar e sair em janelas curtas, diversas organizações colaboram na mesma operação e uma falha de conectividade não pode interromper o acesso. O produto deverá evoluir sem substituição de núcleo para estacionamento rotativo, mensalistas, conveniência, fiscal, financeiro, RH, logística, equipamentos, marketplace e inteligência artificial.

## 11.2 Problema resolvido

O produto elimina a fragmentação entre planilhas de capacidade, plataformas de ingresso, meios de pagamento, listas de cortesias, leitores de acesso, controles de pátio, caixas e sistemas administrativos. A plataforma oferece uma fonte transacional única para responder, em tempo real e com auditoria, às seguintes perguntas:

1. Quantas vagas podem ser vendidas, estão reservadas, vendidas, ocupadas ou indisponíveis?
2. Quem está autorizado a entrar, por qual pátio, em qual horário e com qual veículo?
3. Qual pagamento, cortesia, contrato ou regra originou cada autorização?
4. Quais entradas, recusas, saídas e intervenções manuais ocorreram, por operador e dispositivo?
5. Qual foi a receita bruta, líquida, o custo, o repasse e a margem de cada evento, pátio e canal?
6. Quais equipamentos estão disponíveis e quais operações permanecem seguras em contingência?

## 11.3 Por que não deve ser apenas um sistema de estacionamento

Uma sessão de estacionamento é somente a execução física final de uma cadeia maior. Em eventos, a demanda nasce na agenda do organizador, transforma-se em inventário comercial, pedido, pagamento e credencial, para somente então gerar acesso e movimentação. Depois da saída ainda existem reembolso, conciliação, repasse, documento fiscal, auditoria e análise de desempenho.

Restringir o Kaneko à abertura e ao fechamento de movimentações produziria dependência permanente de sistemas externos e impediria controlar a principal fonte de valor: a relação entre demanda, capacidade, acesso e receita. O produto deverá ser simultaneamente:

- sistema de registro operacional do pátio;
- motor de inventário e comercialização;
- plataforma de credenciais e acesso;
- hub de integração com pagamentos, ticketing e equipamentos;
- retaguarda administrativa brasileira;
- fonte analítica para decisões operacionais e comerciais.

## 11.4 Visão de longo prazo

> Ser a plataforma brasileira de referência para operar e monetizar estacionamentos de eventos e instalações permanentes, conectando pessoas, veículos, vagas, pagamentos, equipamentos e gestão empresarial em um único produto extensível.

O horizonte de produto compreende:

- gestão de portfólio com múltiplas organizações, unidades, pátios, setores e vagas;
- eventos simultâneos e compartilhamento controlado de inventário;
- venda direta, white label, balcão e canais parceiros;
- estacionamento rotativo, contratos mensalistas e permissões corporativas;
- operação assistida ou automatizada por QR, placa, RFID, sensores e cancelas;
- conveniência, PDV, estoque, compras, fiscal, financeiro, RH e logística;
- marketplace e distribuição de inventário;
- BI operacional e financeiro em tempo real;
- otimização de preço, capacidade, fluxo e escala por modelos analíticos e de IA.

## 11.5 Público-alvo e perfis de cliente

| Perfil de cliente | Necessidade predominante | Configuração típica |
|---|---|---|
| Operador de estacionamento para eventos | Pré-venda, credencial, check-in rápido e fechamento | SaaS multiempresa e multipátio |
| Arena, estádio e centro de convenções | Integração com ticketing, VIP, staff e grandes picos | SaaS dedicado ou on-premise híbrido |
| Produtor e organizador | Inventário, canais de venda, cortesias e repasses | Portal SaaS com escopo por evento |
| Rede de estacionamentos | Controle central, rotativo, mensalistas e equipamentos | SaaS multiunidade |
| Shopping, hospital, hotel e universidade | Validações, permissões e integração corporativa | SaaS dedicado ou on-premise |
| Município e concessionária | Pagamento, permissões, fiscalização e analytics | Implantação dedicada, integrações públicas |
| Pequeno estacionamento | Operação manual, caixa e gestão simplificada | SaaS compartilhado |

## 11.6 Modelo de implantação

O produto adota **SaaS como modelo principal**, sem impedir instalação dedicada.

| Modalidade | Uso | Isolamento | Operação |
|---|---|---|---|
| SaaS compartilhado | Pequenos e médios operadores | Tenant lógico obrigatório | Atualização centralizada |
| SaaS dedicado | Grandes redes e venues | Infraestrutura e banco dedicados quando contratado | Operação gerenciada |
| On-premise | Requisitos regulatórios ou de conectividade | Instância exclusiva | Administração compartilhada por contrato |
| Edge híbrido | Acesso físico e operação offline | Dados mínimos locais por pátio/evento | Sincronização com a plataforma central |

O domínio não pode depender da modalidade de hospedagem. O mesmo modelo de organização, autorização, idempotência e auditoria aplica-se a todas elas.

## 11.7 Multiempresa, multipátio e multievento

### Multiempresa

`Organizacao` é o limite obrigatório de propriedade de dados. Todo agregado empresarial deve possuir uma organização proprietária direta ou derivável de forma inequívoca. Consultas, comandos, eventos, arquivos e métricas devem conservar esse escopo. O tenant nunca é confiado exclusivamente a um identificador enviado no corpo da requisição; ele é resolvido pelo contexto autenticado e confrontado com o recurso.

Uma organização pode atuar como operadora, proprietária, promotora ou parceira. Colaborações entre organizações são concedidas por vínculos explícitos e temporais, nunca pela remoção do isolamento.

### Multipátio

Uma organização administra vários pátios. Cada pátio possui capacidade física, setores, regras, equipamentos e calendário. A capacidade operacional e a capacidade comercial são conceitos distintos. Um pátio pode atender diversos eventos, desde que não haja conflito temporal ou sobrealocação.

### Multievento

Eventos são independentes e podem ocorrer simultaneamente. Um evento pode utilizar vários pátios e um pátio pode servir vários eventos ao longo do tempo. A entidade associativa `AlocacaoPatioEvento` delimita janelas, capacidade comprometida, produtos aceitos, rotas e regras de acesso.

## 11.8 Diferenciais competitivos pretendidos

1. **Event-first com ERP integrado:** comercialização e acesso conectados a financeiro, fiscal, estoque, RH e logística.
2. **Brasil por padrão:** Pix, fiscalidade e integrações nacionais como capacidades do roadmap, sem impedir operação internacional.
3. **Hardware agnóstico:** fabricantes conectados por adaptadores; o domínio utiliza contratos canônicos.
4. **Operação offline segura:** decisão local limitada, auditável e sincronizável.
5. **Inventário unificado:** venda direta, balcão, parceiros, cortesias e bloqueios disputam a mesma capacidade transacional.
6. **Rastreabilidade ponta a ponta:** evento → produto → reserva → pedido → pagamento → credencial → acesso → movimentação → conciliação.
7. **Implantação progressiva:** operação manual, semiautomatizada ou totalmente automatizada com o mesmo domínio.

## 11.9 Princípios arquiteturais

| Princípio | Decisão normativa |
|---|---|
| Domínio explícito | Regras pertencem ao contexto que possui o conceito e não a controllers, telas ou integrações. |
| Monólito modular primeiro | Preservar implantação simples, com fronteiras internas rígidas e possibilidade de extração futura. |
| Alta coesão | Cada bounded context possui modelo, aplicação, persistência e contratos próprios. |
| Dependências direcionadas | Contextos consumidores dependem de contratos publicados, não de entidades internas de outro módulo. |
| Consistência proporcional | Invariantes de estoque, pagamento e credencial são fortes dentro do agregado; integrações usam consistência eventual controlada. |
| Eventos confiáveis | Eventos externos são publicados por outbox; consumidores usam inbox e idempotência. |
| API-first | Interfaces web, mobile, parceiros e edge usam contratos versionados. |
| Segurança por escopo | Zero confiança entre tenants, eventos, pátios, usuários e dispositivos. |
| Offline explícito | Contingência é modelada, não tratada como cópia improvisada do banco central. |
| Observabilidade funcional | Métricas técnicas são correlacionadas a pedido, evento, pátio, acesso, dispositivo e pagamento. |
| Evolução sem remoção | Capacidades atuais permanecem, sendo encapsuladas ou especializadas quando necessário. |
| Privacidade e minimização | Placas, documentos, imagens e localização têm finalidade, retenção e acesso definidos. |

## 11.10 Estilo arquitetural alvo

O alvo inicial é um **monólito modular orientado a domínio**, mantendo Spring Boot e PostgreSQL, com módulos compilacionalmente separados e comunicação interna por portas de aplicação e eventos. Filas, cache, mecanismos de busca ou serviços independentes somente devem ser introduzidos quando uma necessidade mensurável justificar sua operação.

Extrações futuras mais prováveis são Pagamentos, Credenciais/Acesso, Equipamentos/Edge, Notificações e Analytics, pois possuem carga, disponibilidade ou ciclo de implantação distintos. A extração não altera a linguagem do domínio nem os contratos publicados.

## 11.11 Critérios de sucesso do produto

- nenhuma venda confirmada sem capacidade comercial correspondente;
- nenhuma entrada autorizada sem direito válido ou exceção auditada;
- nenhuma cobrança duplicada diante de repetição de requisição ou webhook;
- continuidade de check-in em indisponibilidade temporária de internet;
- fechamento de evento reconciliável por pedido, pagamento, credencial e acesso;
- isolamento comprovável entre organizações;
- integração de novo fabricante sem alteração das regras centrais;
- rastreabilidade de toda intervenção financeira ou operacional relevante.
