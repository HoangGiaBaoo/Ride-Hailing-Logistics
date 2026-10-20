#!/usr/bin/env node
/*
 * Hook SessionStart — chay khi mo phien Claude Code moi.
 *
 * Muc dich: nap lai boi canh du an va tien do dang lam, de khong phai nhac tay
 * moi lan mo may.
 */

const fs = require('fs');
const path = require('path');

const thuMucDuAn = process.cwd();

const cacPhan = [];

cacPhan.push(
  'BOI CANH: He thong dat xe & giao hang thoi gian thuc (SRS-RHL-002, docx o thu muc goc). ' +
  'Spring Boot 4.1 / Java 21, modular monolith, PostgreSQL 18 (database ride_hailing) + Redis 7 (container rhl-redis), ' +
  'REST + STOMP WebSocket, trang demo HTML tinh. Yeu cau da lam ro o docs/00-yeu-cau.md.'
);

cacPhan.push(
  'LUAT VANG (dung/sai, khong phai phong cach): ' +
  '(1) tien la BigDecimal, khong float/double; ' +
  '(2) TripStatus chi doi qua Trip.transitionTo va luon ghi trip_status_history; ' +
  '(3) giu/nhan tai xe bang cap nhat co dieu kien hoac khoa — khong "doc roi ghi"; khong bao gio double assignment; ' +
  '(4) tao chuyen, nhan offer, huy, thanh toan, ghi vi, consumer event deu idempotent; ' +
  '(5) driverId, so tien, gia, trang thai khong lay tu client; ' +
  '(6) kiem quyen o server ca vai tro lan quyen so huu; khong lo vi tri/PII ngoai pham vi chuyen; ' +
  '(7) khong tiem repository cua mien khac, event tai chinh di qua Outbox; ' +
  '(8) bi mat chi tu bien moi truong/config/application.properties (gitignored); ' +
  '(9) dinh danh tieng Anh, comment tieng Viet khong dau; ' +
  '(10) lam theo tung dot trong docs/04-chuc-nang.md, khong tu them chuc nang.'
);

const duongDanTienDo = path.join(thuMucDuAn, 'docs', 'TIEN-DO.md');
if (fs.existsSync(duongDanTienDo)) {
  try {
    // Giu TIEN-DO.md ngan: cat bot lam mat "Viec tiep theo" la dau hieu file phinh ra, phai don bot.
    const GIOI_HAN_KY_TU = 4000;
    const noiDung = fs.readFileSync(duongDanTienDo, 'utf8');
    const rutGon = noiDung.length > GIOI_HAN_KY_TU
        ? noiDung.slice(0, GIOI_HAN_KY_TU) + '\n...(cat bot, doc them o docs/TIEN-DO.md)'
        : noiDung;
    cacPhan.push('TIEN DO HIEN TAI (docs/TIEN-DO.md):\n' + rutGon);
  } catch (loi) {
    // khong doc duoc thi bo qua, khong lam gian doan phien lam viec
  }
}

cacPhan.push(
  'Truoc khi lam viec gi, doc file lien quan trong docs/. ' +
  'Go /tien-do de doi chieu tien do, /soat-dispatch sau khi sua trip/dispatch/location/pricing, /giai-thich de duoc giang lai code.'
);

process.stdout.write(JSON.stringify({
  hookSpecificOutput: {
    hookEventName: 'SessionStart',
    additionalContext: cacPhan.join('\n\n')
  }
}));
process.exit(0);
