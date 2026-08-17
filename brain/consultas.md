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

## Estrutura de Dados (DTOs, Entidades)

- `BuscaGlobalResponse`: termo normalizado, total e grupos.
- `GrupoBuscaGlobal`: nome funcional e resultados permitidos.
- `ResultadoBuscaGlobal`: tipo, id, título, subtítulo, estado e caminho da tela.
- Não há entidade ou tabela própria; o módulo usa projeções somente leitura.

## Integrações externas

Não existem. A primeira versão usa PostgreSQL e os dados transacionais existentes.

## Tratamento de Erros

- JWT sem tenant válido: `401`.
- Termo fora de 2 a 100 caracteres ou limite fora de 1 a 10: `400`.
- Módulos sem permissão não são consultados nem retornados.

## Testes (curl ou equivalente)

O teste de integração prova autenticação contextual, validação do termo e isolamento de clientes homônimos entre organizações. O frontend prova abertura por `Ctrl+K`, debounce e renderização agrupada.

## Decisões Técnicas

- O tenant vem exclusivamente do principal autenticado; não há header selecionável.
- Consultas usam projeções diretas para evitar carregar agregados completos.
- Um índice de busca dedicado não foi introduzido nesta versão; poderá ser avaliado com métricas reais.

## Módulos relacionados

[[membership]], [[rbac]], [[clientes]], [[operacao]], [[pedidos]], [[eventos]] e [[fiscal-financeiro]].

## Histórico

| Data | Ação |
|---|---|
| 2026-08-17 | Implementa busca global tenant-aware e entrada permanente no cabeçalho. |
