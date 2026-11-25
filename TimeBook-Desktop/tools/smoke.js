'use strict';
/* 冒烟：启动真实窗口，走一遍「开始计时 → 结束 → 写备注 → 归档计划」，
   并逐页截图，确认渲染与写入都正常 */
const fs = require('fs');
const path = require('path');
const { app, BrowserWindow, ipcMain } = require('electron');

app.disableHardwareAcceleration();
require(path.join(__dirname, '..', 'main.js'));

const wait = ms => new Promise(r => setTimeout(r, ms));

app.whenReady().then(async () => {
  await wait(3200);
  const win = BrowserWindow.getAllWindows()[0];
  if (!win) { console.log('NO_WINDOW'); app.quit(); return; }

  const errors = [];
  win.webContents.on('console-message', (e) => {
    const msg = e && e.message ? e.message : '';
    if (msg && (e.level === 'error' || e.level === 'warning')) errors.push(msg);
  });

  const exec = js => win.webContents.executeJavaScript(js);
  const shot = async name => {
    const img = await win.webContents.capturePage();
    fs.writeFileSync(path.join(__dirname, '..', name), img.toPNG());
    console.log('SHOT', name);
  };
  const click = async sel => {
    const ok = await exec(`(() => { const el = document.querySelector('${sel}'); if (!el) return false; el.click(); return true; })()`);
    if (!ok) console.log('MISS_CLICK', sel);
    await wait(700);
    return ok;
  };

  console.log('--- 静态页面 ---');
  await shot('.shot-timer.png');
  await click('[data-page="stats"]');
  await shot('.shot-stats.png');
  await click('[data-page="plan"]');
  await shot('.shot-plan.png');
  await click('[data-page="items"]');
  await shot('.shot-items.png');
  await click('[data-page="records"]');
  await shot('.shot-records.png');

  // 文字溢出检查
  const overflow = await exec(`JSON.stringify([...document.querySelectorAll('.qcard .nm, .prow .pnm, .tagtree .tnm')]
    .filter(e => e.scrollWidth > e.clientWidth + 1).map(e => e.textContent))`);
  console.log('OVERFLOW:', overflow);

  console.log('--- 计时流程 ---');
  await click('[data-page="timer"]');
  await click('.qcard[data-act="start"]');
  await wait(1400);
  const running = await exec(`JSON.stringify({pill: !!document.querySelector('.runpill'), clock: (document.querySelector('.runpill .ck')||{}).textContent, big: (document.getElementById('bigClock')||{}).textContent,
    scrollTop: document.getElementById('scroll').scrollTop,
    hasRunCard: !!document.querySelector('.run-card'),
    runRect: (()=>{const e=document.querySelector('.run-card');if(!e)return null;const r=e.getBoundingClientRect();return {top:Math.round(r.top),h:Math.round(r.height)};})(),
    gridFirst: (()=>{const g=document.querySelector('.grid-main');return g? g.firstElementChild.innerHTML.slice(0,100):null;})()})`);
  console.log('RUNNING:', running);
  await shot('.shot-running.png');

  await click('[data-act="stop"]');
  await shot('.shot-note.png');
  const noteOpen = await exec(`!!document.getElementById('noteInput')`);
  console.log('NOTE_DIALOG:', noteOpen);
  await exec(`document.getElementById('noteInput').value = '测试备注：验证结束流程'`);
  await click('[data-act="save-note"]');
  await wait(400);
  const afterStop = await exec(`JSON.stringify({pill: !!document.querySelector('.runpill'), hasNote: document.body.innerHTML.includes('测试备注：验证结束流程')})`);
  console.log('AFTER_STOP:', afterStop);
  await shot('.shot-after-stop.png');

  console.log('--- 计划归档 ---');
  await click('[data-page="plan"]');
  await click('[data-act="close-plan"]');
  await shot('.shot-closeplan.png');
  await exec(`document.getElementById('planSummary').value = '测试总结：这期总体按计划走，下期把读书提到每周 4 次。'`);
  await click('[data-act="do-close-plan"]');
  await wait(500);
  const archived = await exec(`document.body.innerHTML.includes('已归档')`);
  console.log('ARCHIVED_BADGE:', archived);
  await click('[data-act="plan-tab"][data-t="history"]');
  await shot('.shot-history.png');
  const histText = await exec(`document.getElementById('scroll').innerText.slice(0,160).replace(/\\n/g,' | ')`);
  console.log('HISTORY_TEXT:', JSON.stringify(histText));

  /* ---------------------------------------------- 合并导入（用固定 fixture 替掉文件选择框） */
  console.log('--- 合并导入 ---');
  const DAY = 86400000;
  const d0 = new Date(); d0.setHours(0, 0, 0, 0);
  const T0 = d0.getTime();
  const at = (dayOffset, h, m) => T0 + dayOffset * DAY + h * 3600000 + m * 60000;

  const fixture = {
    app: 'TimeBook', schemaVersion: 3, exportedAt: Date.now(),
    tags: [
      { id: 1, name: '工作', colorArgb: 0xFF3C6E9C | 0, note: '', archived: false, createdAt: T0, sortOrder: 0 },
      { id: 3, name: '写作', colorArgb: 0xFF9B4E6B | 0, note: '', archived: false, createdAt: T0, sortOrder: 1 }
    ],
    items: [
      { id: 1, name: '写产品方案', colorArgb: 0xFF3C6E9C | 0, note: '', archived: false, createdAt: T0, sortOrder: 0, targetMinutes: 90 },
      { id: 2, name: '冥想', colorArgb: 0xFF2E7D6B | 0, note: '', archived: false, createdAt: T0, sortOrder: 1, targetMinutes: 15 }
    ],
    records: [
      { id: 1, itemId: 1, startAt: at(0, 9, 10), endAt: at(0, 10, 40), note: '先把三个方向的取舍写清楚，卡在定价那段', targetMinutes: 0, remindAt: null },
      { id: 2, itemId: 2, startAt: at(-1, 7, 0), endAt: at(-1, 7, 15), note: '第一次试，坐不住', targetMinutes: 15, remindAt: null },
      { id: 3, itemId: 2, startAt: at(0, 6, 30), endAt: null, note: '', targetMinutes: 0, remindAt: null }
    ],
    plans: [
      { refId: 1, targetType: 'TAG', periodType: 'MONTH', periodKey: (() => { const n = new Date(); return n.getFullYear() + '-' + String(n.getMonth() + 1).padStart(2, '0'); })(), targetMinutes: 9999 },
      { refId: 2, targetType: 'ITEM', periodType: 'MONTH', periodKey: (() => { const n = new Date(); return n.getFullYear() + '-' + String(n.getMonth() + 1).padStart(2, '0'); })(), targetMinutes: 300 }
    ],
    planDocs: [
      { periodType: 'MONTH', periodKey: '2026-09', status: 'CLOSED', summary: '九月总结（来自手机）', closedAt: T0 - 30 * DAY, snapshot: { label: '2026年9月', totalTargetMinutes: 1000, totalUsedMillis: 0, recordCount: 0, rows: [] } }
    ],
    itemTags: [{ itemId: 1, tagId: 1 }, { itemId: 2, tagId: 1 }]
  };

  const dataFile = path.join(process.env.APPDATA, 'TimeBook', 'data.json');
  const before = JSON.parse(fs.readFileSync(dataFile, 'utf8'));

  ipcMain.removeHandler('file:import');
  ipcMain.handle('file:import', () => ({ ok: true, text: JSON.stringify(fixture), file: 'D:\\手机备份\\TimeBook-备份-20261124-1830.json' }));

  await click('#navBackup');
  await shot('.shot-backup.png');
  await click('[data-act="import"]');
  await wait(500);
  await shot('.shot-import.png');
  const dlgText = await exec(`document.querySelector('.dialog').innerText.replace(/\\n/g,' | ')`);
  console.log('IMPORT_DLG:', JSON.stringify(dlgText));

  await click('[data-act="import-merge"]');
  await wait(900);
  const repText = await exec(`document.querySelector('.dialog') ? document.querySelector('.dialog').innerText.replace(/\\n/g,' | ') : 'NO_DIALOG'`);
  console.log('MERGE_REPORT:', JSON.stringify(repText));
  await shot('.shot-merge-report.png');
  await click('[data-act="close-dlg"]');
  await wait(700);

  const after = JSON.parse(fs.readFileSync(dataFile, 'utf8'));
  console.log('MERGE_COUNTS:', JSON.stringify({
    items: before.items.length + '->' + after.items.length,
    tags: before.tags.length + '->' + after.tags.length,
    records: before.records.length + '->' + after.records.length,
    plans: before.plans.length + '->' + after.plans.length,
    docs: before.planDocs.length + '->' + after.planDocs.length,
    readingTag: (after.tags.find(t => t.name === '写作') || {}).id,
    meditationItem: (after.items.find(i => i.name === '冥想') || {}).id,
    running: after.records.filter(r => r.endAt == null).length,
    workTagPlan: (after.plans.find(p => p.targetType === 'TAG' && p.targetMinutes > 8000) || {}).targetMinutes || null
  }));

  /* ---------------------------------------------- 托盘 / 关闭不退出 */
  console.log('--- 托盘常驻 ---');
  win.close();
  await wait(600);
  console.log('AFTER_CLOSE:', JSON.stringify({
    windows: BrowserWindow.getAllWindows().length,
    destroyed: win.isDestroyed(),
    visible: win.isDestroyed() ? null : win.isVisible()
  }));
  win.show();
  await wait(400);
  console.log('AFTER_SHOW:', JSON.stringify({ visible: win.isVisible() }));

  console.log('ERRORS:', JSON.stringify(errors.slice(0, 10)));
  app.quit();
});