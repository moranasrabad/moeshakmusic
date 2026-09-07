'use strict'
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('moeshak', {
  // one generic request/response channel
  invoke: (channel, payload) => ipcRenderer.invoke('cmd', channel, payload || {}),
  // گزارش خطا/کرش رندرر به main برای ثبت در لاگ
  logError: (msg) => { try { ipcRenderer.send('renderer-error', String(msg)) } catch (e) {} },
  // one generic event channel
  on: (channel, cb) => {
    const handler = (e, data) => cb(data)
    ipcRenderer.on('evt:' + channel, handler)
    return () => ipcRenderer.removeListener('evt:' + channel, handler)
  }
})
