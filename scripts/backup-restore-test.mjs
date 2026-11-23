import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const SOURCE_DATABASE = 'estacionamento_kaneko_fiscal_20261105_test';
const PORTS = new Set([55474, 55475]);
const IDENTIFIER = /^[a-z_][a-z0-9_]{0,62}$/;
const ALLOWED = new Set(['host', 'database', 'sourcePort', 'targetPort', 'targetDatabase',
  'user', 'maintenanceUser', 'pgBin', 'outputDirectory', 'execute']);

export function validateOptions(input) {
  for (const key of Object.keys(input)) {
    if (!ALLOWED.has(key)) throw new Error(`Opção não aceita: ${key}`);
  }
  const options = { host: '127.0.0.1', database: SOURCE_DATABASE,
    maintenanceUser: 'postgres', execute: false, ...input };
  options.sourcePort = Number(options.sourcePort);
  options.targetPort = Number(options.targetPort);
  if (options.host !== '127.0.0.1') throw new Error('O ensaio aceita somente 127.0.0.1.');
  if (options.database !== SOURCE_DATABASE) throw new Error('Banco de origem não é a base exclusiva de teste.');
  if (!PORTS.has(options.sourcePort) || !PORTS.has(options.targetPort)) {
    throw new Error('Portas restritas aos clusters de teste 55474/55475.');
  }
  if (!/^estacionamento_restore_test_[a-z0-9_]{1,32}$/.test(options.targetDatabase ?? '')) {
    throw new Error('Destino precisa ter prefixo estacionamento_restore_test_ e nome isolado.');
  }
  if (!IDENTIFIER.test(options.user ?? '') || options.maintenanceUser !== 'postgres') {
    throw new Error('Identidade de banco inválida para o ensaio local.');
  }
  if (!path.isAbsolute(options.pgBin ?? '') || !path.isAbsolute(options.outputDirectory ?? '')) {
    throw new Error('Diretórios de ferramentas e evidências precisam ser absolutos.');
  }
  return options;
}

function command(options, executable, args, input) {
  const program = path.join(options.pgBin, `${executable}${process.platform === 'win32' ? '.exe' : ''}`);
  if (!fs.existsSync(program)) throw new Error(`Ferramenta ausente: ${executable}`);
  const env = { ...process.env, PGCONNECT_TIMEOUT: '5', PGSSLMODE: 'disable', PGTZ: 'UTC' };
  // Não aceitar redirecionamento implícito por serviço/hostaddr/opções externas.
  for (const key of ['PGSERVICE', 'PGSERVICEFILE', 'PGHOSTADDR', 'PGOPTIONS']) delete env[key];
  const result = spawnSync(program, args, {
    env, input, encoding: 'utf8', windowsHide: true, maxBuffer: 32 * 1024 * 1024,
  });
  // stderr do PostgreSQL pode conter valores de linhas. Não o enviar ao console/evidência.
  if (result.error || result.status !== 0) {
    throw new Error(`${executable} falhou (código ${result.status ?? 'indisponível'}); ensaio interrompido, sem limpeza automática.`);
  }
  return result.stdout.trim();
}

function connection(options, port, database, user = options.user) {
  return ['--host', options.host, '--port', String(port), '--username', user,
    '--no-password', '--dbname', database];
}

function query(options, port, database, sql, user = options.user) {
  return command(options, 'psql', [...connection(options, port, database, user),
    '--no-psqlrc', '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1', '--command', sql]);
}

function signature(options, port, database) {
  const names = query(options, port, database,
    "SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename").split(/\r?\n/).filter(Boolean);
  if (!names.includes('flyway_schema_history') || !names.includes('organizacao')) {
    throw new Error('Origem restaurável precisa ter schema da aplicação e histórico Flyway.');
  }
  const sql = names.map(name => {
    const quoted = `"${name.replaceAll('"', '""')}"`;
    const literal = name.replaceAll("'", "''");
    return `SELECT '${literal}',count(*),coalesce(md5(string_agg(md5(to_jsonb(t)::text),'' ORDER BY md5(to_jsonb(t)::text))),'empty') FROM public.${quoted} t;`;
  }).join('\n');
  const rows = command(options, 'psql', [...connection(options, port, database),
    '--no-psqlrc', '--tuples-only', '--no-align', '--set', 'ON_ERROR_STOP=1'],
  `SET TIME ZONE 'UTC';\n${sql}\n`).split(/\r?\n/).filter(line => line.includes('|'));
  const sequences = query(options, port, database,
    "SELECT sequencename,coalesce(last_value::text,'null') FROM pg_sequences WHERE schemaname='public' ORDER BY sequencename");
  return { tables: rows, sequences, digest: createHash('sha256').update(`${rows.join('\n')}\n${sequences}`).digest('hex') };
}

export function runDrill(input) {
  const options = validateOptions(input);
  if (!options.execute) {
    console.log('Somente plano: origem local de teste; destino novo; nenhum comando de banco executado. Use --execute para ensaiar.');
    return { executed: false };
  }
  const directory = path.resolve(options.outputDirectory);
  if (fs.existsSync(directory)) throw new Error('Diretório de evidência já existe; escolha outro, sem sobrescrever.');
  const exists = query(options, options.targetPort, 'postgres',
    `SELECT count(*) FROM pg_database WHERE datname='${options.targetDatabase}'`, options.maintenanceUser);
  if (exists !== '0') throw new Error('Banco de destino já existe; restauração recusada, nenhum banco apagado.');
  const current = query(options, options.sourcePort, options.database, 'SELECT current_database()');
  if (current !== SOURCE_DATABASE) throw new Error('Conexão não corresponde ao banco de teste esperado.');
  const failedMigrations = query(options, options.sourcePort, options.database,
    'SELECT count(*) FROM flyway_schema_history WHERE NOT success');
  if (failedMigrations !== '0') throw new Error('Origem tem migração malsucedida; resolver antes do backup.');
  fs.mkdirSync(directory, { recursive: true, mode: 0o700 });
  const backupFile = path.join(directory, 'test-database.dump');
  const before = signature(options, options.sourcePort, options.database);
  command(options, 'pg_dump', [...connection(options, options.sourcePort, options.database),
    '--format=custom', '--no-owner', '--no-acl', '--file', backupFile]);
  const after = signature(options, options.sourcePort, options.database);
  if (before.digest !== after.digest) {
    throw new Error('A origem mudou durante o ensaio; repita em janela sem escritas. Backup preservado, destino não criado.');
  }
  command(options, 'createdb', ['--host', options.host, '--port', String(options.targetPort),
    '--username', options.maintenanceUser, '--no-password', '--maintenance-db', 'postgres',
    '--owner', options.user, '--template', 'template0', options.targetDatabase]);
  command(options, 'pg_restore', [...connection(options, options.targetPort, options.targetDatabase),
    '--no-owner', '--no-acl', '--exit-on-error', '--single-transaction', backupFile]);
  const restored = signature(options, options.targetPort, options.targetDatabase);
  const ok = before.digest === restored.digest;
  const evidence = { executed: true, at: new Date().toISOString(), sourceHost: options.host,
    sourcePort: options.sourcePort, sourceDatabase: SOURCE_DATABASE,
    targetPort: options.targetPort, targetDatabase: options.targetDatabase,
    tableCount: before.tables.length, contentsAndSequencesMatch: ok,
    sourceDigest: before.digest, restoredDigest: restored.digest,
    backupSha256: createHash('sha256').update(fs.readFileSync(backupFile)).digest('hex'),
    destructiveCommands: false, historicalRecalculation: false,
    scope: 'Ensaio local de dados sintéticos; não comprova restauração em produção nem RPO/RTO.' };
  fs.writeFileSync(path.join(directory, 'verification.json'), `${JSON.stringify(evidence, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
  if (!ok) throw new Error('Restauração divergiu; consulte verification.json. Origem e destino preservados.');
  console.log(`Restauração local verificada: ${evidence.tableCount} tabelas, conteúdos e sequências iguais. Nenhum banco apagado.`);
  return evidence;
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const options = {};
    for (let i = 2; i < process.argv.length; i++) {
      const arg = process.argv[i];
      if (arg === '--execute') { options.execute = true; continue; }
      if (!arg.startsWith('--') || !process.argv[i + 1] || process.argv[i + 1].startsWith('--')) {
        throw new Error('Use opções --nome valor; senhas não são aceitas na linha de comando.');
      }
      const key = arg.slice(2).replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
      options[key] = process.argv[++i];
    }
    runDrill(options);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
