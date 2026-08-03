> Links: [[core]] · [[organizacoes]] · [[membership]] · [[rbac]] · [[instalacoes]] · [[venues]] · [[eventos]] · [[inventario]] · [[produtos-estacionamento]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Dados de Demonstração

## Objetivo

Criar um cenário local coerente para explorar o produto de eventos sem executar
SQL legado ou inserir senhas no repositório.

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
```

## Endpoints (se houver)

Nenhum. O seed é infraestrutura local de inicialização e não amplia a API.

## Estrutura de Dados (DTOs, Entidades)

- administrador global existente;
- organização e primeira Membership administradora;
- Brasil, Paraná e Cascavel quando ainda não existirem;
- venue, pátio com capacidades, setor e 12 vagas;
- evento de 30 dias, alocação, produto comum e lote BRL;
- política de reentrada explícita, produto publicado e vendas abertas.

## Integrações externas (se houver)

Nenhuma.

## Tratamento de Erros

O cenário inteiro roda em uma transação. Falha em qualquer regra reverte todos
os registros. O CNPJ demo é o marcador idempotente; se ele existe, o seed encerra
sem alterar ou duplicar dados. Ausência do administrador gera falha explícita.

## Testes (curl ou equivalente)

- `DemoDataSeedServiceTest` garante o no-op idempotente.
- `SetorTest` protege o uso de conjuntos imutáveis de categorias.
- Validação real: primeiro boot cria o cenário; segundo boot registra no-op.

## Decisões Técnicas

- Desativado por padrão e sem profile de produção implícito.
- Não cria nem redefine senha; reutiliza o administrador seguro do bootstrap.
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
