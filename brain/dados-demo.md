> Links: [[core]] · [[organizacoes]] · [[membership]] · [[rbac]] · [[instalacoes]] · [[venues]] · [[eventos]] · [[inventario]] · [[produtos-estacionamento]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Dados de Demonstração

## Objetivo

Criar um cenário local coerente para explorar eventos, operação tradicional,
conveniência, estoque, fiscal e financeiro sem credenciais reais. O runner
operacional documenta uma senha pública exclusivamente sintética para o banco
de teste; ela não deve ser usada fora desse ambiente.

## Contexto

Além do inicializador histórico, `scripts/seed-operated-organization.mjs` cria
uma organização operada mais volumosa, somente por comando explícito
`--apply-test-only`. Exige API local 5174/18087, PostgreSQL 55474, banco
`estacionamento_kaneko_fiscal_20261105_test`, usuário `kaneko_evento_qa` e V106
aplicada. Valida `current_database()` antes de qualquer escrita. Não apaga
outra organização nem redefine senha de conta preexistente.

`DemoDataInitializer` somente existe quando `APP_DEMO_DATA_ENABLED=true`. Ele
executa depois do bootstrap administrativo e vincula o usuário indicado por
`APP_ADMIN_LOGIN` ao tenant de demonstração.

## Fluxo (camadas da arquitetura)

Seed operacional opt-in:

`registro/provisionamento/RBAC -> locais/pátios/setores/vagas -> eventos e cobrança ->
fixture histórica all-screens isolada no novo tenant -> pedido/QR/check-in pelas APIs ->
45 estadias com OS automática e caixa -> correções auditadas -> notas/estoque/contas/baixas ->
inventário de cobertura de todas as tabelas ativas`.

Reservas, pedidos, confirmação manual, credenciais, hash do QR e presença do
novo cenário são gerados pelos serviços reais. O runner remove esses blocos
do SQL histórico antes de reaproveitá-lo. Não inventa hash de QR ou auditoria.
As novas notas e baixas também passam pelo domínio, com seis casas e sem
recalcular documentos anteriores. Correções usam a API supervisora e o turno
do administrador, preservando a origem e as pendências fiscais.

```text
bootstrap admin
  -> DemoDataInitializer
  -> DemoDataSeedService (transação única)
  -> services reais de geografia/tenant/RBAC/instalações/eventos/inventário
  -> auditoria + idempotência + outbox
  -> evento em VENDAS_ABERTAS
  -> DemoAllScreensSeedService (segunda transação)
  -> db/demo/all-screens.sql
  -> perfis, cadastros e operações demonstrativas idempotentes
```

## Endpoints (se houver)

Nenhum. O seed é infraestrutura local de inicialização e não amplia a API.

## Estrutura de Dados (DTOs, Entidades)

- administrador global existente e operador global `OPERADOR`;
- Membership ativa e papel contextual `OPERADOR` para o operador demo;
- organização e primeira Membership administradora;
- Brasil, Paraná e Cascavel quando ainda não existirem;
- venue, pátio com capacidades, setor e 12 vagas;
- evento de 30 dias, alocação, produto comum e lote BRL;
- política de reentrada explícita, produto publicado e vendas abertas.
- formas e condições de pagamento, cargos, funcionário, clientes e fornecedor;
- transportadora, frota, catálogo de conveniência, serviços e vínculos comerciais;
- local, saldos e razão de estoque com entrada e baixa vinculadas às notas;
- notas de entrada, saída e serviço, contas a pagar e a receber;
- nota de entrada confirmada, movimento de estoque e conta a pagar ligados no mesmo fluxo;
- nota de saída avulsa, conta recebida e histórico financeiro, sem criar
  Pedido de Venda;
- tarifas, veículos, mensalista e movimentações aberta e fechada;
- pedido confirmado, comprador, credencial, QR, presença, sessão e tentativas
  de acesso autorizada e recusada.
- importação XML de nota de entrada conciliada, com XML íntegro, hash SHA-256 e
  item associado ao catálogo, pronta para conferência.

## Integrações externas (se houver)

Nenhuma.

## Tratamento de Erros

Cada etapa roda em sua própria transação. Falha no complemento reverte todos os
dados das telas sem desfazer o núcleo de eventos já válido. CNPJ, documentos,
referências, números e chaves `Demo` são marcadores idempotentes. Recebimentos,
baixas e importações usam chaves únicas próprias e são preservados na segunda
execução. Ausência do administrador gera falha explícita.

## Testes (curl ou equivalente)

- `node --test scripts/seed-operated-organization.test.mjs`: oito testes de
  alvo exclusivo, precisão inteira, chaves estáveis, isolamento do SQL e
  classificação honesta de tabelas vazias, limite de referência do produto,
  resposta JSON vazia e data fiscal no fuso de São Paulo.
- `node scripts/seed-operated-organization.mjs`: sem opt-in é no-op, sem conexão.
- `node --check scripts/seed-operated-organization.mjs`: valida sintaxe.
- Em 2026-11-19, execução real na API 18087 e banco exclusivo de teste criou
  a organização 1057. O inventário encontrou 84 tabelas públicas: 83 populadas
  mais o histórico global Flyway, sem tabela vazia. A execução repetida final
  manteve as contagens das 84 tabelas, inclusive auditoria/outbox/turnos.
  Evidências: `tmp/operated-demo/coverage.before-repeat.json`, `coverage.json`
  e `verification.json`. Somente `demo.caixa` ficou com turno aberto (93).

- `DemoDataSeedServiceTest` garante o no-op idempotente.
- `DemoAllScreensSeedServiceTest` protege o carregamento e a cobertura do script.
- `DemoAllScreensSeedIntegrationTest` executa o seed duas vezes em PostgreSQL e
  verifica perfis e dados representativos de todas as áreas.
- `SetorTest` protege o uso de conjuntos imutáveis de categorias.
- Validação real: primeiro boot cria o cenário; segundo boot mantém as contagens.

## Decisões Técnicas

- Contas `demo.admin`, `demo.gestor`, `demo.caixa` e `demo.portaria` usam
  exclusivamente os papéis existentes, sem ampliar `papel_permissao`.
  A credencial pública de demonstração fica no README do script, nunca em log.
- Somente `demo.admin` recebe a atribuição adicional do papel existente
  `SUPERVISOR`, necessária para `access:operate`; o papel administrativo
  isolado não possui essa permissão. Gestor, Caixa e Portaria não são promovidos.
- O quarto evento exercita um pedido com check-in/checkout QR e cancelamento
  `EM_REEMBOLSO`. O token é descartado da memória ao fim; nenhum reembolso
  externo é executado. A conferência da importação XML congela suas parcelas.
  Colaborador legado é só um convite de pátio, sem privilégio ou FKs inventadas.
- O relatório `tmp/operated-demo/coverage.json` enumera todas as tabelas ativas.
  Contagens tenant-aware usam o owner direto ou FKs para ele; configuração
  global é identificada separadamente. Tabelas vazias técnicas são explícitas,
  enquanto tabela nova não avaliada continua pendência, sem dados fabricados.
- Pagamentos registrados são internos. Não há Pix bancário, maquininha ou
  NFS-e autorizada. As pendências de estadia não se confundem com as notas
  internas de serviços contratados da fixture.

- Desativado por padrão e sem profile de produção implícito.
- Não persiste senha em texto. O operador copia o hash do administrador local;
  por isso usa a mesma senha configurada em `APP_ADMIN_PASSWORD`.
- Login do operador configurável por `APP_DEMO_DATA_OPERATOR_LOGIN`, com valor
  padrão `operador`.
- IDs podem aparecer no log para navegação local; credenciais e tokens não.
- `database/seed.sql` permanece legado e fora deste fluxo.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[membership]]
- [[rbac]]
- [[instalacoes]]
- [[venues]]
- [[eventos]]
- [[inventario]]
- [[produtos-estacionamento]]
- [[auditoria]]
- [[idempotencia]]
- [[outbox]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-11-19 | Runner opt-in de organização operada volumosa e inventário integral de tabelas, com APIs reais de caixa, fiscal e baixas. |
| 2026-11-10 | Retira a Despesa Avulsa do seed, mantendo os exemplos fiscais e financeiros regulares. |
| 2026-11-08 | Seed do fluxo fiscal de teste usa o esquema V96 para novos registros, sem atualizar as notas demonstrativas já existentes. |
| 2026-11-08 | Seed grava novos itens, totais e contas a receber em milionésimos no esquema V97, sem reprocessar registros existentes. |
| 2026-11-08 | Seed de teste atualizado para a persistência inteira da V100 em serviços, financeiro, cadastros, pátio e comércio. |
| 2026-11-06 | Remove o ajuste manual sintético do seed; os movimentos de demonstração têm origem fiscal. |
| 2026-11-05 | O seed deixa de criar ou vincular pedidos de venda; não altera vínculos preexistentes. |
| 2026-08-03 | Cria seed local opt-in, transacional e idempotente do fluxo de eventos. |
| 2026-08-04 | Amplia o seed para perfis admin/operador e dados coerentes em todas as telas. |
| 2026-08-14 | Acrescenta Ordem de Compra ao cenário local de todas as telas. |
| 2026-08-14 | Acrescenta Venda Administrativa ao cenário local de todas as telas. |
| 2026-08-14 | Acrescenta Ordem de Serviço concluída e vinculada à NFS demonstrativa. |
| 2026-09-25 | Completa os encadeamentos de compra e venda, histórico de baixa e importação XML idempotente. |
