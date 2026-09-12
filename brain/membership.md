> Links: [[core]] · [[organizacoes]] · [[rbac]]

# Membership

## Objetivo

Vincular temporalmente um `Usuario` global a uma [[organizacoes|Organizacao]] e
resolver o tenant autenticado sem confiar em contexto enviado pelo cliente.

## Contexto

A Membership nasce em `ATIVO`, pode alternar entre `ATIVO` e `SUSPENSO` e termina
em `ENCERRADO`. Um encerramento preserva o histórico e permite um novo vínculo;
o registro anterior nunca é reaberto. O perfil global `ADMIN/OPERADOR/USUARIO`
continua vindo do `Usuario`; APIs tenant-aware usam [[rbac]], e [[contas]] cria
novas identidades sempre como `USUARIO` sem acesso organizacional implicito.

## Fluxo (camadas da arquitetura)

```text
MembershipController -> MembershipService -> MembroOrganizacaoRepository -> PostgreSQL V9
MeController -> ContextoAutenticadoService -> projeção Membership + Organizacao
MeController -> OrganizacaoSelfServiceService -> OrganizacaoService + MembershipService
JwtAuthFilter -> JwtService -> Usuario atual + Membership/version + Organizacao ativa
```

## Endpoints (se houver)

| Método | Rota | Comportamento |
|---|---|---|
| POST | `/api/v1/organizations/{organizationId}/memberships` | Cria vínculo ativo para usuário ativo em organização ativa. |
| GET | `/api/v1/organizations/{organizationId}/memberships/{membershipId}` | Consulta um vínculo no escopo administrativo informado. |
| PATCH | `/api/v1/organizations/{organizationId}/memberships/{membershipId}` | Altera somente status com `If-Match`. |
| GET | `/api/v1/me` | Retorna identidade atual com `nome` e `perfil` global confiáveis e, quando presente, tenant validado; mantém aliases legados durante a compatibilidade. |
| GET | `/api/v1/me/organizations` | Lista por projeção somente vínculos e organizações ativos. |
| POST | `/api/v1/me/organizations` | Conta global `USUARIO` cria organização e Membership administrativa própria atomicamente, sem ativação automática. |
| POST | `/api/v1/me/active-organization` | Recebe somente `organizationId` e emite novo JWT contextual. |

## Estrutura de Dados (DTOs, Entidades)

`MembroOrganizacao` contém IDs imutáveis de organização e usuário, estado,
origem administrativa, datas de entrada/suspensão/encerramento, timestamps e
`@Version`. O JWT contextual carrega `org_id`, `membership_id` e
`membership_version`; nenhuma dessas claims é aceita no payload de seleção.

## Integrações externas (se houver)

Não existem integrações externas. A assinatura JWT usa HMAC-SHA256 local e a
validação de contexto consulta PostgreSQL a cada requisição contextual.

## Tratamento de Erros

- `400`: payload, status ou `If-Match` malformado.
- `401`: token ausente/inválido ou contexto cuja Membership, versão, usuário ou organização deixou de ser válido.
- `403`: perfil sem autorização; self-service aceita somente conta global `USUARIO`.
- `404`: vínculo/recurso invisível ou organização não acessível na seleção.
- `409`: documento empresarial ou vínculo corrente duplicado, usuário/organização inativos ou transição incompatível.
- `412`: versão divergente.
- `428`: PATCH sem `If-Match`.

## Testes (curl ou equivalente)

Os testes automatizados cobrem máquina de estados, constraints PostgreSQL,
ETag/If-Match, histórico encerrado, authority lida do banco, seleção server-side,
claims contextuais, invalidação por versão/estado/usuário e compatibilidade legada.

## Decisões Técnicas

- `Usuario` permanece global e nenhum dado legado recebe tenant automaticamente.
- A unicidade corrente é um índice parcial; registros encerrados não são apagados.
- `membership_version` é exatamente o `@Version` da entidade.
- A troca de tenant sempre emite outro JWT; headers não selecionam organização.
- Criar uma organização não altera o JWT atual: a seleção continua explícita por
  `/api/v1/me/active-organization`, inclusive quando existe apenas uma opção.
- O self-service é uma transação única: organização, Membership inicial e papel
  `ADMIN_ORGANIZACAO` são confirmados juntos ou integralmente revertidos.
- Uma conta `USUARIO` pode criar organizações adicionais mesmo enquanto usa um
  JWT contextual; o perfil global não é promovido.
- Tokens legados continuam válidos nas rotas compatíveis, mas não contêm tenant.
- A validade permanece em 480 minutos; access/refresh tokens curtos são evolução futura.
- O primeiro vínculo recebe `ADMIN_ORGANIZACAO`; os demais exigem `users:invite`.

## Módulos relacionados

- [[core]]
- [[organizacoes]]
- Usuários — identidade global e authority legada.
- [[rbac]] — controla administração, concessões e revogações no tenant.
- [[auditoria]] — registra criação e transições de estado do vínculo.

## Histórico (data + ação)

| Data | Ação |
|---|---|
| 2026-07-29 | Implementa vínculo temporal, descoberta de organizações e JWT contextual. |
| 2026-07-29 | Administração do vínculo migra de `ROLE_ADMIN` global para permissões contextuais. |
| 2026-07-29 | Criação, suspensão, reativação e encerramento passam a gerar auditoria imutável. |
| 2026-09-10 | Adiciona criação self-service atômica e mantém seleção explícita do tenant. |
| 2026-09-10 | Estabiliza `nome` e `perfil` no contrato de `/api/v1/me` para JWT global e contextual, preservando aliases anteriores. |
