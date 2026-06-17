# Laudo de Auditoria de Produção — Estacionamento Kaneko

> **Modo:** auditoria técnica independente. Sem suavização.
> **Data:** 2026-06-16 · **Versão auditada:** branch atual (backend + frontend)
> **Pergunta-mãe:** *"Eu colocaria este sistema em produção hoje, para uma empresa real com centenas de clientes?"* → **NÃO.** (justificativa na §9)

---

## 1. Metodologia

A auditoria combinou **duas frentes independentes**, para não depender só de leitura de código:

1. **revisão de código por 10 dimensões** (arquitetura, cadastros, fiscal mercadoria, fiscal serviço, relatórios, UX/UI, performance, banco, segurança, mercado), lendo o código real (`src/main/java`, `frontend/src`, `database/schema.sql`, `application.yml`).
2. **Testes de execução ao vivo** contra a API rodando (`:8086`), com token de admin real — *tentando quebrar o sistema*, não apenas lendo. As evidências de HTTP estão na §3 e corroboram (ou refutam) os achados estáticos.

**Nota global ponderada: 4.9 / 10** para o critério "ERP de produção". Para o critério "trabalho acadêmico" o sistema é **forte e aprovável** — a distância entre as duas notas é justamente o objeto deste laudo.

---

## 2. Notas por dimensão

| # | Dimensão | Nota | Veredito curto |
|---|----------|:----:|----------------|
| 1 | Arquitetura e Mapeamento | **6.5** | Camadas limpas, schema versionado; falha em autorização e contrato de paginação |
| 2 | Cadastros (qualidade de dado) | **4.0** | Estrutura boa, mas **nenhuma** validação de formato; unicidade furada |
| 3 | Nota de Entrada / Saída | **5.5** | Motor transacional sólido; irreversível, com race de estoque e desconto quebrado |
| 4 | Nota de Serviço (NFS-e) | **5.0** | ISS sobre bruto; "emitir" é só trocar status |
| 5 | Relatórios | **4.0** | Sem exportação; bug de regime de caixa; faltam os analíticos de ERP |
| 6 | UX/UI e operador novo | **5.5** | Engine elegante, mas operação de balcão é inviável (entrada exige pré-cadastro) |
| 7 | Performance | **5.5** | N+1 nas listas, paginação em memória, buscas sem índice |
| 8 | Banco de Dados | **7.0** | Modelagem madura; faltam CHECKs de composição e índices de busca |
| 9 | **Segurança** | **2.5** | **Pior dimensão.** Broken Access Control confirmado ao vivo |
| 10 | Comparação com ERP de mercado | **3.5** | Documentos fiscais simulados; sem multiempresa nem automação |

---

## 3. Evidências de execução ao vivo (o que eu *testei*, não só li)

Bateria de testes com token de admin contra a API em execução. Cada linha é um HTTP real.

| Teste | Entrada | Resultado | Leitura |
|-------|---------|:---------:|---------|
| **Escalada de privilégio** | OPERADOR autenticado faz `POST /api/usuarios` com `perfil:ADMIN` | **HTTP 201** | 🔴 **FALHA CRÍTICA** — operador cria admin |
| **Operador exclui dado** | OPERADOR faz `DELETE /api/clientes/{id}` | **HTTP 204** | 🔴 **FALHA** — operador apaga cadastro |
| CPF sem formato | `documento:"NAOEHUMCPF"` | **HTTP 201** | 🟠 não valida CPF/CNPJ |
| CEP/telefone lixo | `cep:"XXXX"`, `telefone:"abc"` | **HTTP 201** | 🟠 não valida formato |
| Swagger anônimo | `GET /v3/api-docs` sem token | **HTTP 200** | 🟡 spec da API pública |
| Crédito negativo | `limiteCredito:-50` | **HTTP 400** | ✅ rejeita (Bean Validation) |
| E-mail inválido | `email:"isso-nao-e-email"` | **HTTP 400** | ✅ rejeita |
| Documento duplicado | mesmo `documento` 2x | **HTTP 409** | ✅ unicidade ativa* |
| Tarifa negativa | `valorHora:-10` | **HTTP 400** | ✅ rejeita |
| Nota de saída sem itens | `itens:[]` | **HTTP 400** | ✅ rejeita |
| Baixa &gt; saldo | `valor:99999` em conta de 100 | **HTTP 409** | ✅ rejeita |
| Endpoint sem token | `GET /api/clientes` anônimo | **HTTP 401** | ✅ exige auth |

> *Unicidade só bloqueia quando a string é **idêntica** — com máscara diferente (`123.456.789-09` vs `12345678909`) ela **não** bloqueia (achado P-06). Não testei esse caso ao vivo, mas o código confirma (`ClienteService.java:63`).

**Conclusão da §3:** a camada de *Bean Validation* e o tratamento de erro (ProblemDetail / 400-409-404, **nenhum 500** nos testes) são **sólidos no que cobrem**. O buraco não é robustez — é **autorização** (qualquer logado faz tudo) e **validação de domínio** (formato de documento/placa/CEP).

---

## 4. Top problemas (priorizado, deduplicado entre as 10 dimensões)

### 🔴 CRÍTICOS — bloqueiam produção

| ID | Problema | Onde | Evidência |
|----|----------|------|-----------|
| P-01 | **Sem autorização por perfil**: `.anyRequest().authenticated()`, zero `@PreAuthorize`/`@EnableMethodSecurity`. OPERADOR cria/exclui usuários, cancela notas, baixa contas, altera tarifas. | `SecurityConfig.java:39-42` | **Testado: 201/204** |
| P-02 | **Mass-assignment de `perfil`**: o campo vem do corpo e é gravado direto → auto-promoção a ADMIN. | `UsuarioRequest.java:30`, `UsuarioService.java:87` | Testado (via P-01) |
| P-03 | **Segredo JWT default commitado** no `application.yml` + flag `APP_SECURITY_ENABLED=false` que abre todo `/api/**`. | `application.yml:47,49`; `SecurityConfig.java:47-49` | Leitura |
| P-04 | **admin/admin123** auto-criado, senha trivial pública, sem troca forçada. | `DataInitializer.java`; `application.yml:55-56` | Leitura |
| P-05 | **Nenhuma validação de formato** (CPF/CNPJ/CEP/telefone/placa) — backend, frontend e banco. | todos `*Request.java`; `FieldRenderer.tsx:138` | **Testado: 201** |
| P-06 | **Unicidade de documento furada por máscara** — duplica o mesmo CPF. | `ClienteService.java:63/75` | Leitura |
| P-07 | **Confirmação de nota irreversível sem estorno** — corrompe estoque e contas permanentemente; sem proteção contra dupla confirmação. | `NotaEntradaService.java:165-181`; `NotaSaidaService.java:169-185` | Leitura |
| P-08 | **Race condition de estoque** (sem `@Version`/lock) — overselling sob carga. | `NotaSaidaService.java:151-162`; `AuditableEntity.java` | Leitura |
| P-09 | **Documentos fiscais simulados** — sem XML/DANFE/SEFAZ/certificado; "emitir" só troca status. Não emite documento fiscal válido (risco legal). | `NotaServicoService.java:90-97`; `NotaSaidaService.java:146-166` | Leitura |
| P-10 | **`valor_total` de conta a pagar/receber sem CHECK** e o app grava o valor cru do cliente — saldo devedor corrompível. | `schema.sql` (conta_pagar/receber); services | Leitura |
| P-11 | **Entrada no pátio exige veículo pré-cadastrado em outra tela** — operação central inviável no balcão com fila; sem entrada rápida/avulsa. | `PatioPage` + fluxo de `/veiculos` | Leitura |
| P-12 | **ReferenceSelect carrega só 200 registros e filtra no cliente** — em produção o item não aparece e o operador duplica cadastro. | `ReferenceSelect.tsx` (size=200) | Leitura |

### 🟠 ALTOS

| ID | Problema | Onde |
|----|----------|------|
| P-13 | `percentualDesconto` decorativo: gravado e **nunca aplicado** ao total → divergência financeira silenciosa | `NotaEntradaService.java:118-120`; `NotaSaidaService.java:117-119` |
| P-14 | Item duplicado na nota fura a validação de estoque (só barrado pelo CHECK, vira 409 genérico) | `NotaSaidaService.java:151-162` |
| P-15 | **Placa sem validação/uppercase** — chave operacional do pátio aceita lixo e duplica | `VeiculoRequest.java:14` |
| P-16 | **Exportação inexistente** (PDF/Excel/impressão) — relatórios só vivem em tela | `RelatoriosPage.tsx` |
| P-17 | **Bug "Recebido" (regime de caixa)** errado para baixas parciais — KPI do dashboard mente | `RelatorioService.java` (dataRecebimento) |
| P-18 | Faltam relatórios analíticos de ERP: fluxo de caixa, DRE, inadimplência (aging), ticket médio | `RelatorioService` |
| P-19 | **N+1 nas listagens**: `@EntityGraph` só no `findById`, não no `findAll(Specification)` | repositórios de cadastro |
| P-20 | **Paginação em memória** (HHH000104) nas Notas: `@EntityGraph` com coleção + `Pageable` | repositórios fiscais |
| P-21 | Ordenação por clique no cabeçalho **não é server-side** — ordena só a página atual (engana) | `CrudResourcePage.tsx` |
| P-22 | Saída no pátio **cobra sem confirmação** e sem mostrar o valor antes | `PatioPage` |
| P-23 | Formulários gigantes (22-26 campos) em Dialog sem seções; **clique fora descarta tudo** | `ResourceFormDialog.tsx` |
| P-24 | `clean()` remove campos vazios no PUT — **apagar um valor na edição não salva a remoção** | `CrudResourcePage`/`client.ts` |
| P-25 | Menu com 35 itens **sem busca e sem filtro por perfil** — operador vê telas administrativas | `navigation.tsx` |
| P-26 | **Sem CORS e sem versionamento** (`/api` sem `/v1`) — engessa o deploy (só mesma origem) | ausente em `src/main/java` |
| P-27 | Login **sem rate limiting/lockout** + timing oracle de enumeração de usuário | `AuthController.java:36-47` |
| P-28 | Swagger / api-docs / Actuator liberados **sem autenticação** | `SecurityConfig.java:40-41` — **testado: 200** |
| P-29 | Buscas placa/documento/nome fazem **Seq Scan** (`LOWER LIKE '%x%'` sem índice trigram) | `FiltroSpecs.java:23-24` |
| P-30 | **Nenhum índice único** garante "uma única tarifa vigente" — ambiguidade de preço na saída | `schema.sql` (tarifa) |
| P-31 | **Empresa única** — sem multiempresa/multifilial para escalar a "centenas de clientes" | schema (sem tenant) |
| P-32 | Cobrança de mensalistas **manual**, sem recorrência/régua/automação | `MensalistaController.java:57-64` |
| P-33 | Financeiro sem tesouraria/DRE/fluxo de caixa/conciliação OFX/plano de contas | `RelatorioService` |
| P-34 | ISS sobre **valor bruto**, ignora desconto; sem desconto condicional/incondicional | `NotaServicoService.java` |
| P-35 | XOR de origem da NFS-e incompleto + origem **não vinculada ao cliente** da nota | `NotaServicoService.java` |
| P-36 | DatePickers de Relatórios em `row` fixo **estouram no mobile** | `RelatoriosPage.tsx` |

### 🟡 MÉDIOS

| ID | Problema | Onde |
|----|----------|------|
| P-37 | Tipo de veículo CARRO/MOTO **modelado mas ignorado** na cobrança (moto paga = carro) | `CalculoTarifa.java`; `MovimentacaoService.java:81` |
| P-38 | Endpoints expõem `Page` do Spring Data — contrato instável (warning no Boot 4) | todos `listar(...)` |
| P-39 | Erros do backend **não mapeiam no campo** do formulário (snackbar único) | `client.ts:53-65`; `CrudResourcePage.tsx:66` |
| P-40 | Senha **obrigatória na edição** de usuário, sem confirmação nem medidor de força | `UsuarioRequest.java:21`; `rhUsuario.tsx` |
| P-41 | Produto inativo **não validado** na criação da nota (compra/venda de item morto) | `NotaSaidaService.java` |
| P-42 | Rateio de frete pode **não fechar centavos** (sem ajuste de resíduo) | `NotaEntradaService.java:184-202` |
| P-43 | Cancelamento de NFS-e **sem prazo/motivo/efeito** fiscal (cancelamento fantasma) | `NotaServicoService.java` |
| P-44 | Form de serviço **sem auto-preenchimento** (não puxa `valorPadrao`, não infere cliente) | `fiscal.tsx` |
| P-45 | `produto_fornecedor` unique total **impede recriar vínculo** após inativar | `schema.sql:784` |
| P-46 | `situacao` do título pode **divergir** de `valor_pago` (sem CHECK de coerência) | `schema.sql` (conta_pagar/receber) |
| P-47 | `movimentacao` AVULSO sem garantia de tarifa aplicada (perde rastreabilidade do preço) | `schema.sql` (movimentacao) |
| P-48 | `data_recebimento` **sem índice**, mas é filtrado no relatório diário do dashboard | `schema.sql` |
| P-49 | Relatórios **sem paginação/virtualização** — listas completas em memória | `RelatorioService`/`RelatoriosPage` |
| P-50 | Competência divergente no `totalFaturado` (pátio usa `dataSaida`, vendas `dataEmissao`) | `RelatorioService.java` |

### ⚪ BAIXOS (anotados, não bloqueiam)
- `updated_at` nunca atualizado pelo banco (sem trigger `BEFORE UPDATE`).
- `show-sql=true` / `format_sql=true` no config padrão (ruído em prod).
- Token em `localStorage` (exfiltração via XSS) — preferir cookie HttpOnly.
- Acessibilidade: IconButtons sem `aria-label`, dois drawers anunciados em duplicidade (WCAG).
- Botão de saída do pátio `warning outlined` (contraste baixo na ação mais frequente).
- Dashboard/queries sem *skeleton* nem tratamento de erro de carregamento.
- Modelo fiscal default `"55"` reaproveitado na NFS-e (erro conceitual — NFS-e não é modelo 55).
- Autenticação não usa `AuthenticationManager`/`UserDetailsService` (dívida, reimplementa o framework).

---

## 5. Top melhorias (priorizadas por valor × esforço)

### Ganhos rápidos e altos (fazer primeiro)
1. **Ativar autorização**: `@EnableMethodSecurity` + `@PreAuthorize("hasRole('ADMIN')")` em usuários, exclusões, cancelamentos, baixas e tarifas; gate de UI por perfil. *(fecha P-01)*
2. **Travar `perfil`**: só ADMIN define/altera; para não-admin, forçar OPERADOR ou ignorar o campo. *(fecha P-02)*
3. **Falhar o boot** se `APP_JWT_SECRET` for o default/&lt;32 bytes; remover/blindar `APP_SECURITY_ENABLED=false` em prod. *(fecha P-03)*
4. **Senha admin** aleatória forte no bootstrap + flag "trocar no 1º login". *(fecha P-04)*
5. **Validador `@Documento`** (CPF/CNPJ com dígito verificador) + `@Pattern` de placa/CEP/UF; **normalizar para só-dígitos** antes de gravar/comparar. *(fecha P-05, P-06, P-15)*
6. **Proteger Swagger/Actuator** em prod (auth ou desligar; `/actuator/**` → ROLE_ADMIN). *(fecha P-28)*
7. **Export PDF/Excel** dos relatórios (Apache POI / `GET /api/relatorios/faturamento.xlsx`). *(fecha P-16)*
8. **Entrada rápida no pátio**: criar veículo + entrada na mesma tela (avulso de primeira vez). *(fecha P-11)*
9. **Confirmação de saída** mostrando o valor calculado **antes** de cobrar. *(fecha P-22)*

### Estruturais (médio prazo)
10. **`@Version` em `Produto`** (optimistic lock) + tratar `OptimisticLockException`→409; consolidar demanda por produto antes de validar estoque. *(fecha P-08, P-14)*
11. **Operação de estorno** de nota confirmada (devolve estoque, cancela títulos pendentes, marca CANCELADA). *(fecha P-07)*
12. **Corrigir o desconto percentual** no cálculo do item (derivar do %, ou proibir os dois campos juntos). *(fecha P-13)*
13. **`@EntityGraph` no `findAll(Specification)`** + reescrever paginação das notas (sem coleção paginada em memória). *(fecha P-19, P-20)*
14. **Índices de busca**: GIN trigram (`pg_trgm`) em `LOWER(placa/documento/nome)`; índice em `data_recebimento`. *(fecha P-29, P-48)*
15. **CHECKs de composição** (V2) em `conta_pagar`/`conta_receber` e índice único parcial de tarifa vigente. *(fecha P-10, P-30, P-46)*
16. **ReferenceSelect com busca server-side** (autocomplete que consulta a API por termo). *(fecha P-12)*
17. **Ordenação e busca server-side** no DataGrid (`sortingMode='server'`). *(fecha P-21)*
18. **Mapear erro do backend no campo** (`setError`) + `mode:'onBlur'` + máscaras no front. *(fecha P-39, P-05)*
19. **Relatórios analíticos**: fluxo de caixa (realizado+projetado), DRE, aging de inadimplência, ticket médio. *(fecha P-18, P-33)*
20. **Cobrança recorrente de mensalistas** (`@Scheduled` mensal idempotente + PIX/boleto). *(fecha P-32)*

### Plataforma (longo prazo)
21. **Emissão fiscal real**: integrar provedor (PlugNotas/Focus NFe/NFe.io) — XML, certificado, SEFAZ/prefeitura, chave, protocolo, DANFE, cancelamento. *(fecha P-09, P-34, P-35, P-43)*
22. **Importação de XML de NF-e** de compra → gera nota de entrada/estoque. *(reduz erro de digitação)*
23. **Multiempresa/multifilial** (`tenant_id` NOT NULL + filtro por tenant). *(fecha P-31)*
24. **CORS explícito + versionamento `/api/v1`** + envelope de paginação estável. *(fecha P-26, P-38)*
25. **Rate limiting/lockout** no login + cookie HttpOnly + CSP/HSTS. *(fecha P-27, baixos de segurança)*

---

## 6. Roadmap executivo

### Curto prazo (1–2 semanas) — "tampar os buracos que sangram"
Foco em **segurança e integridade de dado**, que é o que impede qualquer piloto:
`@EnableMethodSecurity` + `@PreAuthorize` (P-01) · travar `perfil` (P-02) · segredo/flag de boot (P-03) · senha admin forçada (P-04) · validador de CPF/CNPJ/placa + normalização (P-05/06/15) · proteger Swagger/Actuator (P-28) · corrigir desconto percentual (P-13) · bug do "Recebido" parcial (P-17) · confirmação de saída no pátio (P-22).

### Médio prazo (1–3 meses) — "ficar utilizável por cliente real"
`@Version` + estorno de nota (P-07/08/14) · export PDF/Excel (P-16) · entrada rápida no pátio (P-11) · ReferenceSelect/ordenção server-side (P-12/21) · índices trigram + N+1 (P-19/20/29) · CHECKs de composição V2 (P-10/30/46) · relatórios analíticos (P-18/33) · UX dos formulários (P-23/24/25/39) · cobrança recorrente de mensalista (P-32).

### Longo prazo (3–6 meses) — "virar ERP de mercado"
Emissão fiscal real com SEFAZ/prefeitura (P-09) · importação de XML (P-22 melhoria) · multiempresa (P-31) · CORS/versionamento/contrato de paginação (P-26/38) · hardening de auth (rate limit, cookie HttpOnly, CSP) · tesouraria/conciliação OFX/plano de contas · hardware de pátio (totem/cancela/LPR) e PDV de conveniência.

---

## 7. Comparação com ERP de mercado (Omie, Bling, Tiny, Conta Azul, Sankhya, Protheus)

| Capacidade | Mercado | Kaneko |
|------------|:-------:|:------:|
| Emissão fiscal real (NF-e/NFS-e SEFAZ) | ✅ | ❌ simulado |
| Importação de XML de compra | ✅ | ❌ |
| Permissão granular por módulo/ação | ✅ | ❌ (qualquer logado faz tudo) |
| Multiempresa/multifilial | ✅ | ❌ empresa única |
| Cobrança recorrente automática | ✅ | ❌ manual |
| Fluxo de caixa / DRE / conciliação | ✅ | ❌ só soma do período |
| Exportação de relatórios (PDF/Excel) | ✅ | ❌ só tela |
| CRUD completo + estoque + financeiro básico | ✅ | ✅ |
| Vertical de estacionamento (pátio/tarifa/mensalista) | parcial | ✅ (diferencial) |

O **núcleo operacional do estacionamento** (pátio, tarifa, mensalista) é o ponto onde o Kaneko tem identidade própria e está bem modelado. Tudo que é "ERP genérico" (fiscal, financeiro analítico, multiempresa, permissões) está **abaixo da linha de mercado**.

---

## 8. O que está genuinamente bom (para não ser injusto)

- **Arquitetura por feature** limpa e consistente nos 28 controllers (Controller fino → Service transacional → Repository → DTO).
- **Schema maduro**: Flyway, `ddl-auto=validate`, `TIMESTAMPTZ`, índices em todas as FKs, índices parciais para regras de negócio, CHECKs extensos.
- **Tratamento de erro RFC 7807** com `traceId` correlacionável; nos testes de execução **nenhum 500** indevido — tudo virou 400/404/409.
- **Motor fiscal transacional** (totais, rateio proporcional, parcelas com resíduo na última) bem feito para o escopo.
- **Frontend config-driven** (registry declarativo) reaproveitável — adicionar um CRUD é barato.
- **252+ testes** cobrindo os fluxos de cada módulo. *(observação: o frontend não tem testes automatizados.)*

---

## 9. Veredito final — "colocaria em produção hoje?"

**NÃO**, para uma empresa real com centenas de clientes — e o motivo é **objetivo, não estético**:

1. **Segurança quebrada e comprovada ao vivo** (P-01/02): um caixa (OPERADOR) cria um usuário ADMIN e apaga cadastros. É *Broken Access Control* (OWASP A01) demonstrável em um HTTP. Sozinho, isso reprova.
2. **Segredo e bypass de produção** (P-03/04): chave JWT pública no repo + flag que desliga toda a auth + admin/admin123. Um deploy distraído expõe o ERP inteiro.
3. **Não emite documento fiscal válido** (P-09): "nota" é troca de status, sem SEFAZ/XML/DANFE. Uma empresa que "emite" aqui está sem respaldo fiscal — risco legal.
4. **Integridade de dado frágil** (P-05/06/07/08/10): aceita CPF/placa lixo, duplica documento por máscara, confirmação irreversível sem estorno, race de estoque, `valor_total` corrompível.

**Porém**, como **trabalho acadêmico** (que é o escopo declarado do projeto), o sistema é **forte e aprovável**: cobre o domínio fim-a-fim, tem schema e arquitetura de bom nível, testes amplos e um frontend moderno. As fragilidades acima são exatamente as **lições de "produção real"** que um projeto de prática profissional deve expor — e estão todas mapeadas, com correção conhecida, neste laudo.

**Caminho mínimo para um piloto controlado:** executar o **Curto prazo (§6)** — após ele, o sistema deixaria de ter falhas que sangram (autorização, segredo, validação) e poderia rodar num piloto fechado, ainda sem capacidade fiscal real.

---

> *Laudo gerado por revisão técnica de código (10 dimensões) + testes de execução ao vivo. Os números de linha referenciam a versão auditada em 2026-06-16. Schema marcado como CONGELADO — as correções de banco (CHECKs/índices) estão propostas como migration **V2**, não aplicadas.*
