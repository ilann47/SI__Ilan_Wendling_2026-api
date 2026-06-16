# Estacionamento Kaneko — API

Sistema de gestão de um estacionamento com loja de conveniência. API REST
(Spring Boot 4 + Java 25 + PostgreSQL), com interface via **Swagger**. Projeto
acadêmico cuja documentação completa está em [`docs/especificacao/`](docs/especificacao/).

## Funcionalidades

- **Pátio** — entrada/saída de veículos (avulso e mensalista) com cálculo de
  tarifa (tolerância de cortesia, valor/hora com teto de diária).
- **Cadastros** — geografia (país/estado/cidade), clientes, fornecedores (com
  e-mails e telefones), RH (cargos/funcionários), formas e condições de
  pagamento (parceladas).
- **Conveniência** — categorias, marcas, unidades, serviços, produtos (estoque)
  e o vínculo produto-fornecedor.
- **Fiscal** — notas de **entrada** (compra: rateio de despesas, soma de estoque,
  geração de contas a pagar), de **saída** (venda: baixa de estoque com validação,
  geração de contas a receber) e de **serviço** (NFS-e com ISS).
- **Financeiro** — contas a pagar, a receber e despesas avulsas, com baixa
  parcial/total.
- **Segurança** — usuários (senha BCrypt) e autenticação JWT.
- **Observabilidade** — trace id de correlação (`X-Trace-Id`), erros padronizados
  (RFC 7807) e health check (actuator).

## Pré-requisitos

- **JDK 25**
- **PostgreSQL 17** acessível em `localhost:5433` (usuário `postgres`, senha `root`
  por padrão — configurável por variáveis de ambiente).
- Maven Wrapper incluso (`mvnw` / `mvnw.cmd`) — não é preciso instalar o Maven.

## Configuração do banco

Crie os bancos de desenvolvimento e de testes:

```sql
CREATE DATABASE estacionamento_kaneko;
CREATE DATABASE estacionamento_kaneko_test;
```

O schema é versionado e aplicado automaticamente pelo **Flyway** na subida
(`src/main/resources/db/migration/V1__init_schema.sql`). O Hibernate roda em
`ddl-auto=validate` — apenas confere o mapeamento contra o schema, nunca o altera.

## Variáveis de ambiente

Todas têm um valor padrão de desenvolvimento (nada é obrigatório para rodar local):

| Variável               | Padrão                          | Descrição                                   |
|------------------------|---------------------------------|---------------------------------------------|
| `DB_HOST`              | `localhost`                     | Host do PostgreSQL                          |
| `DB_PORT`              | `5433`                          | Porta do PostgreSQL                         |
| `DB_NAME`              | `estacionamento_kaneko`         | Banco de desenvolvimento                    |
| `DB_USERNAME`          | `postgres`                      | Usuário do banco                            |
| `DB_PASSWORD`          | `root`                          | Senha do banco                              |
| `SERVER_PORT`          | `8086`                          | Porta HTTP da aplicação                     |
| `APP_SECURITY_ENABLED` | `true`                          | Exige JWT em `/api/**`                       |
| `APP_JWT_SECRET`       | *(chave de dev — troque!)*      | Segredo HMAC do JWT (≥ 32 bytes em produção) |
| `APP_JWT_EXP_MIN`      | `480`                           | Validade do token, em minutos               |

## Como executar

```bash
./mvnw spring-boot:run          # Linux/macOS
mvnw.cmd spring-boot:run        # Windows
```

A API sobe em `http://localhost:8086`.

- **Swagger UI:** http://localhost:8086/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8086/v3/api-docs
- **Health check:** http://localhost:8086/actuator/health

## Autenticação

Com a segurança ligada, os endpoints `/api/**` exigem um token JWT (exceto
`/api/auth/**`, o Swagger e o actuator).

1. Cadastre um usuário (`POST /api/usuarios`):

   ```json
   { "nome": "Administrador", "login": "admin", "senha": "segredo123", "perfil": "ADMIN" }
   ```

2. Faça login (`POST /api/auth/login`) e copie o `token`:

   ```json
   { "login": "admin", "senha": "segredo123" }
   ```

3. Envie o token nas demais chamadas: `Authorization: Bearer <token>` (ou use o
   botão **Authorize** no Swagger).

> Para explorar a API sem token em ambiente local, suba com
> `APP_SECURITY_ENABLED=false`.

## Testes

Os testes de integração usam o banco `estacionamento_kaneko_test` (Flyway aplica o
schema; a cada classe a transação sofre rollback):

```bash
./mvnw test
```

São 252 testes cobrindo todos os fluxos de cada módulo (criar, listar, buscar,
404, atualizar, excluir, e os conflitos/validações 409/400/401), além dos
processos do pátio, fiscais e financeiros.

## Arquitetura

Organização **por funcionalidade** (package-by-feature), sob
`com.kaneko.estacionamento`:

```
common/          BaseEntity/AuditableEntity, exceções, ProblemDetail, segurança (JWT), trace id, OpenAPI
geografia/       país, estado, cidade
pessoas/         cliente
pagamento/       forma e condição de pagamento (parcelas)
rh/              cargo, funcionário
fornecedor/      fornecedor + e-mails/telefones
patio/           veículo, tarifa, mensalista, movimentação (cálculo de tarifa)
conveniencia/    categoria, marca, unidade, serviço, produto, produto-fornecedor
fiscal/          notas de entrada, saída e serviço (+ itens)
financeiro/      contas a pagar, a receber e avulsas
usuario/         cadastro de usuários
```

Cada módulo segue a fatia `Controller → Service → Repository → Entity`, com DTOs
(`record` + Bean Validation) na borda e respostas de erro padronizadas em
`ProblemDetail` (RFC 7807).

## Documentação

A especificação acadêmica completa (introdução, análise de negócio, DER,
requisitos, casos de uso, dicionário de dados, diagramas UML/sequência e
requisitos não funcionais) está em [`docs/especificacao/`](docs/especificacao/).
