> Links: [[core]] · [[eventos]] · [[pedidos]] · [[credenciais]] · [[acesso]] · [[outbox]]

# Bloqueios

## Objetivo

Manter no grafo do projeto a referência operacional para histórias que não
podem ser concluídas sem provider, infraestrutura ou decisão de negócio.

## Contexto

A matriz detalhada está em
[[../docs/especificacao/bloqueios-externos|Registro Central de Bloqueios]]. Este
arquivo não redefine contratos nem transforma capacidade TARGET em AS-IS.

## Fluxo (camadas da arquitetura)

```text
história pendente -> evidência no código/documentação -> classificação
  -> condição objetiva de retomada -> aviso informativo no frontend
```

## Endpoints (se houver)

Nenhum. O frontend deve consumir um catálogo estático versionado enquanto não
existir endpoint administrativo próprio; o catálogo não contém dados de negócio
mutáveis nem credenciais.

## Estrutura de Dados (DTOs, Entidades)

Cada bloqueio possui ID, história/UC, status, tipo, dependência, parte concluída,
parte pendente, condição de retomada e impacto. Não existe entidade persistente.

## Integrações externas (se houver)

As integrações listadas são dependências ausentes; nenhuma é chamada.

## Tratamento de Erros

Uma funcionalidade bloqueada não gera requisição. A interface apresenta aviso
informativo e nunca simula êxito.

## Testes (curl ou equivalente)

Validar links, status permitidos e ausência de ações executáveis associadas aos
bloqueios no frontend.

## Decisões Técnicas

- A fonte central fica na especificação para revisão de produto e arquitetura.
- O `brain` mantém somente o vínculo e o comportamento esperado da interface.
- Segredos e nomes de credenciais reais nunca entram na documentação.

## Módulos relacionados

- [[core]]
- [[eventos]]
- [[pedidos]]
- [[credenciais]]
- [[acesso]]
- [[outbox]]

## Histórico

| Data | Ação |
|---|---|
| 2026-08-02 | Cria referência central dos bloqueios da fase de eventos. |
