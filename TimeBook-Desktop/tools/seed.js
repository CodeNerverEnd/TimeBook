'use strict';
/* 仅用于本地验证：写一份示例数据到 %APPDATA%\TimeBook\data.json
   运行前会把已有 data.json 备份成 data.json.seedbak */
const fs = require('fs');
const path = require('path');

const dir = path.join(process.env.APPDATA, 'TimeBook');
fs.mkdirSync(dir, { recursive: true });
const file = path.join(dir, 'data.json');
if (fs.existsSync(file)) fs.copyFileSync(file, file + '.seedbak');

const DAY = 86400000;
const d0 = new Date();
d0.setHours(0, 0, 0, 0);
const T0 = d0.getTime();
const pad = n => String(n).padStart(2, '0');
const pdk = (y, m) => y + '-' + pad(m);

const tags = [
  { id: 1, name: '工作', colorArgb: 0xFF3C6E9C | 0, note: '', archived: false, createdAt: T0 - 40 * DAY, sortOrder: 0 },
  { id: 2, name: '读书', colorArgb: 0xFF2F6B4F | 0, note: '', archived: false, createdAt: T0 - 40 * DAY, sortOrder: 1 },
  { id: 3, name: '运动', colorArgb: 0xFFB0563A | 0, note: '', archived: false, createdAt: T0 - 40 * DAY, sortOrder: 2 },
  { id: 4, name: '学习', colorArgb: 0xFF6B5B95 | 0, note: '', archived: false, createdAt: T0 - 40 * DAY, sortOrder: 3 },
  { id: 5, name: '生活', colorArgb: 0xFFB4803A | 0, note: '', archived: false, createdAt: T0 - 40 * DAY, sortOrder: 4 }
];

const items = [
  { id: 1, name: '写产品方案', colorArgb: 0xFF3C6E9C | 0, note: '', archived: false, createdAt: T0 - 40 * DAY, sortOrder: 0, targetMinutes: 90 },
  { id: 2, name: '代码评审', colorArgb: 0xFF4A5A63 | 0, note: '', archived: false, createdAt: T0 - 38 * DAY, sortOrder: 1, targetMinutes: 45 },
  { id: 3, name: '读《人类简史》', colorArgb: 0xFF2F6B4F | 0, note: '', archived: false, createdAt: T0 - 30 * DAY, sortOrder: 2, targetMinutes: 45 },
  { id: 4, name: '跑步', colorArgb: 0xFFB0563A | 0, note: '', archived: false, createdAt: T0 - 30 * DAY, sortOrder: 3, targetMinutes: 40 },
  { id: 5, name: '英语听力', colorArgb: 0xFF6B5B95 | 0, note: '', archived: false, createdAt: T0 - 25 * DAY, sortOrder: 4, targetMinutes: 30 },
  { id: 6, name: '整理与复盘', colorArgb: 0xFFB4803A | 0, note: '', archived: false, createdAt: T0 - 20 * DAY, sortOrder: 5, targetMinutes: 20 },
  { id: 7, name: '写周报', colorArgb: 0xFF2E7D6B | 0, note: '', archived: false, createdAt: T0 - 18 * DAY, sortOrder: 6, targetMinutes: 30 }
];

const itemTags = [
  { itemId: 1, tagId: 1 }, { itemId: 2, tagId: 1 }, { itemId: 2, tagId: 4 },
  { itemId: 3, tagId: 2 }, { itemId: 4, tagId: 3 }, { itemId: 5, tagId: 4 },
  { itemId: 6, tagId: 5 }, { itemId: 7, tagId: 1 }
];

let rid = 0;
const rec = (itemId, dayOffset, sh, sm, eh, em, note) => ({
  id: ++rid, itemId, startAt: T0 + dayOffset * DAY + sh * 3600000 + sm * 60000,
  endAt: T0 + dayOffset * DAY + eh * 3600000 + em * 60000, note: note || '',
  targetMinutes: 0, remindAt: null
});

const records = [
  rec(1, 0, 9, 10, 10, 40, '先把三个方向的取舍写清楚，卡在定价那段'),
  rec(2, 0, 10, 50, 11, 25, '主要看订单改价那段，提了两条'),
  rec(3, 0, 12, 30, 13, 5, '农业革命一章，写法有点啰嗦'),
  rec(5, 0, 14, 0, 14, 32, ''),
  rec(6, 0, 14, 45, 15, 0, '清桌面 + 把本周待办过了一遍'),
  rec(1, -1, 9, 20, 11, 0, '把附录的数据表补完了'),
  rec(4, -1, 18, 30, 19, 12, '慢跑 5 公里'),
  rec(3, -1, 21, 0, 21, 40, ''),
  rec(2, -2, 10, 0, 10, 50, ''),
  rec(7, -2, 16, 0, 16, 35, ''),
  rec(5, -2, 8, 20, 8, 50, '通勤路上听'),
  rec(1, -3, 9, 0, 11, 30, ''),
  rec(4, -3, 19, 0, 19, 45, ''),
  rec(3, -4, 22, 0, 22, 50, ''),
  rec(2, -5, 14, 0, 15, 10, ''),
  rec(6, -6, 17, 30, 17, 55, ''),
  rec(1, -9, 9, 30, 12, 0, ''),
  rec(3, -9, 20, 0, 21, 0, ''),
  rec(1, -12, 10, 0, 11, 20, ''),
  rec(4, -12, 18, 0, 18, 40, ''),
  rec(5, -14, 8, 30, 9, 0, ''),
  rec(2, -15, 15, 0, 16, 0, ''),
  rec(1, -18, 9, 0, 10, 30, ''),
  rec(3, -20, 21, 30, 22, 20, ''),
  rec(4, -21, 7, 30, 8, 10, '晨跑'),
  rec(7, -22, 16, 0, 16, 40, '')
];

const now = new Date();
const MK = pdk(now.getFullYear(), now.getMonth() + 1);
const YK = String(now.getFullYear());
const prev = new Date(now.getFullYear(), now.getMonth() - 1, 1);
const PK = pdk(prev.getFullYear(), prev.getMonth() + 1);

const plans = [
  { refId: 1, targetType: 'TAG', periodType: 'MONTH', periodKey: MK, targetMinutes: 3600 },
  { refId: 2, targetType: 'TAG', periodType: 'MONTH', periodKey: MK, targetMinutes: 1200 },
  { refId: 3, targetType: 'TAG', periodType: 'MONTH', periodKey: MK, targetMinutes: 600 },
  { refId: 4, targetType: 'TAG', periodType: 'MONTH', periodKey: MK, targetMinutes: 900 },
  { refId: 1, targetType: 'ITEM', periodType: 'MONTH', periodKey: MK, targetMinutes: 1200 },
  { refId: 1, targetType: 'TAG', periodType: 'YEAR', periodKey: YK, targetMinutes: 42000 },
  { refId: 2, targetType: 'TAG', periodType: 'YEAR', periodKey: YK, targetMinutes: 12000 },
  { refId: 3, targetType: 'TAG', periodType: 'YEAR', periodKey: YK, targetMinutes: 7000 },
  { refId: 4, targetType: 'TAG', periodType: 'YEAR', periodKey: YK, targetMinutes: 9000 },
  { refId: 1, targetType: 'TAG', periodType: 'FIVE', periodKey: '2026-2030', targetMinutes: 210000 },
  { refId: 2, targetType: 'TAG', periodType: 'FIVE', periodKey: '2026-2030', targetMinutes: 60000 }
];

const planDocs = [
  {
    periodType: 'MONTH', periodKey: PK, status: 'CLOSED', closedAt: T0 - 3 * DAY,
    summary: '上个月整体节奏比前一个月稳，工作占了 62 小时，但读写时间被挤掉了。\n这个月想把「读书」从每周 2 次提到 4 次，跑步保持每周 3 次。',
    snapshot: {
      label: prev.getFullYear() + '年' + (prev.getMonth() + 1) + '月',
      totalTargetMinutes: 5700,
      totalUsedMillis: 5305 * 60000,
      recordCount: 42,
      rows: [
        { name: '工作', colorArgb: 0xFF3C6E9C | 0, targetMinutes: 3600, usedMillis: 3720 * 60000, count: 21, items: [] },
        { name: '读书', colorArgb: 0xFF2F6B4F | 0, targetMinutes: 900, usedMillis: 610 * 60000, count: 8, items: [] },
        { name: '运动', colorArgb: 0xFFB0563A | 0, targetMinutes: 600, usedMillis: 520 * 60000, count: 7, items: [] },
        { name: '学习', colorArgb: 0xFF6B5B95 | 0, targetMinutes: 600, usedMillis: 455 * 60000, count: 6, items: [] }
      ]
    }
  }
];

fs.writeFileSync(file, JSON.stringify({ tags, items, itemTags, records, plans, planDocs }, null, 2), 'utf8');
console.log('seed ok ->', file, '| records', records.length);
