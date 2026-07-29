> Links: [[README]] · [[12-dominio-ddd]] · [[17-apis-enterprise]] · [[10-requisitos-nao-funcionais]]

# 18. Permissões e RBAC Enterprise

## 18.1 Modelo de autorização

A autorização efetiva combina:

```text
papel + permissão + escopo + vigência + atributos + estado do recurso
```

RBAC fornece papéis; ABAC complementa com turno, pátio, evento, propriedade, alçada e estado. Uma rota autenticada não é automaticamente autorizada.

```text
Plataforma
└── Organização
    ├── Unidade/Venue
    │   └── Pátio
    │       ├── Setor
    │       └── Faixa/Dispositivo
    └── Evento
```

O mesmo usuário pode ser Gestor em uma organização, Promotor em um evento específico e não possuir acesso aos demais.

## 18.2 Perfis

| Perfil | Responsabilidade | Operações permitidas | Proibições principais |
|---|---|---|---|
| Administrador da plataforma | Control plane Kaneko | provisionar tenants, planos, regiões, integrações globais, suporte JIT | operar dados de cliente sem sessão autorizada |
| Administrador da organização | Governança do tenant | usuários, papéis, unidades, pátios, políticas, integrações, configurações | outra organização e billing global |
| Gestor | Gestão operacional/comercial | eventos, capacidade, produtos, preços, equipes, relatórios, aprovações | segredos globais e dados de outro tenant |
| Organizador | Gestão de eventos atribuídos | criar/configurar eventos, agenda, produtos, lotes, comunicação e dashboard | pátios globais, RH completo e financeiro geral |
| Promotor | Participação comercial | consultar seus eventos/vendas, administrar canais e cortesias dentro da quota | custos internos, outros eventos e infraestrutura |
| Supervisor | Comando da operação | abrir/encerrar operação, equipe, exceções, bloqueios, reentrada, ajustes e fechamento operacional | alterar política global ou aprovar financeiro fora da alçada |
| Operador | Execução cotidiana | check-in, check-out, busca, entrada manual permitida e ocupação local | preço, reembolso, cortesia e DRE |
| Caixa | Recebimento e caixa | abrir/fechar caixa, receber, sangria autorizada, comprovantes e PDV | alterar preço-base ou aprovar próprio estorno |
| Fiscal de acesso | Inspeção de credenciais | validar, recusar, registrar evidência, consultar duplicidade, solicitar supervisor | emitir credencial, receber ou conceder cortesia |
| Fiscal tributário | Documentos fiscais | emitir, consultar, corrigir e cancelar conforme competência | alterar pedido e liquidar contas sem permissão financeira |
| Financeiro | Tesouraria e resultado | contas, baixas, conciliação, reembolso, repasse, fechamento e DRE | operar portão ou modificar histórico de acesso |
| RH | Pessoas e escalas | funcionário, cargo, disponibilidade, escala e custo autorizado | credenciais de clientes, preço e caixa |
| Auditor | Controle independente | leitura histórica, evidências e exportação assinada | qualquer mutação |
| Cliente | Autogestão | próprios pedidos, pagamentos, veículos, credenciais e cancelamentos elegíveis | dados de terceiros e operação interna |
| Integração | Cliente técnico | somente scopes concedidos, por exemplo `events:read`, `orders:write` | login humano e escopos implícitos |
| Dispositivo | Identidade física | telemetria e capacidades provisionadas na instalação | API administrativa e outra localização |

## 18.3 Matriz de operações

Legenda: `E` executa; `A` aprova; `L` leitura; `P` somente próprios/atribuídos; `—` negado.

| Operação | Adm Org | Gestor | Organizador | Promotor | Supervisor | Operador | Caixa | Fiscal Acesso | Fiscal Trib. | Financeiro | RH | Auditor | Cliente |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Gerir usuários/papéis | E | P | — | — | — | — | — | — | — | — | — | L | — |
| Gerir pátios/setores/vagas | E | E | L | L | L | L | — | L | — | L | — | L | — |
| Criar/configurar evento | E | E | P | P | L | — | — | — | — | L | L | L | — |
| Publicar/cancelar evento | A | E | solicitar | solicitar | — | — | — | — | — | validar impacto | — | L | — |
| Alocar capacidade | E | E | solicitar | — | L | — | — | — | — | L | — | L | — |
| Criar produto/lote/preço | E | E | P | P limitado | L | — | L | — | — | L | — | L | — |
| Emitir cortesia | E | A/E | quota | quota | exceção local | — | — | — | — | L | — | L | — |
| Criar pedido | E | E | E | P | E | E manual | E | — | — | L | — | L | E próprio |
| Cancelar pedido | E | E | P | P | P | solicitar | alçada | — | — | A/E financeiro | — | L | próprio conforme política |
| Solicitar reembolso | E | E | solicitar | solicitar | solicitar | — | alçada | — | — | E | — | L | próprio |
| Aprovar reembolso | E | alçada | — | — | alçada operacional | — | não próprio | — | — | A/E | — | L | — |
| Emitir/reemitir credencial | E | E | P | quota | E | suporte limitado | — | — | — | L | — | L | solicitar própria |
| Check-in/check-out | L | L | L | L | E/A | E | — | E | — | — | — | L | — |
| Override de acesso | E | A | — | — | E | solicitar | — | solicitar | — | — | — | L | — |
| Abrir/fechar caixa | L | L | — | — | A divergência | — | E | — | — | L/A | — | L | — |
| Venda PDV | L | L | — | — | L | — | E | — | — | L | — | L | — |
| Emitir/cancelar fiscal | L | L | — | — | — | — | — | — | E | L | — | L | — |
| Baixar contas | L | L | — | — | — | — | alçada caixa | — | — | E | — | L | — |
| Conciliar/fechar evento | L/A | A | L | L próprio | fechar operação | — | fechar caixa | — | L | E | — | L | — |
| DRE | L | L | L limitada | L contratual | L operacional | — | — | — | L fiscal | E | L custo autorizado | L | — |
| Funcionários/escalas | L | L | solicitar escala | — | E execução | própria | própria | própria | — | L custo | E | L | — |
| Equipamentos | E | E | L | — | comando operacional | comando permitido | POS próprio | leitor próprio | — | — | — | L | — |
| Auditoria/exportação | E | L | L própria | L própria | L própria | própria | própria | própria | fiscal | financeira | RH | L total | próprios dados |

## 18.4 Permissões atômicas

Papéis são compostos por permissões, entre elas:

- `organizations:admin`;
- `users:invite`, `roles:grant`, `roles:revoke`;
- `facilities:read`, `facilities:manage`, `capacity:override`;
- `events:create`, `events:update`, `events:publish`, `events:cancel`, `events:close`;
- `inventory:manage`, `inventory:hold`, `pricing:manage`;
- `orders:read`, `orders:create`, `orders:cancel`, `orders:manual-confirm`;
- `payments:initiate`, `payments:refund-request`, `payments:refund-approve`, `payments:reconcile`;
- `credentials:issue`, `credentials:reissue`, `credentials:block`;
- `access:validate`, `access:checkin`, `access:checkout`, `access:override`;
- `movements:open`, `movements:close`, `movements:adjust`;
- `cash:open`, `cash:operate`, `cash:close`, `cash:approve-difference`;
- `fiscal:issue`, `fiscal:cancel`, `fiscal:retry`;
- `finance:read`, `finance:settle`, `finance:close`, `finance:dre`;
- `hr:manage`, `schedules:manage`;
- `devices:provision`, `devices:command`, `devices:diagnose`;
- `audit:read`, `audit:export`.

## 18.5 Regras ABAC complementares

1. Operador e fiscal de acesso atuam apenas no turno, evento, pátio e faixa atribuídos.
2. Promotor acessa somente eventos associados e campos previstos no contrato.
3. Cliente acessa somente recursos cujo `customerId` corresponda à identidade.
4. Dispositivo atua somente nas capacidades e instalação provisionadas.
5. Valor de reembolso, sangria, desconto e cortesia é limitado pela alçada.
6. Estado do recurso pode tornar uma permissão inaplicável: possuir `events:update` não permite editar evento finalizado.
7. Suporte cross-tenant requer sessão just-in-time, aprovação, prazo, motivo e auditoria.

## 18.6 Segregação de funções

- Solicitante de reembolso acima da alçada não é o aprovador.
- Caixa não aprova divergência própria acima da tolerância.
- Cortesia acima da quota exige Gestor.
- Redução de capacidade abaixo do vendido exige Gestor e plano de contingência.
- Fiscal tributário não altera o pedido que originou o documento.
- Financeiro não altera registros físicos de acesso.
- Auditor nunca possui comando.
- Administrador de plataforma não opera tenant sem sessão de suporte autorizada.

## 18.7 Evolução da autenticação

O JWT HS256 atual pode permanecer durante transição. O alvo é OIDC/OAuth 2.1 com:

- access tokens curtos e assinatura assimétrica;
- claims `iss`, `aud`, `sub`, `jti`, `org_id`, escopos e sessão;
- refresh token rotativo;
- MFA para privilégios;
- SSO corporativo;
- client credentials para integrações;
- identidade de dispositivo/mTLS para edge;
- revogação e rotação de chaves por JWKS.

## 18.8 Diferença para o AS-IS

Atualmente existem apenas `ADMIN` e `OPERADOR`; usuários, manutenção de tarifa e exclusões recebem regras específicas, enquanto as demais rotas autenticadas são amplamente acessíveis. A matriz deste documento é TARGET e deve substituir gradualmente essa autorização coarse-grained sem remover o comportamento até que cada tela/API tenha migração definida.
