> Links: [[core]] · [[membership]] · [[rbac]] · [[clientes]] · [[operacao]] · [[pedidos]] · [[eventos]] · [[fiscal-financeiro]]

# Consultas

## Objetivo

Permitir que o usuário encontre registros de diferentes módulos sem conhecer previamente a estrutura do menu.

## Contexto

A busca é sempre executada no tenant do JWT contextual. Os grupos consultados também são limitados pelas permissões efetivas da Membership.

## Fluxo (camadas da arquitetura)

O cabeçalho abre a busca por clique ou `Ctrl+K`, aplica debounce e consulta o controller. O service resolve permissões no [[rbac]], executa projeções de leitura tenant-aware e devolve resultados agrupados para navegação.

## Endpoints

- `GET /api/v1/search?q={termo}&limit={1..10}` — pesquisa Clientes, Veículos, Pedidos, Eventos, Movimentações, Notas e Contas.
- `GET /api/v1/operational-pendencies?limit={1..10}` — pendências por processo, limite por categoria (padrão 5), contagens completas e links ao documento exato.

## Estrutura de Dados (DTOs, Entidades)

- `BuscaGlobalResponse`: termo normalizado, total e grupos.
- `GrupoBuscaGlobal`: nome funcional e resultados permitidos.
- `ResultadoBuscaGlobal`: tipo, id, título, subtítulo, estado e caminho da tela.
- Não há entidade ou tabela própria; o módulo usa projeções somente leitura.
- `PendenciasOperacionaisResponse`: referência no fuso da organização, total completo, limitePorGrupo e grupos com tipo, total e itens (id, título, contraparte, caminho, data e valor).

## Integrações externas

Não existem. A primeira versão usa PostgreSQL e os dados transacionais existentes.

## Tratamento de Erros

- JWT sem tenant válido: `401`.
- Termo fora de 2 a 100 caracteres ou limite fora de 1 a 10: `400`.
- Módulos sem permissão não são consultados nem retornados.

## Testes (curl ou equivalente)

O teste de integração prova autenticação contextual, validação do termo e isolamento de clientes homônimos entre organizações. O frontend prova abertura por `Ctrl+K`, debounce e renderização agrupada.

`PendenciasOperacionaisControllerTest` cobre contagem além do limite, isolamento/RBAC, token global, limite inválido, nota de serviço já gerada, vencidas antigas e despesas avulsas sem fornecedor. Quatro cenários passaram na rodada focalizada integrada de 2026-09-12.

## Decisões Técnicas

- O tenant vem exclusivamente do principal autenticado; não há header selecionável.
- Consultas usam projeções diretas para evitar carregar agregados completos.
- Pendências usam snapshot PostgreSQL `REPEATABLE_READ` e `count(*) OVER()` antes do limite: contagem e itens permanecem coerentes, sem deduzir o total pela primeira página. SQL é parametrizado e cada categoria exige sua permissão no tenant.
- Despesas avulsas vencidas são preservadas como categoria própria, inclusive sem fornecedor associado, e não se confundem com títulos de compra.
- Compras aprovadas/parcialmente recebidas aguardam recebimento. Vendas confirmadas e OS concluídas só aparecem sem qualquer nota vinculada; uma nota já criada, inclusive cancelada, não se confunde com ausência de documento. Contas vencidas pendentes/parciais consideram saldo positivo e todo o histórico, não o período do resumo financeiro.
- Um índice de busca dedicado não foi introduzido nesta versão; poderá ser avaliado com métricas reais.

## Módulos relacionados

[[membership]], [[rbac]], [[clientes]], [[operacao]], [[pedidos]], [[eventos]], [[compras]], [[vendas-administrativas]], [[ordens-servico]] e [[fiscal-financeiro]].

## Histórico

| Data | Ação |
|---|---|
| 2026-08-17 | Implementa busca global tenant-aware e entrada permanente no cabeçalho. |
| 2026-09-12 | Acrescenta consulta operacional com totais completos, RBAC por categoria e destinos exatos; sem novo agregado ou tabela. |
| 2026-09-28 | Corrige destinos da busca global para abrir diretamente clientes, veículos, documentos, contas e movimentações encontrados. |
