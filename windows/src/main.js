'use strict'
const { app, BrowserWindow, protocol, ipcMain, Tray, Menu, nativeImage, shell, Notification } = require('electron')
const path = require('path')
const fs = require('fs')
const { Readable } = require('stream')
const QRCode = require('qrcode')
const { Tg, CHUNK, APP_VERSION } = require('./td')
const { Store } = require('./store')

const sleep = ms => new Promise(r => setTimeout(r, ms))

let tg = null
let store = null
let win = null
let tray = null
let quitting = false

const artCache = new Map()
const dlRequested = new Set()

// ✅ v6.0.0: لوگو به‌صورت data URI — هم در دیو هم در پکیج‌شده بدون مشکل path لود می‌شود
let logoDataUri = ''
try {
  logoDataUri = 'data:image/png;base64,' + fs.readFileSync(path.join(__dirname, '..', 'assets', 'icon.png')).toString('base64')
} catch (e) {
  try {
    logoDataUri = 'data:image/png;base64,' + fs.readFileSync(path.join(process.resourcesPath || '', 'icon.png')).toString('base64')
  } catch (e2) {}
}

// هویت اپ برای نوتیفیکیشن‌های ویندوز
try { app.setAppUserModelId('ir.moeshakteam.moeshakmusic') } catch (e) {}

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
    // ✅ v6.0.0: آیکون تری از مسیر داخل app-آسار/پکیج — با fallback به dataURI
    let img = nativeImage.createFromPath(path.join(__dirname, '..', 'assets', 'icon.png'))
    if (!img || img.isEmpty()) img = nativeImage.createFromDataURL(logoDataUri)
    img = img.resize({ width: 16, height: 16 })
    if (process.platform === 'win32') {
      // روی ویندوز آیکون کوچک باید ۱۶×۱۶ با کیفیت باشه
      img.setTemplateImage && img.setTemplateImage(false)
    }
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
              downloads: store.downloads().length,
              followed: store.followed().length
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

        case 'getMe': {
          try {
            const me = await ensureTg().getMe()
            return { id: me.id, firstName: me.first_name || '', lastName: me.last_name || '', username: me.username || '' }
          } catch (e) { return null }
        }

        case 'app.logo': return logoDataUri

        case 'chats.list': return ensureTg().getChats(payload.limit || 1000)
        case 'chats.all': return ensureTg().getAllChats()
        case 'chats.folders': return ensureTg().getChatFolders()
        case 'chats.search': return ensureTg().searchChats(payload.query || '')
        case 'chats.get': return ensureTg().getChatInfo(payload.chatId)

        case 'scan.start': {
          const t = ensureTg()
          const tracks = await t.scan(payload.chatId, payload.mode || '100', prog => send('scan', prog))
          return { tracks }
        }
        // ✅ v6.0.0: اسکن همهٔ چت‌ها یک‌جا (مثل اندروید — بدون انتخاب یکی‌یکی)
        case 'scan.all': {
          const t = ensureTg()
          const tracks = await t.scanAll(prog => send('scan', prog), { depth: payload.depth || 300 })
          return { tracks }
        }
        case 'scan.cancel': ensureTg().cancelScan(); return true

        // ---- دنبال‌شده‌ها (Following) — مثل اندروید ----
        case 'follow.list': return store.followed()
        case 'follow.add': {
          const list = store.followed()
          if (list.find(f => f.chatId === payload.chatId)) return list
          // دیپ‌اسکن کامل این چت + افزودن به کتابخانه (پس‌زمینه، بدون پراگرس صفحهٔ اسکن)
          const tracks = await ensureTg().scan(payload.chatId, 'all', null, { background: true })
          const known = tracks.map(x => x.chatId + ':' + x.messageId)
          list.push({ chatId: payload.chatId, title: payload.title || '', knownIds: known, createdAt: Date.now() })
          store.saveFollowed(list)
          // نتایج به کتابخانه هم اضافه شوند (کلید پایدار chatId:messageId)
          const cur = store.library()
          const seen = new Set(cur.map(x => x.chatId + ':' + x.messageId))
          const added = tracks.filter(x => !seen.has(x.chatId + ':' + x.messageId))
          if (added.length) store.saveLibrary(cur.concat(added))
          send('follow', list)
          send('lib', store.library())
          return list
        }
        case 'follow.remove': {
          const list = store.followed().filter(f => f.chatId !== payload.chatId)
          store.saveFollowed(list)
          send('follow', list)
          return list
        }
        case 'follow.check': {
          // چک همهٔ چت‌های دنبال‌شده: آهنگ جدید → کتابخانه + نوتیف
          const list = store.followed()
          let totalNew = 0
          const lib = store.library()
          const libSeen = new Set(lib.map(x => x.chatId + ':' + x.messageId))
          for (const f of list) {
            try {
              const known = new Set(f.knownIds || [])
              // چک پس‌زمینه — بدون رویداد پیشرفت رو صفحهٔ اسکن (تا «اسکن دوتایی» دیده نشود)
              const tracks = await ensureTg().scan(f.chatId, 'all', null, { background: true })
              const fresh = []
              for (const tr of tracks) {
                const key = tr.chatId + ':' + tr.messageId
                if (!known.has(key) && !libSeen.has(key)) { fresh.push(tr); libSeen.add(key) }
                known.add(key)
              }
              f.knownIds = Array.from(known)
              if (fresh.length) {
                lib.push(...fresh)
                totalNew += fresh.length
                try {
                  if (Notification.isSupported()) {
                    const first = fresh[0].title || 'Music'
                    const n = new Notification({
                      title: '♪ ' + (f.title || 'Telegram'),
                      body: (fresh.length === 1 ? first : first + ' +' + (fresh.length - 1))
                    })
                    n.on('click', () => { if (win) { win.show(); win.focus() } })
                    n.show()
                  }
                } catch (e) {}
              }
            } catch (e) {}
          }
          store.saveFollowed(list)
          if (totalNew > 0) {
            store.saveLibrary(lib)
            send('lib', lib)
          }
          send('follow', list)
          return { followed: list.length, newTracks: totalNew }
        }

        case 'lib.list': return store.library()
        case 'lib.add': {
          const cur = store.library()
          // ✅ v6.0.2: کلید پایدار chatId:messageId (فقط fileId ممکن است بین چت‌ها تکراری شود)
          const seen = new Set(cur.map(t => t.chatId + ':' + t.messageId))
          const added = (payload.tracks || []).filter(t => !seen.has(t.chatId + ':' + t.messageId))
          store.saveLibrary(cur.concat(added))
          send('lib', store.library())
          return { added: added.length }
        }
        case 'lib.clear': store.saveLibrary([]); send('lib', []); return true
        // ✅ تنظیم کامل کتابخانه (برای حذف گروهی)
        case 'lib.set': {
          store.saveLibrary(payload.tracks || [])
          send('lib', store.library())
          return true
        }

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
        case 'pl.addMany': {
          const pls = store.playlists()
          const pl = pls.find(p => p.id === payload.plId)
          if (pl) {
            for (const track of (payload.tracks || [])) {
              if (!pl.tracks.find(t => (t.chatId + ':' + t.messageId) === (track.chatId + ':' + track.messageId))) pl.tracks.push(track)
            }
            store.savePlaylists(pls); send('pl', pls)
          }
          return true
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
        case 'file.cancel': await ensureTg().cancelDownloadFile(payload.fileId); return true
        case 'app.version': return app.getVersion()
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
    try { ensureTg() } catch (e) { log('TDLib init failed: ' + (e && e.message)) }

    protocol.handle('moeshak-stream', req => handleMedia(req, 'stream'))
    protocol.handle('moeshak-local', req => handleMedia(req, 'local'))
    protocol.handle('moeshak-art', handleArt)

    registerIpc()
    createWindow()
    createTray()

    // ✅ v6.0.0: چک دوره‌ای دنبال‌شده‌ها (مثل اندروید — هر ۱۵ دقیقه)
    const followCheck = async () => {
      try {
        if (!tg || !store) return
        const list = store.followed()
        if (!list.length) return
        checkFollowedSilent()
      } catch (e) {}
    }
    setTimeout(followCheck, 60 * 1000)
    setInterval(followCheck, 15 * 60 * 1000)
  })

async function checkFollowedSilent() {
  try {
    const list = store.followed()
    if (!list.length) return
    let totalNew = 0
    const lib = store.library()
    const libSeen = new Set(lib.map(x => x.chatId + ':' + x.messageId))
    for (const f of list) {
      try {
        const known = new Set(f.knownIds || [])
        const tracks = await tg.scan(f.chatId, 'all', null, { background: true })
        const fresh = []
        for (const tr of tracks) {
          const key = tr.chatId + ':' + tr.messageId
          if (!known.has(key) && !libSeen.has(key)) { fresh.push(tr); libSeen.add(key) }
          known.add(key)
        }
        f.knownIds = Array.from(known)
        if (fresh.length) {
          lib.push(...fresh)
          totalNew += fresh.length
          try {
            if (Notification.isSupported()) {
              const first = fresh[0].title || 'Music'
              const n = new Notification({
                title: '♪ ' + (f.title || 'Telegram'),
                body: (fresh.length === 1 ? first : first + ' +' + (fresh.length - 1))
              })
              n.on('click', () => { if (win) { win.show(); win.focus() } })
              n.show()
            }
          } catch (e) {}
        }
      } catch (e) {}
    }
    store.saveFollowed(list)
    if (totalNew > 0) {
      store.saveLibrary(lib)
      send('lib', lib)
    }
    send('follow', list)
  } catch (e) {
    log('follow check: ' + (e && e.message))
  }
}

  app.on('window-all-closed', () => {
    if (!quitting) { /* keep running in tray */ }
  })

  app.on('before-quit', () => { quitting = true })

  app.on('will-quit', () => {
    if (tg) { tg.close().catch(() => {}) }
  })
}
