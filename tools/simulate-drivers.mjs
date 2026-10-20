#!/usr/bin/env node
// Mo phong UC-09: N tai xe di chuyen quanh TP.HCM, moi tai xe gui vi tri moi `interval` ms (mac dinh 100 tai xe / 3 s).
// Tuy chon --rides: khach mau dat chuyen lien tuc, tai xe tu nhan va chay het vong doi chuyen (UC-03, UC-04).
//
//   node tools/simulate-drivers.mjs --drivers 100 --interval 3000 --duration 300 --rides
//
// Mat khau mac dinh doc tu src/main/resources/application-dev.properties (app.dev.seed-password).
import { readFileSync } from 'node:fs';

const args = Object.fromEntries(process.argv.slice(2).map((a, i, all) => a.startsWith('--')
  ? [a.slice(2), all[i + 1] && !all[i + 1].startsWith('--') ? all[i + 1] : true] : []).filter((x) => x.length));
const BASE = args.base || 'http://localhost:8080';
const DRIVERS = Number(args.drivers || 100);
const INTERVAL = Number(args.interval || 3000);
const DURATION = Number(args.duration || 300) * 1000;
const RIDES = Boolean(args.rides);
const PASSWORD = args.password || process.env.RHL_DEMO_PASSWORD || readSeedPassword();
const CENTER = { lat: 10.7769, lng: 106.7009 };

function readSeedPassword() {
  try {
    const props = readFileSync(new URL('../src/main/resources/application-dev.properties', import.meta.url), 'utf8');
    return props.match(/^app\.dev\.seed-password=(.+)$/m)[1].trim();
  } catch {
    console.error('Khong doc duoc mat khau mau; truyen --password hoac RHL_DEMO_PASSWORD');
    process.exit(1);
  }
}

const stats = { sent: 0, accepted: 0, rejected: 0, errors: 0, latencies: [], offers: 0, completed: 0, trips: 0 };

async function api(method, path, token, body, headers = {}) {
  const started = performance.now();
  const res = await fetch(BASE + '/api/v1' + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const json = await res.json().catch(() => ({}));
  return { ok: res.ok, status: res.status, data: json.data, error: json, ms: performance.now() - started };
}

async function login(email) {
  const r = await api('POST', '/auth/login', null, { login: email, password: PASSWORD });
  if (!r.ok) throw new Error('login ' + email + ': ' + r.status + ' ' + JSON.stringify(r.error));
  return r.data.accessToken;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const jitter = (km) => (Math.random() - 0.5) * 2 * km / 111;
const pins = new Map();   // tripId -> PIN: phia "khach" doc PIN cho phia "tai xe" (nhu ngoai doi)

class Driver {
  constructor(n) {
    this.email = 'driver' + String(n).padStart(3, '0') + '@rhl.dev';
    this.pos = { lat: CENTER.lat + jitter(3), lng: CENTER.lng + jitter(3) };
    this.heading = Math.random() * Math.PI * 2;
    this.seq = Date.now();
    this.trip = null;
  }

  async start() {
    this.token = await login(this.email);
    const on = await api('POST', '/drivers/me/online', this.token);
    if (!on.ok) throw new Error(this.email + ' online: ' + JSON.stringify(on.error));
    this.timer = setInterval(() => this.tick().catch(() => stats.errors++), INTERVAL);
    await sleep(Math.random() * INTERVAL);   // rai deu ban tin, khong don vao mot thoi diem
  }

  move() {
    const target = this.trip && (['ACCEPTED', 'PICKING_UP'].includes(this.trip.status) ? this.trip.pickup
      : this.trip.status === 'IN_TRIP' ? this.trip.dropoff : null);
    const step = 0.0006;   // ~65 m moi nhip
    if (target) {
      const dLat = target.latitude - this.pos.lat, dLng = target.longitude - this.pos.lng;
      const d = Math.hypot(dLat, dLng);
      if (d <= step) this.pos = { lat: target.latitude, lng: target.longitude };
      else this.pos = { lat: this.pos.lat + dLat / d * step, lng: this.pos.lng + dLng / d * step };
      return;
    }
    this.heading += (Math.random() - 0.5) * 0.6;
    this.pos = { lat: this.pos.lat + Math.cos(this.heading) * step * 0.5, lng: this.pos.lng + Math.sin(this.heading) * step * 0.5 };
    // giu tai xe trong ban kinh ~5 km quanh trung tam
    if (Math.hypot(this.pos.lat - CENTER.lat, this.pos.lng - CENTER.lng) > 0.045) this.heading += Math.PI;
  }

  async tick() {
    this.move();
    const r = await api('POST', '/drivers/me/location', this.token, {
      latitude: this.pos.lat, longitude: this.pos.lng, accuracyMeters: 5 + Math.random() * 10,
      deviceTimestamp: new Date().toISOString(), sequence: ++this.seq });
    stats.sent++;
    stats.latencies.push(r.ms);
    if (r.status === 401) this.token = await login(this.email);
    else if (!r.ok) stats.errors++;
    else if (r.data.accepted) stats.accepted++;
    else stats.rejected++;
    if (RIDES) await this.handleWork();
  }

  async handleWork() {
    if (!this.trip) {
      const offer = await api('GET', '/drivers/me/offers/current', this.token);
      if (offer.ok && offer.data) {
        stats.offers++;
        const acc = await api('POST', '/offers/' + offer.data.offerId + '/accept', this.token, undefined,
          { 'Idempotency-Key': 'sim-accept-' + offer.data.offerId });
        if (acc.ok) this.trip = acc.data;
      }
      return;
    }
    const near = (p) => Math.hypot(p.latitude - this.pos.lat, p.longitude - this.pos.lng) < 0.0005;
    const step = async (action, body = {}) => {
      const r = await api('POST', '/trips/' + this.trip.id + '/' + action, this.token, { latitude: this.pos.lat, longitude: this.pos.lng, ...body });
      if (r.ok) this.trip = r.data; else if (r.status === 404 || r.error.code === 'INVALID_TRIP_STATE') this.trip = null;
    };
    switch (this.trip.status) {
      case 'ACCEPTED': return step('pickup-started');
      case 'PICKING_UP': return near(this.trip.pickup) ? step('arrived') : undefined;
      case 'ARRIVED': return pins.has(this.trip.id) ? step('started', { pin: pins.get(this.trip.id) }) : undefined;
      case 'IN_TRIP':
        if (near(this.trip.dropoff)) { await step('completed'); stats.completed++; this.trip = null; }
        return;
      default: this.trip = null;
    }
  }

  async stop() {
    clearInterval(this.timer);
    if (!this.trip) await api('POST', '/drivers/me/offline', this.token).catch(() => {});
  }
}

async function customerLoop(email, until) {
  const token = await login(email);
  while (Date.now() < until) {
    const mine = await api('GET', '/trips/mine?size=1', token);
    const last = mine.data && mine.data.items[0];
    if (last && !['COMPLETED', 'CANCELLED', 'NO_DRIVER'].includes(last.status)) {
      if (!pins.has(last.id)) {
        const t = await api('GET', '/trips/' + last.id, token);
        if (t.ok && t.data.startPin) pins.set(last.id, t.data.startPin);
      }
      await sleep(2000);
      continue;
    }
    const place = (label) => ({ latitude: CENTER.lat + jitter(2), longitude: CENTER.lng + jitter(2), address: label });
    const q = await api('POST', '/quotes', token, { serviceType: 'RIDE', vehicleType: 'MOTORBIKE', pickup: place('Điểm đón mô phỏng'), dropoff: place('Điểm đến mô phỏng') });
    if (q.ok) {
      const t = await api('POST', '/trips', token, { quoteId: q.data.quoteId, paymentMethod: Math.random() < 0.5 ? 'CASH' : 'SANDBOX_CARD',
        surgeConfirmed: true }, { 'Idempotency-Key': 'sim-trip-' + q.data.quoteId });
      if (t.ok) { stats.trips++; pins.set(t.data.id, t.data.startPin); }
    }
    await sleep(3000);
  }
}

function p95(values) {
  if (!values.length) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.floor(sorted.length * 0.95)];
}

async function main() {
  console.log(`Simulating ${DRIVERS} drivers every ${INTERVAL} ms for ${DURATION / 1000} s against ${BASE}${RIDES ? ' with rides' : ''}`);
  const drivers = Array.from({ length: DRIVERS }, (_, i) => new Driver(i + 1));
  for (let i = 0; i < drivers.length; i += 10) {
    await Promise.all(drivers.slice(i, i + 10).map((d) => d.start().catch((e) => { stats.errors++; console.error(e.message); })));
  }
  const until = Date.now() + DURATION;
  if (RIDES) ['customer@rhl.dev', 'customer2@rhl.dev'].forEach((c) => customerLoop(c, until).catch((e) => console.error(e.message)));
  const report = setInterval(() => {
    console.log(`sent=${stats.sent} accepted=${stats.accepted} notCurrent=${stats.rejected} errors=${stats.errors} ` +
      `p95=${p95(stats.latencies).toFixed(1)}ms` + (RIDES ? ` trips=${stats.trips} offers=${stats.offers} completed=${stats.completed}` : ''));
    stats.latencies = [];
  }, 10_000);
  await sleep(DURATION);
  clearInterval(report);
  await Promise.all(drivers.map((d) => d.stop()));
  console.log('Done.', JSON.stringify({ ...stats, latencies: undefined }));
  process.exit(stats.errors > stats.sent * 0.01 ? 1 : 0);
}

main();
