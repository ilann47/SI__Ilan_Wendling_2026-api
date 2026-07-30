> Links: [[organizacoes]] · [[membership]] · [[rbac]] · [[auditoria]] · [[instalacoes]]

# Core do Estacionamento Kaneko

## Fonte oficial

A arquitetura normativa permanece em [docs/especificacao](../docs/especificacao/README.md), especialmente nos documentos 11 a 20. O `brain` registra somente o estado implementado e as relações entre módulos; ele não redefine a arquitetura aprovada.

## Arquitetura

- Monólito modular em Spring Boot 4 e Java 25.
- Organização package-by-feature.
- PostgreSQL 17 com migrations Flyway incrementais.
- Hibernate configurado com `ddl-auto=validate`.
- APIs TARGET sob `/api/v1`, preservando as rotas legadas `/api`.
- Problem Details e `X-Trace-Id` como contrato de erros e correlação.

## Infraestrutura

- Banco relacional: PostgreSQL.
- Persistência: Spring Data JPA/Hibernate.
- Segurança atual: JWT HS256, RBAC contextual nas APIs `/api/v1` implementadas e perfis legados nas APIs `/api`.
- Documentação de API: springdoc OpenAPI.
- Testes de integração contra banco PostgreSQL separado.

## Padrões globais

- Controllers chamam services, nunca repositories.
- A transação começa no application service.
- Cada módulo possui suas entidades, repositories, DTOs e endpoints.
- Regras críticas possuem defesa na aplicação e constraints no PostgreSQL.
- Funcionalidades enterprise são introduzidas incrementalmente e sem quebrar APIs legadas.

## Módulos implementados na evolução enterprise

- [[organizacoes]] — fundação do tenant empresarial.
- [[membership]] — vínculo da identidade global, descoberta e contexto de tenant.
- [[rbac]] — permissões efetivas por papel, Membership e escopo organizacional.
- [[auditoria]] — trilha transacional e imutável das mutações enterprise.
- [[instalacoes]] — topologia física tenant-aware e limites de capacidade.

## Histórico

| Data | Ação |
|---|---|
| 2026-07-29 | Inicialização do brain e registro da História 1 de Organizações. |
| 2026-07-29 | Registro da História 2 de Membership e JWT contextual. |
| 2026-07-29 | Registro da História 3 de RBAC contextual. |
| 2026-07-29 | Registro da História 4 de auditoria append-only. |
| 2026-07-29 | Registro da História 5 de Instalações e criação de pátio tenant-aware. |
