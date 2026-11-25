'use strict';
/* =====================================================================
 * TimeBook 桌面版 · 界面层
 *  - 所有数据来自 %APPDATA%\TimeBook\data.json，改动立即落盘
 *  - 计时用「开始时间戳」表达，关掉应用也不会丢
 * ===================================================================== */
(function () {
  const TB = window.TB;
  const bridge = window.bridge;

  const COLORS = [0xFF2F6B4F, 0xFF3C6E9C, 0xFF9B4E6B, 0xFFB4803A, 0xFF6B5B95, 0xFF2E7D6B, 0xFFB0563A, 0xFF4A5A63];

  const ICONS = {
    timer: '<svg viewBox="0 0 24 24"><circle cx="12" cy="13" r="8"/><path d="M12 9v4l2.5 2M9 2h6"/></svg>',
    records: '<svg viewBox="0 0 24 24"><path d="M4 6h16M4 12h16M4 18h10"/></svg>',
    stats: '<svg viewBox="0 0 24 24"><path d="M5 20V10M12 20V4M19 20v-7"/></svg>',
    plan: '<svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M8 3v4M16 3v4M3 11h18"/></svg>',
    items: '<svg viewBox="0 0 24 24"><path d="M20.6 13.4l-7.2 7.2a2 2 0 01-2.8 0l-7-7a2 2 0 01-.6-1.4V4a1 1 0 011-1h8.2a2 2 0 011.4.6l6.9 6.9a2 2 0 010 2.9z"/><circle cx="7.5" cy="7.5" r="1.2"/></svg>',
    backup: '<svg viewBox="0 0 24 24"><path d="M21 12a9 9 0 11-3-6.7M21 4v5h-5"/></svg>',
    play: '<svg viewBox="0 0 24 24"><path d="M7 4l13 8-13 8z"/></svg>',
    pause: '<svg viewBox="0 0 24 24"><rect x="6" y="5" width="4" height="14"/><rect x="14" y="5" width="4" height="14"/></svg>',
    plus: '<svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>',
    chevL: '<svg viewBox="0 0 24 24"><path d="M15 5l-7 7 7 7"/></svg>',
    chevR: '<svg viewBox="0 0 24 24"><path d="M9 5l7 7-7 7"/></svg>',
    caret: '<svg viewBox="0 0 24 24"><path d="M9 5l7 7-7 7"/></svg>',
    x: '<svg viewBox="0 0 24 24"><path d="M6 6l12 12M18 6L6 18"/></svg>',
    edit: '<svg viewBox="0 0 24 24"><path d="M4 20h4l10-10-4-4L4 16v4z"/><path d="M13.5 6.5l4 4"/></svg>',
    trash: '<svg viewBox="0 0 24 24"><path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"/></svg>',
    note: '<svg viewBox="0 0 24 24"><path d="M5 4h14v16H5z"/><path d="M8 9h8M8 13h5"/></svg>'
  };

  const state = {
    data: TB.emptyData(),
    page: 'timer',
    runningId: null,
    statsMode: 'month',
    statsDate: new Date(),
    planTab: 'current',
    planPeriod: 'MONTH',
    planDate: new Date(),
    recordDate: new Date(),
    expanded: new Set(),
    autoBackup: true,
    lastBackupAt: 0,
    info: null,
    lastDayTick: ''
  };

  /* ------------------------------------------------------------ 小工具 */

  const $ = id => document.getElementById(id);
  const now = () => Date.now();
  const argb = n => '#' + ((Number(n) >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0');
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const itemById = id => state.data.items.find(i => i.id === id);
  const tagById = id => state.data.tags.find(t => t.id === id);
  const runningRecord = () => state.data.records.find(r => r.endAt == null) || null;

  /** 把「当前在计时什么」同步给主进程，托盘菜单和悬停提示据此显示 */
  function pushRunning() {
    const r = runningRecord();
    if (!r) { bridge.setRunning(false, null); return; }
    const item = itemById(r.itemId);
    bridge.setRunning(true, { itemName: item ? item.name : '事项', startAt: r.startAt });
  }
  const tagsOfItem = item => state.data.itemTags.filter(l => l.itemId === item.id).map(l => tagById(l.tagId)).filter(Boolean);
  const sortedItems = () => state.data.items.filter(i => !i.archived).slice().sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
  const sortedTags = () => state.data.tags.slice().sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);

  function toast(msg) {
    const t = $('toast');
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(t._h);
    t._h = setTimeout(() => t.classList.remove('show'), 2100);
  }

  /* ------------------------------------------------------------ 持久化 */

  let saveChain = Promise.resolve();

  function persist() {
    const payload = JSON.parse(JSON.stringify({
      tags: state.data.tags,
      items: state.data.items,
      records: state.data.records,
      plans: state.data.plans,
      planDocs: state.data.planDocs,
      itemTags: state.data.itemTags
    }));
    saveChain = saveChain
      .then(() => bridge.saveData(payload))
      .then(r => { if (r && r.ok === false) toast('保存失败：' + (r.error || '磁盘写入被拒绝')); })
      .catch(e => toast('保存失败：' + e.message));
    return saveChain;
  }

  function backupText() {
    return JSON.stringify(TB.buildBackup(state.data, now()), null, 2);
  }

  function backupName() {
    const d = new Date();
    const p = n => String(n).padStart(2, '0');
    return `TimeBook-备份-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}.json`;
  }

  async function maybeAutoBackup() {
    if (!state.autoBackup) return;
    const today = new Date().toDateString();
    if (localStorage.getItem('tb:autoBackupDay') === today) return;
    try {
      const r = await bridge.autoBackup(backupText());
      if (r.ok) {
        localStorage.setItem('tb:autoBackupDay', today);
        state.lastBackupAt = now();
      }
    } catch (_) {}
  }

  /* ------------------------------------------------------------ 导入结果落地 */

  let pendingImport = null;

  function applyImport(data) {
    state.data = data;
    state.runningId = (runningRecord() || {}).id || null;
    persist();
    pushRunning();
    const rt = runningRecord();
    if (rt) syncReminder(rt); else bridge.clearReminder();
    render();
  }

  /* ------------------------------------------------------------ 计时 */

  function syncReminder(rec) {
    if (!rec || rec.endAt != null || !rec.remindAt) {
      bridge.clearReminder();
      return;
    }
    const item = itemById(rec.itemId);
    bridge.setReminder({
      at: rec.remindAt,
      body: `「${item ? item.name : '事项'}」已经做了 ${rec.targetMinutes} 分钟，要结束了吗？`
    });
  }

  function startTimer(itemId) {
    const item = itemById(itemId);
    if (!item) return;

    if (state.runningId) {
      const cur = runningRecord();
      if (cur && cur.itemId === itemId) return;
      stopTimer(true);
    }

    const t = now();
    const rec = {
      id: TB.nextId(state.data.records),
      itemId,
      startAt: t,
      endAt: null,
      note: '',
      targetMinutes: item.targetMinutes > 0 ? item.targetMinutes : 0,
      remindAt: item.targetMinutes > 0 ? t + item.targetMinutes * 60000 : null
    };
    state.data.records.push(rec);
    state.runningId = rec.id;
    persist();
    pushRunning();
    syncReminder(rec);
    render();
    toast(item.targetMinutes > 0
      ? `开始「${item.name}」，${item.targetMinutes} 分钟后提醒`
      : `开始「${item.name}」`);
  }

  /** 结束当前计时；返回被结束的记录（可能为 null） */
  function stopTimer(quiet) {
    const rec = runningRecord();
    if (!rec) return null;
    const t = now();
    rec.endAt = Math.max(t, rec.startAt);
    state.runningId = null;
    persist();
    pushRunning();
    bridge.clearReminder();
    render();
    if (!quiet) toast('已记录 ' + TB.humanText(rec.endAt - rec.startAt));
    maybeAutoBackup();
    return rec;
  }

  function stopWithNote() {
    const rec = runningRecord();
    if (!rec) { toast('当前没有在计时'); return; }
    const item = itemById(rec.itemId);
    const dur = now() - rec.startAt;
    const stopped = stopTimer(true);
    openNoteDialog(stopped, item, dur);
  }

  /* ------------------------------------------------------------ 渲染：外壳 */

  const NAVS = [
    ['timer', '计时', ICONS.timer],
    ['records', '记录', ICONS.records],
    ['stats', '统计', ICONS.stats],
    ['plan', '计划', ICONS.plan],
    ['items', '事项与标签', ICONS.items]
  ];

  function renderNav() {
    $('nav').innerHTML = NAVS.map(([k, label, ic]) =>
      `<div class="navitem ${state.page === k ? 'active' : ''}" data-page="${k}">${ic}<span>${label}</span></div>`
    ).join('');
    const b = $('navBackup');
    b.innerHTML = ICONS.backup + '<span>备份与数据</span>';
    b.className = 'navitem ' + (state.page === 'backup' ? 'active' : '');
  }

  function renderTopbar() {
    const d = new Date();
    $('tbDate').textContent = TB.dateLabel(d);
    const today = TB.computeStats(state.data, 'day', d, now());
    const due = TB.duePeriods(state.data, now());
    $('tbSub').textContent = `今日 ${TB.humanText(today.totalMillis)} · ${today.recordCount} 条记录` +
      (due.length ? ` · 有 ${due.length} 期计划待总结` : '');

    const rec = runningRecord();
    if (rec) {
      const item = itemById(rec.itemId);
      $('tbRight').innerHTML =
        `<div class="runpill"><span class="dot"></span><span class="nm">${esc(item ? item.name : '已删除事项')}</span>` +
        `<span class="ck" id="pillClock">${TB.clockText(now() - rec.startAt)}</span>` +
        `<button class="mini-btn" data-act="stop">结束</button></div>`;
    } else {
      $('tbRight').innerHTML = '<div class="small">今天还没有在计时</div>';
    }
  }

  /* ------------------------------------------------------------ 渲染：计时页 */

  function renderTimer() {
    const d = new Date();
    const today = TB.computeStats(state.data, 'day', d, now());
    const rec = runningRecord();
    const runItem = rec ? itemById(rec.itemId) : null;
    const used = rec ? now() - rec.startAt : 0;
    const tgt = rec ? rec.targetMinutes : 0;
    const pct = tgt > 0 ? Math.min(100, used / (tgt * 60000) * 100) : 0;

    let runCard;
    if (rec && runItem) {
      const tags = tagsOfItem(runItem);
      const remain = tgt > 0 ? tgt * 60000 - used : 0;
      runCard =
        `<div class="run-card">
          <div class="rc-item"><span class="dot" style="background:${argb(runItem.colorArgb)}"></span>${esc(runItem.name)}</div>
          <div class="rc-tags">${tags.map(t => `<span class="chip"><span class="dot" style="width:6px;height:6px;background:${argb(t.colorArgb)}"></span>${esc(t.name)}</span>`).join('')}</div>
          <div class="rc-clock" id="bigClock">${TB.clockText(used)}</div>
          <div class="rc-hint">${tgt > 0
            ? `本次目标 ${TB.humanText(tgt * 60000)} · ${remain > 0 ? '还剩 ' + TB.humanText(remain) : '已超出 ' + TB.humanText(-remain)}（到点弹系统通知，不会自动结束）`
            : '本次没有设目标时长'}</div>
          ${tgt > 0 ? `<div class="prog" style="margin-top:10px;height:8px;background:rgba(255,255,255,.72)"><i id="runProg" style="width:${pct}%"></i></div>` : ''}
          <div class="rc-actions">
            <button class="btn stop wide" data-act="stop">结束并记录</button>
            <button class="btn ghost" data-act="reminder">设本次提醒</button>
          </div>
          <div class="rc-note" data-act="note-running">${ICONS.note}
            <span class="${rec.note ? '' : 'ph'}">${rec.note ? esc(rec.note) : '＋ 记一句：现在在做什么、进展如何'}</span></div>
        </div>`;
    } else {
      runCard =
        `<div class="run-card idle">
          <div class="rc-item" style="color:#7A7367">没有在计时</div>
          <div class="rc-clock">00:00:00</div>
          <div class="rc-hint">下面点任意事项开始；同一时刻只计一件事，开始新事项会自动结束上一件。</div>
          <div class="rc-actions"><button class="btn" data-act="pick">选择一个事项开始</button></div>
        </div>`;
    }

    const cards = sortedItems().map(it => {
      const tags = tagsOfItem(it);
      const playing = rec && rec.itemId === it.id;
      return `<div class="qcard ${playing ? 'playing' : ''}" data-act="start" data-id="${it.id}">
        <div class="playbtn">${playing ? ICONS.pause.replace(/fill="none"/, '') : ICONS.play}</div>
        <div class="grow"><div class="nm">${esc(it.name)}</div>
          <div class="meta">${it.targetMinutes ? '目标 ' + TB.humanText(it.targetMinutes * 60000) + ' · ' : ''}${tags.map(t => esc(t.name)).join(' / ')}</div>
        </div></div>`;
    }).join('');

    const todayStart = TB.startOfDay(d);
    const todays = state.data.records
      .filter(r => TB.overlapMillis(r, todayStart, todayStart + TB.DAY, now()) > 0)
      .sort((a, b) => b.startAt - a.startAt);

    const tl = todays.length ? todays.map(r => {
      const it = itemById(r.itemId);
      if (!it) return '';
      return `<div class="tlrow">
        <div class="tl-time">${TB.hmText(r.startAt)}</div>
        <div class="tl-body" data-act="edit-record" data-id="${r.id}">
          <span class="dot" style="background:${argb(it.colorArgb)}"></span>
          <div class="grow"><div class="tl-nm">${esc(it.name)}</div>
          <div class="tl-note">${r.note ? esc(r.note) : '<span style="color:#B3ADA2">未写备注</span>'}</div></div>
          <div class="tl-dur">${r.endAt == null ? '进行中' : TB.humanText(TB.overlapMillis(r, todayStart, todayStart + TB.DAY, now()))}</div>
        </div></div>`;
    }).join('') : '<div class="empty">今天还没有记录</div>';

    // 右栏：最近 7 天
    const week = [];
    for (let i = 6; i >= 0; i--) {
      const s = todayStart - i * TB.DAY;
      week.push({ label: '日一二三四五六'[new Date(s).getDay()], v: TB.computeStats(state.data, 'day', new Date(s), now()).totalMillis, today: i === 0 });
    }
    const maxWeek = Math.max(1, ...week.map(w => w.v));
    const weekBars = week.map(w =>
      `<div class="c ${w.today ? 'today' : ''}" title="${TB.humanText(w.v)}"><div class="b" style="height:${Math.max(3, w.v / maxWeek * 100)}%"></div><div class="l">${w.label}</div></div>`
    ).join('');

    const due = TB.duePeriods(state.data, now());
    const dueCard = due.length ? `<div class="side-card" style="border-color:#E8D9BC;background:#FDF8EC">
      <div class="sc-title" style="color:#8A6A24">计划到期，该写总结了</div>
      ${due.slice(0, 3).map(x => `<div class="row" style="margin-top:8px"><span class="small" style="color:#6B5A33">${esc(x.label)}</span>
        <span class="link" style="margin-left:auto" data-act="goto-plan">去总结</span></div>`).join('')}
      </div>` : '';

    $('scroll').innerHTML = `<div class="grid-main"><div>
      ${runCard}
      <div class="sec-title"><span>快速开始</span><span class="grow"></span><span class="link" data-act="new-item">＋ 新建事项</span></div>
      <div class="quick-grid">${cards || '<div class="empty" style="grid-column:1/-1">还没有事项，先建一个吧</div>'}</div>
      <div class="sec-title"><span>今日明细</span><span class="grow"></span><span class="small">${today.recordCount} 条 · ${TB.humanText(today.totalMillis)}</span></div>
      <div>${tl}</div>
    </div><div>
      <div class="side-card"><div class="sc-title">今日</div><div class="sc-big mono">${TB.humanText(today.totalMillis)}</div>
        <div class="kpis">
          <div class="kpi"><div class="v">${today.recordCount}</div><div class="k">记录</div></div>
          <div class="kpi"><div class="v">${today.tagTotals.length}</div><div class="k">标签</div></div>
          <div class="kpi"><div class="v">${new Set(todays.map(r => r.itemId)).size}</div><div class="k">件事</div></div>
        </div></div>
      <div class="side-card"><div class="sc-title">最近 7 天</div><div class="bar7">${weekBars}</div></div>
      ${dueCard}
      <div class="side-card"><div class="sc-title">快捷键</div>
        <div class="small" style="margin-top:8px;line-height:1.85">
          空格：开始 / 结束<br>
          Ctrl + Alt + 空格：全局唤起并开始 / 结束<br>
          Ctrl + N：新建事项<br>
          R：补录一条记录
        </div></div>
    </div></div>`;
  }

  /* ------------------------------------------------------------ 渲染：记录页 */

  function renderRecords() {
    const s = TB.startOfDay(state.recordDate);
    const e = s + TB.DAY;
    const list = state.data.records
      .filter(r => TB.overlapMillis(r, s, e, now()) > 0)
      .sort((a, b) => b.startAt - a.startAt);
    const sum = list.reduce((a, r) => a + TB.overlapMillis(r, s, e, now()), 0);
    const isToday = s === TB.startOfDay(new Date());

    const rows = list.length ? list.map(r => {
      const it = itemById(r.itemId);
      const tags = it ? tagsOfItem(it) : [];
      return `<tr>
        <td class="mono" style="width:112px;color:#57524A">${TB.hmText(r.startAt)} – ${r.endAt ? TB.hmText(r.endAt) : '进行中'}</td>
        <td style="width:196px"><div class="row" style="gap:8px"><span class="dot" style="background:${it ? argb(it.colorArgb) : '#B3ADA2'}"></span>
          <b style="font-weight:600">${esc(it ? it.name : '已删除事项')}</b></div></td>
        <td style="width:130px"><div class="tagline">${tags.map(t => `<span class="chip"><span class="dot" style="width:5px;height:5px;background:${argb(t.colorArgb)}"></span>${esc(t.name)}</span>`).join('')}</div></td>
        <td class="mono" style="width:88px">${TB.humanText(TB.overlapMillis(r, s, e, now()))}</td>
        <td><div class="notecell ${r.note ? '' : 'empty'}" data-act="edit-note" data-id="${r.id}">${r.note ? esc(r.note) : '＋ 加一句备注'}</div></td>
        <td style="width:76px;text-align:right"><button class="icon-btn" data-act="edit-record" data-id="${r.id}">${ICONS.edit}</button><button class="icon-btn" data-act="del-record" data-id="${r.id}">${ICONS.trash}</button></td>
      </tr>`;
    }).join('') : '<tr><td colspan="6"><div class="empty">这一天没有记录</div></td></tr>';

    const longest = list.slice().sort((a, b) => TB.overlapMillis(b, s, e, now()) - TB.overlapMillis(a, s, e, now()))[0];

    $('scroll').innerHTML = `
      <div class="pagehead"><div class="h">记录</div>
        <div style="margin-left:auto"></div>
        <button class="navbtn" data-act="rec-prev">${ICONS.chevL}</button>
        <div style="min-width:172px;text-align:center;font-size:13px;font-weight:600">${state.recordDate.getFullYear()}年${state.recordDate.getMonth() + 1}月${state.recordDate.getDate()}日 ${'周日周一周二周三周四周五周六'.substr(state.recordDate.getDay() * 2, 2)}</div>
        <button class="navbtn" data-act="rec-next">${ICONS.chevR}</button>
        <button class="btn ghost sm" data-act="rec-today" style="margin-left:8px">回到今天</button>
        <button class="btn sm" data-act="add-record" style="margin-left:8px">＋ 补录</button>
      </div>
      <div class="card" style="padding:14px 16px 6px">
        <div class="row" style="margin-bottom:12px;gap:26px;flex-wrap:wrap">
          <div><div class="small">当日合计</div><div style="font-size:20px;font-weight:600;margin-top:2px" class="mono">${TB.humanText(sum)}</div></div>
          <div><div class="small">记录条数</div><div style="font-size:20px;font-weight:600;margin-top:2px" class="mono">${list.length}</div></div>
          <div><div class="small">涉及事项</div><div style="font-size:20px;font-weight:600;margin-top:2px" class="mono">${new Set(list.map(r => r.itemId)).size}</div></div>
          <div style="margin-left:auto"><div class="small">${isToday ? '今天' : '当日'}最长的一段</div>
            <div style="font-size:12.5px;margin-top:6px">${longest && itemById(longest.itemId) ? esc(itemById(longest.itemId).name) : '—'}</div></div>
        </div>
        <table class="rec"><thead><tr><th>时间</th><th>事项</th><th>标签</th><th>时长</th><th>备注</th><th></th></tr></thead>
        <tbody>${rows}</tbody></table>
      </div>`;
  }

  /* ------------------------------------------------------------ 渲染：统计页 */

  function renderStats() {
    const mode = state.statsMode;
    const st = TB.computeStats(state.data, mode, state.statsDate, now());

    let bars = '', xlab = '';
    const vals = st.bucketValues;
    const mx = Math.max(1, ...vals);
    const labels = st.bucketLabels.map((l, i) => {
      if (mode === 'month') return (i % 5 === 0) ? l : '';
      if (mode === 'year') return l;
      return (i % 4 === 0) ? l : '';
    });
    bars = vals.map((v, i) => `<div class="col" title="${mode === 'day' ? labels[i] || vals.indexOf(v) : labels[i] || ''} ${TB.humanText(v)}"><div class="b ${v === 0 ? 'dim' : ''}" style="height:${v ? Math.max(2, v / mx * 100) : 1}%"></div></div>`).join('');
    xlab = labels.map(l => `<div>${l}</div>`).join('');

    const days = Math.max(1, Math.round((Math.min(st.end, now()) - st.start) / TB.DAY));
    const avg = st.totalMillis / days;

    const tagRows = st.tagTotals.map(r => {
      const open = state.expanded.has('stat:' + r.tag.id);
      return `<div class="trow" data-act="toggle-stat" data-id="${r.tag.id}">
          <span class="caret ${open ? 'open' : ''}">${ICONS.caret}</span>
          <span class="dot" style="background:${argb(r.tag.colorArgb)}"></span>
          <div><div class="tnm">${esc(r.tag.name)}</div><div class="tmeta">${r.itemCount} 个事项 · ${r.count} 次</div></div>
          <span class="tv">${TB.humanText(r.millis)}</span></div>
        ${open ? r.items.map(x => `<div class="child"><span class="dot" style="width:6px;height:6px;background:${argb(x.item.colorArgb)}"></span>
          <span class="nm">${esc(x.item.name)}</span><span class="v">${TB.humanText(x.millis)} · ${x.count} 次</span></div>`).join('') : ''}`;
    }).join('') || '<div class="empty">这段时间还没有记录</div>';

    const itemBars = st.itemTotals.length ? st.itemTotals.map(x => {
      const p = st.totalMillis ? x.millis / st.totalMillis * 100 : 0;
      return `<div style="padding:7px 0"><div class="row" style="gap:8px">
          <span class="dot" style="background:${argb(x.item.colorArgb)}"></span>
          <span style="font-size:12px">${esc(x.item.name)}</span>
          <span style="margin-left:auto;font-size:11.5px" class="mono">${TB.humanText(x.millis)} · ${Math.round(p)}% · ${x.count} 次</span></div>
        <div class="prog" style="margin-top:5px"><i style="width:${p}%;background:${argb(x.item.colorArgb)}"></i></div></div>`;
    }).join('') : '<div class="empty">暂无数据</div>';

    const chartTitle = mode === 'day' ? '时段分布（24 小时）' : mode === 'month' ? '每日用时' : '每月用时';

    $('scroll').innerHTML = `
      <div class="pagehead"><div class="h">统计</div>
        <div class="seg" style="margin-left:10px">
          ${[['day', '日'], ['month', '月'], ['year', '年']].map(([m, l]) => `<div class="${mode === m ? 'on' : ''}" data-act="stat-mode" data-mode="${m}">${l}</div>`).join('')}
        </div><div style="margin-left:auto"></div>
        <button class="navbtn" data-act="stat-prev">${ICONS.chevL}</button>
        <div style="min-width:132px;text-align:center;font-size:13px;font-weight:600">${st.rangeLabel}</div>
        <button class="navbtn" data-act="stat-next">${ICONS.chevR}</button>
        <button class="btn ghost sm" data-act="stat-today" style="margin-left:8px">回到今天</button>
      </div>
      <div class="stat-total"><div><div class="lab">本期总时长</div><div class="big">${TB.humanText(st.totalMillis)}</div></div>
        <div class="mini">
          <div><div class="v">${st.recordCount}</div><div class="k">次数</div></div>
          <div><div class="v">${st.itemTotals.length}</div><div class="k">事项</div></div>
          <div><div class="v">${st.tagTotals.length}</div><div class="k">标签</div></div>
          <div><div class="v">${TB.humanText(avg)}</div><div class="k">日均</div></div>
        </div></div>
      <div class="card" style="margin-top:14px">
        <div class="row"><div style="font-size:12.5px;font-weight:600">${chartTitle}</div>
          <span class="small" style="margin-left:auto">悬停看具体数值</span></div>
        <div class="chart">${bars}</div><div class="chart-x">${xlab}</div></div>
      <div class="grid-main" style="margin-top:14px">
        <div class="card" style="padding:8px 14px 12px">
          <div class="row" style="padding:8px 2px 6px"><div style="font-size:12.5px;font-weight:600">按标签</div>
            <span class="small" style="margin-left:auto">点标签展开事项</span></div>
          <div class="tagtree">${tagRows}</div>
          ${st.tagTotals.length > 1 ? '<div class="small" style="margin-top:10px;line-height:1.65">提示：一个事项挂多个标签时，同一段时间会分别计入每个标签，所以标签合计可能大于总时长。</div>' : ''}
        </div>
        <div class="card" style="padding:8px 14px 14px"><div style="font-size:12.5px;font-weight:600;padding:8px 2px 10px">按事项</div>
          ${itemBars}</div>
      </div>`;
  }

  /* ------------------------------------------------------------ 渲染：计划页 */

  function renderPlan() {
    const seg = `<div class="seg">${[['MONTH', '月度'], ['YEAR', '年度'], ['FIVE', '五年']].map(([k, l]) =>
      `<div class="${state.planPeriod === k ? 'on' : ''}" data-act="plan-period" data-p="${k}">${l}</div>`).join('')}</div>`;

    const tabs = `<div class="seg">
      <div class="${state.planTab === 'current' ? 'on' : ''}" data-act="plan-tab" data-t="current">本期计划</div>
      <div class="${state.planTab === 'history' ? 'on' : ''}" data-act="plan-tab" data-t="history">历史计划</div></div>`;

    let body = '';

    if (state.planTab === 'history') {
      const docs = state.data.planDocs.slice().sort((a, b) => (b.closedAt || 0) - (a.closedAt || 0));
      body = docs.length ? docs.map(doc => {
        const snap = doc.snapshot || { totalTargetMinutes: 0, totalUsedMillis: 0, rows: [] };
        const target = (snap.totalTargetMinutes || 0) * 60000;
        const used = snap.totalUsedMillis || (snap.rows || []).reduce((a, r) => a + (r.usedMillis || 0), 0);
        const pct = target ? Math.round(used / target * 100) : 0;
        const open = state.expanded.has('doc:' + doc.periodType + '|' + doc.periodKey);
        return `<div class="hist">
          <div class="hh"><div class="hname">${esc(snap.label || doc.periodKey)}</div>
            <span class="badge closed">已归档</span>
            <span class="small">${{ MONTH: '月度', YEAR: '年度', FIVE: '五年' }[doc.periodType] || ''}${doc.closedAt ? ' · ' + TB.dateLabel(new Date(doc.closedAt)).replace(/ .*$/, '') + ' 归档' : ''}</span>
            <span style="margin-left:auto" class="link" data-act="toggle-doc" data-id="${doc.periodType}|${doc.periodKey}">${open ? '收起明细' : '展开明细'}</span>
          </div>
          <div class="hkm"><span>目标 <b>${target ? TB.humanText(target) : '—'}</b></span>
            <span>实际 <b style="color:${pct >= 100 ? '#2F6B4F' : '#8A6A24'}">${TB.humanText(used)}</b></span>
            <span>完成度 <b>${target ? pct + '%' : '—'}</b></span></div>
          <div class="prog" style="margin-top:8px"><i class="${pct > 100 ? 'over' : ''}" style="width:${Math.min(100, pct)}%"></i></div>
          ${doc.summary ? `<div class="hsum">${esc(doc.summary)}</div>` : '<div class="small" style="margin-top:9px">（这期没有写总结）</div>'}
          ${open ? `<div style="margin-top:12px;border-top:1px solid var(--line);padding-top:8px">
            ${(snap.rows || []).map(r => `<div class="row" style="padding:6px 0;gap:9px">
              <span class="dot" style="background:${argb(r.colorArgb)}"></span>
              <span style="font-size:12px">${esc(r.name)}</span>
              <span class="small" style="margin-left:auto">目标 ${r.targetMinutes ? TB.humanText(r.targetMinutes * 60000) : '—'}</span>
              <span style="width:92px;text-align:right;font-size:11.5px" class="mono">${TB.humanText(r.usedMillis)}</span></div>`).join('')}
            <div class="row" style="margin-top:8px"><span class="link" data-act="edit-summary" data-type="${doc.periodType}" data-key="${doc.periodKey}">编辑总结</span>
              <span class="link" style="margin-left:14px" data-act="reopen-doc" data-type="${doc.periodType}" data-key="${doc.periodKey}">取消归档</span></div>
          </div>` : ''}
        </div>`;
      }).join('') : '<div class="card"><div class="empty">还没有归档的计划。每一期结束后点「结束本期并归档」，写下的总结会留在这里。</div></div>';
    } else {
      const p = TB.computePlan(state.data, state.planPeriod, state.planDate, now());
      const doc = state.data.planDocs.find(d => d.periodType === state.planPeriod && d.periodKey === p.key);
      const closed = !!doc && doc.status === 'CLOSED';
      const pct = p.totalTarget ? Math.round(p.used / (p.totalTarget * 60000) * 100) : 0;
      const remainDays = Math.max(0, Math.ceil((p.end - now()) / TB.DAY));
      const need = remainDays > 0 ? Math.max(0, p.totalTarget * 60000 - p.used) / remainDays : 0;

      const rows = p.rows.map(r => {
        const open = state.expanded.has('plan:' + r.tag.id);
        const rp = r.target ? Math.min(100, Math.round(r.used / (r.target * 60000) * 100)) : 0;
        return `<div class="prow"><div class="ph" data-act="toggle-plan" data-id="${r.tag.id}">
            <span class="caret ${open ? 'open' : ''}" style="width:14px;color:#9A948A">${ICONS.caret}</span>
            <span class="dot" style="background:${argb(r.tag.colorArgb)}"></span>
            <div style="width:150px"><div class="pnm">${esc(r.tag.name)}</div><div class="psub">${r.itemCount} 个事项</div></div>
            <div class="pbar">${r.target
              ? `<div class="prog"><i class="${rp > 100 ? 'over' : ''}" style="width:${Math.min(100, rp)}%"></i></div>`
              : `<div class="small">未设目标 · ${closed ? '' : `<span class="link" data-act="set-tag-target" data-id="${r.tag.id}">设目标</span>`}</div>`}</div>
            <div class="pval">${r.target ? `${TB.humanText(r.used)} <span class="muted">/ ${TB.humanText(r.target * 60000)}</span>` : TB.humanText(r.used)}</div>
            <div class="pct">${r.target ? rp + '%' : '—'}</div></div>
          ${open ? `<div class="pchild">${r.items.map(x => {
            const cp = x.target ? Math.min(100, Math.round(x.used / (x.target * 60000) * 100)) : 0;
            return `<div class="cr"><span class="dot" style="width:6px;height:6px;background:${argb(x.item.colorArgb)}"></span>
              <span class="cnm">${esc(x.item.name)}</span>
              <div class="cbar">${x.target ? `<div class="prog" style="height:5px"><i class="${cp > 100 ? 'over' : ''}" style="width:${Math.min(100, cp)}%;background:${argb(x.item.colorArgb)}"></i></div>` : ''}</div>
              <span class="cval">${TB.humanText(x.used)}${x.target ? ' / ' + TB.humanText(x.target * 60000) : ''}</span>
              ${closed ? '' : `<span class="cset" data-act="set-item-target" data-id="${x.item.id}">${x.target ? '改' : '设目标'}</span>`}</div>`;
          }).join('')}</div>` : ''}
        </div>`;
      }).join('') || '<div class="card"><div class="empty">本期还没有计划。给标签或事项设个目标就会出现在这里。</div></div>';

      body = `<div class="card" style="margin-bottom:14px">
          <div class="row" style="gap:14px;flex-wrap:wrap">
            ${seg}
            <div class="row" style="gap:6px">
              <button class="navbtn" data-act="plan-prev">${ICONS.chevL}</button>
              <div style="min-width:140px;text-align:center;font-size:13.5px;font-weight:600">${esc(p.label)}
                ${closed ? '<span class="badge closed" style="font-weight:400">已归档</span>' : ''}</div>
              <button class="navbtn" data-act="plan-next">${ICONS.chevR}</button>
            </div>
            <div class="row" style="margin-left:auto;gap:8px">
              ${closed
                ? '<button class="btn ghost sm" data-act="reopen">取消归档</button>'
                : '<button class="btn sub sm" data-act="copy-prev">沿用上一期</button><button class="btn sm" data-act="close-plan" style="margin-left:8px">结束本期并归档</button>'}
            </div></div></div>
        <div class="plan-total">
          <div class="row" style="margin-bottom:4px"><span class="small">本期总进度</span>
            <span class="small" style="margin-left:auto">${closed ? '归档时定格' : '随记录实时更新'}</span></div>
          <div class="pt-top"><div class="pct">${pct}%</div>
            <div class="frac">${TB.humanText(p.used)} / ${p.totalTarget ? TB.humanText(p.totalTarget * 60000) : '未设目标'}</div></div>
          <div class="prog"><i class="${pct > 100 ? 'over' : ''}" style="width:${pct > 0 ? Math.min(100, Math.max(2, pct)) : 0}%"></i></div>
          <div class="pt-foot">
            <div>已用 <b>${TB.humanText(p.used)}</b></div>
            <div>记录 <b>${p.count}</b> 次</div>
            <div>剩余 <b>${p.totalTarget ? TB.humanText(Math.max(0, p.totalTarget * 60000 - p.used)) : '—'}</b></div>
            <div>${state.planPeriod === 'FIVE' ? '按五年规划对齐（2021 起算）' : `本期还剩 <b>${remainDays}</b> 天`}${remainDays > 0 && p.totalTarget ? ` · 日均需 <b>${TB.humanText(need)}</b>` : ''}</div>
          </div></div>
        <div class="sec-title"><span>按标签</span><span class="grow"></span>
          <span class="small">合计目标 ${TB.humanText(p.totalTarget * 60000)}${p.orphanItemTarget ? `（含独立事项目标 ${TB.humanText(p.orphanItemTarget * 60000)}）` : ''}</span></div>
        ${rows}`;
    }

    $('scroll').innerHTML = `<div class="pagehead"><div class="h">计划</div><div style="margin-left:10px">${tabs}</div></div>${body}`;
  }

  /* ------------------------------------------------------------ 渲染：事项页 */

  function renderItems() {
    const items = sortedItems();
    const cards = items.map(it => {
      const tags = tagsOfItem(it);
      const total = state.data.records.filter(r => r.itemId === it.id)
        .reduce((a, r) => a + (TB.endOf(r, now()) - r.startAt), 0);
      return `<div class="icard" data-act="edit-item" data-id="${it.id}">
        <div class="row" style="gap:8px"><span class="dot" style="background:${argb(it.colorArgb)}"></span>
          <div class="inm grow">${esc(it.name)}</div></div>
        <div class="itags">${tags.map(t => `<span class="chip"><span class="dot" style="width:5px;height:5px;background:${argb(t.colorArgb)}"></span>${esc(t.name)}</span>`).join('')}</div>
        <div class="imeta"><span>${it.targetMinutes ? '目标 ' + TB.humanText(it.targetMinutes * 60000) : '未设目标'}</span><span>累计 ${TB.humanText(total)}</span></div>
      </div>`;
    }).join('');

    const tagChips = sortedTags().map(t => {
      const n = items.filter(i => state.data.itemTags.some(l => l.itemId === i.id && l.tagId === t.id)).length;
      return `<div class="chip" style="height:26px;padding:0 10px;background:#fff;border:1px solid var(--outline);cursor:pointer" data-act="edit-tag" data-id="${t.id}">
        <span class="dot" style="width:6px;height:6px;background:${argb(t.colorArgb)}"></span>${esc(t.name)}
        <span style="color:#A39C90">${n}</span></div>`;
    }).join('');

    const archived = state.data.items.filter(i => i.archived);

    $('scroll').innerHTML = `
      <div class="pagehead"><div class="h">事项与标签</div><div style="margin-left:auto"></div>
        <button class="btn ghost sm" data-act="new-tag">＋ 新建标签</button>
        <button class="btn sm" data-act="new-item" style="margin-left:8px">＋ 新建事项</button></div>
      <div class="sec-title" style="margin-top:0"><span>标签</span><span class="grow"></span>
        <span class="small">一个事项可挂多个标签，统计时分别计入</span></div>
      <div class="row" style="gap:7px;flex-wrap:wrap">${tagChips || '<span class="small">还没有标签</span>'}</div>
      <div class="sec-title"><span>事项</span><span class="grow"></span><span class="small">${items.length} 个在用</span></div>
      <div class="itemgrid">${cards || '<div class="empty" style="grid-column:1/-1">还没有事项，点右上角新建</div>'}</div>
      ${archived.length ? `<div class="sec-title"><span>已归档</span><span class="grow"></span><span class="small">历史记录保留</span></div>
        <div class="row" style="gap:7px;flex-wrap:wrap">${archived.map(i => `<span class="chip" style="height:26px;padding:0 10px;cursor:pointer" data-act="edit-item" data-id="${i.id}">${esc(i.name)}</span>`).join('')}</div>` : ''}`;
  }

  /* ------------------------------------------------------------ 渲染：备份页 */

  function renderBackup() {
    const totalMs = state.data.records.reduce((a, r) => a + (TB.endOf(r, now()) - r.startAt), 0);
    const info = state.info || {};
    const last = state.lastBackupAt ? new Date(state.lastBackupAt) : null;

    $('scroll').innerHTML = `
      <div class="pagehead"><div class="h">备份与数据</div></div>
      <div class="grid-main"><div>
        <div class="card"><div style="font-size:12.5px;font-weight:600;margin-bottom:12px">数据概况</div>
          <div class="kpis" style="margin-top:0">
            <div class="kpi"><div class="v">${state.data.items.filter(i => !i.archived).length}</div><div class="k">在用事项</div></div>
            <div class="kpi"><div class="v">${state.data.tags.length}</div><div class="k">标签</div></div>
            <div class="kpi"><div class="v">${state.data.records.length}</div><div class="k">记录</div></div>
            <div class="kpi"><div class="v">${TB.humanText(totalMs)}</div><div class="k">累计时长</div></div>
          </div></div>

        <div class="card" style="margin-top:12px"><div style="font-size:12.5px;font-weight:600;margin-bottom:6px">和手机互通</div>
          <div class="small" style="line-height:1.8">桌面版导出的备份与安卓版<b>同格式</b>（schemaVersion 3）：<br>
          手机「事项 → 备份与恢复 → 导出」的 json 可以直接在这里导入（默认按合并处理，不会清掉电脑上已有的记录），反之亦然。<br>
          事项、标签、全部记录、每期计划目标、每期总结与归档快照都会一起带走。</div></div>

        <div class="card" style="margin-top:12px"><div class="row">
          <div><div style="font-size:12.5px;font-weight:600">每日自动备份</div>
            <div class="small" style="margin-top:4px">每天首次打开时写一份到 backups 目录，只保留最近 5 份</div></div>
          <div style="margin-left:auto"><label style="display:flex;align-items:center;gap:8px;cursor:pointer">
            <input type="checkbox" ${state.autoBackup ? 'checked' : ''} data-act="toggle-auto" style="width:15px;height:15px;accent-color:#2F6B4F">
            <span class="small">开启</span></label></div></div></div>

        <div class="card" style="margin-top:12px"><div style="font-size:12.5px;font-weight:600;margin-bottom:6px">直接复制也算备份</div>
          <div class="small" style="line-height:1.8">数据文件：<span class="selectable" style="word-break:break-all">${esc(info.dataFile || '%APPDATA%\\TimeBook\\data.json')}</span><br>
          把这一个文件复制走，就是一份完整数据；覆盖回来即可还原。</div>
          <button class="btn sub sm" style="margin-top:10px" data-act="open-folder">打开数据文件夹</button></div>
      </div><div>
        <div class="side-card"><div class="sc-title">导出 / 导入</div>
          <div class="small" style="margin-top:6px;line-height:1.65">上次备份：${last ? TB.dateLabel(last) + ' ' + TB.hmText(last.getTime()) : '从未备份'}</div>
          <button class="btn" style="width:100%;justify-content:center;margin-top:12px" data-act="export">导出备份文件…</button>
          <button class="btn ghost" style="width:100%;justify-content:center;margin-top:8px" data-act="import">从文件导入…</button>
          <div class="small" style="margin-top:10px;line-height:1.65">导入默认<b>合并</b>：本机数据不删，只补进文件里多出来的部分；需要整盘替换时再选「覆盖」。</div></div>
        <div class="side-card"><div class="sc-title">关于</div>
          <div class="small" style="margin-top:8px;line-height:1.8">TimeBook 桌面版 ${esc(info.version || '')}<br>
          统计口径与手机端一致：跨零点按实际重叠拆分，多标签各自计入。</div></div>
      </div></div>`;
  }

  /* ------------------------------------------------------------ 主渲染 */

  function render(keepScroll) {
    const sc = $('scroll').scrollTop;
    renderNav();
    renderTopbar();
    const fn = { timer: renderTimer, records: renderRecords, stats: renderStats, plan: renderPlan, items: renderItems, backup: renderBackup }[state.page] || renderTimer;
    fn();
    $('scroll').scrollTop = keepScroll ? sc : 0;
  }

  /* ------------------------------------------------------------ 弹窗 */

  function openDialog(html, wide) {
    const o = $('overlay');
    o.innerHTML = `<div class="dialog ${wide ? 'wide' : ''}">${html}</div>`;
    o.hidden = false;
    o.onclick = e => { if (e.target === o) closeDialog(); };
  }

  function closeDialog() {
    const o = $('overlay');
    o.hidden = true;
    o.innerHTML = '';
  }

  function dlgHeader(title) {
    return `<div class="dlg-h"><div class="t">${esc(title)}</div><button class="icon-btn x" data-act="close-dlg">${ICONS.x}</button></div>`;
  }

  function confirmDialog(title, text, okText, onOk) {
    openDialog(`${dlgHeader(title)}
      <div class="dlg-sub">${text}</div>
      <div class="dlg-foot"><button class="btn sub" data-act="close-dlg">取消</button>
      <button class="btn" id="confirmOk">${esc(okText)}</button></div>`, true);
    $('confirmOk').onclick = () => { closeDialog(); onOk(); };
  }

  function openNoteDialog(rec, item, dur) {
    if (!rec) return;
    openDialog(`${dlgHeader('这次做了什么？')}
      <div class="dlg-sub">${esc(item ? item.name : '事项')} · ${TB.humanText(dur)}。备注不是必填，写一句当时的实际情况，以后回看更有用。</div>
      <textarea class="textarea" id="noteInput" placeholder="比如：前半段顺利，后来被打断了…"></textarea>
      <div class="quickwords">${['顺利', '被打断', '走神了', '状态不错', '有点累'].map(w => `<span class="qw" data-act="qw" data-w="${w}">${w}</span>`).join('')}</div>
      <div class="dlg-foot"><button class="btn sub" data-act="skip-note">先不写</button>
        <button class="btn" data-act="save-note" data-id="${rec.id}">保存备注</button></div>`, true);
    setTimeout(() => { const el = $('noteInput'); if (el) el.focus(); }, 30);
  }

  function openNoteOnly(recId) {
    const r = state.data.records.find(x => x.id === recId);
    if (!r) return;
    openDialog(`${dlgHeader('备注')}
      <div class="dlg-sub">${esc((itemById(r.itemId) || {}).name || '已删除事项')} · ${TB.hmText(r.startAt)} 开始 · ${TB.humanText(TB.endOf(r, now()) - r.startAt)}</div>
      <textarea class="textarea" id="noteInput" placeholder="记一句当时的情况">${esc(r.note)}</textarea>
      <div class="quickwords">${['顺利', '被打断', '走神了', '状态不错', '有点累'].map(w => `<span class="qw" data-act="qw" data-w="${w}">${w}</span>`).join('')}</div>
      <div class="dlg-foot"><button class="btn sub" data-act="close-dlg">取消</button>
        <button class="btn" data-act="save-note-only" data-id="${r.id}">保存</button></div>`, true);
    setTimeout(() => { const el = $('noteInput'); if (el) el.focus(); }, 30);
  }

  let editingItem = null;

  function openItemDialog(item) {
    editingItem = item || null;
    const color = item ? item.colorArgb : COLORS[0];
    const selected = item ? state.data.itemTags.filter(l => l.itemId === item.id).map(l => l.tagId) : [];
    openDialog(`${dlgHeader(item ? '编辑事项' : '新建事项')}
      <div class="dlg-sub">事项是具体做的事，比如「写产品方案」「读《人类简史》」。至少挂一个标签。</div>
      <div class="field"><label>名称</label><input class="input" id="itName" value="${esc(item ? item.name : '')}" placeholder="比如：写产品方案"></div>
      <div class="field"><label>标签（可多选）</label><div class="tagpick" id="tagPick">
        ${sortedTags().map(t => `<span class="pk ${selected.includes(t.id) ? 'on' : ''}" data-act="pick-tag" data-id="${t.id}">
          <span class="dot" style="width:6px;height:6px;background:${argb(t.colorArgb)}"></span>${esc(t.name)}</span>`).join('')}
      </div></div>
      <div class="field"><label>默认目标时长（分钟，留空则不提醒）</label>
        <div class="numline"><input class="input" id="itTarget" type="number" min="0" step="5" value="${item && item.targetMinutes ? item.targetMinutes : ''}" placeholder="不设"><span>分钟</span></div></div>
      <div class="field"><label>颜色</label><div class="swatches">
        ${COLORS.map(c => `<span class="sw ${c === color ? 'sel' : ''}" style="background:${argb(c)}" data-act="pick-color" data-c="${c}"></span>`).join('')}
      </div></div>
      <div class="field"><label>备注</label><input class="input" id="itNote" value="${esc(item ? item.note || '' : '')}" placeholder="这件事的做法或注意点（可空）"></div>
      <div class="dlg-foot">
        ${item ? `<button class="btn danger sm" data-act="archive-item" data-id="${item.id}" style="margin-right:auto">${item.archived ? '恢复' : '归档'}</button>
                  <button class="btn danger sm" data-act="delete-item" data-id="${item.id}" style="margin-right:8px">删除</button>` : ''}
        <button class="btn sub" data-act="close-dlg">取消</button>
        <button class="btn" data-act="save-item">${item ? '保存' : '创建'}</button></div>`, true);
  }

  function openTagDialog(tag) {
    openDialog(`${dlgHeader(tag ? '编辑标签' : '新建标签')}
      <div class="dlg-sub">标签是事项的上层归类，比如「工作」「读书」「运动」。</div>
      <div class="field"><label>名称</label><input class="input" id="tgName" value="${esc(tag ? tag.name : '')}"></div>
      <div class="field"><label>颜色</label><div class="swatches">
        ${COLORS.map(c => `<span class="sw ${tag && tag.colorArgb === c ? 'sel' : ''}" style="background:${argb(c)}" data-act="pick-color" data-c="${c}"></span>`).join('')}
      </div></div>
      <div class="dlg-foot">${tag ? `<button class="btn danger sm" data-act="delete-tag" data-id="${tag.id}" style="margin-right:auto">删除标签</button>` : '<span></span>'}
        <button class="btn sub" data-act="close-dlg">取消</button>
        <button class="btn" data-act="save-tag" data-id="${tag ? tag.id : ''}">保存</button></div>`, true);
  }

  function localInput(ts) {
    const d = new Date(ts);
    const p = n => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
  }

  function openAddRecord() {
    openDialog(`${dlgHeader('补录一条记录')}
      <div class="dlg-sub">忘了开计时的时候用。</div>
      <div class="field"><label>事项</label><select class="input" id="arItem">
        ${sortedItems().map(i => `<option value="${i.id}">${esc(i.name)}</option>`).join('')}</select></div>
      <div class="field"><label>开始</label><input class="input" id="arStart" type="datetime-local"></div>
      <div class="field"><label>结束</label><input class="input" id="arEnd" type="datetime-local"></div>
      <div class="field"><label>备注</label><input class="input" id="arNote" placeholder="可空"></div>
      <div class="dlg-foot"><button class="btn sub" data-act="close-dlg">取消</button>
        <button class="btn" data-act="save-add-record">保存</button></div>`, true);
    const t = now();
    $('arStart').value = localInput(t - 3600000);
    $('arEnd').value = localInput(t);
  }

  function openRecordDialog(r) {
    openDialog(`${dlgHeader('编辑记录')}
      <div class="dlg-sub">${esc((itemById(r.itemId) || {}).name || '已删除事项')}</div>
      <div class="field"><label>开始</label><input class="input" id="erStart" type="datetime-local" value="${localInput(r.startAt)}"></div>
      <div class="field"><label>结束</label><input class="input" id="erEnd" type="datetime-local" value="${localInput(TB.endOf(r, now()))}"></div>
      <div class="field"><label>备注</label><textarea class="textarea" id="erNote">${esc(r.note)}</textarea></div>
      <div class="dlg-foot"><button class="btn danger sm" data-act="del-record" data-id="${r.id}" style="margin-right:auto">删除</button>
        <button class="btn sub" data-act="close-dlg">取消</button>
        <button class="btn" data-act="save-record" data-id="${r.id}">保存</button></div>`, true);
  }

  function openSetTarget(kind, id) {
    const key = TB.planKey(state.planPeriod, state.planDate);
    const type = kind === 'tag' ? 'TAG' : 'ITEM';
    const cur = state.data.plans.find(p => p.refId === id && p.targetType === type && p.periodType === state.planPeriod && p.periodKey === key);
    const name = kind === 'tag' ? (tagById(id) || {}).name : (itemById(id) || {}).name;
    openDialog(`${dlgHeader('设目标 · ' + (name || ''))}
      <div class="dlg-sub">${esc(TB.planLabel(state.planPeriod, state.planDate))} · ${kind === 'tag' ? '标签目标' : '事项目标'}${kind === 'item' ? '<br>所属标签也设了目标时，这条按「拆解」算，不重复计入总目标。' : ''}</div>
      <div class="field"><label>这一期打算投入</label><div class="numline">
        <input class="input" id="tgtMin" type="number" min="0" step="30" value="${cur ? cur.targetMinutes : ''}" placeholder="0">
        <span>${cur ? '分钟（' + TB.humanText(cur.targetMinutes * 60000) + '）' : '分钟'}</span></div></div>
      <div class="quickwords">${[600, 1200, 1800, 3600].map(m => `<span class="qw" data-act="tgt-quick" data-m="${m}">${TB.humanText(m * 60000)}</span>`).join('')}</div>
      <div class="dlg-foot">
        <button class="btn danger sm" data-act="clear-target" data-kind="${kind}" data-id="${id}" style="margin-right:auto">清除</button>
        <button class="btn sub" data-act="close-dlg">取消</button>
        <button class="btn" data-act="save-target" data-kind="${kind}" data-id="${id}">保存</button></div>`, true);
  }

  function openClosePlan() {
    const p = TB.computePlan(state.data, state.planPeriod, state.planDate, now());
    const pct = p.totalTarget ? Math.round(p.used / (p.totalTarget * 60000) * 100) : 0;
    openDialog(`${dlgHeader('结束本期，写个总结')}
      <div class="dlg-sub">${esc(p.label)}：目标 ${p.totalTarget ? TB.humanText(p.totalTarget * 60000) : '未设'} · 实际 ${TB.humanText(p.used)} · 完成度 ${pct}%。<br>
        归档后这一期的目标会锁定，统计数字定格；要改可以先「取消归档」。</div>
      <div class="field"><label>这一期总结（可以留空，只存自动统计）</label>
        <textarea class="textarea" id="planSummary" style="min-height:120px" placeholder="做到了什么、没做到的原因、下一期怎么调…"></textarea></div>
      <div class="dlg-foot"><button class="btn sub" data-act="close-dlg">再想想</button>
        <button class="btn" data-act="do-close-plan">写好了，归档</button></div>`, true);
    setTimeout(() => { const el = $('planSummary'); if (el) el.focus(); }, 30);
  }

  function openEditSummary(type, key) {
    const doc = state.data.planDocs.find(d => d.periodType === type && d.periodKey === key);
    if (!doc) return;
    openDialog(`${dlgHeader('编辑总结')}
      <div class="dlg-sub">只改文字，归档时的统计快照不变。</div>
      <textarea class="textarea" id="sumEdit" style="min-height:140px">${esc(doc.summary)}</textarea>
      <div class="dlg-foot"><button class="btn sub" data-act="close-dlg">取消</button>
        <button class="btn" data-act="save-summary" data-type="${type}" data-key="${key}">保存</button></div>`, true);
  }

  function openSelectItem() {
    const items = sortedItems();
    openDialog(`${dlgHeader('选择事项开始计时')}
      <div class="dlg-sub">也可以直接点计时页上的任意一张卡片。</div>
      ${items.map(i => `<div class="qcard" style="margin-bottom:8px" data-act="start" data-id="${i.id}">
        <div class="playbtn">${ICONS.play}</div><div class="grow"><div class="nm">${esc(i.name)}</div>
        <div class="meta">${tagsOfItem(i).map(t => esc(t.name)).join(' / ')}${i.targetMinutes ? ' · 目标 ' + TB.humanText(i.targetMinutes * 60000) : ''}</div></div></div>`).join('') || '<div class="empty">还没有事项</div>'}
      <div class="dlg-foot"><button class="btn sub" data-act="close-dlg">取消</button></div>`, true);
  }

  function openReminder() {
    const rec = runningRecord();
    if (!rec) { toast('先开始一件事再设提醒'); return; }
    openDialog(`${dlgHeader('设本次提醒')}
      <div class="dlg-sub">只影响这一次，不会改事项的默认目标。到点弹系统通知，计时继续。</div>
      <div class="field"><label>做满多少分钟提醒（留空或 0 = 不提醒）</label><div class="numline">
        <input class="input" id="remMin" type="number" min="0" step="5" value="${rec.targetMinutes || ''}"><span>分钟</span></div></div>
      <div class="dlg-foot"><button class="btn sub" data-act="close-dlg">取消</button>
        <button class="btn" data-act="save-reminder">保存</button></div>`, true);
    setTimeout(() => { const el = $('remMin'); if (el) el.focus(); }, 30);
  }

  /* ------------------------------------------------------------ 交互 */

  document.addEventListener('click', e => {
    const pageEl = e.target.closest('[data-page]');
    if (pageEl) { state.page = pageEl.dataset.page; render(); return; }

    const el = e.target.closest('[data-act]');
    if (!el) return;
    const act = el.dataset.act;
    const id = el.dataset.id;

    switch (act) {
      case 'start': closeDialog(); startTimer(Number(id)); return;
      case 'stop': stopWithNote(); return;
      case 'pick': openSelectItem(); return;
      case 'reminder': openReminder(); return;
      case 'close-dlg': closeDialog(); return;

      case 'note-running': {
        const rec = runningRecord();
        if (!rec) return;
        openDialog(`${dlgHeader('记一句')}
          <div class="dlg-sub">正在计时也能改，随时补。</div>
          <textarea class="textarea" id="noteInput" placeholder="现在在做什么、卡在哪、进展如何">${esc(rec.note)}</textarea>
          <div class="quickwords">${['顺利', '被打断', '走神了', '状态不错', '有点累'].map(w => `<span class="qw" data-act="qw" data-w="${w}">${w}</span>`).join('')}</div>
          <div class="dlg-foot"><button class="btn sub" data-act="close-dlg">取消</button>
            <button class="btn" data-act="save-note-running">保存</button></div>`, true);
        setTimeout(() => { const x = $('noteInput'); if (x) x.focus(); }, 30);
        return;
      }
      case 'save-note-running': {
        const rec = runningRecord();
        if (rec) { rec.note = $('noteInput').value.trim(); persist(); }
        closeDialog(); render(true); toast('备注已保存'); return;
      }
      case 'qw': {
        const ta = $('noteInput');
        if (ta) { const w = el.dataset.w; ta.value = ta.value ? ta.value + '，' + w : w; ta.focus(); }
        return;
      }
      case 'skip-note': closeDialog(); return;
      case 'save-note': {
        const rec = state.data.records.find(r => r.id === Number(id));
        if (rec) { rec.note = $('noteInput').value.trim(); persist(); }
        closeDialog(); render(true); toast('备注已保存'); return;
      }
      case 'save-note-only': {
        const rec = state.data.records.find(r => r.id === Number(id));
        if (rec) { rec.note = $('noteInput').value.trim(); persist(); }
        closeDialog(); render(true); toast('备注已保存'); return;
      }
      case 'edit-note': openNoteOnly(Number(id)); return;
      case 'edit-record': {
        const r = state.data.records.find(x => x.id === Number(id));
        if (r) openRecordDialog(r);
        return;
      }
      case 'del-record': {
        const rid = Number(id);
        const rec = state.data.records.find(r => r.id === rid);
        if (!rec) return;
        confirmDialog('删除这条记录？', `${TB.hmText(rec.startAt)} 开始 · ${TB.humanText(TB.endOf(rec, now()) - rec.startAt)}<br>删除后不可恢复。`, '删除', () => {
          state.data.records = state.data.records.filter(r => r.id !== rid);
          if (state.runningId === rid) { state.runningId = null; pushRunning(); bridge.clearReminder(); }
          persist(); render(true); toast('记录已删除');
        });
        return;
      }

      case 'new-item': openItemDialog(null); return;
      case 'edit-item': openItemDialog(itemById(Number(id))); return;
      case 'pick-tag': el.classList.toggle('on'); return;
      case 'pick-color': {
        const box = el.parentElement;
        [...box.children].forEach(c => c.classList.remove('sel'));
        el.classList.add('sel');
        return;
      }
      case 'save-item': {
        const name = $('itName').value.trim();
        const target = Math.max(0, parseInt($('itTarget').value || '0', 10) || 0);
        const colorEl = document.querySelector('.sw.sel');
        const color = colorEl ? Number(colorEl.dataset.c) : COLORS[0];
        const note = $('itNote').value.trim();
        const tagIds = [...document.querySelectorAll('#tagPick .pk.on')].map(x => Number(x.dataset.id));
        if (!name) { toast('事项名称不能为空'); return; }
        if (!tagIds.length) { toast('至少选一个标签，比如「工作」「读书」'); return; }

        if (editingItem) {
          Object.assign(editingItem, { name, targetMinutes: target, colorArgb: color, note });
          state.data.itemTags = state.data.itemTags.filter(l => l.itemId !== editingItem.id);
          tagIds.forEach(tid => state.data.itemTags.push({ itemId: editingItem.id, tagId: tid }));
          persist(); closeDialog(); render(true); toast('事项已更新');
        } else {
          const newItem = {
            id: TB.nextId(state.data.items), name, colorArgb: color, note,
            archived: false, createdAt: now(), sortOrder: state.data.items.length, targetMinutes: target
          };
          state.data.items.push(newItem);
          tagIds.forEach(tid => state.data.itemTags.push({ itemId: newItem.id, tagId: tid }));
          persist(); closeDialog(); render(true);
          toast(target ? `已新建「${name}」，默认目标 ${target} 分钟` : `已新建事项「${name}」`);
        }
        return;
      }
      case 'archive-item': {
        const it = itemById(Number(id));
        if (it) { it.archived = !it.archived; persist(); }
        closeDialog(); render(true); toast(it && it.archived ? '已归档，历史记录保留' : '已恢复');
        return;
      }
      case 'delete-item': {
        const it = itemById(Number(id));
        if (!it) return;
        const cnt = state.data.records.filter(r => r.itemId === it.id).length;
        confirmDialog('删除事项「' + it.name + '」？',
          cnt ? `它会连带删除 <b>${cnt} 条</b>历史记录，不可恢复。<br>如果只是想不再用它，建议改成「归档」。` : '这个事项还没有记录。',
          '删除', () => {
            state.data.records = state.data.records.filter(r => {
              if (r.itemId !== it.id) return true;
              if (r.endAt == null && state.runningId === r.id) { state.runningId = null; pushRunning(); bridge.clearReminder(); }
              return false;
            });
            state.data.itemTags = state.data.itemTags.filter(l => l.itemId !== it.id);
            state.data.plans = state.data.plans.filter(p => !(p.refId === it.id && p.targetType === 'ITEM'));
            state.data.items = state.data.items.filter(x => x.id !== it.id);
            persist(); closeDialog(); render(true); toast('已删除事项及其记录');
          });
        return;
      }

      case 'new-tag': openTagDialog(null); return;
      case 'edit-tag': openTagDialog(tagById(Number(id))); return;
      case 'save-tag': {
        const name = $('tgName').value.trim();
        if (!name) { toast('标签名称不能为空'); return; }
        const colorEl = document.querySelector('.sw.sel');
        const color = colorEl ? Number(colorEl.dataset.c) : COLORS[0];
        const tid = id ? Number(id) : 0;
        if (tid) {
          const t = tagById(tid);
          if (t) { t.name = name; t.colorArgb = color; }
          toast('标签已更新');
        } else {
          state.data.tags.push({ id: TB.nextId(state.data.tags), name, colorArgb: color, note: '', archived: false, createdAt: now(), sortOrder: state.data.tags.length });
          toast('已新建标签「' + name + '」');
        }
        persist(); closeDialog(); render(true); return;
      }
      case 'delete-tag': {
        const t = tagById(Number(id));
        if (!t) return;
        const n = state.data.items.filter(i => state.data.itemTags.some(l => l.itemId === i.id && l.tagId === t.id)).length;
        confirmDialog('删除标签「' + t.name + '」？',
          n ? `有 <b>${n} 个</b>事项挂在这个标签上。删除标签不会删事项和历史记录，但统计里这个标签的汇总会消失。` : '这个标签下没有事项。',
          '删除', () => {
            state.data.tags = state.data.tags.filter(x => x.id !== t.id);
            state.data.itemTags = state.data.itemTags.filter(l => l.tagId !== t.id);
            state.data.plans = state.data.plans.filter(p => !(p.refId === t.id && p.targetType === 'TAG'));
            persist(); closeDialog(); render(true); toast('已删除标签「' + t.name + '」');
          });
        return;
      }

      case 'add-record': openAddRecord(); return;
      case 'save-add-record': {
        const itemId = Number($('arItem').value);
        const s = new Date($('arStart').value).getTime();
        const en = new Date($('arEnd').value).getTime();
        if (!itemId) { toast('请先选择事项'); return; }
        if (!(en > s)) { toast('结束时间必须晚于开始时间'); return; }
        state.data.records.push({
          id: TB.nextId(state.data.records), itemId, startAt: s, endAt: en,
          note: $('arNote').value.trim(), targetMinutes: 0, remindAt: null
        });
        persist(); closeDialog(); state.recordDate = new Date(s);
        if (state.page === 'records') render(); else render();
        toast('已补录 ' + TB.humanText(en - s));
        return;
      }
      case 'save-record': {
        const rec = state.data.records.find(r => r.id === Number(id));
        if (!rec) return;
        const s = new Date($('erStart').value).getTime();
        const en = new Date($('erEnd').value).getTime();
        if (!(en > s)) { toast('结束时间必须晚于开始时间'); return; }
        rec.startAt = s; rec.endAt = en; rec.note = $('erNote').value.trim();
        persist(); closeDialog(); render(true); toast('记录已保存');
        return;
      }
      case 'rec-prev': state.recordDate = new Date(state.recordDate.getTime() - TB.DAY); render(); return;
      case 'rec-next': state.recordDate = new Date(state.recordDate.getTime() + TB.DAY); render(); return;
      case 'rec-today': state.recordDate = new Date(); render(); return;

      case 'stat-mode': state.statsMode = el.dataset.mode; render(); return;
      case 'stat-prev': {
        const d = state.statsDate;
        state.statsDate = state.statsMode === 'month' ? new Date(d.getFullYear(), d.getMonth() - 1, 1)
          : state.statsMode === 'year' ? new Date(d.getFullYear() - 1, 0, 1)
          : new Date(d.getTime() - TB.DAY);
        render(); return;
      }
      case 'stat-next': {
        const d = state.statsDate;
        state.statsDate = state.statsMode === 'month' ? new Date(d.getFullYear(), d.getMonth() + 1, 1)
          : state.statsMode === 'year' ? new Date(d.getFullYear() + 1, 0, 1)
          : new Date(d.getTime() + TB.DAY);
        render(); return;
      }
      case 'stat-today': state.statsDate = new Date(); render(); return;

      case 'toggle-stat': {
        const k = 'stat:' + id;
        if (state.expanded.has(k)) state.expanded.delete(k); else state.expanded.add(k);
        render(true); return;
      }
      case 'toggle-plan': {
        const k = 'plan:' + id;
        if (state.expanded.has(k)) state.expanded.delete(k); else state.expanded.add(k);
        render(true); return;
      }

      case 'goto-plan': state.page = 'plan'; state.planTab = 'current'; render(); return;
      case 'plan-tab': state.planTab = el.dataset.t; render(); return;
      case 'plan-period': state.planPeriod = el.dataset.p; render(); return;
      case 'plan-prev': state.planDate = TB.shiftPlanDate(state.planPeriod, state.planDate, -1); render(); return;
      case 'plan-next': state.planDate = TB.shiftPlanDate(state.planPeriod, state.planDate, 1); render(); return;

      case 'set-tag-target': openSetTarget('tag', Number(id)); return;
      case 'set-item-target': openSetTarget('item', Number(id)); return;
      case 'tgt-quick': $('tgtMin').value = el.dataset.m; return;
      case 'save-target': {
        const kind = el.dataset.kind;
        const refId = Number(el.dataset.id);
        const minutes = Math.max(0, parseInt($('tgtMin').value || '0', 10) || 0);
        const type = kind === 'tag' ? 'TAG' : 'ITEM';
        const key = TB.planKey(state.planPeriod, state.planDate);
        state.data.plans = state.data.plans.filter(p => !(p.refId === refId && p.targetType === type && p.periodType === state.planPeriod && p.periodKey === key));
        if (minutes > 0) state.data.plans.push({ refId, targetType: type, periodType: state.planPeriod, periodKey: key, targetMinutes: minutes });
        persist(); closeDialog(); render(true);
        toast(minutes > 0 ? '目标 ' + TB.humanText(minutes * 60000) : '已清除目标');
        return;
      }
      case 'clear-target': {
        const kind = el.dataset.kind;
        const refId = Number(el.dataset.id);
        const type = kind === 'tag' ? 'TAG' : 'ITEM';
        const key = TB.planKey(state.planPeriod, state.planDate);
        state.data.plans = state.data.plans.filter(p => !(p.refId === refId && p.targetType === type && p.periodType === state.planPeriod && p.periodKey === key));
        persist(); closeDialog(); render(true); toast('已清除目标'); return;
      }
      case 'copy-prev': {
        const prevDate = TB.shiftPlanDate(state.planPeriod, state.planDate, -1);
        const prevKey = TB.planKey(state.planPeriod, prevDate);
        const key = TB.planKey(state.planPeriod, state.planDate);
        const prev = state.data.plans.filter(p => p.periodType === state.planPeriod && p.periodKey === prevKey && p.targetMinutes > 0);
        if (!prev.length) { toast('上一期没有设置过计划'); return; }
        state.data.plans = state.data.plans.filter(p => !(p.periodType === state.planPeriod && p.periodKey === key));
        prev.forEach(p => state.data.plans.push({ ...p, periodKey: key }));
        persist(); render(); toast(`已沿用上一期计划（${prev.length} 条）`);
        return;
      }
      case 'close-plan': openClosePlan(); return;
      case 'do-close-plan': {
        const summary = $('planSummary').value.trim();
        const p = TB.computePlan(state.data, state.planPeriod, state.planDate, now());
        if (!p.totalTarget && p.used <= 0) { toast('本期还没有计划或记录，不用归档'); return; }
        state.data.planDocs = state.data.planDocs.filter(d => !(d.periodType === state.planPeriod && d.periodKey === p.key));
        state.data.planDocs.push({
          periodType: state.planPeriod, periodKey: p.key, status: 'CLOSED',
          summary, closedAt: now(), snapshot: TB.snapshotOf(p)
        });
        persist(); closeDialog(); render();
        toast(`「${p.label}」已归档，总结留在历史计划里`);
        return;
      }
      case 'reopen': {
        const key = TB.planKey(state.planPeriod, state.planDate);
        state.data.planDocs = state.data.planDocs.filter(d => !(d.periodType === state.planPeriod && d.periodKey === key));
        persist(); render(); toast('已取消归档，回到本期');
        return;
      }
      case 'toggle-doc': {
        const k = 'doc:' + id;
        if (state.expanded.has(k)) state.expanded.delete(k); else state.expanded.add(k);
        render(true); return;
      }
      case 'edit-summary': openEditSummary(el.dataset.type, el.dataset.key); return;
      case 'save-summary': {
        const doc = state.data.planDocs.find(d => d.periodType === el.dataset.type && d.periodKey === el.dataset.key);
        if (doc) { doc.summary = $('sumEdit').value.trim(); persist(); }
        closeDialog(); render(true); toast('总结已保存'); return;
      }
      case 'reopen-doc': {
        const type = el.dataset.type, key = el.dataset.key;
        confirmDialog('取消归档？', '这一期会回到「本期」，目标重新可改；归档快照会被删除。', '取消归档', () => {
          state.data.planDocs = state.data.planDocs.filter(d => !(d.periodType === type && d.periodKey === key));
          persist(); render(); toast('已取消归档');
        });
        return;
      }

      case 'toggle-auto': {
        state.autoBackup = !state.autoBackup;
        localStorage.setItem('tb:autoBackup', state.autoBackup ? '1' : '0');
        render(true); return;
      }
      case 'export': {
        if (!state.data.items.length && !state.data.records.length) { toast('还没有数据可导出'); return; }
        bridge.exportFile(backupText(), backupName()).then(r => {
          if (r.ok) { state.lastBackupAt = now(); toast('已导出到 ' + r.file); render(true); }
          else if (!r.canceled) toast('导出失败：' + (r.error || ''));
        });
        return;
      }
      case 'import': {
        bridge.importFile().then(res => {
          if (!res.ok) { if (!res.canceled) toast('读取失败：' + (res.error || '')); return; }
          let parsed;
          try { parsed = TB.parseBackup(JSON.parse(res.text)); }
          catch (err) { toast('导入失败：' + err.message); return; }
          pendingImport = { data: parsed, file: res.file };
          const cur = state.data;
          const fileName = esc(res.file.split(/[\\/]/).pop());
          openDialog(`${dlgHeader('导入备份')}
            <div class="dlg-sub">文件：${fileName}<br>
              文件里：<b>${parsed.items.length}</b> 个事项 / <b>${parsed.tags.length}</b> 个标签 / <b>${parsed.records.length}</b> 条记录 / <b>${parsed.plans.length}</b> 条计划目标<br>
              本机现有：<b>${cur.items.length}</b> 个事项 / <b>${cur.tags.length}</b> 个标签 / <b>${cur.records.length}</b> 条记录</div>
            <div class="card" style="padding:12px 14px">
              <div class="small" style="line-height:1.85"><b>合并导入</b>（推荐）：本机数据一条不删，只把文件里多出来的事项、记录、计划补进来。<br>
                同名事项自动认作同一个，同一时间段同一条记录不会重复计一次；计划目标冲突时默认保留本机。</div>
              <label class="optrow"><input type="checkbox" id="impPreferFile"> 计划目标冲突时，以导入文件为准</label>
            </div>
            <div class="dlg-foot"><button class="btn sub" data-act="close-dlg">取消</button>
              <button class="btn ghost" data-act="import-overwrite">覆盖（清空本机）</button>
              <button class="btn" data-act="import-merge">合并导入</button></div>`, true);
        });
        return;
      }
      case 'import-merge': {
        if (!pendingImport) { closeDialog(); toast('请重新选择备份文件'); return; }
        const preferFile = !!($('impPreferFile') && $('impPreferFile').checked);
        const { data, report } = TB.mergeData(state.data, pendingImport.data, { prefer: preferFile ? 'import' : 'local' });
        pendingImport = null;
        closeDialog();
        applyImport(data);
        const lines = [
          `事项：新增 <b>${report.itemsAdded}</b> 个，沿用已有 <b>${report.itemsMatched}</b> 个`,
          `记录：新增 <b>${report.recordsAdded}</b> 条，跳过重复 <b>${report.recordsSkipped}</b> 条`
            + (report.runningSkipped ? `，跳过未结束 <b>${report.runningSkipped}</b> 条` : ''),
          `计划目标：新增 <b>${report.plansAdded}</b> 条`
            + (report.plansConflict ? `，冲突 <b>${report.plansConflict}</b> 条（${preferFile ? '已用文件覆盖' : '保留本机'}）` : ''),
          `归档期：${report.docsAdded ? `补进 <b>${report.docsAdded}</b> 期` : '没有新增'}`,
          `标签：新增 <b>${report.tagsAdded}</b> 个，沿用已有 <b>${report.tagsMatched}</b> 个`
        ];
        openDialog(`${dlgHeader('合并完成')}
          <div class="dlg-sub" style="margin-bottom:12px">文件已并入本机，原有数据一条都没删。</div>
          <div class="small" style="line-height:2">${lines.join('<br>')}</div>
          <div class="dlg-foot"><button class="btn" data-act="close-dlg">好</button></div>`, true);
        return;
      }
      case 'import-overwrite': {
        if (!pendingImport) { closeDialog(); return; }
        const incomingN = pendingImport.data;
        confirmDialog('确认清空本机再导入？',
          `本机现有 <b>${state.data.items.length}</b> 个事项、<b>${state.data.records.length}</b> 条记录会被<b>全部替换</b>为文件里的内容，删除后无法找回。<br>只想补数据请用「合并导入」。`,
          '确认覆盖', () => {
            const data = pendingImport.data;
            pendingImport = null;
            applyImport(data);
            toast(`已覆盖导入：${data.items.length} 个事项 / ${data.records.length} 条记录`);
          });
        return;
      }
      case 'open-folder': bridge.openDataFolder(); return;
    }
  });

  /* ------------------------------------------------------------ 键盘 */

  document.addEventListener('keydown', e => {
    if (e.key === 'Escape') { closeDialog(); return; }
    const typing = ['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement.tagName);
    if (typing) return;
    if (e.code === 'Space') {
      e.preventDefault();
      if (runningRecord()) stopWithNote(); else openSelectItem();
      return;
    }
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'n') { e.preventDefault(); openItemDialog(null); return; }
    if (e.key.toLowerCase() === 'r' && !e.ctrlKey && !e.metaKey) { e.preventDefault(); openAddRecord(); }
  });

  /* ------------------------------------------------------------ 每秒刷新 */

  function tick() {
    const rec = runningRecord();
    if (!rec) return;
    const used = now() - rec.startAt;
    const big = $('bigClock');
    const pill = $('pillClock');
    if (big) big.textContent = TB.clockText(used);
    if (pill) pill.textContent = TB.clockText(used);
    const prog = $('runProg');
    if (prog && rec.targetMinutes > 0) {
      prog.style.width = Math.min(100, used / (rec.targetMinutes * 60000) * 100) + '%';
    }
    // 跨天时把「今日」刷一下，避免凌晨后数字还停在昨天
    const day = new Date().toDateString();
    if (state.lastDayTick && state.lastDayTick !== day) { state.lastDayTick = day; render(true); }
    state.lastDayTick = day;
  }

  /* ------------------------------------------------------------ 启动 */

  async function boot() {
    state.autoBackup = localStorage.getItem('tb:autoBackup') !== '0';
    const res = await bridge.loadData();
    state.data = res.data;
    state.info = await bridge.info();

    // 首次启动：把默认标签立刻落盘，别让界面显示有数据、文件夹里却什么都没有
    if (!res.existed) persist();

    const rt = runningRecord();
    state.runningId = rt ? rt.id : null;
    pushRunning();
    if (rt) syncReminder(rt);

    render();
    setInterval(tick, 1000);

    bridge.onCommand(cmd => {
      if (cmd === 'toggle') {
        if (runningRecord()) stopWithNote(); else openSelectItem();
      } else if (cmd === 'reminded') {
        toast('到点了：' + (runningRecord() ? (itemById(runningRecord().itemId) || {}).name || '' : ''));
        renderTopbar();
      }
    });

    // 启动后补一次自动备份（同一天只写一次）
    setTimeout(maybeAutoBackup, 1500);
  }

  boot().catch(err => {
    document.body.innerHTML = '<div style="padding:40px;font-size:13px;color:#B3261E">启动失败：' + esc(err.message) + '</div>';
  });
})();
