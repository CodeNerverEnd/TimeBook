'use strict';
/* =====================================================================
 * TimeBook 桌面版 · 主进程
 *  - 数据落在 %APPDATA%\TimeBook\data.json（原子写 + 上一版 .bak）
 *  - 备份文件与安卓版同格式，手机 ↔ 电脑可直接互导
 *  - 提醒交给主进程定时，窗口最小化也准时弹系统通知
 *  - 关闭窗口只是收进托盘，计时与提醒继续；托盘菜单里才能真正退出
 * ===================================================================== */

const { app, BrowserWindow, ipcMain, dialog, Notification, shell, Menu, globalShortcut, Tray, nativeImage } = require('electron');
const path = require('path');
const fs = require('fs');

app.setName('TimeBook');
app.setAppUserModelId('com.timebook.desktop');

const APP_DIR = path.join(app.getPath('appData'), 'TimeBook');
const DATA_FILE = path.join(APP_DIR, 'data.json');
const BACKUP_DIR = path.join(APP_DIR, 'backups');
const KEEP_AUTO_BACKUP = 5;

let win = null;
let tray = null;
let remindTimer = null;
let isRunning = false;
let runningName = '';
let runningStartAt = 0;
let trayTicker = null;
let balloonShown = false;

/* ------------------------------------------------------------ 文件 */

function ensureDir(p) {
  if (!fs.existsSync(p)) fs.mkdirSync(p, { recursive: true });
}

function defaultData() {
  const now = Date.now();
  return {
    tags: [
      { id: 1, name: '工作', colorArgb: 0xFF3C6E9C | 0, note: '', archived: false, createdAt: now, sortOrder: 0 },
      { id: 2, name: '学习', colorArgb: 0xFF6B5B95 | 0, note: '', archived: false, createdAt: now, sortOrder: 1 },
      { id: 3, name: '生活', colorArgb: 0xFFB4803A | 0, note: '', archived: false, createdAt: now, sortOrder: 2 }
    ],
    items: [], records: [], plans: [], planDocs: [], itemTags: []
  };
}

function normalize(d) {
  return {
    tags: Array.isArray(d.tags) ? d.tags : [],
    items: Array.isArray(d.items) ? d.items : [],
    records: Array.isArray(d.records) ? d.records : [],
    plans: Array.isArray(d.plans) ? d.plans : [],
    planDocs: Array.isArray(d.planDocs) ? d.planDocs : [],
    itemTags: Array.isArray(d.itemTags) ? d.itemTags : []
  };
}

function readData() {
  try {
    if (!fs.existsSync(DATA_FILE)) return defaultData();
    return normalize(JSON.parse(fs.readFileSync(DATA_FILE, 'utf8')));
  } catch (e) {
    // 文件损坏：先把坏文件留档，再退回空库，绝不静默覆盖用户数据
    try { fs.copyFileSync(DATA_FILE, DATA_FILE + '.broken-' + Date.now()); } catch (_) {}
    return defaultData();
  }
}

function writeData(data) {
  ensureDir(APP_DIR);
  const tmp = DATA_FILE + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2), 'utf8');
  try {
    if (fs.existsSync(DATA_FILE)) fs.copyFileSync(DATA_FILE, DATA_FILE + '.bak');
  } catch (_) {}
  fs.renameSync(tmp, DATA_FILE);
}

function backupFileName(now) {
  const d = new Date(now);
  const p = n => String(n).padStart(2, '0');
  return `TimeBook-备份-${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}.json`;
}

/* ------------------------------------------------------------ 窗口 */

function showWindow() {
  if (!win || win.isDestroyed()) { createWindow(); return; }
  if (win.isMinimized()) win.restore();
  win.show();
  win.focus();
}

function createWindow() {
  win = new BrowserWindow({
    width: 1280,
    height: 860,
    minWidth: 1020,
    minHeight: 660,
    backgroundColor: '#FAF8F3',
    title: 'TimeBook · 柳比歇夫时间统计',
    icon: path.join(__dirname, 'build', 'icon.png'),
    show: false,
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      spellcheck: false
    }
  });

  Menu.setApplicationMenu(null);
  win.loadFile(path.join(__dirname, 'src', 'index.html'));
  win.once('ready-to-show', () => win.show());

  // 关闭窗口 = 收进托盘：计时和到点提醒都继续，不打断正在做的事
  win.on('close', e => {
    if (app.isQuitting) return;
    e.preventDefault();
    win.hide();
    if (!balloonShown) {
      balloonShown = true;
      try {
        if (tray) tray.displayBalloon({
          title: 'TimeBook 还在后台运行',
          content: '计时和到点提醒继续有效。右键托盘图标可以退出。',
          iconType: 'info'
        });
      } catch (_) {}
    }
  });

  win.on('closed', () => { win = null; });
}

/* ------------------------------------------------------------ 托盘 */

function trayImage() {
  const ico = path.join(__dirname, 'build', 'tray.ico');
  if (fs.existsSync(ico)) return nativeImage.createFromPath(ico);
  return nativeImage.createFromPath(path.join(__dirname, 'build', 'tray.png'));
}

function clockText(ms) {
  const t = Math.max(0, Math.floor(ms / 1000));
  const p = n => String(n).padStart(2, '0');
  return p(Math.floor(t / 3600)) + ':' + p(Math.floor((t % 3600) / 60)) + ':' + p(t % 60);
}

function sendCmd(cmd) {
  if (win && !win.isDestroyed()) win.webContents.send('cmd', cmd);
}

function runningTip() {
  if (!isRunning) return '没有在计时';
  const used = runningStartAt ? clockText(Date.now() - runningStartAt) : '';
  return '正在计时：' + (runningName || '事项') + (used ? ' ' + used : '');
}

function refreshTray() {
  if (!tray) return;
  const tip = runningTip();
  tray.setToolTip('TimeBook · ' + tip);
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: tip, enabled: false },
    { type: 'separator' },
    { label: '显示主窗口', click: () => showWindow() },
    {
      label: isRunning ? '结束计时并写备注…' : '开始计时…',
      click: () => { showWindow(); setTimeout(() => sendCmd('toggle'), 90); }
    },
    { type: 'separator' },
    { label: '打开数据文件夹', click: () => { ensureDir(APP_DIR); shell.showItemInFolder(DATA_FILE); } },
    { type: 'separator' },
    { label: '退出 TimeBook', click: () => { app.isQuitting = true; app.quit(); } }
  ]));
}

function createTray() {
  try {
    tray = new Tray(trayImage());
  } catch (err) {
    console.error('[tray] 创建失败：', err && err.message);
    return;
  }
  tray.setToolTip('TimeBook · 柳比歇夫时间统计');
  console.log('[tray] ready');
  tray.on('click', () => {
    // 窗口就在眼前 → 收起来；被别的窗口盖住或已隐藏 → 叫到最前
    if (win && !win.isDestroyed() && win.isVisible() && !win.isMinimized() && win.isFocused()) win.hide();
    else showWindow();
  });
  tray.on('double-click', () => showWindow());
  refreshTray();

  // 计时中每秒刷新一次托盘提示，鼠标悬停就能看到已用时间
  trayTicker = setInterval(() => {
    if (!tray) return;
    if (isRunning) tray.setToolTip('TimeBook · ' + runningTip());
  }, 1000);
}

/* ------------------------------------------------------------ IPC */

ipcMain.handle('data:load', () => ({ data: readData(), file: DATA_FILE, existed: fs.existsSync(DATA_FILE) }));

ipcMain.handle('data:save', (_e, data) => {
  try {
    writeData(normalize(data));
    return { ok: true };
  } catch (err) {
    return { ok: false, error: err.message };
  }
});

ipcMain.on('running:update', (_e, payload) => {
  if (payload && typeof payload === 'object') {
    isRunning = !!payload.running;
    runningName = isRunning ? String(payload.itemName || '') : '';
    runningStartAt = isRunning ? Number(payload.startAt || 0) : 0;
  } else {
    isRunning = !!payload;
    if (!isRunning) { runningName = ''; runningStartAt = 0; }
  }
  refreshTray();
});

ipcMain.handle('reminder:set', (_e, payload) => {
  if (remindTimer) { clearTimeout(remindTimer); remindTimer = null; }
  if (!payload || !payload.at) return { ok: true };
  const delay = payload.at - Date.now();
  if (delay <= 0) return { ok: true, past: true };
  if (delay > 0x7fffffff) return { ok: true, skipped: true };
  remindTimer = setTimeout(() => {
    remindTimer = null;
    if (!Notification.isSupported()) return;
    new Notification({
      title: 'TimeBook 提醒',
      body: payload.body || '到点了',
      silent: false
    }).show();
    if (win && !win.isDestroyed()) win.webContents.send('cmd', 'reminded');
  }, delay);
  return { ok: true };
});

ipcMain.on('reminder:clear', () => {
  if (remindTimer) { clearTimeout(remindTimer); remindTimer = null; }
});

ipcMain.handle('notify', (_e, { title, body }) => {
  if (!Notification.isSupported()) return { ok: false };
  new Notification({ title: title || 'TimeBook', body: body || '' }).show();
  return { ok: true };
});

ipcMain.handle('file:export', async (_e, { text, name }) => {
  const res = await dialog.showSaveDialog(win, {
    title: '导出备份',
    defaultPath: path.join(app.getPath('documents'), name || backupFileName(Date.now())),
    filters: [{ name: 'TimeBook 备份', extensions: ['json'] }]
  });
  if (res.canceled || !res.filePath) return { ok: false, canceled: true };
  try {
    fs.writeFileSync(res.filePath, text, 'utf8');
    return { ok: true, file: res.filePath };
  } catch (err) {
    return { ok: false, error: err.message };
  }
});

ipcMain.handle('file:import', async () => {
  const res = await dialog.showOpenDialog(win, {
    title: '选择备份文件',
    properties: ['openFile'],
    filters: [{ name: 'TimeBook 备份', extensions: ['json'] }]
  });
  if (res.canceled || !res.filePaths.length) return { ok: false, canceled: true };
  try {
    const text = fs.readFileSync(res.filePaths[0], 'utf8');
    return { ok: true, text, file: res.filePaths[0] };
  } catch (err) {
    return { ok: false, error: err.message };
  }
});

/** 自动备份：写进 backups/，只留最近 5 份 */
ipcMain.handle('backup:auto', (_e, text) => {
  try {
    ensureDir(BACKUP_DIR);
    const file = path.join(BACKUP_DIR, backupFileName(Date.now()));
    fs.writeFileSync(file, text, 'utf8');
    const files = fs.readdirSync(BACKUP_DIR)
      .filter(f => f.startsWith('TimeBook-备份-') && f.endsWith('.json'))
      .map(f => ({ f, t: fs.statSync(path.join(BACKUP_DIR, f)).mtimeMs }))
      .sort((a, b) => b.t - a.t);
    files.slice(KEEP_AUTO_BACKUP).forEach(x => {
      try { fs.unlinkSync(path.join(BACKUP_DIR, x.f)); } catch (_) {}
    });
    return { ok: true, file, kept: Math.min(files.length, KEEP_AUTO_BACKUP) };
  } catch (err) {
    return { ok: false, error: err.message };
  }
});

ipcMain.handle('dir:open', () => {
  ensureDir(APP_DIR);
  shell.showItemInFolder(DATA_FILE);
  return { ok: true, dir: APP_DIR };
});

ipcMain.handle('app:info', () => ({
  version: app.getVersion(),
  electron: process.versions.electron,
  dataFile: DATA_FILE,
  backupDir: BACKUP_DIR
}));

/* ------------------------------------------------------------ 生命周期 */

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', () => showWindow());

  app.whenReady().then(() => {
    ensureDir(APP_DIR);
    createTray();
    createWindow();

    // Ctrl+Alt+Space：窗口叫回来；有计时就结束，没有就开始选事项
    globalShortcut.register('CommandOrControl+Alt+Space', () => {
      if (!win || win.isDestroyed()) createWindow();
      showWindow();
      setTimeout(() => sendCmd('toggle'), 90);
    });

    app.on('activate', () => showWindow());
  });

  // 关掉窗口只是收进托盘，进程继续跑（计时、提醒都不受影响）
  app.on('window-all-closed', () => {});

  app.on('before-quit', () => { app.isQuitting = true; });

  app.on('will-quit', () => {
    app.isQuitting = true;
    globalShortcut.unregisterAll();
    if (trayTicker) { clearInterval(trayTicker); trayTicker = null; }
    if (tray) { tray.destroy(); tray = null; }
  });
}
