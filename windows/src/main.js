'use strict'
const { app, BrowserWindow, protocol, ipcMain, Tray, Menu, nativeImage, shell } = require('electron')
const path = require('path')
const fs = require('fs')
const { Readable } = require('stream')
const QRCode = require('qrcode')
const { Tg, CHUNK } = require('./td')
const { Store } = require('./store')

const sleep = ms => new Promise(r => setTimeout(r, ms))

let tg = null
let store = null
let win = null
let tray = null
let quitting = false

const artCache = new Map()
const dlRequested = new Set()

// ---------------------------------------------------------------------------
// logging
const logLines = []
function log(line) {
  const entry = { ts: new Date().toISOString(), line: String(line) }
  logLines.push(entry)
  if (logLines.length > 500) logLines.shift()
  if (win) win.webContents.send('evt:log', entry)
}

// ---------------------------------------------------------------------------
// schemes (must be registered before app ready)
protocol.registerSchemesAsPrivileged([
  { scheme: 'moeshak-stream', privileges: { stream: true, supportFetchAPI: true, bypassCSP: true } },
  { scheme: 'moeshak-local', privileges: { stream: true, supportFetchAPI: true, bypassCSP: true } },
  { scheme: 'moeshak-art', privileges: { stream: true, supportFetchAPI: true, bypassCSP: true } }
])

// ---------------------------------------------------------------------------
function ensureTg() {
  if (tg) return tg
  const settings = store.settings()
  const apiId = parseInt(settings.apiId || '6', 10)
  const apiHash = settings.apiHash || 'eb06d4abfb49dc3eeb1aeb98ae0f581e'
  tg = new Tg({
    apiId,
    apiHash,
    databaseDirectory: path.join(app.getPath('userData'), 'tdlib'),
    filesDirectory: path.join(app.getPath('userData'), 'tdlib-files'),
    onEvent: (type, data) => {
      if (type === 'auth') lastAuth = data
      if (win && !win.isDestroyed()) win.webContents.send('evt:' + type, data)
      if (type === 'error') log('TDLib: ' + data)
    }
  })
  log('TDLib client created (api_id=' + apiId + ')')
  return tg
}

// ---------------------------------------------------------------------------
// streaming helpers
async function ensureDownload(fileId) {
  if (dlRequested.has(fileId)) return
  dlRequested.add(fileId)
  try {
    await tg.downloadFile(fileId, 32)
  } catch (e) {
    dlRequested.delete(fileId)
  }
}

function fsRead(pathStr, offset, count) {
  return new Promise((resolve, reject) => {
    let fd
    try { fd = fs.openSync(pathStr, 'r') } catch (e) { return resolve(Buffer.alloc(0)) }
    const buf = Buffer.alloc(count)
    fs.read(fd, buf, 0, count, offset, (err, bytesRead) => {
      try { fs.closeSync(fd) } catch (e) {}
      if (err) return reject(err)
      resolve(buf.subarray(0, bytesRead))
    })
  })
}

async function readChunk(fileId, offset, count, mode) {
  if (mode === 'local') {
    for (let i = 0; i < 90; i++) {
      const f = await tg.getFile(fileId)
      if (f && f.completed && f.path) return await fsRead(f.path, offset, count)
      await ensureDownload(fileId)
      await sleep(500)
    }
    throw new Error('download timeout')
  }
  await ensureDownload(fileId)
  for (let i = 0; i < 100; i++) {
    try {
      const data = await tg.readFilePart(fileId, offset, count)
      if (data && data.length) return data
    } catch (e) { /* not ready yet */ }
    await sleep(250)
  }
  throw new Error('stream timeout')
}

async function handleMedia(request, mode) {
  try {
    const u = new URL(request.url)
    const fileId = parseInt(u.hostname, 10)
    const size = parseInt(u.searchParams.get('size') || '0', 10)
    const mime = u.searchParams.get('mime') || 'audio/mpeg'
    const range = request.headers.get('range')

    let start = 0, end = size > 0 ? size - 1 : 0
    if (size <= 0) {
      const f = await tg.getFile(fileId)
      if (f && f.size > 0) { end = f.size - 1 } else { return new Response(null, { status: 416 }) }
    }
    if (range) {
      const m = /bytes=(\d*)-(\d*)/.exec(range)
      if (m) {
        if (m[1] !== '') start = parseInt(m[1], 10)
        if (m[2] !== '') end = Math.min(parseInt(m[2], 10), size - 1)
      }
    }
    if (start > end || start >= size) {
      return new Response(null, { status: 416, headers: { 'Content-Range': `bytes */${size}` } })
    }
    end = Math.min(end, size - 1)
    const length = end - start + 1
    const status = range ? 206 : 200
    const headers = {
      'Content-Type': mime,
      'Accept-Ranges': 'bytes',
      'Content-Length': String(length),
      'Content-Range': range ? `bytes ${start}-${end}/${size}` : undefined,
      'Cache-Control': 'no-store'
    }
    let pos = start
    const body = new ReadableStream({
      async pull(controller) {
        try {
          if (pos > end) { controller.close(); return }
          const want = Math.min(CHUNK, end - pos + 1)
          const chunk = await readChunk(fileId, pos, want, mode)
          if (!chunk || !chunk.length) { controller.close(); return }
          pos += chunk.length
          controller.enqueue(new Uint8Array(chunk))
        } catch (e) {
          controller.error(e)
        }
      }
    })
    return new Response(body, { status, headers })
  } catch (e) {
    return new Response(null, { status: 500 })
  }
}

async function getArt(fileId) {
  if (!fileId) return null
  if (artCache.has(fileId)) return artCache.get(fileId)
  try {
    await tg.downloadFile(fileId, 1)
    for (let i = 0; i < 40; i++) {
      const f = await tg.getFile(fileId)
      if (f && f.completed && f.path) {
        const buf = await fs.promises.readFile(f.path)
        if (buf.length) { artCache.set(fileId, buf); return buf }
      }
      await sleep(250)
    }
  } catch (e) {}
  return null
}

async function handleArt(request) {
  try {
    const fileId = parseInt(new URL(request.url).hostname, 10)
    const buf = await getArt(fileId)
    if (!buf) return new Response(null, { status: 404 })
    return new Response(buf, { status: 200, headers: { 'Content-Type': 'image/jpeg', 'Cache-Control': 'max-age=86400' } })
  } catch (e) {
    return new Response(null, { status: 404 })
  }
}

// ---------------------------------------------------------------------------
// window & tray
function createWindow() {
  win = new BrowserWindow({
    width: 1180,
    height: 780,
    minWidth: 900,
    minHeight: 620,
    backgroundColor: '#0e0e13',
    icon: nativeImage.createFromPath(path.join(__dirname, '..', 'assets', 'icon.png')),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false
    }
  })
  win.loadFile(path.join(__dirname, '..', 'renderer', 'index.html'))
  win.on('closed', () => { win = null })
}

function createTray() {
  try {
    const img = nativeImage.createFromPath(path.join(__dirname, '..', 'assets', 'icon.png')).resize({ width: 16, height: 16 })
    tray = new Tray(img)
    tray.setToolTip('Moeshak Music')
    tray.setContextMenu(Menu.buildFromTemplate([
      { label: 'Open Moeshak Music', click: () => { if (win) { win.show(); win.focus() } else createWindow() } },
      { type: 'separator' },
      { label: 'Quit', click: () => { quitting = true; app.quit() } }
    ]))
    tray.on('double-click', () => { if (win) { win.show(); win.focus() } })
  } catch (e) {}
}

// ---------------------------------------------------------------------------
// IPC
function send(chan, data) { if (win && !win.isDestroyed()) win.webContents.send('evt:' + chan, data) }

function registerIpc() {
  ipcMain.handle('cmd', async (e, channel, payload) => {
    try {
      switch (channel) {
        case 'getState': {
          const s = store.settings()
          return {
            auth: lastAuth || { state: 'closed' },
            settings: s,
            counts: {
              library: store.library().length,
              favorites: store.favorites().length,
              playlists: store.playlists().length,
              downloads: store.downloads().length
            }
          }
        }
        case 'auth.setPhone': await ensureTg().setPhone(payload.phone); return true
        case 'auth.setCode': await ensureTg().setCode(payload.code); return true
        case 'auth.setPassword': await ensureTg().setPassword(payload.password); return true
        case 'auth.setName': await ensureTg().registerUser(payload.firstName, payload.lastName); return true
        case 'auth.qr': {
          await ensureTg().qrLogin()
          for (let i = 0; i < 20 && !tg.lastQrLink; i++) await sleep(250)
          const link = tg.lastQrLink
          if (!link) return { link: '', qr: '' }
          const qr = await QRCode.toDataURL(link, { width: 320, margin: 1 })
          return { link, qr }
        }
        case 'qr.image': {
          if (!payload.link) return { qr: '' }
          const qr = await QRCode.toDataURL(payload.link, { width: 320, margin: 1 })
          return { qr }
        }
        case 'auth.logout': await ensureTg().logout(); return true

        case 'chats.list': return ensureTg().getChats(payload.limit || 200)
        case 'chats.search': return ensureTg().searchChats(payload.query || '')
        case 'chats.get': return ensureTg().getChatInfo(payload.chatId)

        case 'scan.start': {
          const t = ensureTg()
          const tracks = await t.scan(payload.chatId, payload.mode || '100', prog => send('scan', prog))
          return { tracks }
        }
        case 'scan.cancel': ensureTg().cancelScan(); return true

        case 'lib.list': return store.library()
        case 'lib.add': {
          const cur = store.library()
          const seen = new Set(cur.map(t => t.id))
          const added = (payload.tracks || []).filter(t => !seen.has(t.id))
          store.saveLibrary(cur.concat(added))
          send('lib', store.library())
          return true
        }
        case 'lib.clear': store.saveLibrary([]); send('lib', []); return true

        case 'fav.toggle': {
          const track = payload.track
          const favs = store.favorites()
          const idx = favs.findIndex(t => t.id === track.id)
          if (idx >= 0) favs.splice(idx, 1)
          else favs.unshift(track)
          store.saveFavorites(favs)
          send('fav', favs)
          return favs
        }
        case 'fav.list': return store.favorites()

        case 'pl.list': return store.playlists()
        case 'pl.create': {
          const pls = store.playlists()
          const pl = { id: Date.now(), name: payload.name || 'Playlist', tracks: [] }
          pls.push(pl); store.savePlaylists(pls); send('pl', pls); return pl
        }
        case 'pl.delete': {
          let pls = store.playlists().filter(p => p.id !== payload.id)
          store.savePlaylists(pls); send('pl', pls); return true
        }
        case 'pl.add': {
          const pls = store.playlists()
          const pl = pls.find(p => p.id === payload.plId)
          if (pl && !pl.tracks.find(t => t.id === payload.track.id)) pl.tracks.push(payload.track)
          store.savePlaylists(pls); send('pl', pls); return true
        }
        case 'pl.remove': {
          const pls = store.playlists()
          const pl = pls.find(p => p.id === payload.plId)
          if (pl) pl.tracks = pl.tracks.filter(t => t.id !== payload.trackId)
          store.savePlaylists(pls); send('pl', pls); return true
        }

        case 'dl.list': return store.downloads()
        case 'dl.add': {
          const dl = store.downloads()
          if (!dl.find(t => t.id === payload.track.id)) dl.unshift(payload.track)
          store.saveDownloads(dl); send('dl', dl)
          try { await ensureTg().downloadFile(payload.track.fileId, 32) } catch (e) {}
          return true
        }
        case 'dl.remove': {
          const dl = store.downloads().filter(t => t.id !== payload.trackId)
          store.saveDownloads(dl); send('dl', dl)
          try { await ensureTg().deleteFile(payload.fileId) } catch (e) {}
          return true
        }
        case 'file.download': await ensureTg().downloadFile(payload.fileId, 32); return true
        case 'file.path': {
          const f = await ensureTg().getFile(payload.fileId)
          return { path: (f && f.completed) ? f.path : '', completed: !!(f && f.completed) }
        }

        case 'settings.get': return store.settings()
        case 'settings.set': {
          const s = store.saveSettings(payload.patch || {})
          send('settings', s)
          return s
        }
        case 'proxy.set': await ensureTg().setProxy(payload.proxy); return true

        case 'log.list': return logLines.slice(-300)
        case 'log.clear': logLines.length = 0; return true

        default: return null
      }
    } catch (err) {
      log('IPC ' + channel + ': ' + (err && err.message))
      throw err
    }
  })
}

// ---------------------------------------------------------------------------
// lifecycle
let lastAuth = null

const gotLock = app.requestSingleInstanceLock()
if (!gotLock) {
  app.quit()
} else {
  app.on('second-instance', () => { if (win) { win.show(); win.focus() } })

  app.whenReady().then(() => {
    store = new Store(app.getPath('userData'))
    ensureTg()

    protocol.handle('moeshak-stream', req => handleMedia(req, 'stream'))
    protocol.handle('moeshak-local', req => handleMedia(req, 'local'))
    protocol.handle('moeshak-art', handleArt)

    registerIpc()
    createWindow()
    createTray()
  })

  app.on('window-all-closed', () => {
    if (!quitting) { /* keep running in tray */ }
  })

  app.on('before-quit', () => { quitting = true })

  app.on('will-quit', () => {
    if (tg) { tg.close().catch(() => {}) }
  })
}
