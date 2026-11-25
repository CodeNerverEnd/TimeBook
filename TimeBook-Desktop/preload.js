'use strict';

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('bridge', {
  loadData: () => ipcRenderer.invoke('data:load'),
  saveData: data => ipcRenderer.invoke('data:save', data),
  exportFile: (text, name) => ipcRenderer.invoke('file:export', { text, name }),
  importFile: () => ipcRenderer.invoke('file:import'),
  autoBackup: text => ipcRenderer.invoke('backup:auto', text),
  openDataFolder: () => ipcRenderer.invoke('dir:open'),
  notify: (title, body) => ipcRenderer.invoke('notify', { title, body }),
  setReminder: payload => ipcRenderer.invoke('reminder:set', payload),
  clearReminder: () => ipcRenderer.send('reminder:clear'),
  setRunning: (running, info) => ipcRenderer.send('running:update', {
    running: !!running,
    itemName: info && info.itemName ? String(info.itemName) : '',
    startAt: info && info.startAt ? Number(info.startAt) : 0
  }),
  info: () => ipcRenderer.invoke('app:info'),
  onCommand: cb => ipcRenderer.on('cmd', (_e, cmd) => cb(cmd))
});
