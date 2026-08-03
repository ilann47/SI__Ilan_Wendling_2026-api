> Links: [[../brain/core]] · [[../brain/unificacao-multitenant]] · [[../docs/especificacao/22-unificacao-multitenant]] · [[../docs/especificacao/21-status-implementacao]]

# Onda 0 — Fundação da unificação multi-tenant

## Objetivo

Preparar a migração incremental dos módulos empresariais existentes para o
contexto organizacional já utilizado pelo domínio de eventos, sem associar dados
históricos a um tenant arbitrário e sem interromper as APIs atuais.

## Estado inicial comprovado

- `Usuario`, geografia e catálogos estruturais de RBAC são globais.
- Eventos, instalações enterprise, inventário, pedidos, credenciais e acesso já
  possuem `organizacao_id` e RBAC contextual.
- Cadastros, conveniência, operação tradicional, fiscal, financeiro, logística e
  RH permanecem globais nas entidades, repositories, constraints e controllers.
- `patio` já contém `organizacao_id` opcional e dois mapeamentos JPA temporários.
- Ordem de Compra e Ordem de Serviço não existem no código atual.

## Entregas atômicas

1. Documentar ownership AS-IS/TARGET, dependências e ordem de migração.
2. Corrigir a documentação que posterga os módulos permanentes.
3. Introduzir permissões contextuais dos domínios empresariais sem concedê-las
   implicitamente a papéis operacionais.
4. Criar o padrão de teste de isolamento reutilizável. **Concluído em 2026-08-03.**
5. Migrar formas e condições de pagamento como primeira dependência empresarial.
6. Migrar clientes e fornecedores.
7. Prosseguir pela ordem registrada em `22-unificacao-multitenant.md`.

## Verificação por entrega

- migration aplica de V37 para a nova versão em banco PostgreSQL limpo e existente;
- Hibernate `validate` inicia sem divergência;
- token global não acessa o recorte tenant-aware;
- tenant A não lê, altera ou referencia dados do tenant B;
- payload não aceita `organizationId`;
- banco rejeita foreign key cruzada;
- dados históricos sem tenant permanecem intactos;
- OpenAPI, `brain` e frontend são atualizados quando o contrato muda;
- suíte anterior e novos testes permanecem verdes.

## Riscos controlados

- Nenhum backfill automático de ownership.
- Nenhuma migration aplicada ao banco local com checksum divergente.
- Nenhuma remoção de rota ou menu como substituto de autorização.
- Nenhuma criação de Ordem de Compra/Serviço antes do respectivo contrato de domínio.
- O arquivo local `Nio2WebServerCustomizer.java` permanece fora de todos os commits.
