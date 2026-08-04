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
5. Migrar formas e condições de pagamento como primeira dependência empresarial. **Concluído na V38.**
6. Migrar clientes, logística e fornecedores. **Cliente V40, Transportadora V41,
   Fornecedor V42, hardenings V44/V47, frota logística V48 e owner de
   Transportadora V49 concluídos.**
7. Migrar RH incrementalmente. **Cargo V43, Funcionário V45 e hardening de ownership V46 concluídos; vínculo laboral canônico permanece pendente.**
8. Prosseguir pela ordem registrada em `22-unificacao-multitenant.md`.
9. Migrar catálogo de conveniência. **Categoria, Marca e Unidade de Medida
   concluídas em V50/V51; Produto e Serviço concluídos na V52. Consumers fiscais,
   ProdutoFornecedor concluído na V53; consumers fiscais e relatório permanecem
   globais e limitados ao histórico.**
10. Introduzir estoque tenant-aware. **Local, saldo, razão, ajuste, compensação,
    limiar agregado, auditoria, outbox, idempotência e hardenings transacionais
    de cadeia/concorrência concluídos na V54.**
11. Migrar operação tradicional em fatias dependentes. **Veículo concluído na
    V58; Tarifa, Mensalista e Movimentação permanecem nas próximas fatias.**

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
- Backfill futuro somente por UC732 transacional: grafo bloqueado, simulação de
  conflitos, ator/motivo/lote auditados e invariantes restabelecidas antes do commit.
- Rollout de retrofit coordenado e sem overlap: backup, bloqueio de escrita,
  drenagem de HTTP/workers, Flyway/validate, subida do binário novo e smoke antes
  da reabertura; não reiniciar binário antigo após dados tenant-aware.
- Nenhuma migration aplicada ao banco local com checksum divergente.
- Rollout V48/V49 sem overlap; backfill somente pelo procedimento UC732.
- V50, V51, V52, V53 e V54 não são publicáveis isoladamente; o rollout backend deve ser
  coordenado antes
  da ativação do frontend tenant-aware do catálogo.
- Nenhuma remoção de rota ou menu como substituto de autorização.
- Nenhuma criação de Ordem de Compra/Serviço antes do respectivo contrato de domínio.
- O arquivo local `Nio2WebServerCustomizer.java` permanece fora de todos os commits.
- V55 conclui Nota de Entrada, integração de recebimento no razão e Conta a Pagar tenant-aware.
- V56 conclui Nota de Saída, baixa no razão e Conta a Receber tenant-aware.
- V57 conclui Nota de Serviço e faturamento tenant-aware.
- V58 inicia a operação tenant-aware com Veículo, mantendo consumidores operacionais legados restritos ao histórico.
