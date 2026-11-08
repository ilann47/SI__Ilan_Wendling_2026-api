> Links: [[core]] · [[clientes]] · [[conveniencia]] · [[estoque]] · [[fiscal-financeiro]] · [[rbac]] · [[auditoria]] · [[idempotencia]] · [[outbox]]

# Vendas Administrativas — histórico

**Encerrado pela V82.** A interface, a API, o código operacional e o esquema
foram removidos. As seções abaixo registram a fase intermediária anterior à V82;
não descrevem o contrato atual. Para mercadorias, use [[fiscal-financeiro]].
O Flyway não reescreve as migrações históricas. A V82 remove tabelas, vínculos
e permissões exclusivos e não apaga o razão nem os títulos de outros fluxos.

## Objetivo

Preservar os vínculos de estoque, contas e notas do antigo Pedido de Venda.
Novas vendas de mercadoria são lançadas diretamente pela Nota de Saída.

## Contexto

A V64 registrava pedidos em `RASCUNHO`, `CONFIRMADA` ou `CANCELADA`. Esses
estados ficam no histórico; não há novos pedidos, confirmações ou cancelamentos.

## Fluxo (camadas da arquitetura)

Contas a receber e razão exibem a origem histórica sem abrir o pedido.
Notas já vinculadas mantêm o vínculo. Confirmar um rascunho fiscal legado
apenas documenta a baixa e os títulos que já existiam na venda.

## Endpoints (se houver)

Não há mais API pública de Pedido de Venda: `/api/v1/administrative-sales`
e subrotas foram retiradas do roteamento e do OpenAPI. Para novas operações
use `/api/notas-saida`; os efeitos anteriores seguem consultáveis pelos
respectivos módulos, conforme RBAC.

## Estrutura de Dados (DTOs, Entidades)

`VendaAdministrativa`, `ItemVendaAdministrativa`, vínculo preexistente em
`NotaSaida` e origem em `ContaReceber`. Sem alteração nas tabelas ou nos dados.

## Integrações externas (se houver)

Nenhuma. A Nota de Saída é documento interno; transmissão fiscal permanece fora.

## Tratamento de Erros

A rota antiga não existe. Com autenticação válida, a API responde `404`.
Criar Nota de Saída com `vendaAdministrativaId` responde `409`.

## Testes (curl ou equivalente)

As regressões cobrem remoção das rotas, Nota de Saída avulsa e
preservação dos efeitos de registros legados sem duplicação.

## Decisões Técnicas

- O Pedido de Venda e a geração de notas a partir dele foram descontinuados.
- A Nota de Saída já vinculada é documental e não repete estoque/recebível.
  O detalhe da nota consulta movimentos e contas originais da venda conforme
  RBAC; na confirmação documental, o custo é lido da baixa original, não
  recalculado. A reversão fiscal V81 não reverte uma venda vinculada.
- A edição de rascunho fiscal legado não pode mudar a venda original nem
  sobrescrever os dados comerciais registrados.
- Baixas de venda históricas não são compensadas isoladamente no razão.
- Pedidos de eventos, ordens de serviço e baixas de títulos não são removidos.

## Módulos relacionados

[[clientes]], [[conveniencia]], [[estoque]], [[fiscal-financeiro]], [[rbac]],
[[auditoria]], [[idempotencia]] e [[outbox]].

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-11-05 | Retira Pedido de Venda da interface/API e preserva somente vínculos e efeitos históricos; nova venda parte da Nota de Saída avulsa. |
| 2026-11-05 | V80 reaproveita custo, razão e títulos da venda na nota vinculada sem repetir efeitos; V81 limita reversão à saída avulsa. |
| 2026-09-12 | Conecta geração idempotente de nota, documentos, contas e movimentos ao pedido; frontend mantém rota/entidade e usa o rótulo Pedidos de venda. |
| 2026-09-12 | Adiciona filtros reais de número/situação antes da paginação e testes dedicados de consulta, concorrência de confirmação/nota e seções documentais com RBAC. |
| 2026-08-14 | V64 implementa Venda Administrativa e integração fiscal/financeira. |
