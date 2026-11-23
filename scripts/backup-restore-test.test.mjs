import test from 'node:test';
import assert from 'node:assert/strict';
import { validateOptions, runDrill } from './backup-restore-test.mjs';

const fixture = {
  host: '127.0.0.1', database: 'estacionamento_kaneko_fiscal_20261105_test',
  sourcePort: 55474, targetPort: 55475, targetDatabase: 'estacionamento_restore_test_drill',
  user: 'kaneko_evento_qa', pgBin: 'C:/Program Files/PostgreSQL/17/bin',
  outputDirectory: 'C:/temporary-restore-artifacts',
};

test('accepts only the known local source and an isolated restore name', () => {
  assert.equal(validateOptions(fixture).host, '127.0.0.1');
});

for (const [name, patch] of Object.entries({
  remote: { host: 'database.example.com' },
  localhostAlias: { host: 'localhost' },
  production: { database: 'estacionamento_kaneko' },
  sameDatabase: { targetDatabase: fixture.database },
  unscopedDestination: { targetDatabase: 'postgres' },
  unsafeDestination: { targetDatabase: 'estacionamento_restore_test_a;drop' },
  unexpectedSourcePort: { sourcePort: 5433 },
  unexpectedTargetPort: { targetPort: 5432 },
  noUser: { user: '' },
  invalidUser: { user: 'qa --password=anything' },
  relativePgPath: { pgBin: '../bin' },
  relativeOutputPath: { outputDirectory: '../backup' },
})) {
  test(`rejects ${name} before running any command`, () => {
    assert.throws(() => validateOptions({ ...fixture, ...patch }));
  });
}

test('does not permit a caller to pass a password through command arguments', () => {
  assert.throws(() => validateOptions({ ...fixture, password: 'do-not-print-this' }), /password/);
});

test('the default is a plan and executes no PostgreSQL command', () => {
  assert.deepEqual(runDrill({ ...fixture, pgBin: 'C:/nonexistent-pg-binaries' }), { executed: false });
});
