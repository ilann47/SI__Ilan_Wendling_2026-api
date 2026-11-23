> Links: [[core]] · [[consultas]] · [[rbac]]

# Navegação

## Objetivo

Dar acesso previsível às áreas funcionais sem exigir conhecimento da estrutura interna dos módulos.

## Contexto

Os destinos continuam filtrados pelas permissões efetivas do usuário. A organização visual não altera rotas, contratos ou autorização.

## Fluxo (camadas da arquitetura)

O layout filtra os itens do registry de navegação pelo [[rbac]], identifica a área da rota atual e mantém essa seção aberta. O usuário pode recolher seções ou compactar a lateral no desktop.

## Endpoints

Não há endpoint próprio. A navegação usa as permissões do contexto autenticado e a [[consultas|busca global]].

## Estrutura de Dados (DTOs, Entidades)

- `NavGroup`: uma das seis áreas principais.
- `NavItem`: rótulo, rota, ícone e permissões alternativas.

## Integrações externas

Não existem.

## Tratamento de Erros

Itens sem permissão são omitidos. Falha de troca de organização mantém o contexto atual e é apresentada pelo snackbar global.

## Testes (curl ou equivalente)

Os testes verificam a preservação das rotas, permissões, seis áreas principais, acessibilidade e recolhimento das seções.

## Decisões Técnicas

- Seis áreas: Operação, Comercial, Fiscal e financeiro, Clientes e parceiros, Produtos e estoque e Administração.
- A rota ativa abre sua área automaticamente.
- A lateral usa 248 px aberta e 72 px compacta; no celular permanece um drawer completo.

## Módulos relacionados

[[core]], [[consultas]] e [[rbac]].

## Histórico

| Data | Ação |
|---|---|
| 2026-08-17 | Reduz a navegação de doze para seis áreas e adiciona seções e lateral recolhíveis. |
