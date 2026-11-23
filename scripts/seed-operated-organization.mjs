import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

// Credencial deliberadamente pública, somente para identidades sintéticas no banco de teste.
const DEMO_PASSWORD = 'Demo!Parking-2026-Explore';
const ORGANIZATION = 'Yes7 Parking — Organização Demonstrativa';
const DOCUMENT = cnpj('851234560001');
const DEFAULTS = { apiUrl: 'http://127.0.0.1:5174', dbHost: '127.0.0.1', dbPort: 55474,
  dbName: 'estacionamento_kaneko_fiscal_20261105_test', dbUser: 'kaneko_evento_qa' };
const PSQLEXE = 'C:/Program Files/PostgreSQL/17/bin/psql.exe';
const q = (value) => "'" + String(value).replaceAll("'", "''") + "'";

export function assertTestTarget(target) {
  const url = new URL(target.apiUrl);
  if (url.protocol !== 'http:' || !['127.0.0.1', 'localhost'].includes(url.hostname)
      || !['5174', '18087'].includes(url.port) || url.username || url.password || url.pathname !== '/' || url.search || url.hash) {
    throw new Error('A API precisa ser a prévia local 5174 ou a API de QA 18087.');
  }
  if (target.dbHost !== DEFAULTS.dbHost || Number(target.dbPort) !== DEFAULTS.dbPort
      || target.dbName !== DEFAULTS.dbName || target.dbUser !== DEFAULTS.dbUser) {
    throw new Error('Este seed só pode acessar o banco exclusivo _test na porta 55474 com o usuário de QA.');
  }
}

export function micros(value) {
  const text = String(value);
  if (!/^-?\d+(\.\d{1,6})?$/.test(text)) throw new Error('Valor precisa ter até seis casas decimais.');
  const [whole, fraction = ''] = text.replace('-', '').split('.');
  const scaled = BigInt(whole) * 1_000_000n + BigInt(fraction.padEnd(6, '0'));
  return text.startsWith('-') ? -scaled : scaled;
}
export function decimal(value) {
  const number = BigInt(value);
  const abs = number < 0n ? -number : number;
  return `${number < 0n ? '-' : ''}${abs / 1_000_000n}.${String(abs % 1_000_000n).padStart(6, '0')}`;
}
export function stableKey(value) {
  const hex = createHash('sha256').update('yes7-operated-demo-v1:' + value).digest('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-4${hex.slice(13, 16)}-8${hex.slice(17, 20)}-${hex.slice(20, 32)}`;
}

export function catalogReference(index) {
  if (!Number.isInteger(index) || index < 1 || index > 99) throw new Error('Índice de catálogo inválido.');
  return 'DEMOCAT' + String(index).padStart(2, '0');
}

export function readOptionalJson(text, method, path) {
  if (!text.trim()) return null;
  try { return JSON.parse(text); }
  catch { throw new Error(`${method} ${path} não devolveu JSON válido.`); }
}

function cnpj(root) {
  const digit = (value, weights) => {
    const rest = [...value].reduce((sum, n, i) => sum + Number(n) * weights[i], 0) % 11;
    return rest < 2 ? '0' : String(11 - rest);
  };
  const first = root + digit(root, [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
  return first + digit(first, [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]);
}

export function prepareAllScreensSql(source, { orgId, cityId, adminLogin, operatorLogin }) {
  if (!Number.isSafeInteger(orgId) || orgId < 1 || !Number.isSafeInteger(cityId) || cityId < 1
      || ![adminLogin, operatorLogin].every((s) => /^[a-z0-9.-]{3,50}$/.test(s))) {
    throw new Error('Contexto demonstrativo inválido.');
  }
  const marker = "where o.documento = '11222333000181';";
  if (!source.includes(marker)) throw new Error('O contrato do seed all-screens mudou. Revise o adaptador antes de continuar.');
  const commerceStart = source.indexOf('insert into reserva_inventario');
  const commerceEnd = source.indexOf('insert into ordem_servico');
  if (commerceStart < 0 || commerceEnd <= commerceStart) throw new Error('O bloco comercial do seed mudou. Revise antes de executar.');
  // Reservas, pedidos, QR, pagamentos e presenças serão feitos pelo domínio, não por SQL sintético.
  const fixture = source.slice(0, commerceStart) + '\n-- Comércio de evento criado pela API após esta fixture.\n' + source.slice(commerceEnd);
  return `select set_config('app.demo_org_id',${q(orgId)},true);\n`
    + `select set_config('app.demo_city_id',${q(cityId)},true);\n`
    + `select set_config('app.demo_admin_login',${q(adminLogin)},true);\n`
    + `select set_config('app.demo_operator_login',${q(operatorLogin)},true);\n`
    + fixture.replace(marker, "where o.id = current_setting('app.demo_org_id')::bigint;")
      .replace("join cidade c on lower(c.nome) = 'cascavel'", "join cidade c on c.id=current_setting('app.demo_city_id')::bigint")
      .replaceAll('11222333000181', DOCUMENT)
      .replaceAll('Lavagem Expressa Demo', 'Estacionamento Contratado Demo')
      .replaceAll('Lavagem externa durante o estacionamento', 'Permanência contratada para visitantes de evento')
      .replaceAll('Lavagem expressa demonstrativa', 'Permanência contratada demonstrativa')
      .replaceAll('Manobrista Demo', 'Permanência VIP Demo')
      .replaceAll('Servico de manobrista para eventos', 'Permanência em estacionamento VIP de evento')
      .replace(/lavagem/gi, 'Estacionamento contratado');
}

const technical = /^(outbox|idempotencia|tentativa|representacao|reembolso|webhook|integracao|reconciliacao|auditoria|.*_ajuste|.*reversao)/;
export function classifyCoverage(table, count) {
  if (table === 'flyway_schema_history') return 'infraestrutura-global';
  if (count > 0) return 'populada';
  return technical.test(table) ? 'tecnica-vazia-intencional' : 'pendente-avaliacao';
}

function database(target) {
  assertTestTarget(target);
  const sql = (statement) => {
    const result = spawnSync(PSQLEXE, ['-X', '-w', '-h', target.dbHost, '-p', String(target.dbPort),
      '-U', target.dbUser, '-d', target.dbName, '-Atq', '-v', 'ON_ERROR_STOP=1'], {
      input: statement, encoding: 'utf8', windowsHide: true, maxBuffer: 32 * 1024 * 1024,
    });
    if (result.status !== 0) throw new Error('Falha no PostgreSQL de QA: ' + (result.stderr || result.error?.message || 'sem detalhe').slice(0, 1000));
    return result.stdout.trim();
  };
  const actual = sql('select current_database();');
  if (actual !== target.dbName || !actual.endsWith('_test')) throw new Error('O banco conectado não é o banco de teste esperado.');
  const json = (statement) => JSON.parse(sql(`select coalesce(json_agg(result),'[]'::json) from (${statement}) result;`));
  return { sql, json };
}

function api(target) {
  assertTestTarget(target);
  return async (method, path, token, body, options = {}) => {
    const response = await fetch(new URL(path, target.apiUrl), {
      method,
      headers: { ...(token ? { Authorization: 'Bearer ' + token } : {}),
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(options.key ? { 'Idempotency-Key': options.key } : {}),
        ...(options.version == null ? {} : { 'If-Match': `"${options.version}"` }) },
      body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(30000),
    });
    if (!response.ok) {
      if (options.allow?.includes(response.status)) return { rejected: response.status };
      // Não logar corpo de autenticação nem tokens; o trace identifica a falha.
      const details = await response.json().catch(() => ({}));
      throw new Error(`${method} ${path} retornou ${response.status}: ${details.detail ?? details.message ?? 'verifique o contrato'} (trace ${response.headers.get('X-Trace-Id') ?? 'não informado'})`);
    }
    if (response.status === 204) return null;
    const text = await response.text();
    return readOptionalJson(text, method, path);
  };
}

const rows = (result) => Array.isArray(result) ? result : result?.content ?? [];
const dates = () => ({ startsAt: new Date(Date.now() - 2 * 3600000).toISOString(), endsAt: new Date(Date.now() + 30 * 86400000).toISOString() });
export function businessDate(days = 0, now = Date.now()) {
  const fields = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo',
    year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date(now + days * 86400000));
  const part = (type) => fields.find((field) => field.type === type).value;
  return `${part('year')}-${part('month')}-${part('day')}`;
}
const date = businessDate;

async function coreScenario(call, db) {
  const account = async (name) => {
    const login = 'demo.' + name;
    let response = await call('POST', '/api/auth/register', null, { login, senha: DEMO_PASSWORD,
      nome: `Demonstração ${name}`, email: `${login}@example.test` }, { allow: [409] });
    if (response.rejected) response = await call('POST', '/api/auth/login', null, { login, senha: DEMO_PASSWORD });
    return { login, token: response.token, id: response.id ?? response.usuario?.id };
  };
  const admin = await account('admin');
  const existing = db.json(`select id from organizacao where documento=${q(DOCUMENT)}`)[0];
  const organization = existing ?? (await call('POST', '/api/v1/me/organizations', admin.token, {
    document: DOCUMENT, legalName: ORGANIZATION, tradeName: 'Yes7 Parking Demo', currency: 'BRL',
    timeZone: 'America/Sao_Paulo', region: 'BR-PR', plan: 'STANDARD',
  })).organization;
  const orgId = Number(organization.id);
  const activate = async (who) => (await call('POST', '/api/v1/me/active-organization', who.token, { organizationId: orgId })).token;
  admin.token = await activate(admin);
  const adminMembership = db.json(`select m.id from membro_organizacao m join usuario u on u.id=m.usuario_id
    where m.organizacao_id=${orgId} and u.login='demo.admin' and m.status='ATIVO'`)[0];
  const supervisorAssignment = db.json(`select a.id from atribuicao_acesso a join papel p on p.id=a.papel_id
    where a.membro_organizacao_id=${Number(adminMembership.id)} and p.codigo='SUPERVISOR' and a.revogado_em is null`);
  if (!supervisorAssignment.length) await call('POST', `/api/v1/organizations/${orgId}/memberships/${adminMembership.id}/role-assignments`,
    admin.token, { roleCode: 'SUPERVISOR' });
  admin.token = await activate(admin);
  const users = { admin };
  for (const [name, roleCodes] of [['gestor', ['GESTOR']], ['caixa', ['CAIXA_PATIO']], ['portaria', ['OPERADOR']]]) {
    const user = await account(name);
    const userId = Number(db.json(`select id from usuario where lower(login)=${q(user.login)}`)[0].id);
    let membership = db.json(`select id from membro_organizacao where organizacao_id=${orgId} and usuario_id=${userId} and status='ATIVO'`)[0];
    if (!membership) membership = await call('POST', `/api/v1/organizations/${orgId}/memberships`, admin.token, { userId });
    for (const roleCode of roleCodes) {
      const assigned = db.json(`select a.id from atribuicao_acesso a join papel p on p.id=a.papel_id
        where a.membro_organizacao_id=${Number(membership.id)} and p.codigo=${q(roleCode)} and a.revogado_em is null`);
      if (!assigned.length) await call('POST', `/api/v1/organizations/${orgId}/memberships/${membership.id}/role-assignments`, admin.token, { roleCode });
    }
    user.token = await activate(user); users[name] = user;
  }
  const city = rows(await call('GET', '/api/v1/installation-cities?size=100', admin.token)).find((c) => c.nome?.toLowerCase() === 'cascavel')
    ?? rows(await call('GET', '/api/v1/installation-cities?size=100', admin.token))[0];
  if (!city) throw new Error('Cadastre uma cidade ativa antes do seed; o script não assume IDs geográficos.');
  const address = { address: 'Avenida Demonstração', number: '2026', complement: 'Dados sintéticos', district: 'Centro', postalCode: '85810-000', cityId: city.id };
  const venues = [];
  for (const name of ['Arena Yes7 Demo', 'Centro de Eventos Yes7 Demo']) {
    const found = rows(await call('GET', '/api/v1/venues?size=100', admin.token)).find((v) => v.name === name);
    venues.push(found ?? await call('POST', '/api/v1/venues', admin.token, { ...address, name, timeZone: 'America/Sao_Paulo' }));
  }
  const facilities = [];
  for (const [index, name] of ['Patio Principal Demo', 'Pátio Sul Demo', 'Pátio Coberto Demo'].entries()) {
    const found = rows(await call('GET', '/api/v1/parking-facilities?size=100', admin.token)).find((p) => p.name === name);
    const facility = found ?? await call('POST', '/api/v1/parking-facilities', admin.token, {
      ...address, name, physicalCapacity: 300, operationalCapacity: 240, active: true, notes: 'Cenário operacional demonstrativo — sem dados reais.',
    });
    facilities.push(facility);
    for (const [code, label, type] of [['A', 'Visitantes', 'COMUM'], ['VIP', 'Coberto', 'VIP'], ['PCD', 'Acessível', 'PCD']]) {
      const sectors = rows(await call('GET', `/api/v1/sectors?parkingFacilityId=${facility.id}&size=100`, admin.token));
      const sector = sectors.find((s) => s.code === code) ?? await call('POST', `/api/v1/parking-facilities/${facility.id}/sectors`, admin.token, {
        code, name: `${label} Demo`, capacity: code === 'A' ? 180 : 30, categories: [type],
      });
      const spaces = Array.from({ length: code === 'A' ? 30 : 8 }, (_, i) => ({
        code: `${code}-${String(i + 1).padStart(3, '0')}`, type, accessible: type === 'PCD', position: `${label} · fileira ${Math.floor(i / 10) + 1}`,
      }));
      const priorSpaces = rows(await call('GET', `/api/v1/spaces?sectorId=${sector.id}&size=100`, admin.token));
      await call('POST', `/api/v1/sectors/${sector.id}/spaces:batch`, admin.token, { spaces });
      if (index === 2 && code === 'A') {
        const list = rows(await call('GET', `/api/v1/spaces?sectorId=${sector.id}&size=100`, admin.token));
        for (const [i, status] of ['BLOQUEADA', 'MANUTENCAO'].entries()) {
          const space = list.find((s) => s.code === spaces[i].code);
          if (space && space.status === 'LIVRE' && !priorSpaces.some((s) => s.code === space.code)) {
            await call('PATCH', `/api/v1/spaces/${space.id}/status`, admin.token, { status }, { version: space.version });
          }
        }
      }
    }
  }
  const serviceName = 'Estacionamento de Evento Demo';
  const serviceOptions = rows(await call('GET',
    `/api/v1/events/service-options?q=${encodeURIComponent(serviceName)}`, admin.token));
  const eventService = serviceOptions.find((service) => service.name === serviceName)
    ?? await call('POST', '/api/servicos', admin.token, {
      nome: serviceName, descricao: 'Serviço de estacionamento escolhido no cenário de demonstração.', ativo: true,
    });
  const serviceId = eventService.id;
  const events = [];
  const catalog = rows(await call('GET', '/api/v1/events?size=100', admin.token));
  for (const [i, name] of ['Festival Kaneko Demo', 'Festival Pagamento na Saída Demo', 'Evento Encerrado Demo'].entries()) {
    let event = catalog.find((e) => e.name === name);
    if (!event) event = await call('POST', '/api/v1/events', admin.token, { name, venueId: venues[i % venues.length].id, serviceId,
      ...dates(), timeZone: 'America/Sao_Paulo', externalId: `DEMO-OPERADO-${i + 1}` }, { key: stableKey('event:' + i) });
    if (!event.reentryPolicy && event.status === 'RASCUNHO') event = await call('PATCH', `/api/v1/events/${event.id}`, admin.token,
      { reentryPolicy: 'ENTRADA_UNICA' }, { version: event.version });
    const allocations = rows(await call('GET', `/api/v1/events/${event.id}/parking-allocations?size=100`, admin.token));
    let allocation = allocations.find((a) => a.parkingFacilityId === facilities[i].id);
    if (!allocation) allocation = await call('POST', `/api/v1/events/${event.id}/parking-allocations`, admin.token, {
      parkingFacilityId: facilities[i].id, startsAt: event.startsAt, endsAt: event.endsAt,
      operationalCapacity: 240, sellableCapacity: i === 0 ? 90 : 0, reservedCapacity: i === 0 ? 10 : 0,
    }, { key: stableKey('allocation:' + i) });
    events.push({ ...event, allocation, facility: facilities[i], modo: i === 1 ? 'SAIDA' : 'ENTRADA', valor: i === 1 ? '35.123456' : '30.000001' });
  }
  return { users, orgId, cityId: Number(city.id), venues, facilities, events, serviceId };
}

async function ensureCommercialCore(call, scenario) {
  const token = scenario.users.admin.token;
  let event = scenario.events[0];
  const products = rows(await call('GET', `/api/v1/events/${event.id}/parking-products?size=100`, token));
  let product = products.find((p) => p.name === 'Estacionamento Comum Demo');
  if (!product) product = await call('POST', `/api/v1/events/${event.id}/parking-products`, token, {
    name: 'Estacionamento Comum Demo', category: 'COMUM', parkingAllocationId: event.allocation.id,
    accessStartsAt: event.startsAt, accessEndsAt: event.endsAt, right: 'ESTACIONAMENTO_EVENTO', quota: 90,
    benefits: ['ACESSO_QR', 'REENTRADA'], restrictions: ['VALIDO_SOMENTE_NO_EVENTO'],
  }, { key: stableKey('commercial-product') });
  const lots = rows(await call('GET', `/api/v1/parking-products/${product.id}/price-tiers?size=100`, token));
  if (!lots.some((l) => l.name === 'Lote Demonstracao')) await call('POST', `/api/v1/parking-products/${product.id}/price-tiers`, token, {
    name: 'Lote Demonstracao', price: '35.000000', currency: 'BRL', salesStartsAt: event.startsAt,
    salesEndsAt: event.endsAt, quantity: 90, priority: 1,
  }, { key: stableKey('commercial-tier') });
  if (product.status === 'RASCUNHO') product = await call('POST', `/api/v1/parking-products/${product.id}/publication`, token, { confirmed: true }, { version: product.version });
  if (event.status === 'RASCUNHO') event = await call('POST', `/api/v1/events/${event.id}/publication`, token, { confirmed: true }, { version: event.version });
  if (event.status === 'PUBLICADO') event = await call('POST', `/api/v1/events/${event.id}/sales-opening`, token, { startsAt: null }, { version: event.version });
  scenario.events[0] = { ...scenario.events[0], ...event };
  scenario.commercialProductId = product.id;
}

async function seedCommercialOrder(call, db, scenario) {
  const org = scenario.orgId;
  const token = scenario.users.admin.token;
  let existing = db.json(`select p.id,p.status,p.version from pedido p join comprador_pedido b on b.pedido_id=p.id
    where p.organizacao_id=${org} and b.email='reserva.demo@example.test' order by p.id limit 1`)[0];
  if (!existing) {
    const hold = await call('POST', '/api/v1/inventory-holds', token, { parkingProductId: scenario.commercialProductId,
      quantity: 1, channel: 'WEB', buyerReference: 'reserva.demo@example.test' }, { key: stableKey('commercial-hold') });
    existing = await call('POST', '/api/v1/orders', token, { inventoryHoldId: hold.id,
      buyer: { name: 'Visitante Reserva Demonstrativa', email: 'reserva.demo@example.test', document: '12345678909' },
      vehiclePlate: 'YQR1A01', channel: 'WEB' }, { key: stableKey('commercial-order') });
  }
  if (existing.status === 'AGUARDANDO_PAGAMENTO') existing = await call('POST', `/api/v1/orders/${existing.id}/manual-confirmation`, token, {
    paymentMethod: 'PIX', evidenceReference: 'SIMULACAO-SEM-TRANSACAO-BANCARIA',
    reason: 'Registro demonstrativo de recebimento interno, sem confirmação por gateway.',
  }, { key: stableKey('commercial-manual'), version: existing.version });
  if (!['CONFIRMADO', 'ATENDIDO', 'CONCLUIDO'].includes(existing.status)) throw new Error('O pedido demonstrativo não está confirmado. Revise o histórico sem apagá-lo.');
  const credentials = db.json(`select id from credencial_acesso where organizacao_id=${org} and pedido_id=${Number(existing.id)}`);
  const credential = credentials[0] ?? await call('POST', `/api/v1/orders/${existing.id}/credentials`, token, { preferredMedium: 'QR' }, { key: stableKey('commercial-credential') });
  scenario.commercialCredentialId = credential.id;
}

async function seedCommercialAccess(call, db, scenario) {
  const org = scenario.orgId;
  const credentialId = Number(scenario.commercialCredentialId);
  const existing = db.json(`select id from presenca where organizacao_id=${org} and credencial_id=${credentialId}`);
  if (existing.length) return;
  const token = scenario.users.admin.token;
  const credential = await call('GET', `/api/v1/credentials/${credentialId}`, token);
  const qr = await call('POST', `/api/v1/credentials/${credentialId}/qr-code`, token, undefined, { version: credential.version });
  const event = scenario.events[0];
  const body = { qrToken: qr.token, eventId: event.id, parkingFacilityId: event.facility.id, lane: 'PORTARIA-DEMO-RESERVA' };
  const accepted = await call('POST', '/api/v1/check-ins', scenario.users.portaria.token, body, { key: stableKey('commercial-checkin') });
  if (accepted.decision !== 'AUTORIZADA') throw new Error('O check-in demonstrativo foi recusado: ' + accepted.reasonCode);
  // A segunda leitura produz uma recusa legítima, sem inventar token, hash ou motivo.
  await call('POST', '/api/v1/check-ins', scenario.users.portaria.token, body, { key: stableKey('commercial-repeat-checkin') });
}

async function startEvent(call, scenario, event) {
  const token = scenario.users.admin.token;
  if (['OPERACAO_ENCERRADA', 'FINALIZADO', 'EM_CONCILIACAO'].includes(event.status)) return;
  const configured = await call('GET', `/api/v1/events/${event.id}/cobranca-patio`, token);
  if (configured) { event.modo = configured.modo; event.valor = configured.valor; }
  else await call('PUT', `/api/v1/events/${event.id}/cobranca-patio`, token, { modo: event.modo, valor: event.valor });
  if (event.status !== 'EM_OPERACAO') {
    const action = event.id === scenario.events[0].id ? 'operation-start' : 'patio-operation-start';
    if (action === 'operation-start' && event.status === 'VENDAS_ABERTAS') {
      const closed = await call('POST', `/api/v1/events/${event.id}/sales-closing`, token, { confirmed: true }, { version: event.version });
      event.version = closed.version; event.status = closed.status;
    }
    const started = await call('POST', `/api/v1/events/${event.id}/${action}`, token, { confirmed: true }, { version: event.version });
    event.version = started.version; event.status = started.status;
  }
}

async function seedParking(call, db, scenario) {
  const token = scenario.users.caixa.token;
  const org = scenario.orgId;
  let cash = await call('GET', '/api/v1/patio/caixa', token);
  let turn = cash.aberto;
  // A conclusão é verificada pelas placas de fixture, nunca por modificar as estadias anteriores.
  const existingCount = Number(db.json(`select count(*)::int n from patio_evento_estadia s join veiculo v on v.id=(select veiculo_id from movimentacao where id=s.movimentacao_id)
    where s.organizacao_id=${org} and v.placa like 'YSD%'`)[0].n);
  if (existingCount >= 45) return;
  if (!turn) turn = await call('POST', '/api/v1/patio/turnos', token, { fundoTroco: '150.000000' });
  for (const [eventIndex, event] of scenario.events.entries()) {
    if (['OPERACAO_ENCERRADA', 'FINALIZADO', 'EM_CONCILIACAO'].includes(event.status)) continue;
    const count = eventIndex === 2 ? 5 : 20;
    for (let i = 0; i < count; i++) {
      const plate = `YSD${eventIndex}${String.fromCharCode(65 + Math.floor(i / 100))}${String(i % 100).padStart(2, '0')}`;
      let existing = db.json(`select m.id,m.status from movimentacao m join veiculo v on v.id=m.veiculo_id
        where m.organizacao_id=${org} and v.placa=${q(plate)} order by m.id desc limit 1`)[0];
      if (existing?.status === 'FECHADO') continue;
      const meio = ['DINHEIRO', 'PIX', 'CARTAO'][i % 3];
      if (!existing) {
        const body = { placa: plate, eventoId: event.id, patioId: event.facility.id, chaveEntrada: stableKey('plate:' + plate) };
        if (event.modo === 'ENTRADA') Object.assign(body, { meio, valorEsperado: event.valor,
          ...(meio === 'DINHEIRO' ? { recebido: '50.000000' } : {}) });
        existing = await call('POST', '/api/v1/patio/entradas', token, body);
      }
      // Deixar carros presentes em ambos os modos e concluir todo o terceiro evento.
      if (eventIndex === 2 || i < 12) {
        const preview = await call('POST', `/api/v1/patio/movimentacoes/${existing.id}/previa-saida`, token);
        const amount = micros(preview.valor);
        await call('POST', `/api/v1/patio/movimentacoes/${existing.id}/saida`, token, { turnoId: preview.turnoId,
          chave: preview.chave, meio: amount === 0n ? 'SEM_COBRANCA' : meio,
          ...(amount > 0n && meio === 'DINHEIRO' ? { recebido: '50.000000' } : {}) });
      }
    }
  }
  cash = await call('GET', '/api/v1/patio/caixa', token);
  if (cash.aberto) await call('POST', `/api/v1/patio/turnos/${cash.aberto.id}/fechamento`, token, { dinheiroContado: cash.aberto.dinheiroEsperado });
  // Deixar um turno aberto para o usuário experimentar, preservando o fechamento de demonstração.
  await call('POST', '/api/v1/patio/turnos', token, { fundoTroco: '100.000000' });
  const last = scenario.events[2];
  if (last.status === 'EM_OPERACAO') await call('POST', `/api/v1/events/${last.id}/operation-closing`, scenario.users.admin.token,
    { confirmed: true, financialPendingAcknowledged: true }, { version: last.version });
}

export async function seedOperatedOrganization(target = DEFAULTS) {
  assertTestTarget(target);
  const db = database(target);
  const call = api(target);
  if (!db.json("select to_regclass('public.patio_evento_estadia')::text tabela")[0].tabela) throw new Error('Aplique as migrations do caixa de evento antes do seed.');
  if (!db.json("select version from flyway_schema_history where version='106' and success").length) {
    throw new Error('O seed completo exige a V106 aplicada no banco de teste. Nenhuma conta demonstrativa foi criada.');
  }
  const scenario = await coreScenario(call, db);
  await ensureCommercialCore(call, scenario);
  const original = await readFile(resolve(import.meta.dirname, '../src/main/resources/db/demo/all-screens.sql'), 'utf8');
  db.sql('begin;\n' + prepareAllScreensSql(original, { orgId: scenario.orgId, cityId: scenario.cityId,
    adminLogin: 'demo.admin', operatorLogin: 'demo.portaria' }) + '\ncommit;');
  seedExtraCatalogs(db, scenario);
  await seedCommercialOrder(call, db, scenario);
  for (const event of scenario.events) await startEvent(call, scenario, event);
  await seedCommercialAccess(call, db, scenario);
  await seedParking(call, db, scenario);
  await seedCorrections(call, db, scenario);
  await seedFiscal(call, db, scenario);
  await seedCompleteEdgeTables(call, db, scenario);
  const report = inventory(db, scenario.orgId);
  const output = resolve(import.meta.dirname, '../tmp/operated-demo');
  await mkdir(output, { recursive: true });
  await writeFile(resolve(output, 'coverage.json'), JSON.stringify({ generatedAt: new Date().toISOString(),
    organizationId: scenario.orgId, organization: ORGANIZATION, tables: report }, null, 2));
  console.log(`Seed demonstrativo concluído no banco de TESTE. Organização ${scenario.orgId}: ${ORGANIZATION}.`);
  console.log('Logins: demo.admin, demo.gestor, demo.caixa e demo.portaria. Credencial de demonstração no README do seed.');
  console.log(`Cobertura: ${report.filter((r) => r.classification === 'populada').length} tabelas populadas; inventário completo em tmp/operated-demo/coverage.json.`);
  return { organizationId: scenario.orgId, coverage: report };
}

async function seedFiscal(call, db, scenario) {
  // Complemento implementado abaixo para sempre usar a confirmação/baixa de domínio.
  await seedFiscalDocuments(call, db, scenario);
}

async function seedCompleteEdgeTables(call, db, scenario) {
  const org = scenario.orgId;
  const token = scenario.users.admin.token;
  // Convite legado sem privilégio de login: o schema só oferece vínculo com pátio.
  // Não inventar relação com usuário/funcionário nem promover perfil global para usar a rota legada.
  db.sql(`insert into colaborador(patio_id,nome,email,perfil,status,ativo)
    select ${Number(scenario.facilities[0].id)},'Equipe convidada Demo','convite.demo@example.test','OPERADOR','CONVIDADO',true
    where not exists(select 1 from colaborador where patio_id=${Number(scenario.facilities[0].id)} and email='convite.demo@example.test');`);
  const imported = db.json(`select i.id,i.version,i.status from importacao_nota_entrada i
    where i.organizacao_id=${org} and not exists(select 1 from parcela_importacao_nota p where p.importacao_id=i.id)
    and i.status='CONCILIADA' order by i.id limit 1`)[0];
  if (imported) {
    const lines = db.json(`select sequencia,quantidade_micros::text quantidade from item_importacao_nota_entrada where importacao_id=${Number(imported.id)} order by sequencia`);
    await call('POST', `/api/notas-entrada/importacoes/${imported.id}/conferencia`, token,
      { itens: lines.map((line) => ({ sequencia: line.sequencia, quantidadeConferida: decimal(line.quantidade) })) }, { version: imported.version });
  }
  await seedCommercialCancellation(call, db, scenario);
}

async function seedCommercialCancellation(call, db, scenario) {
  const org = scenario.orgId;
  const token = scenario.users.admin.token;
  const name = 'Feira Credenciais e Reembolso Demo';
  if (db.json(`select c.id from cancelamento_pedido c join pedido p on p.id=c.pedido_id join evento e on e.id=p.evento_id
    where c.organizacao_id=${org} and e.nome=${q(name)}`).length) return;
  // O primeiro cenário QR já está dentro. Seu token foi corretamente descartado;
  // criar outra representação para esse estado seria inválido. Este cenário mantém
  // QR somente em memória e exercita entrada/saída/cancelamento na mesma execução.
  const facilityName = 'Pátio Credenciais Demo';
  let facility = rows(await call('GET', '/api/v1/parking-facilities?size=100', token)).find((p) => p.name === facilityName);
  if (!facility) facility = await call('POST', '/api/v1/parking-facilities', token, { name: facilityName,
    address: 'Avenida Demonstração', number: '2026', district: 'Centro', postalCode: '85810-000', cityId: scenario.cityId,
    physicalCapacity: 60, operationalCapacity: 50, active: true, notes: 'Cenário de credencial cancelada e reembolso pendente, sem gateway.' });
  let event = rows(await call('GET', '/api/v1/events?size=100', token)).find((e) => e.name === name);
  if (!event) event = await call('POST', '/api/v1/events', token, { name, venueId: scenario.venues[0].id, serviceId: scenario.serviceId,
    ...dates(), timeZone: 'America/Sao_Paulo', externalId: 'DEMO-REEMBOLSO' }, { key: stableKey('refund-event') });
  if (!event.reentryPolicy) event = await call('PATCH', `/api/v1/events/${event.id}`, token,
    { reentryPolicy: 'REENTRADA_PERMITIDA' }, { version: event.version });
  let allocation = rows(await call('GET', `/api/v1/events/${event.id}/parking-allocations`, token)).find((a) => a.parkingFacilityId === facility.id);
  if (!allocation) allocation = await call('POST', `/api/v1/events/${event.id}/parking-allocations`, token, {
    parkingFacilityId: facility.id, startsAt: event.startsAt, endsAt: event.endsAt,
    operationalCapacity: 50, sellableCapacity: 50, reservedCapacity: 0,
  }, { key: stableKey('refund-allocation') });
  let product = rows(await call('GET', `/api/v1/events/${event.id}/parking-products`, token)).find((p) => p.name === 'Credencial Feira Demo');
  if (!product) product = await call('POST', `/api/v1/events/${event.id}/parking-products`, token, {
    name: 'Credencial Feira Demo', category: 'COMUM', parkingAllocationId: allocation.id,
    accessStartsAt: event.startsAt, accessEndsAt: event.endsAt, right: 'ESTACIONAMENTO_EVENTO', quota: 50, benefits: [], restrictions: [],
  }, { key: stableKey('refund-product') });
  if (!rows(await call('GET', `/api/v1/parking-products/${product.id}/price-tiers`, token)).length) await call('POST', `/api/v1/parking-products/${product.id}/price-tiers`, token, {
    name: 'Lote Reembolso Demo', price: '45.123456', currency: 'BRL', salesStartsAt: event.startsAt, salesEndsAt: event.endsAt,
    quantity: 50, priority: 1,
  }, { key: stableKey('refund-tier') });
  if (product.status === 'RASCUNHO') product = await call('POST', `/api/v1/parking-products/${product.id}/publication`, token, { confirmed: true }, { version: product.version });
  if (event.status === 'RASCUNHO') event = await call('POST', `/api/v1/events/${event.id}/publication`, token, { confirmed: true }, { version: event.version });
  if (event.status === 'PUBLICADO') event = await call('POST', `/api/v1/events/${event.id}/sales-opening`, token, { startsAt: null }, { version: event.version });
  let order = db.json(`select p.id,p.status,p.version from pedido p join comprador_pedido b on b.pedido_id=p.id
    where p.organizacao_id=${org} and p.evento_id=${Number(event.id)} and b.email='reembolso.demo@example.test' order by p.id limit 1`)[0];
  if (!order) {
    const hold = await call('POST', '/api/v1/inventory-holds', token, { parkingProductId: product.id, quantity: 1,
      channel: 'WEB', buyerReference: 'reembolso.demo@example.test' }, { key: stableKey('refund-hold') });
    order = await call('POST', '/api/v1/orders', token, { inventoryHoldId: hold.id, vehiclePlate: 'YQR1A02', channel: 'WEB',
      buyer: { name: 'Cancelamento Demonstrativo', email: 'reembolso.demo@example.test', document: '12345678909' } }, { key: stableKey('refund-order') });
  }
  if (order.status === 'AGUARDANDO_PAGAMENTO') order = await call('POST', `/api/v1/orders/${order.id}/manual-confirmation`, token,
    { paymentMethod: 'PIX', evidenceReference: 'SIMULACAO-SEM-GATEWAY', reason: 'Confirmação interna de teste para demonstrar reembolso pendente.' },
    { key: stableKey('refund-confirmation'), version: order.version });
  let credential = db.json(`select id,status from credencial_acesso where organizacao_id=${org} and pedido_id=${Number(order.id)}`)[0];
  if (!credential) credential = await call('POST', `/api/v1/orders/${order.id}/credentials`, token, { preferredMedium: 'QR' }, { key: stableKey('refund-credential') });
  if (event.status === 'VENDAS_ABERTAS') event = await call('POST', `/api/v1/events/${event.id}/sales-closing`, token, { confirmed: true }, { version: event.version });
  if (event.status === 'VENDAS_ENCERRADAS') event = await call('POST', `/api/v1/events/${event.id}/operation-start`, token, { confirmed: true }, { version: event.version });
  const presence = db.json(`select id,status from presenca where organizacao_id=${org} and credencial_id=${Number(credential.id)}`)[0];
  if (!presence) {
    credential = await call('GET', `/api/v1/credentials/${credential.id}`, token);
    const qr = await call('POST', `/api/v1/credentials/${credential.id}/qr-code`, token, undefined, { version: credential.version });
    const body = { qrToken: qr.token, eventId: event.id, parkingFacilityId: facility.id, lane: 'PORTARIA-DEMO-REEMBOLSO' };
    const checkin = await call('POST', '/api/v1/check-ins', scenario.users.portaria.token, body, { key: stableKey('refund-checkin') });
    if (checkin.decision !== 'AUTORIZADA') throw new Error('Check-in de reembolso recusado: ' + checkin.reasonCode);
    const checkout = await call('POST', '/api/v1/check-outs', scenario.users.portaria.token, body, { key: stableKey('refund-checkout') });
    if (checkout.decision !== 'AUTORIZADA') throw new Error('Checkout de reembolso recusado: ' + checkout.reasonCode);
  } else if (presence.status === 'DENTRO') {
    throw new Error('O cenário de reembolso foi interrompido com QR dentro; não é seguro inventar outro token. Faça a saída com o QR original.');
  }
  order = await call('GET', `/api/v1/orders/${order.id}`, token);
  const cancellation = await call('POST', `/api/v1/orders/${order.id}/cancellation`, token,
    { reason: 'Cancelamento demonstrativo após saída; reembolso permanece pendente e não foi executado externamente.' },
    { key: stableKey('refund-cancellation'), version: order.version });
  if (!cancellation.refundRequired || cancellation.orderStatus !== 'EM_REEMBOLSO') throw new Error('O cenário não produziu a pendência de reembolso esperada.');
}

async function seedCorrections(call, db, scenario) {
  const token = scenario.users.admin.token;
  const completed = Number(db.sql(`select count(*) from patio_operacao_correcao where organizacao_id=${scenario.orgId}
    and chave in (${['CORRIGIR_PLACA', 'CORRIGIR_MEIO', 'CANCELAR_ESTADIA'].map((action) => q(stableKey('correction:' + action))).join(',')});`));
  if (completed === 3) return;
  let cash = await call('GET', '/api/v1/patio/caixa', token);
  if (!cash.aberto) await call('POST', '/api/v1/patio/turnos', token, { fundoTroco: '100.000000' });
  const commands = [
    { source: ['YSD0A19', 'YSD9A19'], acao: 'CORRIGIR_PLACA', placa: 'YSD9A19', motivo: 'Correção demonstrativa de placa informada incorretamente na portaria.' },
    { source: ['YSD0A00'], acao: 'CORRIGIR_MEIO', meio: 'PIX', motivo: 'Correção demonstrativa: recebimento ocorreu por Pix, não em dinheiro.' },
    { source: ['YSD1A19'], acao: 'CANCELAR_ESTADIA', motivo: 'Entrada demonstrativa registrada por engano, cancelada com histórico preservado.' },
  ];
  for (const command of commands) {
    const movement = db.json(`select m.id from movimentacao m join veiculo v on v.id=m.veiculo_id
      where m.organizacao_id=${scenario.orgId} and v.placa in (${command.source.map(q).join(',')}) order by m.id limit 1`)[0];
    if (!movement) throw new Error('Não foi localizada a estadia demonstrativa da correção ' + command.acao);
    const { source, ...body } = command;
    await call('POST', `/api/v1/patio/movimentacoes/${movement.id}/correcao`, token,
      { ...body, chave: stableKey('correction:' + command.acao) });
    void source;
  }
  cash = await call('GET', '/api/v1/patio/caixa', token);
  if (cash.aberto) await call('POST', `/api/v1/patio/turnos/${cash.aberto.id}/fechamento`, token,
    { dinheiroContado: cash.aberto.dinheiroEsperado });
}

function inventory(db, orgId) {
  const tables = db.json("select table_name from information_schema.tables where table_schema='public' and table_type='BASE TABLE' order by table_name");
  return tables.map(({ table_name: table }) => {
    if (!/^[a-z_][a-z0-9_]*$/.test(table)) throw new Error('Nome inesperado no inventário.');
    const hasOwner = db.json(`select column_name from information_schema.columns where table_schema='public' and table_name=${q(table)} and column_name='organizacao_id'`).length > 0;
    let predicate = hasOwner ? `t.organizacao_id=${orgId}` : '';
    let scope = hasOwner ? 'organizacao-demonstrativa' : 'configuracao-global';
    if (!hasOwner && table === 'usuario') { predicate = "t.login in ('demo.admin','demo.gestor','demo.caixa','demo.portaria')"; scope = 'identidades-demonstrativas'; }
    if (!hasOwner && table !== 'usuario') {
      const relations = db.json(`select child.attname child_column,parent.relname parent_table,target.attname parent_column
        from pg_constraint fk join pg_class childtable on childtable.oid=fk.conrelid
        join pg_namespace ns on ns.oid=childtable.relnamespace join pg_class parent on parent.oid=fk.confrelid
        join lateral unnest(fk.conkey,fk.confkey) k(child_key,parent_key) on true
        join pg_attribute child on child.attrelid=childtable.oid and child.attnum=k.child_key
        join pg_attribute target on target.attrelid=parent.oid and target.attnum=k.parent_key
        where fk.contype='f' and ns.nspname='public' and childtable.relname=${q(table)}
        and exists(select 1 from information_schema.columns c where c.table_schema='public'
          and c.table_name=parent.relname and c.column_name='organizacao_id')`);
      if (relations.length) {
        predicate = relations.map((r) => {
          for (const name of [r.child_column, r.parent_table, r.parent_column]) if (!/^[a-z_][a-z0-9_]*$/.test(name)) throw new Error('Relacionamento inesperado.');
          return `exists(select 1 from "${r.parent_table}" p where p."${r.parent_column}"=t."${r.child_column}" and p.organizacao_id=${orgId})`;
        }).join(' or ');
        scope = 'relacao-com-organizacao-demonstrativa';
      }
    }
    const count = Number(db.sql(`select count(*) from "${table}" t${predicate ? ` where (${predicate})` : ''};`));
    return { table, count, scope, classification: classifyCoverage(table, count) };
  });
}

function seedExtraCatalogs(db, { orgId: org }) {
  const customers = Array.from({ length: 24 }, (_, i) => [
    `Visitante Demonstração ${String(i + 1).padStart(2, '0')}`,
    cnpj(String(85123000 + i).padStart(8, '0') + '0001'),
    `visitante${i + 1}@example.test`,
  ]);
  const suppliers = Array.from({ length: 6 }, (_, i) => [
    ['Bebidas', 'Alimentos', 'Sinalização', 'Papelaria', 'Equipamentos', 'Uniformes'][i] + ' Demonstração Ltda',
    cnpj(String(85234000 + i).padStart(8, '0') + '0001'),
  ]);
  const products = ['Água com gás 500ml', 'Água sem gás 1,5L', 'Refrigerante sem açúcar', 'Suco de uva',
    'Café expresso', 'Chocolate em barra', 'Biscoito salgado', 'Salgadinho de milho', 'Bobina de ticket',
    'Fita de demarcação', 'Cone de sinalização', 'Colete refletivo', 'Pulseira credencial', 'Capa de chuva'];
  const clientValues = customers.map(([name, doc, email]) => `(${q(name)},${q(doc)},${q(email)})`).join(',');
  const supplierValues = suppliers.map(([name, doc]) => `(${q(name)},${q(doc)})`).join(',');
  const productValues = products.map((name, i) => `(${q(name)},${q(catalogReference(i + 1))},${i + 1})`).join(',');
  db.sql(`begin;
    insert into cliente (nome,apelido,tipo,documento,telefone,email,endereco,numero,bairro,cep,cidade_id,
      condicao_pagamento_id,limite_credito_micros,observacao,ativo,organizacao_id)
    select v.nome,v.nome,'JURIDICA',v.doc,'45999990000',v.email,'Avenida Demonstração','100','Centro','85810-000',base.cidade_id,
      base.condicao_pagamento_id,5000000000,'Cliente sintético para navegar no sistema.',true,${org}
    from (values ${clientValues}) v(nome,doc,email)
    cross join lateral(select * from cliente where organizacao_id=${org} and documento='12345678909' limit 1) base
    where not exists(select 1 from cliente c where c.organizacao_id=${org} and c.documento=v.doc);
    insert into fornecedor (nome,nome_fantasia,tipo,documento,endereco,numero,bairro,cep,cidade_id,
      condicao_pagamento_id,limite_credito_micros,observacao,ativo,transportadora_id,organizacao_id)
    select v.nome,v.nome,'JURIDICA',v.doc,base.endereco,'200',base.bairro,base.cep,base.cidade_id,
      base.condicao_pagamento_id,20000000000,'Fornecedor sintético da organização demonstrativa.',true,base.transportadora_id,${org}
    from (values ${supplierValues}) v(nome,doc)
    cross join lateral(select * from fornecedor where organizacao_id=${org} and documento='66777888000199' limit 1) base
    where not exists(select 1 from fornecedor f where f.organizacao_id=${org} and f.documento=v.doc);
    insert into fornecedor_email(fornecedor_id,email,tipo,principal,ativo)
    select f.id,'vendas.'||f.id||'@example.test','VENDAS',true,true from fornecedor f
    where f.organizacao_id=${org} and not exists(select 1 from fornecedor_email e where e.fornecedor_id=f.id);
    insert into fornecedor_telefone(fornecedor_id,telefone,tipo,principal,ativo)
    select f.id,'45999990000','WHATSAPP',true,true from fornecedor f
    where f.organizacao_id=${org} and not exists(select 1 from fornecedor_telefone t where t.fornecedor_id=f.id);
    insert into produto (nome,referencia,marca_id,unidade_medida_id,categoria_id,valor_compra_micros,valor_venda_micros,
      percentual_lucro_micros,quantidade_micros,quantidade_minima_micros,descricao,observacao,ativo,custo_micros,organizacao_id,version)
    select v.nome,v.ref,base.marca_id,base.unidade_medida_id,base.categoria_id,
      (v.posicao*1000000+123456),(v.posicao*2000000+654321),50000000,0,5000000,
      'Catálogo demonstrativo relacionado a fornecedor.','Saldo inicial zero; entradas somente por nota.',true,
      (v.posicao*1000000+123456),${org},0
    from (values ${productValues}) v(nome,ref,posicao)
    cross join lateral(select * from produto where organizacao_id=${org} order by id limit 1) base
    where not exists(select 1 from produto p where p.organizacao_id=${org} and p.referencia=v.ref);
    insert into produto_fornecedor(produto_id,fornecedor_id,codigo_prod,custo_micros,ativo,organizacao_id,version)
    select p.id,f.id,'FORN-'||p.referencia,p.valor_compra_micros,true,${org},0 from produto p
    cross join lateral(select id from fornecedor where organizacao_id=${org} order by id limit 1) f
    where p.organizacao_id=${org} and not exists(select 1 from produto_fornecedor pf where pf.produto_id=p.id and pf.fornecedor_id=f.id);
    commit;`);
}

async function seedFiscalDocuments(call, db, scenario) {
  const org = scenario.orgId;
  const token = scenario.users.admin.token;
  const ids = db.json(`select f.id fornecedor_id, c.id cliente_id, cp.id condicao_id, fp.id forma_id,l.id local_id,s.id servico_id
    from fornecedor f join cliente c on c.organizacao_id=f.organizacao_id
    join condicao_pagamento cp on cp.organizacao_id=f.organizacao_id and cp.nome='30/60 dias Demo'
    join forma_pagamento fp on fp.organizacao_id=f.organizacao_id and fp.nome='PIX Demo'
    join local_estoque l on l.organizacao_id=f.organizacao_id
    join servico s on s.organizacao_id=f.organizacao_id and s.nome='Estacionamento Contratado Demo'
    where f.organizacao_id=${org} and f.documento='66777888000199' and c.documento='12345678909' order by l.id limit 1`)[0];
  if (!ids) throw new Error('O catálogo fiscal demonstrativo não foi criado.');
  const products = db.json(`select id, referencia from produto where organizacao_id=${org} order by id limit 2`);
  if (products.length < 2) throw new Error('O catálogo demonstrativo precisa dos dois produtos de conveniência.');
  const confirm = async (path, note) => {
    if (note.situacao === 'PENDENTE') return call('POST', `${path}/${note.id}/confirmacao`, token, undefined, { version: note.version });
    return note;
  };
  // Oito compras reais, dois itens em cada uma, com valores e quantidades de seis casas.
  for (let i = 1; i <= 8; i++) {
    const numero = `DEMO-E-${String(i).padStart(3, '0')}`;
    let note = rows(await call('GET', `/api/notas-entrada?numero=${numero}&size=100`, token)).find((n) => n.numero === numero);
    if (!note) note = await call('POST', '/api/notas-entrada', token, {
      numero, modelo: '55', serie: 'DEM', fornecedorId: ids.fornecedor_id, condicaoPagamentoId: ids.condicao_id,
      localEstoqueId: ids.local_id, dataEmissao: date(-40 + i), dataChegada: date(-40 + i), tipoFrete: 'CIF',
      valorFrete: '12.345678', valorSeguro: '1.123456', outrasDespesas: '0.500001', valorDesconto: '0',
      observacao: 'Compra demonstrativa de conveniência — cenário sintético.',
      itens: [{ produtoId: products[0].id, quantidade: '40.123456', valorUnitario: '2.123456', valorDesconto: '0.123456' },
        { produtoId: products[1].id, quantidade: '30.654321', valorUnitario: '3.654321', valorDesconto: '0.654321' }],
    }, { key: stableKey(numero) });
    note = await confirm('/api/notas-entrada', note);
    const titles = db.json(`select id,valor_total_micros::text total,valor_pago_micros::text pago from conta_pagar
      where organizacao_id=${org} and nota_entrada_id=${Number(note.id)} order by numero_parcela`);
    if (i % 3 !== 0) for (const title of titles) {
      const expectedPaid = i % 3 === 1 ? BigInt(title.total) : BigInt(title.total) / 2n;
      const remaining = expectedPaid - BigInt(title.pago);
      if (remaining > 0n) await call('POST', `/api/contas-pagar/${title.id}/baixa`, token, { valor: decimal(remaining), data: date(-1) }, { key: stableKey(`pay:${title.id}`) });
    }
  }
  // Doze vendas reais retiram apenas o estoque adquirido pelas notas acima.
  for (let i = 1; i <= 12; i++) {
    const numero = `DEMO-S-${String(i).padStart(3, '0')}`;
    let note = rows(await call('GET', `/api/notas-saida?numero=${numero}&size=100`, token)).find((n) => n.numero === numero);
    if (!note) note = await call('POST', '/api/notas-saida', token, {
      numero, modelo: '55', serie: 'DEM', clienteId: ids.cliente_id, condicaoPagamentoId: ids.condicao_id,
      localEstoqueId: ids.local_id, dataEmissao: date(-20 + i), dataSaida: date(-20 + i), tipoFrete: 'SEM',
      valorFrete: '0', valorSeguro: '0', outrasDespesas: '0', valorDesconto: '0',
      observacao: 'Venda demonstrativa da conveniência, sem pedido de venda.',
      itens: [{ produtoId: products[i % 2].id, quantidade: '2.123456', valorUnitario: i % 2 ? '8.654321' : '5.123456', valorDesconto: '0.123456' }],
    }, { key: stableKey(numero) });
    note = await confirm('/api/notas-saida', note);
    const titles = db.json(`select id,valor_total_micros::text total,valor_recebido_micros::text recebido from conta_receber
      where organizacao_id=${org} and nota_saida_id=${Number(note.id)} order by numero_parcela`);
    if (i % 3 !== 0) for (const title of titles) {
      const expectedPaid = i % 3 === 1 ? BigInt(title.total) : BigInt(title.total) / 2n;
      const remaining = expectedPaid - BigInt(title.recebido);
      if (remaining > 0n) await call('POST', `/api/contas-receber/${title.id}/baixa`, token, { valor: decimal(remaining), data: date(-1) }, { key: stableKey(`receive:${title.id}`) });
    }
  }
  // Rascunhos fiscais para visualizar trabalho pendente sem gerar efeitos.
  for (let i = 1; i <= 3; i++) {
    const numero = `DEMO-RASC-${i}`;
    if (!rows(await call('GET', `/api/notas-entrada?numero=${numero}`, token)).some((n) => n.numero === numero)) {
      await call('POST', '/api/notas-entrada', token, { numero, modelo: '55', serie: 'DEM', fornecedorId: ids.fornecedor_id,
        condicaoPagamentoId: ids.condicao_id, localEstoqueId: ids.local_id, dataEmissao: date(), dataChegada: date(),
        tipoFrete: 'SEM', valorFrete: '0', valorSeguro: '0', outrasDespesas: '0', valorDesconto: '0',
        observacao: 'Rascunho demonstrativo — ainda não confirmado.',
        itens: [{ produtoId: products[0].id, quantidade: '10.000001', valorUnitario: '2.333333' }],
      }, { key: stableKey(numero) });
    }
  }
  // Notas internas de contrato de estacionamento, distintas das pendências NFS-e das estadias.
  for (let i = 1; i <= 4; i++) {
    const numero = `DEMO-NFS-${i}`;
    let note = rows(await call('GET', `/api/notas-servico?numero=${numero}&size=100`, token)).find((n) => n.numero === numero);
    if (!note) note = await call('POST', '/api/notas-servico', token, { numero, modelo: 'NFS-e', serie: 'DEM',
      clienteId: ids.cliente_id, servicoId: ids.servico_id, condicaoPagamentoId: ids.condicao_id, formaPagamentoId: ids.forma_id,
      dataEmissao: date(-5), valorServico: '120.123456', aliquotaIss: '2.500001', valorDesconto: '0.123456',
      observacao: 'Documento interno demonstrativo de estacionamento contratado. Sem autorização fiscal externa.',
    });
    if (i !== 4 && note.situacao === 'PENDENTE') await call('POST', `/api/notas-servico/${note.id}/emissao`, token, undefined, { version: note.version });
    if (i === 1) {
      const titles = db.json(`select id,valor_total_micros::text total,valor_recebido_micros::text recebido from conta_receber
        where organizacao_id=${org} and nota_servico_id=${Number(note.id)}`);
      for (const title of titles) {
        const remaining = BigInt(title.total) - BigInt(title.recebido);
        if (remaining > 0n) await call('POST', `/api/contas-receber/${title.id}/baixa`, token, { valor: decimal(remaining), data: date(-1) }, { key: stableKey(`service:${title.id}`) });
      }
    }
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  if (!process.argv.includes('--apply-test-only')) {
    console.log('Seed não executado. Leia scripts/seed-operated-organization.README.md e use --apply-test-only no banco exclusivo de teste.');
  } else {
    const apiArg = process.argv.find((value) => value.startsWith('--api='));
    seedOperatedOrganization({ ...DEFAULTS, ...(apiArg ? { apiUrl: apiArg.slice(6) } : {}) })
      .catch((error) => { console.error(error.message); process.exitCode = 1; });
  }
}
