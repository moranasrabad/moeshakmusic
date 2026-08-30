'use strict'
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('moeshak', {
  // one generic request/response channel
  invoke: (channel, payload) => ipcRenderer.invoke('cmd', channel, payload || {}),
  // one generic event channel
  on: (channel, cb) => {
    const handler = (e, data) => cb(data)
    ipcRenderer.on('evt:' + channel, handler)
    return () => ipcRenderer.removeListener('evt:' + channel, handler)
  }
})
