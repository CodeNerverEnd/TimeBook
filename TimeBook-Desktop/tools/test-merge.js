'use strict';
/* 合并导入逻辑自测：node tools/test-merge.js */

const TB = require('../src/core.js');
const assert = require('assert');

const T0 = new Date(2026, 10, 24, 9, 0, 0).getTime();
const H = 3600000;

/* 本机：标签 工作(1) / 学习(2)，事项 写方案(1) 挂工作，2 条记录，一条月计划 */
const local = {
  tags: [
    { id: 1, name: '工作', colorArgb: 0xFF3C6E9C | 0, note: '', archived: false, createdAt: T0, sortOrder: 0 },
    { id: 2, name: '学习', colorArgb: 0xFF6B5B95 | 0, note: '', archived: false, createdAt: T0, sortOrder: 1 }
  ],
  items: [
    { id: 1, name: '写方案', colorArgb: 0xFF3C6E9C | 0, note: '', archived: false, createdAt: T0, sortOrder: 0, targetMinutes: 120 }
  ],
  records: [
    { id: 1, itemId: 1, startAt: T0, endAt: T0 + H, note: '顺利', targetMinutes: 120, remindAt: null },
    { id: 2, itemId: 1, startAt: T0 + 3 * H, endAt: T0 + 4 * H, note: '', targetMinutes: 0, remindAt: null }
  ],
  plans: [{ refId: 1, targetType: 'TAG', periodType: 'MONTH', periodKey: '2026-11', targetMinutes: 2400 }],
  planDocs: [],
  itemTags: [{ itemId: 1, tagId: 1 }]
};

/* 备份文件：id 全部与手机侧撞车，但内容不同 */
const incoming = {
  tags: [
    { id: 1, name: '阅读', colorArgb: 0xFF2F6B4F | 0, note: '', archived: false, createdAt: T0, sortOrder: 0 },  // id 撞了，名字不同 → 新标签
    { id: 2, name: '工作', colorArgb: 0xFF3C6E9C | 0, note: '', archived: false, createdAt: T0, sortOrder: 1 }   // 同名 → 复用本机 id 1
  ],
  items: [
    { id: 1, name: '读书', colorArgb: 0xFF2F6B4F | 0, note: '', archived: false, createdAt: T0, sortOrder: 0, targetMinutes: 0 },
    { id: 2, name: '写方案', colorArgb: 0xFF3C6E9C | 0, note: '', archived: false, createdAt: T0, sortOrder: 1, targetMinutes: 120 }
  ],
  records: [
    { id: 1, itemId: 2, startAt: T0, endAt: T0 + H, note: '顺利', targetMinutes: 120, remindAt: null },       // 与本机重复
    { id: 2, itemId: 2, startAt: T0 + 5 * H, endAt: T0 + 6 * H, note: '被电话打断', targetMinutes: 0, remindAt: null },
    { id: 3, itemId: 1, startAt: T0 + H, endAt: T0 + 2 * H, note: '第一章', targetMinutes: 0, remindAt: null },
    { id: 4, itemId: 1, startAt: T0 + 8 * H, endAt: null, note: '', targetMinutes: 0, remindAt: null }         // 未结束 → 跳过
  ],
  plans: [
    { refId: 2, targetType: 'TAG', periodType: 'MONTH', periodKey: '2026-11', targetMinutes: 3000 },            // 工作标签（本机 id=1）冲突
    { refId: 1, targetType: 'ITEM', periodType: 'MONTH', periodKey: '2026-11', targetMinutes: 600 }             // 读书（新事项）→ 新增
  ],
  planDocs: [
    { periodType: 'MONTH', periodKey: '2026-10', status: 'CLOSED', summary: '上个月还行', closedAt: T0, snapshot: { label: '2026年10月' } }
  ],
  itemTags: [{ itemId: 1, tagId: 1 }, { itemId: 2, tagId: 1 }, { itemId: 2, tagId: 2 }]
};

const r1 = TB.mergeData(local, incoming, { prefer: 'local' });
const d1 = r1.data;
const rep1 = r1.report;

assert.strictEqual(rep1.tagsAdded, 1, '应新增 1 个标签（阅读）');
assert.strictEqual(rep1.tagsMatched, 1, '应复用 1 个标签（工作）');
assert.strictEqual(d1.tags.length, 3);
const reading = d1.tags.find(t => t.name === '阅读');
assert.ok(reading && reading.id === 3, '新标签应拿到全新 id=3');
assert.strictEqual(d1.tags.find(t => t.name === '工作').id, 1, '工作标签保持 id=1');

assert.strictEqual(rep1.itemsAdded, 1, '应新增 1 个事项（读书）');
assert.strictEqual(rep1.itemsMatched, 1, '写方案应复用');
const book = d1.items.find(i => i.name === '读书');
assert.ok(book && book.id === 2, '读书应拿到 id=2');
assert.strictEqual(book.sortOrder, 1, '新事项排在本机事项后面');

assert.strictEqual(rep1.recordsAdded, 2, '应新增 2 条记录');
assert.strictEqual(rep1.recordsSkipped, 1, '应跳过 1 条重复记录');
assert.strictEqual(rep1.runningSkipped, 1, '应跳过 1 条未结束记录');
assert.strictEqual(d1.records.length, 4);
assert.strictEqual(d1.records.filter(r => r.endAt == null).length, 0, '合并后不应出现未结束记录');
const bookRec = d1.records.find(r => r.itemId === 2 && r.startAt === T0 + H);
assert.ok(bookRec, '读书的记录应挂到新事项 id=2 上');
assert.strictEqual(bookRec.note, '第一章', '备注要跟着记录走');
assert.ok(d1.records.every(r => r.id > 0 && d1.records.filter(x => x.id === r.id).length === 1), '记录 id 不能重复');

assert.strictEqual(rep1.plansAdded, 1, '应新增 1 条事项目标');
assert.strictEqual(rep1.plansConflict, 1, '工作标签目标冲突 1 条');
const tagPlan = d1.plans.find(p => p.targetType === 'TAG' && p.periodKey === '2026-11');
assert.strictEqual(tagPlan.targetMinutes, 2400, '冲突时默认保留本机目标');
const itemPlan = d1.plans.find(p => p.targetType === 'ITEM');
assert.strictEqual(itemPlan.refId, 2, '事项目标应重映射到本机事项 id（读书 → 2）');

assert.strictEqual(rep1.docsAdded, 1, '应补进 1 条已归档期');

assert.strictEqual(rep1.linksAdded, 2, '应补 2 条标签关联');
assert.strictEqual(d1.itemTags.length, 3);
assert.ok(d1.itemTags.some(l => l.itemId === 2 && l.tagId === 3), '读书应挂到阅读标签');

/* 反向策略：冲突以文件为准 */
const r2 = TB.mergeData(local, incoming, { prefer: 'import' });
assert.strictEqual(r2.report.plansUpdated, 1);
assert.strictEqual(r2.data.plans.find(p => p.targetType === 'TAG').targetMinutes, 3000);

/* 幂等：同一份文件再合并一次，不应再长数据 */
const r3 = TB.mergeData(d1, incoming, { prefer: 'local' });
assert.strictEqual(r3.report.itemsAdded, 0, '二次合并不应新增事项');
assert.strictEqual(r3.report.recordsAdded, 0, '二次合并不应新增记录');
assert.strictEqual(r3.report.plansAdded, 0, '二次合并不应新增计划');
assert.strictEqual(r3.report.tagsAdded, 0, '二次合并不应新增标签');
assert.strictEqual(r3.data.records.length, d1.records.length);

/* 空文件不炸 */
const r4 = TB.mergeData(local, TB.emptyData(), {});
assert.strictEqual(r4.data.records.length, local.records.length);
assert.strictEqual(r4.data.tags.length, local.tags.length);

/* 真实备份往返：buildBackup → parseBackup → merge 幂等 */
const backup = JSON.parse(JSON.stringify(TB.buildBackup(local, T0)));
const parsedBack = TB.parseBackup(backup);
const r5 = TB.mergeData(local, parsedBack, { prefer: 'local' });
assert.strictEqual(r5.report.recordsAdded, 0, '自己导出再合并进来不应新增记录');
assert.strictEqual(r5.report.itemsAdded, 0);
assert.strictEqual(r5.report.plansConflict, 0);
assert.strictEqual(r5.data.records.length, 2);

console.log('合并逻辑自测通过：');
console.log(JSON.stringify({ first: rep1, idempotent: r3.report, selfImport: r5.report }, null, 2));
