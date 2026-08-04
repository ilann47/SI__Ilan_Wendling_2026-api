> Links: [[core]] · [[estoque]] · [[conveniencia]] · [[fornecedores]] · [[pagamentos-cadastrais]] · [[rbac]]

# Fiscal e Financeiro de Entrada

## Objetivo
Registrar notas de entrada/saída por organização, movimentos no razão e títulos financeiros.

## Contexto
A V55 migra `nota_entrada`, `item_nota_entrada` e `conta_pagar` de forma aditiva.

## Fluxo (camadas da arquitetura)
O JWT define a organização. A criação valida pais do tenant. A confirmação
serializa a nota, registra `RECEBIMENTO` por item e gera parcelas na mesma transação.

## Endpoints (se houver)
- `/api/notas-entrada`: consulta, criação e transições.
- `/api/contas-pagar`: consulta, lançamento, baixa e cancelamento.

## Estrutura de Dados (DTOs, Entidades)
Nota e Conta carregam owner e versão; Item carrega owner. Nota tenant exige local.

## Integrações externas (se houver)
Não há transmissão fiscal ou pagamento externo; a integração é com [[estoque]].

## Tratamento de Erros
Cross-tenant retorna 404; duplicidade/transição inválida 409; sem permissão 403.

## Testes (curl ou equivalente)
V1→V55, Hibernate validate, legado, isolamento, razão e títulos são cobertos.

## Decisões Técnicas
- Tenant usa razão V54; `produto.quantidade` permanece histórico.
- Relatórios legados veem somente contas sem owner até a onda de fechamento.

## Módulos relacionados
[[estoque]], [[conveniencia]], [[fornecedores]], [[pagamentos-cadastrais]], [[rbac]].

## Histórico (data + ação)
| Data | Ação |
|---|---|
| 2026-08-04 | Nota de entrada e conta a pagar migradas na V55. |
| 2026-08-04 | Nota de saída e conta a receber migradas na V56. |
| 2026-08-04 | Nota de serviço e faturamento a receber migrados na V57. |
