> Links: [[core]] · [[operacao]] · [[eventos]] · [[rbac]] · [[auditoria]] · [[fiscal-financeiro]] · [[bloqueios]]

# Prontidão e recuperação operacional

## Objetivo

Impedir inicialização evidentemente insegura em produção e tornar verificável
a restauração local, sem converter uma demonstração em integração bancária/fiscal.

## Contexto

A prévia 5174 e a API 18087 usam exclusivamente dados de teste. O perfil padrão
e o perfil `test` mantêm o comportamento anterior. Só `prod`/`production`
ativam a nova configuração restritiva.

## Fluxo (camadas da arquitetura)

`ConfigData` carrega as propriedades → `ProductionSafetyEnvironment` verifica
antes da criação dos beans → somente configuração válida permite seguir para
datasource/Hibernate. O registro usa a interface nativa de Spring Boot 4 em
`META-INF/spring.factories`.

Os perfis de produção não admitem `test`, `dev`, `demo` ou `local` simultâneos.
Segurança precisa estar ativa, bootstrap e seed demo desligados, Hibernate em
`validate`, SQL sem conteúdo no log e Flyway automático desligado, sem baseline
ou clean. Chave JWT própria (pelo menos 32 bytes, não a padrão) e identidade
PostgreSQL própria são obrigatórias. As verificações não comprovam entropia da
chave, grants do usuário ou ausência de contas demo já existentes: conferir esses
itens no gate humano de implantação.

### Transporte e mesma origem

Definir `APP_PUBLIC_URL` como a origem HTTPS pública (sem credenciais/caminho).
O frontend e `/api` são publicados na mesma origem; a aplicação não anuncia
suporte CORS por uma propriedade sem implementação. Uma allowlist CORS não vazia
é recusada neste perfil.

Escolher explicitamente `APP_TLS_MODE`:

- `LOOPBACK_PROXY`: backend vinculado a `127.0.0.1`, headers encaminhados não
  confiados (`none`). Proxy local termina TLS e encaminha `/api` e `/actuator`.
  Firewall/rede não podem publicar diretamente o backend. Configurar HTTPS,
  HSTS e política de cache no proxy real e verificar externamente.
- `DIRECT`: `server.ssl.enabled=true` e certificado/keystore fornecido pelo
  responsável em canal seguro; headers encaminhados também permanecem `none`.

Nenhum certificado, domínio, reverse proxy ou infraestrutura foi inventado.
Estas são condições verificadas de configuração, não prova de TLS em produção.

## Endpoints

Não foi criada API administrativa. Em produção, Swagger/API docs ficam
desligados; a exposição do Actuator limita-se a `health` e `info`, sem detalhes
públicos. Probes: `/actuator/health/liveness` observa estado do processo;
`/actuator/health/readiness` inclui disponibilidade do banco. O balanceador pode
usar readiness para retirar tráfego, sem reiniciar o processo só porque o banco
ficou indisponível. Autorização de endpoints continua em [[rbac]].

## Estrutura de Dados (DTOs, Entidades)

Nenhuma tabela/entidade é alterada por esta frente. O ensaio de recuperação usa
`pg_dump` custom e `pg_restore --single-transaction --exit-on-error` para destino
novo. `verification.json` guarda apenas hashes/contagens e nomes de recursos de
teste. Não grava linhas ou credenciais. Dump contém dados e precisa de ACL privada.

## Integrações externas

Nenhuma integração nova foi conectada. Permanecem gates obrigatórios:

1. **Pagamento**: escolher adquirente/gateway, contratar, configurar sandbox e
   credenciais via canal seguro. Homologar criação/status/webhook autenticado,
   repetição, timeout inconclusivo, captura, conciliação, estorno e taxas. Somente
   confirmação canônica do provedor pode marcar uma transação integrada como paga.
   O recebimento manual atual precisa continuar explicitamente manual.
2. **NFS-e**: escolher município/padrão/provedor, certificado e responsável fiscal.
   Homologar envio, protocolo, rejeição, consulta após timeout, cancelamento e
   armazenamento. Pendência vinculada à estadia/OS não é nota autorizada.
3. **Equipamento**: testar impressora térmica/leitor/terminal e rede reais no
   local. `window.print()` é uma folha de recibo, não homologação de hardware.
4. **Offline**: sem sincronização segura implementada, não confirmar operação
   desconectada como se estivesse persistida. Na indisponibilidade, orientar o
   procedimento contingencial acordado e reconciliar antes de registrar novamente.

## Tratamento de Erros

### Sessão na prévia local

O inicializador antigo gerava uma chave de assinatura nova em cada execução,
invalidando sessões após reiniciar a API. `scripts/local-session-key.ps1` mantém
48 bytes aleatórios protegidos por DPAPI CurrentUser e ACL exclusiva do usuário,
no estado privado do runtime local. Nunca imprime a chave e recusa estado
corrompido em vez de rotacionar silenciosamente. A adoção requer novo login uma
vez; os reinícios seguintes reutilizam o mesmo material. É configuração de teste,
não provisionamento de chaves de produção.

`scripts/test-local-session-key.ps1` valida oito condições, incluindo estabilidade,
isolamento de runtimes, proteção em disco, ACL e recusa sem sobrescrita.

Configuração recusada mostra nomes das propriedades, nunca seus valores. O
guard roda antes de seeds/datasource. Erros 403 de negócio continuam sem encerrar
a sessão; este módulo não altera autenticação, permissões ou transições fiscais.

Falha no ensaio de restore preserva origem, destino parcial e artefatos. Nenhum
`drop`, `repair`, reprocessamento ou fallback para banco de produção é automático.

## Testes (curl ou equivalente)

- `ProductionSafetyEnvironmentTest`: perfil teste intacto, produção válida,
  flags inseguras, JWT sem vazamento, identidade PostgreSQL, HTTPS/proxy/SSL,
  CORS recusado, perfis misturados e registro do post-processor.
- `node --test scripts/backup-restore-test.test.mjs`: validações do escopo
  local e dry-run. São testes puros, não prova de restauração.
- Ensaio real: seguir `scripts/backup-restore-test.README.md`; o responsável
  deve registrar o caminho do `verification.json` e o resultado real da execução.

### Gate mínimo antes de abrir para clientes

- Validar build exato e matriz Caixa/Gestor/Admin/tenant, sem contas de teste no
  banco produtivo. Conferir grants PostgreSQL e provisionar administrador por
  procedimento supervisionado, sem ativar bootstrap com senha padrão.
- Promover schema por plano revisado e restaurável. As migrations de corte
  V96–V100 possuem proteção exclusivamente de teste; **não removê-la** para subir
  produção. Falta o plano específico de promoção do schema de seis casas.
- Ensaiar backup/restore com dados representativos e retenção/criptografia
  definida; medir RPO/RTO aprovados, preferencialmente em ambiente equivalente.
- Configurar monitores externos para HTTPS/readiness, latência/5xx e banco;
  alertar por falha de backup, disco, locks, outbox/retries, cobranças inconclusivas,
  diferenças de caixa e notas rejeitadas/pendentes. Definir responsável e escalada.
  O sistema não declara esses alertas externos configurados por existir este guia.
- Pilotar evento com múltiplos operadores, fila de pico, limite de vagas,
  perda de conexão, reenvio, troca de turno e contingência treinada; validar com
  responsável pelo estacionamento e contabilidade antes da abertura geral.

## Decisões Técnicas

Fail-fast de configuração não é um scanner de segurança nem autoriza deploy.
Flyway de produção é desativado por desenho: promover schema exige aprovação
específica, sem executar cutovers de teste numa base real. Nenhum monitor,
pagamento ou documento fiscal externo é simulado como sucesso.

## Módulos relacionados

[[core]] · [[operacao]] · [[eventos]] · [[rbac]] · [[auditoria]] · [[fiscal-financeiro]] · [[bloqueios]]

## Histórico

| Data | Ação |
|---|---|
| 2026-11-22 | Corrige rotação involuntária da assinatura no launcher local com DPAPI CurrentUser e testes sem exposição do material. |
| 2026-11-22 | Adiciona guardas prévios ao datasource para perfil de produção e ensaio de backup/restore restrito ao teste local; registra gates externos sem alegar validação produtiva. |
