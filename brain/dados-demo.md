> Links: [[core]] · [[organizacoes]] · [[membership]] · [[rbac]] · [[instalacoes]] · [[venues]] · [[eventos]] · [[inventario]] · [[produtos-estacionamento]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Dados de Demonstração

## Objetivo

Criar um cenário local coerente para explorar eventos, operação tradicional,
conveniência, estoque, fiscal e financeiro sem inserir senhas no repositório.

## Contexto

`DemoDataInitializer` somente existe quando `APP_DEMO_DATA_ENABLED=true`. Ele
executa depois do bootstrap administrativo e vincula o usuário indicado por
`APP_ADMIN_LOGIN` ao tenant de demonstração.

## Fluxo (camadas da arquitetura)

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
- local, saldos e razão de estoque com recebimento, baixa e ajuste;
- notas de entrada, saída e serviço, contas a pagar/receber e despesa avulsa;
- ordem de compra aprovada pronta para demonstrar o fluxo de recebimento;
- venda administrativa em rascunho pronta para confirmação;
- tarifas, veículos, mensalista e movimentações aberta e fechada;
- pedido confirmado, comprador, credencial, QR, presença, sessão e tentativas
  de acesso autorizada e recusada.

## Integrações externas (se houver)

Nenhuma.

## Tratamento de Erros

Cada etapa roda em sua própria transação. Falha no complemento reverte todos os
dados das telas sem desfazer o núcleo de eventos já válido. CNPJ, documentos,
referências e chaves `Demo` são marcadores idempotentes. Ausência do
administrador gera falha explícita.

## Testes (curl ou equivalente)

- `DemoDataSeedServiceTest` garante o no-op idempotente.
- `DemoAllScreensSeedServiceTest` protege o carregamento e a cobertura do script.
- `DemoAllScreensSeedIntegrationTest` executa o seed duas vezes em PostgreSQL e
  verifica perfis e dados representativos de todas as áreas.
- `SetorTest` protege o uso de conjuntos imutáveis de categorias.
- Validação real: primeiro boot cria o cenário; segundo boot mantém as contagens.

## Decisões Técnicas

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
| 2026-08-03 | Cria seed local opt-in, transacional e idempotente do fluxo de eventos. |
| 2026-08-04 | Amplia o seed para perfis admin/operador e dados coerentes em todas as telas. |
| 2026-08-14 | Acrescenta Ordem de Compra ao cenário local de todas as telas. |
| 2026-08-14 | Acrescenta Venda Administrativa ao cenário local de todas as telas. |
| 2026-08-14 | Acrescenta Ordem de Serviço concluída e vinculada à NFS demonstrativa. |
