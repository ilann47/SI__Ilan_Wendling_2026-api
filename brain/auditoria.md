> Links: [[core]] · [[organizacoes]] · [[membership]] · [[rbac]] · [[eventos]] · [[pedidos]] · [[credenciais]]

# Auditoria

## Objetivo

Preservar uma trilha imutável e correlacionável das mutações enterprise, capaz de
identificar o que mudou, quem iniciou a operação, em qual tenant e requisição.

## Contexto

A História 4 instrumenta Organização, Membership e RBAC. Instalações, eventos e
demais módulos aderem ao mesmo serviço quando forem implementados. A auditoria é
separada de logs operacionais e não contém senhas, tokens, documento empresarial
ou serialização genérica de entidades.

## Fluxo (camadas da arquitetura)

```text
Caso de uso transacional -> snapshot explícito antes/depois -> AuditoriaService
ContextoAuditoriaProvider -> principal + request + MDC
RegistroAuditoriaRepository -> PostgreSQL V12 -> trigger append-only
```

Uma falha ao persistir a auditoria reverte a mutação de negócio na mesma transação.

## Endpoints (se houver)

Nenhum endpoint público foi criado. A baseline não fixa ainda o contrato de
consulta/exportação; essa superfície será adicionada com `audit:read`/`audit:export`.

## Estrutura de Dados (DTOs, Entidades)

`RegistroAuditoria` armazena organização, ação, tipo/ID lógico do recurso,
ator/tipo, origem, evento/pátio/dispositivo opcionais, IP, trace ID, motivo,
estados JSONB redigidos, versões anterior/posterior, classe de retenção e instante UTC.

## Integrações externas (se houver)

Nenhuma. A primeira implementação é síncrona e usa somente PostgreSQL.

## Tratamento de Erros

A auditoria exige uma transação ativa. Violações de estrutura ou persistência
falham o caso de uso. UPDATE e DELETE são recusados por trigger no banco.

## Testes (curl ou equivalente)

Testes cobrem ator humano, tenant, origem, IP, trace, snapshots redigidos,
antes/depois, versões, motivo, ausência de registro em mutação rejeitada e
imutabilidade física no PostgreSQL.

## Decisões Técnicas

- Persistência append-only; sem update/delete pela entidade e com trigger defensivo.
- Snapshots são allowlists montadas pelo módulo produtor, nunca serialização genérica.
- Classes de retenção são gravadas, mas prazos e expurgo aguardam definição legal.
- Chamadas via API usam o principal validado; execução sem request é classificada como sistema.
- `X-Trace-Id` aceito possui formato seguro e até 100 caracteres; valores inválidos são substituídos.
- Não há broker, AOP ou endpoint de consulta nesta fundação.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- [[membership]]
- [[rbac]]
- [[instalacoes]] — publica `PATIO_CRIADO` com snapshot explícito de capacidade.
- [[venues]] — publica `UNIDADE_CRIADA` com snapshot explícito de localização.
- [[eventos]] — registra `EVENTO_CRIADO` com referência direta ao evento.
- [[pedidos]] — registra criação, expiração e confirmação manual com evidência,
  ator, valores e estados explícitos.

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-29 | Implementa fundação transacional append-only e instrumenta os módulos enterprise existentes. |
| 2026-07-29 | Instalações passa a registrar criação de pátio na mesma transação. |
| 2026-07-29 | Venue passa a registrar criação de unidade na mesma transação. |
| 2026-07-30 | Evento passa a registrar criação, versão e estado inicial na mesma transação. |
| 2026-07-30 | Produto de estacionamento passa a auditar criação de lote e publicação. |
| 2026-07-30 | Evento passa a auditar a publicação e suas versões inicial/final. |
| 2026-07-30 | Evento passa a auditar a abertura imediata de vendas e sua versão. |
| 2026-07-30 | Reserva de inventário passa a auditar criação, expiração e liberação. |
| 2026-07-30 | Pedido passa a auditar criação e expiração financeira. |
| 2026-07-30 | Confirmação manual registra evidência conciliável e transições de pedido/inventário. |
| 2026-07-30 | Emissão de credencial e atendimento integral do pedido são auditados. |
| 2026-07-30 | Geração QR registra somente metadados seguros, sem token ou hash. |
