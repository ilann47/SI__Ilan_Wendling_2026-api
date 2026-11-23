import assert from 'node:assert/strict';
import { test } from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { assertTestTarget, decimal, micros, stableKey, prepareAllScreensSql, classifyCoverage, catalogReference, readOptionalJson, businessDate } from './seed-operated-organization.mjs';

test('recusa banco/porta e origem fora do alvo exclusivo de teste', () => {
  const target = { apiUrl: 'http://127.0.0.1:5174', dbHost: '127.0.0.1', dbPort: 55474,
    dbName: 'estacionamento_kaneko_fiscal_20261105_test', dbUser: 'kaneko_evento_qa' };
  assert.doesNotThrow(() => assertTestTarget(target));
  for (const patch of [{ dbName: 'prod' }, { dbPort: 5432 }, { dbHost: '10.0.0.3' }, { apiUrl: 'https://parking.example' },
    { apiUrl: 'http://127.0.0.1:8086' }, { dbName: 'other_test' }, { dbUser: 'postgres' }]) {
    assert.throws(() => assertTestTarget({ ...target, ...patch }));
  }
});

test('centavos e sexta casa usam apenas inteiros sem Number financeiro', () => {
  assert.equal(micros('120.000001'), 120000001n);
  assert.equal(decimal(120000001n), '120.000001');
  assert.equal(decimal(micros('0.333333') * 3n), '0.999999');
  assert.throws(() => micros('1.0000001'));
});

test('chaves estáveis repetem o mesmo comando mas separam operações', () => {
  assert.equal(stableKey('entrada:QAD1A23'), stableKey('entrada:QAD1A23'));
  assert.notEqual(stableKey('entrada:QAD1A23'), stableKey('saida:QAD1A23'));
  assert.match(stableKey('entrada'), /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-8[0-9a-f]{3}-[0-9a-f]{12}$/);
});

test('reusa o seed existente só no tenant demonstrativo e sem lavagem', async () => {
  const source = await readFile(resolve(import.meta.dirname, '../src/main/resources/db/demo/all-screens.sql'), 'utf8');
  const transformed = prepareAllScreensSql(source, { orgId: 123, cityId: 7, adminLogin: 'demo.admin', operatorLogin: 'demo.portaria' });
  assert.match(transformed, /o\.id = current_setting\('app\.demo_org_id'\)::bigint/);
  assert.match(transformed, /c\.id=current_setting\('app\.demo_city_id'\)::bigint/);
  assert.doesNotMatch(transformed, /Lavagem|lavagem|Lavagem Expressa/);
  assert.match(transformed, /Estacionamento Contratado Demo/);
  assert.doesNotMatch(transformed, /insert into reserva_inventario|insert into pedido|insert into representacao_qr|insert into presenca/i);
  assert.doesNotMatch(transformed, /0000000000000000000000000000000000000000000000000000000000000001/);
  assert.throws(() => prepareAllScreensSql(source, { orgId: 0, cityId: 7, adminLogin: 'demo.admin', operatorLogin: 'demo.portaria' }));
  assert.throws(() => prepareAllScreensSql(source, { orgId: 123, cityId: 7, adminLogin: "x';drop table usuario;--", operatorLogin: 'demo.portaria' }));
});

test('cobertura não inventa registros técnicos para preencher tabela vazia', () => {
  assert.equal(classifyCoverage('outbox_evento', 0), 'tecnica-vazia-intencional');
  assert.equal(classifyCoverage('flyway_schema_history', 106), 'infraestrutura-global');
  assert.equal(classifyCoverage('produto', 14), 'populada');
  assert.equal(classifyCoverage('nova_tabela_desconhecida', 0), 'pendente-avaliacao');
});

test('referência dos produtos cabe no varchar(10) do contrato atual', () => {
  for (let index = 1; index <= 99; index++) assert.ok(catalogReference(index).length <= 10);
  assert.equal(catalogReference(1), 'DEMOCAT01');
  assert.throws(() => catalogReference(100));
});

test('consulta de configuração ausente pode responder 200 com corpo vazio', () => {
  assert.equal(readOptionalJson('', 'GET', '/cobranca-patio'), null);
  assert.equal(readOptionalJson('null', 'GET', '/cobranca-patio'), null);
  assert.deepEqual(readOptionalJson('{"modo":"ENTRADA"}', 'GET', '/cobranca-patio'), { modo: 'ENTRADA' });
  assert.throws(() => readOptionalJson('<html>', 'GET', '/cobranca-patio'), /GET \/cobranca-patio/);
});

test('datas fiscais seguem São Paulo e não viram amanhã ao passar meia-noite UTC', () => {
  const instant = new Date('2026-11-20T01:00:00Z').getTime();
  assert.equal(businessDate(0, instant), '2026-11-19');
  assert.equal(businessDate(-1, instant), '2026-11-18');
});
