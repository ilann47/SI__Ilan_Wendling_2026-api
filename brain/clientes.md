> Links: [[core]] · [[unificacao-multitenant]] · [[organizacoes]] · [[membership]] · [[rbac]] · [[pagamentos-cadastrais]] · [[testes-multitenancy]] · [[fiscal-financeiro]] · [[operacao]]

# Clientes

## Objetivo

Cadastrar pessoas físicas e jurídicas clientes dentro da organização ativa,
preservando dados históricos e impedindo referências entre tenants.

## Contexto

Cliente era uma raiz global usada por veículos, mensalistas, fiscal e contas a
receber. A V40 adiciona ownership sem backfill automático. Consumers ainda
globais enxergam somente clientes históricos sem owner até suas próprias ondas.
No esquema local de teste V99, o limite de crédito é gravado em milionésimos,
sem alterar a unidade do contrato HTTP.

## Fluxo (camadas da arquitetura)

```text
JWT contextual + customers:read/manage
  -> ClienteController
  -> ClienteService(organizacaoId, comando/filtro)
  -> ClienteRepository por id + organizacaoId
  -> cliente + condição do mesmo tenant
  -> auditoria PRIVACIDADE
```

Ao salvar um tomador identificado no pagamento ou no rascunho de NFS-e do
pátio, a própria transação fiscal cadastra nome, tipo e CPF/CNPJ, se o
documento ainda não existir na organização. Não altera cadastro existente
nem atribui veículo, crédito ou contrato mensalista.

## Endpoints (se houver)

- `GET /api/clientes` e `GET /api/clientes/{id}`: `customers:read`.
- `POST /api/clientes`: `customers:manage`.
- `PUT /api/clientes/{id}`: `customers:manage`.
- `DELETE /api/clientes/{id}`: inativação lógica com `customers:manage`.

As URLs foram preservadas; o body não controla organização.

## Estrutura de Dados (DTOs, Entidades)

- `Cliente.organizacaoId`: owner imutável após a criação.
- Documento normalizado é único por organização.
- V109: nome com até 160 caracteres e origem fiscal imutável. A API expõe
  `cadastroFiscalBasico` somente na resposta: cliente originado no pátio que
  ainda não tem telefone ou e-mail utilizável. Editar um CNPJ alfanumérico
  mantém as letras, em formato canônico.
- Cidade permanece referência global.
- Condição de pagamento deve pertencer ao mesmo tenant.
- DTO rejeita campos desconhecidos, incluindo `organizationId`.

## Integrações externas (se houver)

Nenhuma.

## Tratamento de Erros

- `401`: autenticação ausente ou inválida.
- `403`: token global ou Membership sem permissão.
- `404`: cliente/condição inexistente ou de outra organização.
- `409`: documento já usado no mesmo tenant.
- Um cliente fiscal básico precisa de telefone ou e-mail válido para criar
  ou ativar um contrato mensalista, além dos requisitos usuais do contrato.

## Testes (curl ou equivalente)

`ClienteTenantApiTest` cobre RBAC, isolamento A/B, body hostil, histórico e
auditoria. `ClienteTenantConstraintTest` valida unicidade e FK composta.
`ClienteMigrationCompatibilityTest` prova V39→V40 com linha histórica real.
`PatioCaixaApiTest` verifica o cadastro em entrada, saída e edição fiscal,
sem duplicar nem reativar clientes. `MensalistaTenantApiTest` verifica a
ativação bloqueada até o preenchimento de um contato.

## Decisões Técnicas

- A FK composta protege referências entre tenants; um trigger transitório também impede que registros sem owner apontem para condições tenant-aware enquanto `organizacao_id` permanecer opcional para preservar dados históricos.

- V40 é aditiva; `organizacao_id` permanece nullable até reconciliação explícita.
- Histórico null é invisível nas APIs tenant e mantém unicidade anterior.
- Auditoria evita contatos e documento; registra somente estado operacional mínimo.
- Consumers globais usam `findByIdAndOrganizacaoIdIsNull` temporariamente.
- `ADMIN_ORGANIZACAO` recebe `customers:read/manage`; nenhum outro papel é ampliado.

## Módulos relacionados

- [[core]]
- [[unificacao-multitenant]]
- [[organizacoes]]
- [[membership]]
- [[rbac]]
- [[pagamentos-cadastrais]]
- [[testes-multitenancy]]

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-08-03 | Migra Cliente para tenancy real na V40. |
| 2026-11-08 | V99 converte o limite de crédito histórico sem recalcular seu valor. |
| 2026-11-22 | V109 cadastra o cliente fiscal básico pelo tomador, com unicidade por organização e auditoria sem documento ou nome. |
