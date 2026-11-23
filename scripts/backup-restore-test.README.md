# Ensaio de backup e restauração — somente teste local

O utilitário **não é ferramenta para restaurar produção**. Só aceita
`127.0.0.1`, portas 55474/55475 e a origem
`estacionamento_kaneko_fiscal_20261105_test`. O destino precisa ter nome
`estacionamento_restore_test_<sufixo>` e não pode existir. Não há `drop`,
`clean`, sobrescrita, reparo Flyway, atualização ou recálculo de documento.

Sem `--execute`, apenas valida o plano, sem chamar PostgreSQL. Não recebe senha
por argumento. As ferramentas nativas resolvem a autenticação local já
provisionada pelo responsável; não leia ou cole arquivos privados no chat.

## Execução

Pare escritas e não execute a suíte contra a origem durante o ensaio. O backup
do `pg_dump` é consistente; a comparação adicional antes/depois recusa um ensaio
com alterações concorrentes. Não é necessário parar ou apagar a base.

```powershell
node scripts/backup-restore-test.mjs `
  --source-port 55474 --target-port 55475 `
  --target-database estacionamento_restore_test_20261122 `
  --user kaneko_evento_qa `
  --pg-bin "C:/Program Files/PostgreSQL/17/bin" `
  --output-directory "C:/Users/ilan.wendling/claude/yes7parking/tmp/restore-drill-20261122" `
  --execute
```

O comando usa `postgres` apenas na instância local de destino para criar o banco
novo com owner `kaneko_evento_qa`. A aplicação continua sem `createdb`/superuser.
É preciso ter a role de aplicação criada no cluster de destino. Nenhum servidor
é iniciado, reiniciado ou parado pelo script.

## Evidência

`verification.json` contém data, origem/destino exclusivamente locais, número de
tabelas, hashes SHA-256 e o resultado da comparação. Não contém linhas, documentos,
contatos, senha ou token. A comparação inclui contagem e conteúdo canônico de cada
tabela pública, histórico Flyway e posições das sequências. O `pg_restore` executa
em transação única e interrompe ao primeiro erro.

O arquivo `test-database.dump` contém os próprios dados sintéticos. Guarde a pasta
com ACL privada do usuário, não commite/envie o dump e não o publique como evidência.
`mode` Unix não substitui ACL de Windows. O script recusa pasta de evidência já
existente e preserva artefatos em falhas para investigação; não limpa nada sozinho.

Se a comparação falhar, não substitua o banco de origem. Investigue o destino
isolado e os registros do ensaio. Um sucesso comprova apenas esta cópia local;
não comprova restore de produção, recuperação ponto no tempo, RPO ou RTO.

Teste também a recusa de destino existente, usando outro diretório de evidência.
O banco restaurado não é reutilizado automaticamente pelo preview ou pela suíte.

## Testes sem banco

```powershell
node --test scripts/backup-restore-test.test.mjs
node --check scripts/backup-restore-test.mjs
```

Asserções de validação/dry-run não substituem a execução real de `--execute`.
