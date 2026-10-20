#!/usr/bin/env node
/*
 * PostToolUse hook - runs after every file write or edit.
 *
 * Scope: catch things that make the system WRONG under the SRS (money precision,
 * state machine bypass, cross-domain coupling, leaked secrets). This hook is
 * deliberately NOT a style checker. Lombok, Stream API, records, method length -
 * none of that is its business.
 *
 * Every rule must be precise enough that a hit is almost always a real problem.
 * A checker that cries wolf gets ignored, and then it catches nothing.
 *
 * Exit codes:
 *   0 = nothing to report
 *   2 = findings on stderr, fed back to Claude so it can fix them
 */

const fs = require('fs');

const BASE_PACKAGE = 'org.example.ride_hailing_logistics';
const DOMAINS = ['identity', 'driver', 'location', 'pricing', 'trip', 'payment', 'wallet', 'platform', 'realtime'];

let input = '';
process.stdin.on('data', (chunk) => (input += chunk));
process.stdin.on('end', () => {
  let payload;
  try {
    payload = JSON.parse(input.replace(/^﻿/, ''));
  } catch (err) {
    process.exit(0);
  }

  const rawPath = (payload.tool_input && payload.tool_input.file_path) || '';
  if (!rawPath || !fs.existsSync(rawPath)) process.exit(0);

  let content;
  try {
    content = fs.readFileSync(rawPath, 'utf8');
  } catch (err) {
    process.exit(0);
  }

  const path = rawPath.split('\\').join('/');
  const violations = [];
  const warnings = [];

  if (path.endsWith('.java')) {
    checkJava(path, content, violations, warnings);
  } else if (/src\/main\/resources\/application[^/]*\.properties$/.test(path)) {
    checkAppConfig(content, violations);
  } else if (path.endsWith('.html')) {
    checkPage(content, warnings);
  }

  if (violations.length === 0 && warnings.length === 0) process.exit(0);

  const out = ['[Project rules] ' + path];
  violations.forEach((v) => out.push('  VIOLATION: ' + v));
  warnings.forEach((w) => out.push('  CHECK: ' + w));
  out.push('  (Rules: .claude/rules/quy-tac-code.md, .claude/rules/bao-mat.md)');
  console.error(out.join('\n'));
  process.exit(2);
});

/* ------------------------------------------------------------------ */

/**
 * Strip comments and string literals so a rule never fires on prose.
 * Mentioning "setStatus" in a comment is fine; calling it is the violation.
 */
function stripCommentsAndStrings(src) {
  return src
    .replace(/"""[\s\S]*?"""/g, '""')
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/[^\n]*/g, ' ')
    .replace(/"(?:[^"\\\n]|\\.)*"/g, '""');
}

function domainOf(path) {
  const m = path.match(/ride_hailing_logistics\/([a-z_]+)\//);
  return m ? m[1] : null;
}

function checkJava(path, content, violations, warnings) {
  const code = stripCommentsAndStrings(content);
  const isTest = path.includes('/src/test/');
  const domain = domainOf(path);

  // 1. No float anywhere. Money must never be float/double (FR-PRI-016, FR-WAL-008).
  const floatLine = lineOf(code, /(^|[^.\w])float\s+\w/);
  if (floatLine > 0) {
    violations.push('line ' + floatLine + ' declares a "float". Money is BigDecimal; coordinates/distances are double.');
  }
  const moneyName = /(amount|fare|price|fee|balance|commission|subtotal|earning|total)\w*/i;
  const doubleMoney = lineOfMatch(code, /\b(double|Double)\s+(\w+)\s*[;=,)]/g, (m) => moneyName.test(m[2]) && !/(distance|meters|km|seconds|minutes|weight|kg)/i.test(m[2]));
  if (doubleMoney > 0 && !isTest) {
    violations.push('line ' + doubleMoney + ' stores a money-like value as double. Use BigDecimal (FR-PRI-016).');
  }

  // 2. Trip status only changes through the state machine (FR-TRIP-008..010).
  if (!isTest && !/\/trip\/Trip\.java$/.test(path)) {
    const bypass = lineOf(code, /\.setStatus\s*\(\s*TripStatus\./);
    if (bypass > 0) {
      violations.push(
        'line ' + bypass + ' sets TripStatus directly. Go through Trip.transitionTo(...) so the ' +
        'transition is validated and recorded in trip_status_history.'
      );
    }
  }

  // 3. Domain boundaries (CON-02, DR-002): never inject another domain's repository.
  if (domain && DOMAINS.includes(domain) && !isTest) {
    const re = new RegExp('import\\s+' + BASE_PACKAGE.replace(/\./g, '\\.') + '\\.([a-z_]+)\\.[\\w.]*Repository\\s*;', 'g');
    let m;
    while ((m = re.exec(code)) !== null) {
      if (m[1] !== domain) {
        violations.push(
          'imports a repository of domain "' + m[1] + '" from domain "' + domain + '". Call that ' +
          "domain's public service or listen to its event instead (CON-02)."
        );
      }
    }
  }

  // 4. Enum persisted by ordinal breaks silently when a constant is inserted.
  if (/EnumType\.ORDINAL/.test(code)) {
    violations.push('EnumType.ORDINAL - use EnumType.STRING.');
  }

  // 5. Hard-coded secrets (NFR-SEC-003). Check raw content: the value IS a string literal.
  if (!isTest) {
    const secret = content.match(/(secret|password|apiKey|signingKey)\w*\s*=\s*"[^"$]{8,}"/i);
    if (secret) {
      violations.push('looks like a hard-coded secret: ' + secret[0].slice(0, 40) + '... Read it from configuration.');
    }
  }

  // 6. Entities: @Data generates equals/hashCode over every field (lazy loading, mutable ids).
  if (/@Entity\b/.test(code) && /@Data\b/.test(code)) {
    warnings.push('@Data on a JPA entity. Use @Getter @Setter.');
  }

  // 7. Time is stored in UTC (DR-007). LocalDateTime on an entity loses the zone.
  if (/@Entity\b/.test(code) && /\bLocalDateTime\b/.test(code)) {
    warnings.push('entity uses LocalDateTime. Store Instant (timestamptz, UTC).');
  }

  // 8. JPQL built by concatenation is an injection risk.
  if (/@Query\s*\(\s*(value\s*=\s*)?""\s*\+\s*\w/.test(code)) {
    warnings.push('@Query built by concatenating a variable. Use :named parameters.');
  }

  // 9. System.out in application code - use @Slf4j.
  const hasMain = /static\s+void\s+main\s*\(/.test(code);
  if (!isTest && !hasMain && /System\.out\.print/.test(code)) {
    warnings.push('System.out.print in application code. Use Lombok @Slf4j.');
  }
}

function checkAppConfig(content, violations) {
  // Secrets in the committed config must be placeholders (NFR-SEC-003).
  const keys = ['spring.datasource.password', 'app.security.jwt.secret', 'app.payment.sandbox.secret'];
  content.split(/\r?\n/).forEach((line, i) => {
    const m = line.match(/^\s*([\w.-]+)\s*=\s*(.*)$/);
    if (!m) return;
    if (keys.includes(m[1]) && m[2].trim() !== '' && !/^\$\{[A-Z0-9_]+\}$/.test(m[2].trim())) {
      violations.push('line ' + (i + 1) + ': ' + m[1] + ' must be ${ENV_VAR} without a default. Put local values in config/application.properties (gitignored).');
    }
  });
}

function checkPage(content, warnings) {
  if (/<head[\s>]/i.test(content) && !/charset\s*=\s*["']?UTF-8/i.test(content)) {
    warnings.push('page has a <head> but no <meta charset="UTF-8">.');
  }
  // Demo pages render server data; innerHTML with it is an XSS hole (NFR-SEC-006).
  if (/\.innerHTML\s*=\s*[^'"`;]*\b(data|msg|payload|trip|offer|res)\b/.test(content)) {
    warnings.push('innerHTML assigned from server data. Use textContent or escape first.');
  }
}

/* ------------------------------------------------------------------ */

function lineOf(code, pattern) {
  const lines = code.split('\n');
  for (let i = 0; i < lines.length; i++) {
    if (pattern.test(lines[i])) return i + 1;
  }
  return 0;
}

function lineOfMatch(code, globalPattern, predicate) {
  const lines = code.split('\n');
  for (let i = 0; i < lines.length; i++) {
    globalPattern.lastIndex = 0;
    let m;
    while ((m = globalPattern.exec(lines[i])) !== null) {
      if (predicate(m)) return i + 1;
    }
  }
  return 0;
}
