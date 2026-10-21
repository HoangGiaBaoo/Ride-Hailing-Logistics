#!/usr/bin/env node
// Du lieu mau cho moi truong dev, tao HOAN TOAN qua API cong khai cua gateway (khong chen thang DB cua service nao):
// bang gia, hoa hong, 2 khach, tai xe demo + N tai xe (dang ky -> ho so -> xe -> 3 giay to -> nop -> duyet).
// Nhan vien (admin, reviewer...) do identity-service tu tao o profile dev. Chay lai nhieu lan an toan: cai gi co roi thi bo qua.
//
//   node tools/seed-dev.mjs                  # 100 tai xe
//   node tools/seed-dev.mjs --drivers 20
import { readFileSync } from 'node:fs';

const args = Object.fromEntries(process.argv.slice(2).map((a, i, all) => a.startsWith('--')
  ? [a.slice(2), all[i + 1] && !all[i + 1].startsWith('--') ? all[i + 1] : true] : []).filter((x) => x.length));
const BASE = args.base || 'http://localhost:8080';
const DRIVERS = Number(args.drivers ?? 100);
const PASSWORD = args.password || process.env.RHL_DEMO_PASSWORD || readSeedPassword();
const CONCURRENCY = 8;

function readSeedPassword() {
  try {
    const props = readFileSync(new URL('../config-server/src/main/resources/configs/identity-service-dev.properties', import.meta.url), 'utf8');
    return props.match(/^app\.dev\.seed-password=(.+)$/m)[1].trim();
  } catch {
    console.error('Khong doc duoc mat khau mau; truyen --password hoac RHL_DEMO_PASSWORD');
    process.exit(1);
  }
}

async function api(method, path, token, body, form) {
  const res = await fetch(BASE + '/api/v1' + path, {
    method,
    headers: { ...(form ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: form ?? (body === undefined ? undefined : JSON.stringify(body))
  });
  const json = await res.json().catch(() => ({}));
  return { ok: res.ok, status: res.status, data: json.data, code: json.code ?? json.error?.code, json };
}

async function must(promise, what) {
  const r = await promise;
  if (!r.ok) throw new Error(`${what}: ${r.status} ${JSON.stringify(r.json)}`);
  return r.data;
}

async function login(email) {
  return (await must(api('POST', '/auth/login', null, { login: email, password: PASSWORD }), 'login ' + email)).accessToken;
}

async function registerIfAbsent(email, phone, fullName, role) {
  const r = await api('POST', '/auth/register', null, { email, phone, password: PASSWORD, fullName, role });
  if (r.ok || r.status === 409) return;
  throw new Error(`register ${email}: ${r.status} ${JSON.stringify(r.json)}`);
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const pad = (n, w) => String(n).padStart(w, '0');

// PNG 1x1 hop le: driver-service kiem magic bytes chu khong tin duoi file (NFR-SEC-011)
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64');

async function seedPricing(admin) {
  const now = new Date().toISOString();
  const existing = await must(api('GET', '/admin/pricing-rules', admin), 'list pricing rules');
  const rules = [
    ['RIDE', 'MOTORBIKE', 10_000, 4_500, 300, 15_000, 2_000],
    ['RIDE', 'CAR_4', 25_000, 10_000, 500, 35_000, 3_000],
    ['RIDE', 'CAR_7', 30_000, 12_500, 600, 45_000, 3_000],
    ['DELIVERY', 'MOTORBIKE', 12_000, 5_000, 300, 18_000, 2_000],
    ['DELIVERY', 'VAN', 60_000, 15_000, 800, 90_000, 5_000]
  ];
  for (const [serviceType, vehicleType, baseFare, perKm, perMinute, minimumFare, bookingFee] of rules) {
    if (existing.some((r) => r.serviceType === serviceType && r.vehicleType === vehicleType && !r.effectiveTo)) continue;
    await must(api('POST', '/admin/pricing-rules', admin, { serviceType, vehicleType, regionCode: 'HCM', baseFare, perKm,
      perMinute, minimumFare, bookingFee, effectiveFrom: now, effectiveTo: null, reason: 'Bảng giá mẫu' }), 'pricing rule');
  }
  const commissions = await must(api('GET', '/admin/commission-rules', admin), 'list commission rules');
  for (const [serviceType, rate] of [['RIDE', 0.2], ['DELIVERY', 0.15]]) {
    if (commissions.some((c) => c.serviceType === serviceType && !c.effectiveTo)) continue;
    await must(api('POST', '/admin/commission-rules', admin, { serviceType, rate, effectiveFrom: now }), 'commission rule');
  }
}

async function seedDriver(i, reviewer) {
  const n = pad(i, 3);
  const email = i === 0 ? 'driver.demo@rhl.dev' : `driver${n}@rhl.dev`;
  const type = i <= 60 ? 'MOTORBIKE' : i <= 85 ? 'CAR_4' : i <= 95 ? 'CAR_7' : 'VAN';
  await registerIfAbsent(email, '093' + pad(i, 7), i === 0 ? 'Tài Xế Demo' : 'Tài Xế ' + n, 'DRIVER');
  const token = await login(email);

  // Ho so tai xe do driver-service tao khi nhan event UserRegistered -> co the chua co ngay sau khi dang ky
  let me;
  for (let attempt = 0; attempt < 50; attempt++) {
    const r = await api('GET', '/drivers/me', token);
    if (r.ok) { me = r.data; break; }
    await sleep(200);
  }
  if (!me) throw new Error(`${email}: driver profile never appeared (UserRegistered not consumed?)`);
  if (me.reviewStatus === 'APPROVED') return 'skip';

  if (me.reviewStatus !== 'PENDING_REVIEW') {
    const expiry = new Date(Date.now() + 3 * 365 * 86_400_000).toISOString().slice(0, 10);
    await must(api('PUT', '/drivers/me/profile', token, { licenseNumber: '79' + pad(i, 10), licenseClass: type === 'MOTORBIKE' ? 'A1' : 'B2',
      licenseExpiry: expiry, nationalIdNumber: '0790' + pad(i, 8), address: `Quận ${1 + (i % 12)}, TP.HCM` }), email + ' profile');
    await must(api('PUT', '/drivers/me/vehicle', token, { vehicleType: type, plateNumber: (type === 'MOTORBIKE' ? '59X' : '51F') + pad(i, 5),
      brand: type === 'MOTORBIKE' ? 'Honda' : 'Toyota', model: type === 'MOTORBIKE' ? 'Wave' : type === 'VAN' ? 'Hiace' : 'Vios',
      color: 'Trắng', manufactureYear: 2022 }), email + ' vehicle');
    for (const doc of ['DRIVER_LICENSE', 'NATIONAL_ID', 'VEHICLE_REGISTRATION']) {
      const form = new FormData();
      form.append('type', doc);
      form.append('number', doc.slice(0, 3) + pad(i, 6));
      form.append('expiresAt', expiry);
      form.append('file', new Blob([PNG], { type: 'image/png' }), doc.toLowerCase() + '.png');
      await must(api('POST', '/drivers/me/documents', token, undefined, form), email + ' ' + doc);
    }
    await must(api('POST', '/drivers/me/submit', token), email + ' submit');
  }
  await must(api('POST', `/review/drivers/${me.driverId}/decisions`, reviewer, { decision: 'APPROVE', reason: 'Dữ liệu mẫu' }), email + ' approve');
  return 'new';
}

async function main() {
  const started = Date.now();
  const admin = await login('admin@rhl.dev');
  const reviewer = await login('reviewer@rhl.dev');
  await seedPricing(admin);
  await registerIfAbsent('customer@rhl.dev', '0922000001', 'Khách Hàng Demo', 'CUSTOMER');
  await registerIfAbsent('customer2@rhl.dev', '0922000002', 'Khách Hàng Hai', 'CUSTOMER');

  const queue = Array.from({ length: DRIVERS + 1 }, (_, i) => i);
  const result = { new: 0, skip: 0, failed: 0 };
  await Promise.all(Array.from({ length: CONCURRENCY }, async () => {
    for (let i = queue.shift(); i !== undefined; i = queue.shift()) {
      try {
        result[await seedDriver(i, reviewer)]++;
      } catch (e) {
        result.failed++;
        console.error(e.message);
      }
    }
  }));
  console.log(`Xong trong ${((Date.now() - started) / 1000).toFixed(1)} s: tai xe moi ${result.new}, da co ${result.skip}, loi ${result.failed}.`);
  if (result.failed) process.exit(1);
}

main().catch((e) => { console.error(e.message); process.exit(1); });
