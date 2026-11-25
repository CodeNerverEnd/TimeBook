/* =====================================================================
 * TimeBook 桌面版 · 统计与计划核心
 * 与 Android 版 util/Stats.kt、util/Plan.kt 逐条对齐：
 *   - 跨零点记录按「实际重叠时长」拆分，日/月/年不会重复也不会漏
 *   - 一个事项挂多个标签时，每个标签各分到这段时间（标签合计可能大于总时长）
 *   - 计划两级：标签目标计入总目标；事项目标在所属标签也有目标时按拆解，不重复计入
 *   - 五年周期固定从 2021 起算
 * 这一层不碰 DOM、不碰 Electron，方便单独验证。
 * ===================================================================== */

const DAY = 86400000;
const FIVE_YEAR_BASE = 2021;

const pad2 = n => String(n).padStart(2, '0');
const startOfDay = d => { const x = new Date(d); x.setHours(0, 0, 0, 0); return x.getTime(); };
const endOfDay = d => startOfDay(d) + DAY;

/** 01:25:03 */
function clockText(ms) {
  const t = Math.max(0, Math.floor(ms / 1000));
  return pad2(Math.floor(t / 3600)) + ':' + pad2(Math.floor((t % 3600) / 60)) + ':' + pad2(t % 60);
}

/** 1小时25分 / 25分 / 40秒 */
function humanText(ms) {
  const t = Math.max(0, ms);
  if (t < 1000) return '0分';
  const m = Math.floor(t / 60000);
  if (m < 1) return Math.floor(t / 1000) + '秒';
  const h = Math.floor(m / 60), mm = m % 60;
  if (h && mm) return h + '小时' + mm + '分';
  if (h) return h + '小时';
  return mm + '分';
}

/** 08:05 */
function hmText(ts) {
  const d = new Date(ts);
  return pad2(d.getHours()) + ':' + pad2(d.getMinutes());
}

function dateLabel(d) {
  const w = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][new Date(d).getDay()];
  return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日 ' + w;
}

const endOf = (r, now) => (r.endAt == null ? now : r.endAt);

/** 记录与区间的重叠毫秒数 */
function overlapMillis(r, rangeStart, rangeEnd, now) {
  return Math.max(0, Math.min(endOf(r, now), rangeEnd) - Math.max(r.startAt, rangeStart));
}

/* ---------------------------------------------------------------- 统计 */

/** mode: 'day' | 'month' | 'year' */
function statsRange(mode, anchor) {
  const d = new Date(anchor);
  if (mode === 'day') return { start: startOfDay(d), end: startOfDay(d) + DAY };
  if (mode === 'month') {
    return {
      start: new Date(d.getFullYear(), d.getMonth(), 1).getTime(),
      end: new Date(d.getFullYear(), d.getMonth() + 1, 1).getTime()
    };
  }
  return {
    start: new Date(d.getFullYear(), 0, 1).getTime(),
    end: new Date(d.getFullYear() + 1, 0, 1).getTime()
  };
}

function statsLabel(mode, anchor) {
  const d = new Date(anchor);
  if (mode === 'day') return d.getFullYear() + '年' + (d.getMonth() + 1) + '月' + d.getDate() + '日';
  if (mode === 'month') return d.getFullYear() + '年' + (d.getMonth() + 1) + '月';
  return d.getFullYear() + '年';
}

/**
 * 统计结果：
 *  totalMillis / recordCount / itemTotals / tagTotals / buckets
 */
function computeStats(data, mode, anchor, now) {
  const { start, end } = statsRange(mode, anchor);
  const itemMap = new Map(data.items.map(i => [i.id, i]));
  const totals = new Map();
  const counts = new Map();
  let sum = 0, count = 0;

  for (const r of data.records) {
    const s = Math.max(r.startAt, start);
    const e = Math.min(endOf(r, now), end);
    if (e <= s) continue;
    const delta = e - s;
    totals.set(r.itemId, (totals.get(r.itemId) || 0) + delta);
    counts.set(r.itemId, (counts.get(r.itemId) || 0) + 1);
    sum += delta;
    count++;
  }

  const itemTotals = [...totals.entries()]
    .filter(([id]) => itemMap.has(id))
    .map(([id, ms]) => ({ item: itemMap.get(id), millis: ms, count: counts.get(id) || 0 }))
    .sort((a, b) => b.millis - a.millis || a.item.name.localeCompare(b.item.name, 'zh'));

  // 标签汇总：把事项用时累加到它挂的每个标签上
  const tagMap = new Map(data.tags.map(t => [t.id, t]));
  const itemTotalIndex = new Map(itemTotals.map(it => [it.item.id, it]));
  const itemsByTag = new Map();
  for (const link of data.itemTags) {
    const it = itemTotalIndex.get(link.itemId);
    if (!it || !tagMap.has(link.tagId)) continue;
    if (!itemsByTag.has(link.tagId)) itemsByTag.set(link.tagId, []);
    itemsByTag.get(link.tagId).push(it);
  }
  const tagTotals = [...itemsByTag.entries()].map(([tagId, list]) => {
    const items = list.slice().sort((a, b) => b.millis - a.millis);
    return {
      tag: tagMap.get(tagId),
      millis: items.reduce((a, b) => a + b.millis, 0),
      count: items.reduce((a, b) => a + b.count, 0),
      itemCount: items.length,
      items
    };
  }).sort((a, b) => b.millis - a.millis || a.tag.name.localeCompare(b.tag.name, 'zh'));

  // 趋势桶
  const buckets = { labels: [], values: [] };
  if (mode === 'month') {
    const days = new Date(anchor.getFullYear(), anchor.getMonth() + 1, 0).getDate();
    for (let d = 1; d <= days; d++) { buckets.labels.push(String(d)); buckets.values.push(0); }
  } else if (mode === 'year') {
    for (let m = 1; m <= 12; m++) { buckets.labels.push(m + '月'); buckets.values.push(0); }
  } else {
    for (let h = 0; h < 24; h++) { buckets.labels.push(pad2(h)); buckets.values.push(0); }
  }

  for (const r of data.records) {
    let cursor = Math.max(r.startAt, start);
    const stop = Math.min(endOf(r, now), end);
    if (stop <= cursor) continue;
    let guard = 0;
    while (cursor < stop && guard++ < 10000) {
      const d0 = startOfDay(cursor);
      let segEnd;
      if (mode === 'day') {
        segEnd = d0 + (new Date(cursor).getHours() + 1) * 3600000;
      } else {
        segEnd = d0 + DAY;
      }
      segEnd = Math.min(segEnd, stop);
      if (segEnd <= cursor) break;
      const idx = mode === 'month'
        ? new Date(d0).getDate() - 1
        : mode === 'year' ? new Date(d0).getMonth() : new Date(cursor).getHours();
      if (idx >= 0 && idx < buckets.values.length) buckets.values[idx] += segEnd - cursor;
      cursor = segEnd;
    }
  }

  return {
    rangeLabel: statsLabel(mode, anchor),
    start, end, totalMillis: sum, recordCount: count,
    itemTotals, tagTotals,
    bucketLabels: buckets.labels, bucketValues: buckets.values
  };
}

/* ---------------------------------------------------------------- 计划 */

function fiveStartYear(year) {
  const diff = year - FIVE_YEAR_BASE;
  const offset = diff >= 0 ? Math.floor(diff / 5) * 5 : -Math.ceil(-diff / 5) * 5;
  return FIVE_YEAR_BASE + offset;
}

/** period: 'MONTH' | 'YEAR' | 'FIVE' */
function planKey(period, date) {
  const y = date.getFullYear();
  const m = date.getMonth() + 1;
  if (period === 'MONTH') return y + '-' + pad2(m);
  if (period === 'YEAR') return String(y);
  const s = fiveStartYear(y);
  return s + '-' + (s + 4);
}

function planLabel(period, date) {
  const y = date.getFullYear();
  if (period === 'MONTH') return y + '年' + (date.getMonth() + 1) + '月';
  if (period === 'YEAR') return y + '年';
  const s = fiveStartYear(y);
  return s + '–' + (s + 4) + '年';
}

function planRange(period, date) {
  const y = date.getFullYear();
  const m = date.getMonth();
  if (period === 'MONTH') return { start: new Date(y, m, 1).getTime(), end: new Date(y, m + 1, 1).getTime() };
  if (period === 'YEAR') return { start: new Date(y, 0, 1).getTime(), end: new Date(y + 1, 0, 1).getTime() };
  const s = fiveStartYear(y);
  return { start: new Date(s, 0, 1).getTime(), end: new Date(s + 5, 0, 1).getTime() };
}

function shiftPlanDate(period, date, delta) {
  const d = new Date(date);
  if (period === 'MONTH') return new Date(d.getFullYear(), d.getMonth() + delta, 1);
  if (period === 'YEAR') return new Date(d.getFullYear() + delta, 0, 1);
  return new Date(d.getFullYear() + delta * 5, 0, 1);
}

function planPeriodFromKey(type, key) {
  if (type === 'MONTH') {
    const [y, m] = key.split('-').map(Number);
    if (!y || !m) return null;
    return { period: 'MONTH', key, label: planLabel('MONTH', new Date(y, m - 1, 1)), ...planRange('MONTH', new Date(y, m - 1, 1)) };
  }
  if (type === 'YEAR') {
    const y = Number(key);
    if (!y) return null;
    return { period: 'YEAR', key, label: planLabel('YEAR', new Date(y, 0, 1)), ...planRange('YEAR', new Date(y, 0, 1)) };
  }
  const s = Number(String(key).split('-')[0]);
  if (!s) return null;
  return { period: 'FIVE', key, label: planLabel('FIVE', new Date(s, 0, 1)), ...planRange('FIVE', new Date(s, 0, 1)) };
}

/**
 * 计划 vs 实际。
 * 返回：totalTarget / tagTarget / orphanItemTarget / used / rows
 * rows[i] = { tag, target, used, count, itemCount, items:[{item, used, count, target}] }
 */
function computePlan(data, period, date, now) {
  const key = planKey(period, date);
  const { start, end } = planRange(period, date);

  const itemMillis = new Map();
  const itemCounts = new Map();
  for (const r of data.records) {
    const s = Math.max(r.startAt, start);
    const e = Math.min(endOf(r, now), end);
    if (e <= s) continue;
    itemMillis.set(r.itemId, (itemMillis.get(r.itemId) || 0) + (e - s));
    itemCounts.set(r.itemId, (itemCounts.get(r.itemId) || 0) + 1);
  }

  const periodTargets = data.plans.filter(p => p.periodType === period && p.periodKey === key);
  const tagTargets = new Map();
  const itemTargets = new Map();
  for (const p of periodTargets) {
    if (p.targetType === 'ITEM') itemTargets.set(p.refId, p.targetMinutes);
    else tagTargets.set(p.refId, p.targetMinutes);
  }

  const tagIdsOfItem = new Map();
  const itemsOfTag = new Map();
  for (const link of data.itemTags) {
    if (!tagIdsOfItem.has(link.itemId)) tagIdsOfItem.set(link.itemId, []);
    tagIdsOfItem.get(link.itemId).push(link.tagId);
    if (!itemsOfTag.has(link.tagId)) itemsOfTag.set(link.tagId, []);
    itemsOfTag.get(link.tagId).push(link.itemId);
  }
  const itemIndex = new Map(data.items.map(i => [i.id, i]));

  const rows = data.tags.map(tag => {
    const ids = (itemsOfTag.get(tag.id) || []).filter(id => itemIndex.has(id));
    const items = ids.map(id => itemIndex.get(id)).map(item => {
      const used = itemMillis.get(item.id) || 0;
      return { item, used, count: itemCounts.get(item.id) || 0, target: itemTargets.get(item.id) || 0 };
    })
      .filter(x => !(x.item.archived && x.used <= 0))
      .sort((a, b) => b.used - a.used || b.target - a.target || a.item.name.localeCompare(b.item.name, 'zh'));

    return {
      tag,
      target: tagTargets.get(tag.id) || 0,
      used: ids.reduce((a, id) => a + (itemMillis.get(id) || 0), 0),
      count: ids.reduce((a, id) => a + (itemCounts.get(id) || 0), 0),
      itemCount: ids.length,
      items
    };
  }).filter(r => r.target > 0 || r.used > 0 || r.items.some(i => i.target > 0))
    .sort((a, b) => b.target - a.target || b.used - a.used || a.tag.name.localeCompare(b.tag.name, 'zh'));

  // 所属标签都没设目标的事项目标，才计入总目标
  let orphan = 0;
  for (const [itemId, minutes] of itemTargets.entries()) {
    const tags = tagIdsOfItem.get(itemId) || [];
    const covered = tags.some(tid => (tagTargets.get(tid) || 0) > 0);
    if (!covered) orphan += minutes;
  }

  const tagTarget = rows.reduce((a, b) => a + b.target, 0);
  return {
    period, key, label: planLabel(period, date), start, end,
    tagTarget, orphanItemTarget: orphan, totalTarget: tagTarget + orphan,
    used: [...itemMillis.values()].reduce((a, b) => a + b, 0),
    count: [...itemCounts.values()].reduce((a, b) => a + b, 0),
    rows,
    ended: end <= now
  };
}

/** 归档快照：数字定格，之后删记录也不变 */
function snapshotOf(overview) {
  return {
    label: overview.label,
    totalTargetMinutes: overview.totalTarget,
    totalUsedMillis: overview.used,
    recordCount: overview.count,
    rows: overview.rows
      .filter(r => r.target > 0 || r.used > 0)
      .map(r => ({
        name: r.tag.name,
        colorArgb: r.tag.colorArgb,
        targetMinutes: r.target,
        usedMillis: r.used,
        count: r.count,
        items: r.items
          .filter(i => i.target > 0 || i.used > 0)
          .map(i => ({
            name: i.item.name,
            colorArgb: i.item.colorArgb,
            targetMinutes: i.target,
            usedMillis: i.used,
            count: i.count
          }))
      }))
  };
}

/** 到期未归档的期（只从已设目标的期里找，不遍历无穷空周期） */
function duePeriods(data, now) {
  const closed = new Set(data.planDocs.filter(d => d.status === 'CLOSED').map(d => d.periodType + '|' + d.periodKey));
  const seen = new Set();
  const out = [];
  for (const t of data.plans) {
    if (t.targetMinutes <= 0) continue;
    const id = t.periodType + '|' + t.periodKey;
    if (closed.has(id) || seen.has(id)) continue;
    seen.add(id);
    const spec = planPeriodFromKey(t.periodType, t.periodKey);
    if (spec && spec.end <= now) out.push({ period: spec.period, key: spec.key, label: spec.label, end: spec.end });
  }
  return out.sort((a, b) => b.end - a.end);
}

/* ---------------------------------------------------------------- 备份 */

const SCHEMA_VERSION = 3;

/** 本地数据 → 备份 JSON（与安卓版同格式，手机可直接导入） */
function buildBackup(data, now) {
  return {
    app: 'TimeBook',
    schemaVersion: SCHEMA_VERSION,
    exportedAt: now,
    tags: data.tags,
    plans: data.plans,
    planDocs: data.planDocs,
    itemTags: data.itemTags,
    items: data.items,
    records: data.records
  };
}

/** 备份 JSON → 本地数据（兼容 v2：plans 里没有 targetType、没有 planDocs） */
function parseBackup(raw) {
  if (!raw || typeof raw !== 'object') throw new Error('文件内容不是有效的备份');
  if (raw.app && raw.app !== 'TimeBook') throw new Error('不是 TimeBook 的备份文件');

  const tags = (raw.tags || []).map(t => ({
    id: Number(t.id),
    name: String(t.name || '').trim() || '未命名标签',
    colorArgb: Number(t.colorArgb || 0xFF2F6B4F) | 0,
    note: String(t.note || ''),
    archived: !!t.archived,
    createdAt: Number(t.createdAt || Date.now()),
    sortOrder: Number(t.sortOrder || 0)
  }));

  const items = (raw.items || []).map(i => ({
    id: Number(i.id),
    name: String(i.name || '').trim() || '未命名事项',
    colorArgb: Number(i.colorArgb || 0xFF2F6B4F) | 0,
    note: String(i.note || ''),
    archived: !!i.archived,
    createdAt: Number(i.createdAt || Date.now()),
    sortOrder: Number(i.sortOrder || 0),
    targetMinutes: Number(i.targetMinutes || 0)
  }));

  const records = (raw.records || []).map(r => ({
    id: Number(r.id),
    itemId: Number(r.itemId),
    startAt: Number(r.startAt),
    endAt: r.endAt == null || r.endAt === '' ? null : Number(r.endAt),
    note: String(r.note || ''),
    targetMinutes: Number(r.targetMinutes || 0),
    remindAt: r.remindAt == null || r.remindAt === '' ? null : Number(r.remindAt)
  })).filter(r => r.itemId && r.startAt);

  const plans = (raw.plans || []).map(p => {
    const type = p.targetType === 'ITEM' ? 'ITEM' : 'TAG';
    return {
      refId: Number(p.refId != null ? p.refId : p.tagId),
      targetType: type,
      periodType: String(p.periodType || 'MONTH'),
      periodKey: String(p.periodKey || ''),
      targetMinutes: Number(p.targetMinutes || 0)
    };
  }).filter(p => p.refId && p.periodKey);

  const planDocs = (raw.planDocs || []).map(d => ({
    periodType: String(d.periodType || 'MONTH'),
    periodKey: String(d.periodKey || ''),
    status: d.status === 'ACTIVE' ? 'ACTIVE' : 'CLOSED',
    summary: String(d.summary || ''),
    closedAt: d.closedAt == null ? null : Number(d.closedAt),
    snapshot: d.snapshot || null
  })).filter(d => d.periodKey);

  let itemTags = (raw.itemTags || []).map(l => ({ itemId: Number(l.itemId), tagId: Number(l.tagId) }))
    .filter(l => l.itemId && l.tagId);

  const itemIds = new Set(items.map(i => i.id));
  const tagIds = new Set(tags.map(t => t.id));
  itemTags = itemTags.filter(l => itemIds.has(l.itemId) && tagIds.has(l.tagId));

  // 旧备份没有标签关联：建一个「未分类」兜住，别让事项成孤儿
  if (!itemTags.length && items.length) {
    let fallback = tags.find(t => t.name === '未分类');
    if (!fallback) {
      fallback = { id: nextId(tags), name: '未分类', colorArgb: 0xFF4A5A63 | 0, note: '', archived: false, createdAt: Date.now(), sortOrder: 0 };
      tags.push(fallback);
    }
    itemTags = items.map(i => ({ itemId: i.id, tagId: fallback.id }));
  }

  return { tags, items, records, plans, planDocs, itemTags };
}

const nextId = list => list.reduce((m, x) => Math.max(m, Number(x.id) || 0), 0) + 1;

function emptyData() {
  return { tags: [], items: [], records: [], plans: [], planDocs: [], itemTags: [] };
}

/* ---------------------------------------------------------------- 合并导入 */

const nameKey = s => String(s == null ? '' : s).trim().toLowerCase();
const maxSort = list => list.reduce((m, x) => Math.max(m, Number(x.sortOrder) || 0), -1);

/**
 * 把 incoming（备份文件解析结果）并进 local（本机数据），返回新数据 + 统计报告。
 * 口径：
 *   - 标签 / 事项：同 id 且同名 → 同一条；否则同名也算同一条（重新映射 id）；都没有才新建
 *   - 记录：同一事项 + 同一开始 + 同一结束 = 同一条，重复的跳过
 *   - 未结束的记录（导出时正在计时）不合并，避免出现两条同时在跑
 *   - 计划目标：同期同对象只有一条；数值冲突默认保留本机（prefer='import' 则以文件为准）
 *   - 已归档的期：本机没有就补进来；归档状态优先于未归档；文案只补空着的总结
 */
function mergeData(local, incoming, opts) {
  const prefer = (opts && opts.prefer === 'import') ? 'import' : 'local';

  const out = {
    tags: local.tags.map(t => ({ ...t })),
    items: local.items.map(i => ({ ...i })),
    records: local.records.map(r => ({ ...r })),
    plans: local.plans.map(p => ({ ...p })),
    planDocs: local.planDocs.map(d => ({ ...d })),
    itemTags: local.itemTags.map(l => ({ ...l }))
  };

  const report = {
    tagsAdded: 0, tagsMatched: 0,
    itemsAdded: 0, itemsMatched: 0,
    recordsAdded: 0, recordsSkipped: 0, runningSkipped: 0,
    plansAdded: 0, plansConflict: 0, plansUpdated: 0,
    docsAdded: 0, docsUpdated: 0, docsKept: 0,
    linksAdded: 0
  };

  /* 标签 */
  let nextTagId = nextId(out.tags);
  let tagSort = maxSort(out.tags);
  const tagById = new Map(out.tags.map(t => [t.id, t]));
  const tagByName = new Map(out.tags.map(t => [nameKey(t.name), t]));
  const tagRemap = new Map();

  for (const t of incoming.tags) {
    let hit = tagById.get(t.id) || null;
    if (hit && nameKey(hit.name) !== nameKey(t.name)) hit = null;   // id 撞了但不是同一个标签
    if (!hit) hit = tagByName.get(nameKey(t.name)) || null;
    if (hit) {
      tagRemap.set(t.id, hit.id);
      report.tagsMatched++;
      continue;
    }
    const copy = { ...t, id: nextTagId++, sortOrder: ++tagSort };
    out.tags.push(copy);
    tagById.set(copy.id, copy);
    tagByName.set(nameKey(copy.name), copy);
    tagRemap.set(t.id, copy.id);
    report.tagsAdded++;
  }

  /* 事项 */
  let nextItemId = nextId(out.items);
  let itemSort = maxSort(out.items);
  const itemByIdMap = new Map(out.items.map(i => [i.id, i]));
  const itemByName = new Map(out.items.map(i => [nameKey(i.name), i]));
  const itemRemap = new Map();

  for (const it of incoming.items) {
    let hit = itemByIdMap.get(it.id) || null;
    if (hit && nameKey(hit.name) !== nameKey(it.name)) hit = null;
    if (!hit) hit = itemByName.get(nameKey(it.name)) || null;
    if (hit) {
      itemRemap.set(it.id, hit.id);
      report.itemsMatched++;
      continue;
    }
    const copy = { ...it, id: nextItemId++, sortOrder: ++itemSort };
    out.items.push(copy);
    itemByIdMap.set(copy.id, copy);
    itemByName.set(nameKey(copy.name), copy);
    itemRemap.set(it.id, copy.id);
    report.itemsAdded++;
  }

  /* 记录：按 事项+开始+结束 去重 */
  let nextRecordId = nextId(out.records);
  const seenRecords = new Set(out.records.map(r => r.itemId + '|' + r.startAt + '|' + (r.endAt == null ? 'run' : r.endAt)));

  for (const r of incoming.records) {
    if (r.endAt == null) { report.runningSkipped++; continue; }
    const itemId = itemRemap.get(r.itemId);
    if (!itemId) continue;
    const k = itemId + '|' + r.startAt + '|' + r.endAt;
    if (seenRecords.has(k)) { report.recordsSkipped++; continue; }
    seenRecords.add(k);
    out.records.push({ ...r, id: nextRecordId++, itemId });
    report.recordsAdded++;
  }

  /* 计划目标 */
  const planKeyOf = p => p.periodType + '|' + p.periodKey + '|' + p.targetType + '|' + p.refId;
  const planMap = new Map(out.plans.map(p => [planKeyOf(p), p]));

  for (const p of incoming.plans) {
    const refId = p.targetType === 'ITEM' ? itemRemap.get(p.refId) : tagRemap.get(p.refId);
    if (!refId) continue;
    const cand = { ...p, refId };
    const k = planKeyOf(cand);
    const cur = planMap.get(k);
    if (!cur) {
      out.plans.push(cand);
      planMap.set(k, cand);
      report.plansAdded++;
      continue;
    }
    if (Number(cur.targetMinutes) === Number(cand.targetMinutes)) continue;
    report.plansConflict++;
    if (prefer === 'import') { cur.targetMinutes = cand.targetMinutes; report.plansUpdated++; }
  }

  /* 归档与总结 */
  const docKey = d => d.periodType + '|' + d.periodKey;
  const docMap = new Map(out.planDocs.map(d => [docKey(d), d]));

  for (const d of incoming.planDocs) {
    const k = docKey(d);
    const cur = docMap.get(k);
    if (!cur) {
      const copy = { ...d };
      out.planDocs.push(copy);
      docMap.set(k, copy);
      report.docsAdded++;
      continue;
    }
    if (cur.status !== 'CLOSED' && d.status === 'CLOSED') {
      Object.assign(cur, { ...d });
      report.docsUpdated++;
      continue;
    }
    if (cur.status === 'CLOSED' && d.status === 'CLOSED' && !cur.summary && d.summary) {
      cur.summary = d.summary;
      report.docsUpdated++;
      continue;
    }
    report.docsKept++;
  }

  /* 事项 ↔ 标签 */
  const seenLinks = new Set(out.itemTags.map(l => l.itemId + '|' + l.tagId));
  for (const l of incoming.itemTags) {
    const itemId = itemRemap.get(l.itemId);
    const tagId = tagRemap.get(l.tagId);
    if (!itemId || !tagId) continue;
    const k = itemId + '|' + tagId;
    if (seenLinks.has(k)) continue;
    seenLinks.add(k);
    out.itemTags.push({ itemId, tagId });
    report.linksAdded++;
  }

  return { data: out, report };
}

const TB_CORE = {
  DAY, SCHEMA_VERSION,
  clockText, humanText, hmText, dateLabel,
  startOfDay, endOfDay, endOf, overlapMillis,
  statsRange, statsLabel, computeStats,
  fiveStartYear, planKey, planLabel, planRange, shiftPlanDate, planPeriodFromKey,
  computePlan, snapshotOf, duePeriods,
  buildBackup, parseBackup, emptyData, nextId, mergeData
};

/* 渲染进程当普通脚本加载时挂到 window.TB，Node 里用 require 便于单独验证 */
if (typeof module !== 'undefined' && module.exports) module.exports = TB_CORE;
else if (typeof window !== 'undefined') window.TB = TB_CORE;
