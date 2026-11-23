# Organização operada — somente banco de teste

Seed explícito para explorar a plataforma com uma organização sintética e relações
reais entre eventos, instalações, estadias, OS, caixa, documentos, estoque e contas.
Não tem ativação automática e não se aplica em ambientes empresariais.

## Alvo obrigatório

- API local `http://127.0.0.1:5174` (proxy) ou `http://127.0.0.1:18087`.
- PostgreSQL `127.0.0.1:55474`.
- Banco `estacionamento_kaneko_fiscal_20261105_test`.
- Usuário `kaneko_evento_qa`, sem alteração de privilégios pelo seed.
- O runner confirma `current_database()` antes da primeira mutação.
- Requer PostgreSQL CLI 17 e Node 24; sem dependências npm adicionais.

Nenhum dado existente é apagado. A organização demonstrativa é identificada por
um CNPJ sintético estável. A repetição reutiliza os mesmos cadastros, documentos e
comandos idempotentes. Uma conta preexistente com o mesmo login e outra senha
causa erro; o runner nunca redefine sua senha nem transfere organizações.

## Executar depois dos testes operacionais

Na raiz da API, com a prévia de QA já saudável:

```powershell
node --test scripts/seed-operated-organization.test.mjs
node scripts/seed-operated-organization.mjs --apply-test-only
# Alternativa sem o proxy frontend:
node scripts/seed-operated-organization.mjs --apply-test-only --api=http://127.0.0.1:18087
```

Sem `--apply-test-only`, o programa apenas mostra a orientação e não se conecta.
As credenciais de conexão ao PostgreSQL vêm do mecanismo normal do psql; não são
copiadas de `.env` ou persistidas pelo runner. Use apenas a sessão/runtime de QA.

## Contas para navegar

Senha pública **exclusivamente demonstrativa** para as quatro contas:
`Demo!Parking-2026-Explore`.

| Login | Papel existente | Uso |
|---|---|---|
| `demo.admin` | `ADMIN_ORGANIZACAO` + atribuição explícita `SUPERVISOR` | Explorar todos os módulos, iniciar a operação e gerir correções |
| `demo.gestor` | `GESTOR` | Planejamento e consultas autorizadas pelo papel vigente |
| `demo.caixa` | `CAIXA_PATIO` | Entrada, pagamento, saída e fechamento |
| `demo.portaria` | `OPERADOR` | Credenciais e portaria, sem adicionar permissões financeiras |

O runner não acrescenta permissões aos papéis globais nem transforma Gestor em
administrador. Escolha **Yes7 Parking — Organização Demonstrativa** após entrar.
O administrador demonstrativo recebe também o papel existente de Supervisor,
porque `access:operate` não pertence ao papel administrativo e é necessário
para iniciar a operação do evento. Essa atribuição é só desse usuário de teste.
Tokens e senhas não são escritos nos logs nem no inventário de cobertura.

## Cenário produzido

- 2 locais, 4 pátios, 9 setores, 138 vagas, incluindo bloqueio e manutenção.
- 4 eventos: cobrança na entrada, cobrança na saída, operação encerrada e
  um cenário de venda antecipada com credencial e reembolso pendente.
- 45 estadias avulsas: 15 ainda abertas, 29 encerradas e uma cancelada; recebimentos reais
  internos em dinheiro, Pix e cartão, OS automáticas e pendências fiscais ligadas.
- Histórico de fechamentos de caixa e novo turno aberto para experimentar entradas.
- Três correções reais pelo domínio: placa da estadia aberta, meio de pagamento
  de uma estadia paga e cancelamento de entrada indevida. Os ajustes mantêm o
  histórico; o administrador usa turno próprio para a contrapartida e o fecha
  após as correções. Só `demo.caixa` permanece com turno aberto ao final.
- Evento com venda antecipada, produto, lote, pedido, comprador, credencial,
  representação QR, presença e tentativas de acesso produzidas pelas APIs.
  A confirmação manual é rotulada como simulação sem transação bancária.
- Outro pedido passa por check-in e checkout QR reais antes do cancelamento.
  Fica `EM_REEMBOLSO`: essa pendência não significa que dinheiro foi devolvido.
  O QR fica apenas em memória durante a execução, nunca em arquivo ou log.
- 26 clientes, 7 fornecedores com contatos, transportadora/frota, RH,
  formas/condições/parcelas e catálogo de 16 produtos relacionados a fornecedor.
- 8 compras e 12 vendas novas confirmadas pela API, com sexta casa em quantidade,
  preço, desconto e rateio; estoque e contas são gerados pelo domínio.
- Contas integralmente baixadas, parcialmente baixadas e pendentes; datas antigas
  permitem observar vencimentos. Nenhuma baixa bancária externa é realizada.
- 3 notas de entrada em rascunho; 4 notas internas de serviço de estacionamento
  contratado (uma em rascunho), distintas das pendências das estadias de evento.
- Fixture XML conciliada do seed já existente para exercitar importação.
- A conferência desse XML é registrada pela API e congela o cronograma de
  parcelas, sem confirmar outra nota nem duplicar estoque/contas.
- Um colaborador legado em `CONVIDADO`, relacionado somente ao pátio. Esse
  registro não dá acesso ao sistema e não inventa FK com funcionário/usuário.

As quantidades acima são alvos da primeira execução concluída. A segunda execução
não duplica as estadias/notas. Se a organização for operada manualmente depois,
o seed não desfaz essas ações nem restaura estados antigos.

## Cobertura honesta de tabelas

Após a execução, consulte `tmp/operated-demo/coverage.json`. O inventário inclui
todas as tabelas ativas do schema `public`. Conta registros da organização quando
há `organizacao_id`; para subtabelas sem owner, resolve FKs para tabelas do tenant.
Configurações globais são identificadas separadamente, sem confundi-las com dados
da organização demonstrativa.

Tabelas de webhooks, integração externa, conciliação, reembolso ou reversão podem
permanecer vazias quando o fluxo não foi efetivamente exercitado. O runner não
fabrica eventos de gateway, autorização de NFS-e, auditoria assinada ou baixas para
alegar cobertura. Tabelas novas não reconhecidas aparecem como
`pendente-avaliacao`, para não serem silenciosamente ignoradas.

O SQL histórico `all-screens.sql` é reaproveitado apenas dentro do tenant novo
como fixture de catálogos/histórico. As novas notas, baixas e operações do caixa
passam pelos serviços HTTP reais. Dados históricos de outras organizações não
são recalculados nem reprecificados. Os serviços demonstrativos são todos de
estacionamento; nenhuma lavagem é adicionada ao novo cenário.

## Limites

Execução local verificada em 2026-11-19: organização 1057, 84 tabelas públicas
inventariadas (83 populadas + Flyway), nenhuma tabela vazia. A repetição final
não mudou contagens de nenhuma das 84 tabelas. Somente `demo.caixa` permaneceu
com turno aberto (93). Consulte `tmp/operated-demo/verification.json` e os
relatórios antes/depois. Os relatórios não contêm senha nem token.

Nada aqui representa Pix confirmado pelo banco, transação na maquininha ou
NFS-e autorizada. A sessão demonstra o registro interno e as pendências reais.
Não use esses usuários ou essa senha fora deste banco de teste.
