> Links: [[core]] · [[membership]] · [[organizacoes]] · [[rbac]]

# Contas

## Objetivo

Permitir que uma pessoa crie sua identidade global no Kaneko sem receber
privilegios administrativos ou acesso implicito a qualquer organizacao.

## Contexto

`Usuario` continua sendo a identidade autenticavel global. O autocadastro cria o
perfil neutro `USUARIO`; acesso a tenants sera concedido posteriormente por
[[membership]] e [[rbac]]. Os perfis legados `ADMIN` e `OPERADOR` permanecem
inalterados para compatibilidade das APIs existentes.

## Fluxo (camadas da arquitetura)

```text
POST /api/auth/register
  -> AuthController
  -> CadastroContaService
  -> UsuarioRepository
  -> PostgreSQL (V67)
  -> JwtService (token global sem claims de tenant)
```

## Endpoints (se houver)

| Metodo | Rota | Autenticacao | Comportamento |
|---|---|---|---|
| POST | `/api/auth/register` | Publico | Cria conta `USUARIO` ativa e retorna JWT global. |

## Estrutura de Dados (DTOs, Entidades)

`CadastroContaRequest` aceita exclusivamente `nome`, `login`, `email` e `senha`.
Login e e-mail sao normalizados em minusculas; a senha possui 8 a 72 caracteres
e e persistida somente como hash BCrypt. `CadastroContaResponse` nunca devolve
a senha e inclui o token global emitido apos a persistencia.

## Integracoes externas (se houver)

Nao existem nesta entrega. Verificacao de e-mail, convite e envio de mensagens
serao tratados em historias proprias, sem antecipacao neste endpoint.

## Tratamento de Erros

- `400`: campos invalidos, senha fora do limite ou campo nao permitido.
- `409`: login ou e-mail ja cadastrado, inclusive por corrida detectada pela
  constraint case-insensitive do PostgreSQL.

## Testes (curl ou equivalente)

`AuthControllerTest`, `SecurityEnabledTest` e
`UsuarioRepositoryConstraintTest` cobrem normalizacao, BCrypt, JWT sem tenant,
campos proibidos, `ProblemDetail.erros`, duplicidade, ausencia de privilegio
ADMIN e retorno vazio de `/api/v1/me/organizations` sem Membership.

`UsuarioControllerTest` usa ordenacao explicita `id,desc` nas verificacoes de
listagem e filtro: os usuarios da fixture aparecem na primeira pagina mesmo
quando o banco de testes contem registros de outras execucoes. As assercoes de
perfil e ausencia de senha permanecem, sem limpeza de dados ou mudanca na API.

## Decisoes Tecnicas

- O autocadastro nunca reutiliza o DTO administrativo de usuario.
- `USUARIO` e um perfil global neutro; permissoes empresariais nao sao inferidas.
- O fallback das APIs legadas aceita apenas `ADMIN`/`OPERADOR`; `USUARIO` pode
  entrar em `/api/v1`, onde cada operacao exige contexto e RBAC quando aplicavel.
- O endpoint emite JWT global para permitir o proximo passo do onboarding.
- As unicidades de login e e-mail continuam garantidas por indices funcionais
  `LOWER(...)`, alem da verificacao antecipada da aplicacao.

## Modulos relacionados

[[membership]] vincula a conta a uma [[organizacoes|Organizacao]]; [[rbac]]
define o acesso efetivo dentro desse vinculo.

## Historico

| Data | Acao |
|---|---|
| 2026-09-09 | Autocadastro global seguro, perfil neutro e JWT sem tenant. |
| 2026-09-12 | Estabiliza os testes de listagem de usuarios com ordenacao explicita para a validacao pre-commit. |
