#!/usr/bin/env node
// 炙巷食谱 - 固定开销自动记账脚本
// 用法：
//   node auto_fixed_expense.js daily     每天跑：每月1号记店租/员工薪资/老板薪资；炭火每3天记一笔
//   node auto_fixed_expense.js backfill  8月补记：8/26-8/31 共6天，店租+员工薪资按天折算
//   node auto_fixed_expense.js list      列出已自动记账的记录（验证用）

const fs = require('fs');
const os = require('os');
const path = require('path');

// ---------- 配置 ----------
const FIXED = {
  rent:        { name: '店租',     type: '租金',     amount: 1800 },
  staffSalary: { name: '员工薪资', type: '员工',     amount: 1800 },
  bossSalary:  { name: '老板薪资', type: '老板薪资', amount: 3000 },
  charcoal:    { name: '炭火',     type: '炭火',     amount: 38 },
};
const CHARCOAL_START = '2026-09-01'; // 炭火起始日，每3天记一笔
const CHARCOAL_INTERVAL = 3;
// ---------- 配置结束 ----------

function loadEnv() {
  const envPath = path.join(os.homedir(), '.openclaw', 'workspace', '.env');
  const txt = fs.readFileSync(envPath, 'utf8');
  const env = {};
  for (const line of txt.split('\n')) {
    const m = line.match(/^([A-Za-z_][A-Za-z0-9_]*)=(.*)$/);
    if (m) env[m[1]] = m[2].replace(/^["']|["']$/g, '');
  }
  return env;
}
const env = loadEnv();
const URL = env.ZXSP_SUPABASE_URL;
const KEY = env.ZXSP_SUPABASE_SERVICE_ROLE_KEY;

// 今天（Asia/Kuala_Lumpur 时区）→ {y,m,d,str}
function todayKL() {
  const s = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Kuala_Lumpur',
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date());
  const [y, m, d] = s.split('-').map(Number);
  return { y, m, d, str: s };
}

function daysBetween(d1, d2) {
  const a = new Date(d1 + 'T00:00:00Z');
  const b = new Date(d2 + 'T00:00:00Z');
  return Math.round((b - a) / 86400000);
}

async function api(method, pathSuffix, body) {
  const headers = {
    'apikey': KEY,
    'Authorization': `Bearer ${KEY}`,
    'Content-Type': 'application/json',
  };
  if (method === 'POST') headers['Prefer'] = 'return=representation';
  const resp = await fetch(`${URL}/rest/v1/${pathSuffix}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  return resp;
}

// 查日志（是否已记）→ 返回 false=未记过，对象=已记过；查询失败抛异常
async function logExists(name, date) {
  const resp = await api('GET', `fixed_expense_log?expense_name=eq.${encodeURIComponent(name)}&period_date=eq.${date}&select=id`);
  if (!resp.ok) throw new Error(`日志查询失败 HTTP ${resp.status}`);
  const arr = await resp.json();
  return arr && arr.length ? arr[0] : false;
}

// 记一笔固定开销（幂等：先占日志位，失败则回滚）
async function record(name, type, amount, periodDate, datetimeIso) {
  let exist;
  try { exist = await logExists(name, periodDate); }
  catch (e) { console.error(`  ✗ ${e.message}，跳过：${name} ${periodDate}`); return false; }
  if (exist) { console.log(`  - 已记过，跳过：${name} ${periodDate}`); return true; }

  // 1. 先占日志位（UNIQUE 幂等）
  const logResp = await api('POST', 'fixed_expense_log', {
    expense_name: name, expense_type: type, amount_myr: amount, period_date: periodDate,
  });
  if (!logResp.ok) { console.error(`  ✗ 日志占位失败：${name} ${periodDate} -> ${logResp.status}`); return false; }

  // 2. 记 expense_records
  const body = {
    expense_title: name,
    expense_type: type,
    amount_myr: amount,
    expense_currency: 'MYR',
    pay_method: 'cash',
    transaction_ref: '',
    is_personal: false,
    operate_staff_id: 1,
    transaction_datetime: datetimeIso,
    notes: '自动记账',
  };
  const recResp = await api('POST', 'expense_records', body);
  if (!recResp.ok) {
    // 回滚日志占位
    const recArr = await logExists(name, periodDate);
    // 占位那条是刚插入的，删掉它
    const delResp = await api('DELETE', `fixed_expense_log?expense_name=eq.${encodeURIComponent(name)}&period_date=eq.${periodDate}`);
    console.error(`  ✗ 记账失败：${name} ${periodDate} -> ${recResp.status} ${await recResp.text()}（日志已回滚${delResp.ok ? '' : '，但回滚失败请手动检查'}）`);
    return false;
  }
  console.log(`  ✓ 已记：${name} RM${amount} @ ${periodDate}`);
  return true;
}

async function main() {
  const mode = process.argv[2] || 'daily';

  if (mode === 'backfill') {
    // 8月补记：8/26-8/31 共6天，店租 + 员工薪资按天折算 1800*6/30=360
    console.log('=== 8月补记（8/26-8/31，按天折算）===');
    const dt = '2026-08-31T12:00:00+08:00';
    await record(FIXED.rent.name, FIXED.rent.type, 360, '2026-08-31', dt);
    await record(FIXED.staffSalary.name, FIXED.staffSalary.type, 360, '2026-08-31', dt);
    console.log('补记完成');
    return;
  }

  if (mode === 'list') {
    const resp = await api('GET', 'fixed_expense_log?select=*&order=period_date.desc');
    const arr = await resp.json();
    console.log('已自动记账记录：');
    if (Array.isArray(arr) && arr.length) {
      for (const r of arr) console.log(`  ${r.period_date}  ${r.expense_name}  RM${r.amount_myr}`);
    } else {
      console.log('  （空）');
    }
    return;
  }

  // daily
  const t = todayKL();
  console.log(`=== 每日固定开销记账 ${t.str} ===`);

  // 每月1号
  if (t.d === 1) {
    await record(FIXED.rent.name, FIXED.rent.type, FIXED.rent.amount, t.str, `${t.str}T12:00:00+08:00`);
    await record(FIXED.staffSalary.name, FIXED.staffSalary.type, FIXED.staffSalary.amount, t.str, `${t.str}T12:00:00+08:00`);
    await record(FIXED.bossSalary.name, FIXED.bossSalary.type, FIXED.bossSalary.amount, t.str, `${t.str}T12:00:00+08:00`);
  }

  // 炭火每3天（从 CHARCOAL_START 起）
  const dd = daysBetween(CHARCOAL_START, t.str);
  if (dd >= 0 && dd % CHARCOAL_INTERVAL === 0) {
    await record(FIXED.charcoal.name, FIXED.charcoal.type, FIXED.charcoal.amount, t.str, `${t.str}T12:00:00+08:00`);
  }

  console.log('完成');
}

main().catch(e => { console.error(e); process.exit(1); });
