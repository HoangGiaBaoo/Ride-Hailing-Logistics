// Thu vien dung chung cho cac trang demo: goi REST kem JWT, ket noi STOMP, dinh dang so lieu kieu Viet Nam.
// Quy tac: KHONG dung innerHTML voi du lieu tu server (ten, dia chi la du lieu nguoi dung nhap) — chi textContent.
(function (global) {
  'use strict';

  const API = '/api/v1';
  const store = {
    get(k) { try { return sessionStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { sessionStorage.setItem(k, v); } catch (e) { /* trinh duyet chan storage: van chay duoc trong phien */ } },
    del(k) { try { sessionStorage.removeItem(k); } catch (e) { } }
  };

  function session() {
    const raw = store.get('rhl.session');
    return raw ? JSON.parse(raw) : null;
  }

  function saveSession(tokens) {
    store.set('rhl.session', JSON.stringify(tokens));
  }

  function logout() {
    const s = session();
    if (s) fetch(API + '/auth/logout', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: s.refreshToken }) }).catch(() => {});
    store.del('rhl.session');
    location.href = '/index.html';
  }

  async function login(loginId, password) {
    const res = await fetch(API + '/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ login: loginId, password }) });
    const body = await res.json();
    if (!res.ok) throw new ApiError(res.status, body);
    saveSession(body.data);
    return body.data;
  }

  async function refresh() {
    const s = session();
    if (!s) return false;
    const res = await fetch(API + '/auth/refresh', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: s.refreshToken }) });
    if (!res.ok) return false;
    saveSession((await res.json()).data);
    return true;
  }

  class ApiError extends Error {
    constructor(status, body) {
      super((body && body.message) || ('HTTP ' + status));
      this.status = status;
      this.code = body && body.code;
      this.details = body && body.details;
    }
  }

  /** Goi API; het han token thi refresh mot lan roi thu lai. opts.idempotencyKey cho thao tac co hieu ung. */
  async function call(method, path, body, opts = {}) {
    const doFetch = () => {
      const headers = { 'Content-Type': 'application/json' };
      const s = session();
      if (s) headers.Authorization = 'Bearer ' + s.accessToken;
      if (opts.idempotencyKey) headers['Idempotency-Key'] = opts.idempotencyKey;
      return fetch(API + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
    };
    let res = await doFetch();
    if (res.status === 401 && session() && await refresh()) res = await doFetch();
    const text = await res.text();
    const json = text ? JSON.parse(text) : {};
    if (!res.ok) throw new ApiError(res.status, json);
    return json.data;
  }

  function newKey(prefix) {
    return prefix + '-' + (crypto.randomUUID ? crypto.randomUUID() : Date.now() + '-' + Math.random().toString(16).slice(2));
  }

  /**
   * Ket noi STOMP; tu ket noi lai. onConnect duoc goi moi lan (ke ca sau reconnect) de subscribe lai
   * va lay snapshot (FR-RT-005). Token gui trong frame CONNECT (COM-005).
   */
  function connectRealtime(onConnect, onStatus) {
    const client = new StompJs.Client({
      brokerURL: (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws',
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      beforeConnect: async () => {
        await refresh();
        const s = session();
        client.connectHeaders = { Authorization: 'Bearer ' + (s ? s.accessToken : '') };
      }
    });
    client.onConnect = () => { onStatus && onStatus(true); onConnect(client); };
    client.onWebSocketClose = () => onStatus && onStatus(false);
    client.onStompError = (frame) => { console.warn('STOMP error', frame.headers.message); onStatus && onStatus(false); };
    client.activate();
    return client;
  }

  /** Bo ban tin cu/trung theo sequence cua tung kenh (FR-RT-006). */
  function sequenced(handler) {
    let last = -1;
    return (msg) => {
      const env = JSON.parse(msg.body);
      if (env.sequence <= last) return;
      last = env.sequence;
      handler(env);
    };
  }

  const money = new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 });
  const fmt = {
    money: (v) => v == null ? '—' : money.format(Number(v)),
    km: (m) => m == null ? '—' : (m / 1000).toLocaleString('vi-VN', { maximumFractionDigits: 1 }) + ' km',
    minutes: (s) => s == null ? '—' : Math.max(1, Math.round(s / 60)) + ' phút',
    time: (iso) => iso ? new Date(iso).toLocaleTimeString('vi-VN') : '—',
    datetime: (iso) => iso ? new Date(iso).toLocaleString('vi-VN') : '—'
  };

  /** Tao phan tu DOM an toan: text luon qua textContent. */
  function el(tag, attrs, ...children) {
    const node = document.createElement(tag);
    Object.entries(attrs || {}).forEach(([k, v]) => {
      if (k === 'class') node.className = v;
      else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
      else if (v !== false && v != null) node.setAttribute(k, v === true ? '' : v);
    });
    children.flat().forEach((c) => node.append(c instanceof Node ? c : document.createTextNode(c == null ? '' : String(c))));
    return node;
  }

  function show(container, ...nodes) {
    container.replaceChildren(...nodes);
  }

  function toast(message, kind) {
    const box = document.getElementById('toast') || document.body.appendChild(el('div', { id: 'toast' }));
    const item = el('div', { class: 'toast ' + (kind || 'info') }, message);
    box.append(item);
    setTimeout(() => item.remove(), 5000);
  }

  function errorText(e) {
    if (e && e.details && e.details.reasons) return e.message + ' ' + e.details.reasons.join(' ');
    if (e && e.details && e.details.missing) return e.message + ' ' + e.details.missing.join(' ');
    if (e && e.details && e.details.fields) return e.message + ' ' + Object.values(e.details.fields).join(' ');
    return (e && e.message) || String(e);
  }

  /** Khoa nut khi dang gui de chong bam lap (NFR-USA-003); server van idempotent. */
  async function guarded(button, work) {
    if (button.disabled) return;
    button.disabled = true;
    try { return await work(); } catch (e) { toast(errorText(e), 'error'); throw e; } finally { button.disabled = false; }
  }

  function requireRole(role) {
    const s = session();
    if (!s || !s.user.roles.includes(role)) {
      location.href = '/index.html?need=' + role;
      return null;
    }
    return s;
  }

  global.RHL = { call, login, logout, session, refresh, newKey, connectRealtime, sequenced, fmt, el, show, toast,
    errorText, guarded, requireRole, ApiError };
})(window);
