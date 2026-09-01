'use strict'
/* global MediaMetadata */
const api = window.moeshak
const $ = sel => document.querySelector(sel)

// ---------------- i18n ----------------
const I18N = {
  fa: {
    appName: 'موشک موزیک', loginSub: 'پلیر موزیک متصل به تلگرام — مستقیم با TDLib',
    phone: 'شماره تلفن', qrLogin: 'ورود با QR', next: 'ادامه', signUp: 'ثبت‌نام',
    codeSent: 'کد تأیید به تلگرام فرستاده شد', qrHint: 'تلگرام ← تنظیمات ← دستگاه‌ها ← اتصال دستگاه ← اسکن کن',
    refresh: 'تازه‌سازی', logout: 'خروج',
    tracks: 'آهنگ‌ها', scan: 'اسکن', playlists: 'پلی‌لیست‌ها', favorites: 'فیوریت',
    downloads: 'دانلودها', channels: 'کانال‌ها', chats: 'چت‌ها', followed: 'دنبال‌شده‌ها', settings: 'تنظیمات', log: 'لاگ',
    empty: 'چیزی اینجا نیست', emptyTracks: 'هنوز آهنگی اسکن نکردی. از تب اسکن یا کانال‌ها شروع کن.',
    search: 'جستجو…', playAll: 'پخش همه', clear: 'پاک کردن',
    scanHint: 'یک کانال/گروه انتخاب کن و عمق اسکن را بزن — یا «اسکن همهٔ چت‌ها» را بزن', depth: 'عمق اسکن', start: 'شروع اسکن', cancel: 'لغو',
    scanAllChats: '🔍 اسکن همهٔ چت‌ها', scanned: 'اسکن شد', found: 'آهنگ پیدا شد', addAll: 'افزودن همه به کتابخانه',
    follow: 'دنبال‌کردن', unfollow: 'لغو دنبال', followedNone: 'هنوز چتی را دنبال نکرده‌ای. از تب چت‌ها یا کانال‌ها دکمهٔ 🔔 را بزن.',
    followedCheck: 'بررسی آهنگ جدید', followedCount: 'چت دنبال‌شده', allChats: 'همه', archive: 'آرشیو',
    newPlaylist: 'پلی‌لیست جدید', name: 'نام', create: 'ساخت', delete: 'حذف',
    addToPlaylist: 'افزودن به پلی‌لیست', download: 'دانلود', remove: 'حذف',
    addToQueue: 'افزودن به صف', fullScan: 'اسکن کامل', downloadAll: 'دانلود کامل',
    theme: 'تم', dark: 'شب', light: 'روز', accent: 'رنگ اکسنت', lang: 'زبان',
    proxy: 'پروکسی', proxyType: 'نوع', proxyServer: 'سرور', proxyPort: 'پورت', proxyUser: 'یوزر', proxyPass: 'پسورد',
    proxySave: 'ذخیره پروکسی', apiKeys: 'کلید API شخصی', apiId: 'api_id', apiHash: 'api_hash',
    apiHint: 'بعد از تغییر، اپ را ری‌استارت کن', save: 'ذخیره',
    connReady: 'اتصال برقرار است', connConnecting: 'در حال اتصال…', connWaiting: 'در انتظار شبکه…', connUpdating: 'به‌روزرسانی…',
    logoutConfirm: 'از حساب خارج شوی؟', yes: 'بله', no: 'نه',
    scanDone: 'اسکن تمام شد', track: 'آهنگ',
    downloaded: 'دانلود شده ✓', downloading: 'در حال دانلود…', cancel: 'لغو',
    version: 'نسخه', activeDownloads: 'دانلودهای فعال'
  },
  en: {
    appName: 'Moeshak Music', loginSub: 'Telegram music player — powered by TDLib',
    phone: 'Phone number', qrLogin: 'QR login', next: 'Next', signUp: 'Sign up',
    codeSent: 'Code sent to your Telegram', qrHint: 'Telegram → Settings → Devices → Link Device → scan',
    refresh: 'Refresh', logout: 'Log out',
    tracks: 'Tracks', scan: 'Scan', playlists: 'Playlists', favorites: 'Favorites',
    downloads: 'Downloads', channels: 'Channels', chats: 'Chats', followed: 'Following', settings: 'Settings', log: 'Log',
    empty: 'Nothing here', emptyTracks: 'No tracks yet. Start from Scan or Channels.',
    search: 'Search…', playAll: 'Play all', clear: 'Clear',
    scanHint: 'Pick a channel/group and choose depth — or hit “Scan all chats”', depth: 'Depth', start: 'Start scan', cancel: 'Cancel',
    scanAllChats: '🔍 Scan all chats', scanned: 'Scanned', found: 'tracks found', addAll: 'Add all to library',
    follow: 'Follow', unfollow: 'Unfollow', followedNone: 'Not following any chats yet. Tap 🔔 in Chats or Channels.',
    followedCheck: 'Check for new tracks', followedCount: 'followed chats', allChats: 'All', archive: 'Archive',
    newPlaylist: 'New playlist', name: 'Name', create: 'Create', delete: 'Delete',
    addToPlaylist: 'Add to playlist', download: 'Download', remove: 'Remove',
    addToQueue: 'Add to queue', fullScan: 'Full scan', downloadAll: 'Download all',
    theme: 'Theme', dark: 'Dark', light: 'Light', accent: 'Accent', lang: 'Language',
    proxy: 'Proxy', proxyType: 'Type', proxyServer: 'Server', proxyPort: 'Port', proxyUser: 'User', proxyPass: 'Pass',
    proxySave: 'Save proxy', apiKeys: 'Personal API keys', apiId: 'api_id', apiHash: 'api_hash',
    apiHint: 'Restart the app after changing', save: 'Save',
    connReady: 'Connected', connConnecting: 'Connecting…', connWaiting: 'Waiting for network…', connUpdating: 'Updating…',
    logoutConfirm: 'Log out?', yes: 'Yes', no: 'No',
    scanDone: 'Scan finished', track: 'track',
    downloaded: 'Downloaded ✓', downloading: 'Downloading…', cancel: 'Cancel',
    version: 'Version', activeDownloads: 'Active downloads'
  }
}
let LANG = 'fa'
const t = k => (I18N[LANG] && I18N[LANG][k]) || k

// ---------------- state ----------------
const S = {
  auth: 'closed',
  me: null,
  settings: { theme: 'dark', accent: 'purple', lang: 'fa' },
  tracks: [], favorites: [], playlists: [], downloads: [], channels: [], followed: [],
  scanResults: [], folders: [],
  tab: 'tracks',
  queue: [], qIndex: -1,
  shuffle: false, repeat: 'off',
  current: null, playing: false,
  dlInfo: {}
}

const audio = $('#audio')
let localFallback = false

// ---------------- helpers ----------------
function fmt(sec) {
  sec = Math.max(0, Math.floor(sec || 0))
  const m = Math.floor(sec / 60), s = sec % 60
  return m + ':' + String(s).padStart(2, '0')
}
function artUrl(fileId) { return fileId ? ('moeshak-art://' + fileId) : '' }
function coverOf(track) {
  // کاور خودِ موزیک اول: تامبنیل آلبوم ← مینی‌تامب — عکس کانال فقط فالبک
  if (track.albumCoverFileId) return artUrl(track.albumCoverFileId)
  if (track.albumCoverMini) return track.albumCoverMini
  return artUrl(track.chatPhotoFileId || 0)
}
function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])) }

// ---------------- events ----------------
api.on('auth', d => {
  S.auth = d.state
  if (d.state === 'ready') enterApp()
  else renderLogin(d)
})
api.on('connection', d => {
  const map = { connectionStateReady: 'connReady', connectionStateConnecting: 'connConnecting', connectionStateConnectingToProxy: 'connConnecting', connectionStateWaitingForNetwork: 'connWaiting', connectionStateUpdating: 'connUpdating' }
  const el = $('#connStatus')
  if (el) el.textContent = t(map[d.state] || 'connConnecting')
})
api.on('scan', renderScanProgress)
api.on('file', info => {
  if (info && info.id) {
    S.dlInfo[info.id] = info
    if (S.tab === 'downloads') renderDownloads()
    if (S.tab === 'settings') renderSettingsDl()
    renderNowDl()
  }
})
api.on('lib', list => { S.tracks = list; if (S.tab === 'tracks') renderTracks() })
api.on('fav', list => { S.favorites = list; if (S.tab === 'favorites') renderFavorites() })
api.on('pl', list => { S.playlists = list; if (S.tab === 'playlists' || S.tab === 'playlist') renderPlaylists() })
api.on('dl', list => { S.downloads = list; if (S.tab === 'downloads') renderDownloads(); renderNowDl() })
api.on('follow', list => { S.followed = list; if (S.tab === 'followed') renderFollowed() })
api.on('log', entry => { const box = $('#logBox'); if (box && S.tab === 'log') appendLog(entry) })

// ---------------- boot ----------------
// ✅ v6.0.0: لوگو از main به‌صورت dataURI — در پکیج‌شده هم حتماً لود می‌شود
async function fixLogo() {
  try {
    const uri = await api.invoke('app.logo')
    if (uri) {
      const l = $('#loginLogo'), b = $('#brandLogo')
      if (l) l.src = uri
      if (b) b.src = uri
    }
  } catch (e) {}
}

async function boot() {
  fixLogo()
  let st = { auth: { state: 'closed' }, settings: { theme: 'dark', accent: 'purple', lang: 'fa' } }
  try { st = await api.invoke('getState') } catch (e) { /* keep defaults */ }
  S.settings = st.settings || S.settings
  LANG = S.settings.lang || 'fa'
  applyTheme()
  try {
    const logs = await api.invoke('log.list')
    S.pendingLogs = logs || []
    const box = $('#logBox')
    if (box) { box.innerHTML = ''; S.pendingLogs.forEach(appendLog) }
  } catch (e) {}
  const authState = (st.auth && st.auth.state) || S.auth || 'closed'
  S.auth = authState
  if (authState === 'ready') enterApp()
  else renderLogin({ state: authState })
}

function applyTheme() {
  LANG = S.settings.lang || 'fa'
  document.documentElement.lang = LANG
  document.documentElement.dir = LANG === 'fa' ? 'rtl' : 'ltr'
  document.body.setAttribute('data-theme', S.settings.theme || 'dark')
  document.body.setAttribute('data-accent', S.settings.accent || 'purple')
  document.querySelectorAll('[data-i18n]').forEach(el => { el.textContent = t(el.getAttribute('data-i18n')) })
  document.title = t('appName')
}

// ---------------- login ----------------
function renderLogin(d) {
  $('#app').classList.add('hidden')
  $('#login').classList.remove('hidden')
  const st = d ? d.state : S.auth
  const show = id => { ['loginPhone', 'loginCode', 'loginPassword', 'loginRegister', 'loginQr'].forEach(x => $('#' + x).classList.toggle('hidden', x !== id)) }
  if (st === 'waitPhoneNumber') show('loginPhone')
  else if (st === 'waitCode') show('loginCode')
  else if (st === 'waitPassword') { show('loginPassword'); $('#passwordHint').textContent = d && d.hint ? ('Hint: ' + d.hint) : '' }
  else if (st === 'waitRegistration') show('loginRegister')
  else if (st === 'waitOtherDevice') { show('loginQr'); if (d && d.link) loadQrImage(d.link) }
  else { show('loginPhone') }
  if (d && d.state === 'waitOtherDevice' && d.link) { $('#tabQr').click() }
}

async function loadQrImage(link) {
  const qr = await api.invoke('qr.image', { link }).catch(() => null)
  if (qr && qr.qr) $('#qrImg').src = qr.qr
}

async function loginError(e) {
  const el = $('#loginError')
  el.classList.remove('hidden')
  el.textContent = (e && e.message) || String(e)
}

// ---------------- main app ----------------
async function enterApp() {
  $('#login').classList.add('hidden')
  $('#app').classList.remove('hidden')
  applyTheme()
  try { S.me = await api.invoke('getMe') } catch (e) { S.me = null }
  renderNav()
  loadTabs()
  loadChannels()
  loadFollowed()
  fixLogo()
  // چک خودکار دنبال‌شده‌ها بعد از ورود
  api.invoke('follow.check').then(r => {
    if (r && r.newTracks) toast('🔔 +' + r.newTracks)
  }).catch(() => {})
}

async function loadFollowed() {
  try { S.followed = await api.invoke('follow.list') } catch (e) { S.followed = [] }
  if (S.tab === 'followed') renderFollowed()
}

function renderNav() {
  const items = [
    ['tracks', '🎵', 'tracks'], ['scan', '🔍', 'scan'], ['playlists', '📁', 'playlists'],
    ['favorites', '❤️', 'favorites'], ['downloads', '⬇️', 'downloads'],
    ['followed', '🔔', 'followed'],
    ['channels', '📢', 'channels'], ['chats', '💬', 'chats'], ['settings', '⚙️', 'settings'], ['log', '🧾', 'log']
  ]
  $('#nav').innerHTML = items.map(([id, ico, key]) =>
    `<button class="nav-item ${id === S.tab ? 'active' : ''}" data-tab="${id}"><span class="ico">${ico}</span><span>${t(key)}</span></button>`
  ).join('')
  $('#nav').querySelectorAll('.nav-item').forEach(b => b.onclick = () => switchTab(b.dataset.tab))
  const meBox = $('#meBox')
  if (S.me) {
    meBox.classList.remove('hidden')
    meBox.innerHTML = `<span>${esc(S.me.firstName || '')} ${esc(S.me.lastName || '')}</span>`
  }
}

function switchTab(tab) {
  S.tab = tab
  renderNav()
  loadTabs()
}

function loadTabs() {
  const body = $('#tabBody')
  $('#pageTitle').textContent = t(S.tab)
  $('#topActions').innerHTML = ''
  body.innerHTML = ''
  switch (S.tab) {
    case 'tracks': renderTracks(); break
    case 'scan': renderScan(); break
    case 'playlists': renderPlaylists(); break
    case 'favorites': renderFavorites(); break
    case 'downloads': renderDownloads(); break
    case 'followed': renderFollowed(); break
    case 'channels': renderChannels(); break
    case 'chats': renderChats(); break
    case 'settings': renderSettings(); break
    case 'log': renderLog(); break
  }
}

// ---------------- track row ----------------
function trackRowHtml(track, index, listKey) {
  const isPlaying = S.current && S.current.id === track.id
  return `<div class="track-row ${isPlaying ? 'playing' : ''}" data-idx="${index}">
    <div class="track-cover" style="background-image:url('${coverOf(track)}')">${track.albumCoverFileId || track.chatPhotoFileId ? '' : '♪'}</div>
    <div class="track-meta">
      <div class="track-title">${esc(track.title)}</div>
      <div class="track-sub">${esc(track.performer || track.chatTitle || '')} · ${fmt(track.duration)}</div>
    </div>
    <div class="track-actions">
      <button class="ic" data-act="fav" title="♥">${S.favorites.some(f => f.id === track.id) ? '❤️' : '🤍'}</button>
      <button class="ic" data-act="pl" title="${t('addToPlaylist')}">＋</button>
      <button class="ic" data-act="dl" title="${t('download')}">⬇</button>
      <button class="ic" data-act="queue" title="${t('addToQueue')}">≡</button>
    </div>
  </div>`
}

function wireTrackRows(container, listKey) {
  container.querySelectorAll('.track-row').forEach(row => {
    const idx = parseInt(row.dataset.idx, 10)
    row.onclick = e => {
      if (e.target.closest('button')) return
      playList(getList(listKey), idx)
    }
    row.querySelectorAll('button').forEach(b => b.onclick = e => {
      e.stopPropagation()
      const list = getList(listKey)
      const track = list[idx]
      const act = b.dataset.act
      if (act === 'fav') api.invoke('fav.toggle', { track })
      else if (act === 'pl') openAddToPlaylist(track)
      else if (act === 'dl') api.invoke('dl.add', { track })
      else if (act === 'queue') { S.queue.push(track); toast('✓') }
    })
  })
}

function getList(key) {
  if (key === 'favorites') return S.favorites
  if (key === 'downloads') return S.downloads
  if (key === 'scanResults') return S.scanResults
  if (key === 'playlist') return S.playlistTracks || []
  if (key === 'search') return S.searchResults || []
  return S.tracks
}

// ---------------- tabs ----------------
function renderTracks() {
  const body = $('#tabBody')
  if (!S.tracks.length) {
    body.innerHTML = `<div class="empty">${t('emptyTracks')}</div>`
    return
  }
  $('#topActions').innerHTML = `<button class="btn ghost small" id="playAllBtn">${t('playAll')}</button>`
  $('#playAllBtn').onclick = () => playList(S.tracks, 0)
  body.innerHTML = `<input id="searchInput" placeholder="${t('search')}" />` +
    S.tracks.map((tr, i) => trackRowHtml(tr, i, 'tracks')).join('')
  wireTrackRows(body, 'tracks')
  const si = $('#searchInput')
  si.oninput = () => {
    const q = si.value.trim().toLowerCase()
    if (!q) { renderTracks(); return }
    S.searchResults = S.tracks.filter(tr => (tr.title + ' ' + (tr.performer || '')).toLowerCase().includes(q))
    body.querySelectorAll('.track-row').forEach(r => r.remove())
    const holder = document.createElement('div')
    holder.innerHTML = S.searchResults.map((tr, i) => trackRowHtml(tr, i, 'search')).join('')
    body.appendChild(holder)
    wireTrackRows(holder, 'search')
  }
}

function renderFavorites() {
  const body = $('#tabBody')
  if (!S.favorites.length) { body.innerHTML = `<div class="empty">${t('empty')}</div>`; return }
  body.innerHTML = S.favorites.map((tr, i) => trackRowHtml(tr, i, 'favorites')).join('')
  wireTrackRows(body, 'favorites')
}

function renderDownloads() {
  const body = $('#tabBody')
  if (!S.downloads.length) { body.innerHTML = `<div class="empty">${t('empty')}</div>`; return }
  body.innerHTML = S.downloads.map((tr, i) => {
    const info = S.dlInfo[tr.fileId]
    const done = info && info.completed
    const prog = info && info.size ? Math.round(100 * info.downloaded / info.size) : null
    return `<div class="track-row">
      <div class="track-cover" style="background-image:url('${coverOf(tr)}')">♪</div>
      <div class="track-meta">
        <div class="track-title">${esc(tr.title)}</div>
        <div class="track-sub">${done ? '✓' : (prog == null ? t('download') : prog + '%')}</div>
      </div>
      <div class="track-actions">
        <button class="ic" data-act="play">▶</button>
        <button class="ic" data-act="remove">🗑</button>
      </div>
    </div>`
  }).join('')
  body.querySelectorAll('.track-row').forEach((row, i) => {
    const tr = S.downloads[i]
    row.querySelector('[data-act=play]').onclick = () => playList(S.downloads, i)
    row.querySelector('[data-act=remove]').onclick = () => api.invoke('dl.remove', { trackId: tr.id, fileId: tr.fileId })
  })
}

// ---------------- download status helpers ----------------
function isTrackDownloaded(track) {
  if (!track) return false
  const info = S.dlInfo[track.fileId]
  return !!(info && info.completed)
}
function nowDlInfo() {
  if (!S.current) return null
  return S.dlInfo[S.current.fileId] || null
}
function activeDlList() {
  const out = []
  for (const key of Object.keys(S.dlInfo)) {
    const f = S.dlInfo[key]
    if (!f || f.completed) continue
    if (f.downloading || (f.downloaded || 0) > 0) {
      out.push({ id: f.id, prog: f.size ? Math.min(100, Math.round(100 * (f.downloaded || 0) / f.size)) : 0 })
    }
  }
  return out
}
function downloadCurrent() {
  const track = S.current
  if (!track) return
  if (isTrackDownloaded(track)) { toast(t('downloaded')); return }
  api.invoke('dl.add', { track })
  renderNowDl()
}
function renderNowDl() {
  const btn = $('#npDl'), status = $('#npDlStatus'), pbDl = $('#pbDl')
  if (!S.current) return
  const info = nowDlInfo()
  const done = isTrackDownloaded(S.current)
  let label, busy = false
  if (done) label = t('downloaded')
  else if (info && (info.downloading || (info.downloaded || 0) > 0)) {
    const prog = info.size ? Math.min(100, Math.round(100 * info.downloaded / info.size)) : 0
    label = t('downloading') + ' ' + prog + '%'
    busy = true
  } else label = t('download')
  if (btn) { btn.textContent = done ? '✓' : '⬇'; btn.classList.toggle('busy', busy) }
  if (status) status.textContent = label
  if (pbDl) pbDl.textContent = done ? '✓' : '⬇'
}
function renderSettingsDl() {
  const box = $('#dlActiveBox')
  if (!box) return
  const actives = activeDlList()
  box.innerHTML = ''
  if (!actives.length) return
  const rows = actives.map(a => {
    const track = S.downloads.find(t => t.fileId === a.id) || S.tracks.find(t => t.fileId === a.id)
    const title = track ? track.title : ('#' + a.id)
    return `<div class="track-row">
      <div class="track-meta">
        <div class="track-title">${esc(title)}</div>
        <div class="track-sub">${a.prog}%</div>
        <div class="progress-bar" style="margin-top:4px"><div class="progress-fill" style="width:${a.prog}%"></div></div>
      </div>
      <div class="track-actions">
        <button class="ic" data-cancel="${a.id}" title="${t('cancel')}">✕</button>
      </div>
    </div>`
  }).join('')
  box.innerHTML = `<p class="section-label">${t('activeDownloads')}</p>` + rows
  box.querySelectorAll('[data-cancel]').forEach(b => b.onclick = () => {
    api.invoke('file.cancel', { fileId: parseInt(b.dataset.cancel, 10) })
    toast('✕ ' + t('cancel'))
  })
}

let scanBusy = false
function renderScan() {
  const body = $('#tabBody')
  const chats = S.channels.length ? S.channels : S.chats
  body.innerHTML = `
    <p class="section-label">${t('scanHint')}</p>
    <button id="scanAllBtn" class="btn primary" style="margin-bottom:12px">${t('scanAllChats')}</button>
    <select id="scanChat" class="set-select" style="width:100%"></select>
    <p class="section-label">${t('depth')}</p>
    <div class="scan-controls">
      ${[50, 100, 300, 'all'].map(d => `<button class="chip ${d === 100 ? 'active' : ''}" data-depth="${d}">${d === 'all' ? '∞' : d}</button>`).join('')}
      <button id="scanStart" class="btn primary" style="width:auto;padding:8px 16px">${t('start')}</button>
      <button id="scanCancel" class="btn ghost" style="width:auto;padding:8px 16px">${t('cancel')}</button>
    </div>
    <div id="scanProgress" class="hidden">
      <div class="progress-bar"><div class="progress-fill" id="scanFill"></div></div>
      <div class="progress-label" id="scanLabel"></div>
    </div>
    <div id="scanResults"></div>`
  const sel = $('#scanChat')
  chats.forEach(c => { const o = document.createElement('option'); o.value = c.id; o.textContent = c.title; sel.appendChild(o) })
  let depth = 100
  body.querySelectorAll('.chip').forEach(ch => ch.onclick = () => {
    body.querySelectorAll('.chip').forEach(x => x.classList.remove('active'))
    ch.classList.add('active')
    depth = ch.dataset.depth
  })
  // ✅ v6.0.0: اسکن همهٔ چت‌ها یک‌جا — مثل اندروید (دیگر لازم نیست یکی‌یکی انتخاب کنی)
  $('#scanAllBtn').onclick = async () => {
    if (scanBusy) { api.invoke('scan.cancel'); return }
    scanBusy = true
    $('#scanAllBtn').textContent = t('cancel')
    $('#scanProgress').classList.remove('hidden')
    $('#scanFill').style.width = '0%'
    try {
      const depthPerChat = depth === 'all' ? 300 : parseInt(depth, 10) || 300
      const res = await api.invoke('scan.all', { depth: depthPerChat })
      S.scanResults = res.tracks || []
    } finally {
      scanBusy = false
      $('#scanAllBtn').textContent = t('scanAllChats')
      renderScanResults()
    }
  }
  $('#scanStart').onclick = async () => {
    if (scanBusy) return
    scanBusy = true
    const chatId = parseInt(sel.value, 10)
    $('#scanProgress').classList.remove('hidden')
    $('#scanFill').style.width = '0%'
    try {
      const res = await api.invoke('scan.start', { chatId, mode: depth })
      S.scanResults = res.tracks || []
    } finally {
      scanBusy = false
      renderScanResults()
    }
  }
  $('#scanCancel').onclick = () => api.invoke('scan.cancel')
  if (S.scanResults.length) renderScanResults()
}

function renderScanProgress(p) {
  const fill = $('#scanFill'), label = $('#scanLabel')
  if (!fill) return
  if (p.allChats) {
    const pct = p.chatCount ? Math.round(100 * (p.chatIndex || 0) / p.chatCount) : 0
    fill.style.width = Math.min(100, pct) + '%'
    label.textContent = `📂 ${p.chatIndex || 0}/${p.chatCount || '?'} — «${p.chatTitle || ''}» — ${p.found || 0} ${t('found')}`
  } else {
    if (p.total) fill.style.width = Math.min(100, Math.round(100 * p.processed / p.total)) + '%'
    label.textContent = `${t('scanned')} ${p.processed}${p.total ? '/' + p.total : ''} — ${p.found} ${t('found')}`
  }
  if (p.done) {
    setTimeout(() => {
      const box = $('#scanProgress'); if (box) box.classList.add('hidden')
    }, 2000)
  }
}

function renderScanResults() {
  const box = $('#scanResults')
  if (!box) return
  if (!S.scanResults.length) { box.innerHTML = `<div class="empty">${t('empty')}</div>`; return }
  box.innerHTML = `<p class="section-label">${S.scanResults.length} ${t('track')}</p>
    <button id="addAllBtn" class="btn primary" style="width:auto;padding:8px 16px;margin-bottom:8px">${t('addAll')}</button>` +
    S.scanResults.map((tr, i) => trackRowHtml(tr, i, 'scanResults')).join('')
  $('#addAllBtn').onclick = async () => {
    await api.invoke('lib.add', { tracks: S.scanResults })
    S.tracks = await api.invoke('lib.list')
    toast('✓ ' + S.scanResults.length)
  }
  wireTrackRows(box, 'scanResults')
}

function renderPlaylists() {
  const body = $('#tabBody')
  const inPlaylist = S.tab === 'playlist' && S.playlistTracks
  $('#topActions').innerHTML = `<button class="btn ghost small" id="newPlBtn">＋ ${t('newPlaylist')}</button>`
  $('#newPlBtn').onclick = openNewPlaylist
  if (inPlaylist) {
    const pl = S.playlists.find(p => p.id === S.playlistId)
    body.innerHTML = `<h3 style="margin:6px 0">${esc(pl ? pl.name : '')}</h3>` +
      (S.playlistTracks || []).map((tr, i) => trackRowHtml(tr, i, 'playlist')).join('') || `<div class="empty">${t('empty')}</div>`
    wireTrackRows(body, 'playlist')
    return
  }
  if (!S.playlists.length) { body.innerHTML = `<div class="empty">${t('empty')}</div>`; return }
  body.innerHTML = S.playlists.map(pl => `
    <div class="chat-row">
      <div class="chat-avatar">📁</div>
      <div class="track-meta">
        <div class="track-title">${esc(pl.name)}</div>
        <div class="track-sub">${pl.tracks.length} ${t('track')}</div>
      </div>
      <div class="track-actions" style="opacity:1">
        <button class="ic" data-act="open">▶</button>
        <button class="ic" data-act="del">🗑</button>
      </div>
    </div>`).join('')
  body.querySelectorAll('.chat-row').forEach((row, i) => {
    const pl = S.playlists[i]
    row.querySelector('[data-act=open]').onclick = () => { S.tab = 'playlist'; S.playlistId = pl.id; S.playlistTracks = pl.tracks; renderNav(); loadTabs() }
    row.querySelector('[data-act=del]').onclick = () => api.invoke('pl.delete', { id: pl.id })
  })
}

function renderChannels() {
  const body = $('#tabBody')
  if (!S.channels.length) { body.innerHTML = `<div class="empty">${t('empty')}</div>`; return }
  body.innerHTML = S.channels.map(c => `
    <div class="chat-row">
      <div class="chat-avatar" style="background-image:url('${artUrl(c.photoFileId)}')">${c.photoFileId ? '' : esc((c.title || '?')[0])}</div>
      <div class="track-meta">
        <div class="track-title">${esc(c.title)}</div>
        <div class="track-sub">${c.kind}</div>
      </div>
      <div class="track-actions" style="opacity:1">
        <button class="ic" data-act="follow" title="${t('follow')}">${(S.followed||[]).some(f => f.chatId === c.id) ? '🔔' : '🔕'}</button>
        <button class="ic" data-act="scan" title="${t('fullScan')}">🔍</button>
        <button class="ic" data-act="dl" title="${t('downloadAll')}">⬇</button>
      </div>
    </div>`).join('')
  body.querySelectorAll('.chat-row').forEach((row, i) => {
    const c = S.channels[i]
    const folBtn = row.querySelector('[data-act=follow]')
    if (folBtn) folBtn.onclick = async () => {
      if ((S.followed||[]).some(f => f.chatId === c.id)) {
        await api.invoke('follow.remove', { chatId: c.id })
        toast('✓ ' + t('unfollow'))
      } else {
        toast('🔔 ' + c.title)
        await api.invoke('follow.add', { chatId: c.id, title: c.title })
        toast('✓ ' + t('follow'))
      }
      S.followed = await api.invoke('follow.list')
      renderChannels()
    }
    row.querySelector('[data-act=scan]').onclick = async () => {
      toast('🔍 ' + c.title)
      const res = await api.invoke('scan.start', { chatId: c.id, mode: 'all' })
      await api.invoke('lib.add', { tracks: res.tracks })
      S.tracks = await api.invoke('lib.list')
      toast('✓ ' + (res.tracks || []).length)
    }
    row.querySelector('[data-act=dl]').onclick = async () => {
      toast('⬇ ' + c.title)
      const res = await api.invoke('scan.start', { chatId: c.id, mode: 'all' })
      for (const tr of (res.tracks || [])) await api.invoke('dl.add', { track: tr })
      toast('✓')
    }
  })
}

// ✅ v6.0.0: چت‌ها دقیقاً مثل فولدرهای خود تلگرام (همه / آرشیو / فولدرهای سفارشی)
let activeFolder = -1
function renderChats() {
  const body = $('#tabBody')
  body.innerHTML = `
    <div id="folderBar" class="folder-bar"></div>
    <input id="chatSearch" placeholder="${t('search')}" />
    <div id="chatList"></div>`
  const list = $('#chatList')
  const followedIds = new Set((S.followed || []).map(f => f.chatId))

  const draw = chats => {
    list.innerHTML = chats.map(c => `
      <div class="chat-row">
        <div class="chat-avatar" style="background-image:url('${artUrl(c.photoFileId)}')">${c.photoFileId ? '' : esc((c.title || '?')[0])}</div>
        <div class="track-meta">
          <div class="track-title">${esc(c.title)}</div>
          <div class="track-sub">${c.kind}${c.archived ? ' · 📦' : ''}</div>
        </div>
        <div class="track-actions" style="opacity:1">
          <button class="ic" data-act="follow" title="${followedIds.has(c.id) ? t('unfollow') : t('follow')}">${followedIds.has(c.id) ? '🔔' : '🔕'}</button>
          <button class="ic" data-act="scan" title="${t('fullScan')}">🔍</button>
        </div>
      </div>`).join('')
    list.querySelectorAll('.chat-row').forEach((row, i) => {
      row.querySelector('[data-act=scan]').onclick = async () => {
        toast('🔍 ' + chats[i].title)
        const res = await api.invoke('scan.start', { chatId: chats[i].id, mode: 'all' })
        S.scanResults = res.tracks || []
        S.tab = 'scan'; renderNav(); loadTabs()
      }
      const fb = row.querySelector('[data-act=follow]')
      fb.onclick = async () => {
        if (followedIds.has(chats[i].id)) {
          await api.invoke('follow.remove', { chatId: chats[i].id })
          toast('✓ ' + t('unfollow'))
        } else {
          toast('🔔 ' + chats[i].title)
          await api.invoke('follow.add', { chatId: chats[i].id, title: chats[i].title })
          toast('✓ ' + t('follow'))
        }
        S.followed = await api.invoke('follow.list')
        renderChats()
      }
    })
  }

  const renderFolderBar = folders => {
    const bar = $('#folderBar')
    const tabs = folders.map((f, i) => {
      let label
      if (f.name === 'all') label = t('allChats')
      else if (f.name === 'archive') label = t('archive')
      else label = f.title || ('📁 ' + i)
      return `<button class="chip ${activeFolder === (f.id !== undefined && f.id >= 0 ? f.id : (f.name === 'archive' ? -2 : -1)) ? 'active' : ''}" data-fi="${i}">${esc(label)} (${f.chats.length})</button>`
    }).join('')
    bar.innerHTML = tabs
    bar.querySelectorAll('[data-fi]').forEach(b => b.onclick = () => {
      const f = folders[parseInt(b.dataset.fi, 10)]
      activeFolder = f.name === 'archive' ? -2 : (f.name === 'custom' ? f.id : -1)
      draw(f.chats)
      bar.querySelectorAll('.chip').forEach(x => x.classList.remove('active'))
      b.classList.add('active')
    })
  }

  api.invoke('chats.folders').then(folders => {
    S.folders = folders
    renderFolderBar(folders)
    let cur = folders.find(f => (f.name === 'custom' && f.id === activeFolder))
    if (!cur) cur = folders.find(f => f.name === (activeFolder === -2 ? 'archive' : 'all')) || folders[0]
    S.chats = (folders.find(f => f.name === 'all') || { chats: [] }).chats
    draw(cur ? cur.chats : [])
  }).catch(() => {
    api.invoke('chats.list').then(chats => { S.chats = chats; draw(chats) })
  })

  $('#chatSearch').oninput = async () => {
    const q = $('#chatSearch').value.trim()
    if (!q) {
      const cur = (S.folders || []).find(f => f.name === (activeFolder === -2 ? 'archive' : 'all'))
      draw(cur ? cur.chats : S.chats)
      return
    }
    draw(await api.invoke('chats.search', { query: q }))
  }
}

// ✅ v6.0.0: صفحهٔ دنبال‌شده‌ها — مثل اندروید
function renderFollowed() {
  const body = $('#tabBody')
  $('#topActions').innerHTML = `<button class="btn ghost small" id="followCheckBtn">${t('followedCheck')}</button>`
  $('#followCheckBtn').onclick = async () => {
    toast('⏳')
    const r = await api.invoke('follow.check')
    toast(r && r.newTracks ? '🔔 +' + r.newTracks : '✓')
  }
  const list = S.followed || []
  if (!list.length) { body.innerHTML = `<div class="empty">${t('followedNone')}</div>`; return }
  body.innerHTML = `<p class="section-label">${list.length} ${t('followedCount')}</p>` + list.map((f, i) => `
    <div class="chat-row">
      <div class="chat-avatar">${esc((f.title || '?')[0])}</div>
      <div class="track-meta">
        <div class="track-title">${esc(f.title || ('#' + f.chatId))}</div>
        <div class="track-sub">${(f.knownIds || []).length} ${t('track')}</div>
      </div>
      <div class="track-actions" style="opacity:1">
        <button class="ic" data-act="play" title="▶">▶</button>
        <button class="ic" data-act="scan" title="${t('fullScan')}">🔍</button>
        <button class="ic" data-act="unfollow" title="${t('unfollow')}">🔕</button>
      </div>
    </div>`).join('')
  body.querySelectorAll('.chat-row').forEach((row, i) => {
    const f = list[i]
    row.querySelector('[data-act=play]').onclick = async () => {
      const tracks = (await api.invoke('lib.list')).filter(tr => tr.chatId === f.chatId)
      if (!tracks.length) { toast(t('empty')); return }
      playList(tracks, 0)
    }
    row.querySelector('[data-act=scan]').onclick = async () => {
      toast('🔍 ' + f.title)
      const res = await api.invoke('scan.start', { chatId: f.chatId, mode: 'all' })
      await api.invoke('lib.add', { tracks: res.tracks || [] })
      S.tracks = await api.invoke('lib.list')
      toast('✓ ' + (res.tracks || []).length)
    }
    row.querySelector('[data-act=unfollow]').onclick = async () => {
      await api.invoke('follow.remove', { chatId: f.chatId })
      S.followed = await api.invoke('follow.list')
      renderFollowed()
    }
  })
}

function renderSettings() {
  const body = $('#tabBody')
  const s = S.settings
  body.innerHTML = `
    <div class="set-row"><div><label>${t('version')}</label></div><div id="verValue" class="hint">—</div></div>
    <div class="set-row"><div><label>${t('theme')}</label></div>
      <select id="setTheme" class="set-select">
        <option value="dark" ${s.theme === 'dark' ? 'selected' : ''}>${t('dark')}</option>
        <option value="light" ${s.theme === 'light' ? 'selected' : ''}>${t('light')}</option>
      </select></div>
    <div class="set-row"><div><label>${t('accent')}</label></div>
      <div class="swatches">${['purple','blue','green','red','orange','pink','teal'].map(c =>
        `<div class="swatch ${s.accent === c ? 'active' : ''}" data-accent="${c}" style="background:${accentHex(c)}"></div>`).join('')}</div></div>
    <div class="set-row"><div><label>${t('lang')}</label></div>
      <select id="setLang" class="set-select">
        <option value="fa" ${s.lang === 'fa' ? 'selected' : ''}>فارسی</option>
        <option value="en" ${s.lang === 'en' ? 'selected' : ''}>English</option>
      </select></div>
    <p class="section-label">${t('proxy')}</p>
    <input id="pxServer" placeholder="${t('proxyServer')}" dir="ltr" value="${esc(s.proxy ? s.proxy.server : '')}" />
    <input id="pxPort" placeholder="${t('proxyPort')}" dir="ltr" value="${esc(s.proxy ? s.proxy.port : '')}" style="margin-top:8px" />
    <select id="pxType" class="set-select" style="margin-top:8px;width:100%">
      <option value="socks5" ${s.proxy && s.proxy.type !== 'http' ? 'selected' : ''}>SOCKS5</option>
      <option value="http" ${s.proxy && s.proxy.type === 'http' ? 'selected' : ''}>HTTP</option>
    </select>
    <button id="pxSave" class="btn primary" style="margin-top:10px">${t('proxySave')}</button>
    <div id="dlActiveBox"></div>
    <p class="section-label">${t('apiKeys')}</p>
    <input id="apiIdInput" placeholder="${t('apiId')}" dir="ltr" value="${esc(s.apiId || '')}" />
    <input id="apiHashInput" placeholder="${t('apiHash')}" dir="ltr" value="${esc(s.apiHash || '')}" style="margin-top:8px" />
    <p class="hint">${t('apiHint')}</p>
    <button id="apiSave" class="btn primary" style="margin-top:10px">${t('save')}</button>`

  $('#setTheme').onchange = async e => { S.settings.theme = e.target.value; await api.invoke('settings.set', { patch: { theme: e.target.value } }); applyTheme() }
  $('#setLang').onchange = async e => { S.settings.lang = e.target.value; await api.invoke('settings.set', { patch: { lang: e.target.value } }); applyTheme(); renderNav(); loadTabs() }
  body.querySelectorAll('.swatch').forEach(sw => sw.onclick = async () => {
    S.settings.accent = sw.dataset.accent
    await api.invoke('settings.set', { patch: { accent: sw.dataset.accent } })
    applyTheme(); renderSettings()
  })
  $('#pxSave').onclick = async () => {
    const proxy = { type: $('#pxType').value, server: $('#pxServer').value.trim(), port: $('#pxPort').value.trim() }
    try { await api.invoke('proxy.set', { proxy }); toast('✓') } catch (e) { toast('✗ ' + e.message) }
  }
  $('#apiSave').onclick = async () => {
    await api.invoke('settings.set', { patch: { apiId: $('#apiIdInput').value.trim(), apiHash: $('#apiHashInput').value.trim() } })
    toast('✓ — ' + t('apiHint'))
  }
  renderSettingsDl()
  api.invoke('app.version').then(v => { const el = $('#verValue'); if (el && v) el.textContent = 'v' + v }).catch(() => {})
}

function accentHex(c) {
  return { purple: '#a855f7', blue: '#3b82f6', green: '#22c55e', red: '#ef4444', orange: '#f97316', pink: '#ec4899', teal: '#14b8a6' }[c] || '#a855f7'
}

function renderLog() {
  $('#tabBody').innerHTML = `<div class="log-box" id="logBox"></div>`
  const box = $('#logBox')
  if (S.pendingLogs && S.pendingLogs.length) {
    S.pendingLogs.forEach(appendLog)
  } else {
    api.invoke('log.list').then(lines => { const b = $('#logBox'); if (b) { b.innerHTML = ''; lines.forEach(appendLog) } })
  }
}

function appendLog(entry) {
  const box = $('#logBox')
  if (!box) return
  const line = document.createElement('div')
  line.textContent = `[${(entry.ts || '').slice(11, 19)}] ${entry.line}`
  box.appendChild(line)
  box.scrollTop = box.scrollHeight
}

// ---------------- modals ----------------
function modal(html) {
  const ov = document.createElement('div')
  ov.className = 'np'
  ov.style.cssText = 'z-index:100;background:rgba(0,0,0,.55)'
  const card = document.createElement('div')
  card.style.cssText = 'background:var(--bg2);border:1px solid var(--line);border-radius:16px;padding:20px;min-width:320px;display:flex;flex-direction:column;gap:10px'
  card.innerHTML = html
  ov.appendChild(card)
  document.body.appendChild(ov)
  ov.onclick = e => { if (e.target === ov) ov.remove() }
  return ov
}

function openNewPlaylist() {
  const ov = modal(`<h3>${t('newPlaylist')}</h3><input id="plName" placeholder="${t('name')}"/><button id="plCreate" class="btn primary">${t('create')}</button>`)
  ov.querySelector('#plCreate').onclick = async () => {
    const name = ov.querySelector('#plName').value.trim() || t('newPlaylist')
    await api.invoke('pl.create', { name })
    ov.remove()
    renderPlaylists()
  }
}

function openAddToPlaylist(track) {
  const ov = modal(`<h3>${t('addToPlaylist')}</h3><div id="plList">${S.playlists.map(p =>
    `<button class="btn ghost" data-id="${p.id}" style="text-align:start">${esc(p.name)} (${p.tracks.length})</button>`).join('') || `<div class="empty">${t('empty')}</div>`}</div><button id="plNew2" class="btn ghost">＋ ${t('newPlaylist')}</button>`)
  ov.querySelectorAll('[data-id]').forEach(b => b.onclick = async () => {
    await api.invoke('pl.add', { plId: parseInt(b.dataset.id, 10), track })
    ov.remove(); toast('✓')
  })
  ov.querySelector('#plNew2').onclick = () => { ov.remove(); openNewPlaylist() }
}

// ---------------- player ----------------
function setMeta(track) {
  S.current = track
  localFallback = false
  const src = `moeshak-stream://${track.fileId}?size=${track.size || 0}&mime=${encodeURIComponent(track.mimeType || 'audio/mpeg')}`
  audio.src = src
  $('#pbTitle').textContent = track.title
  $('#pbArtist').textContent = track.performer || track.chatTitle || ''
  $('#npTitle').textContent = track.title
  $('#npArtist').textContent = track.performer || track.chatTitle || ''
  const cov = coverOf(track)
  $('#pbCover').style.backgroundImage = `url('${cov}')`
  $('#npCover').style.backgroundImage = `url('${cov}')`
  if ('mediaSession' in navigator) {
    const md = { title: track.title, artist: track.performer || track.chatTitle || 'Moeshak', album: track.chatTitle || 'Telegram' }
    if (cov) md.artwork = [{ src: cov, sizes: '512x512' }]
    navigator.mediaSession.metadata = new MediaMetadata(md)
  }
  updateNowPlaying()
  renderNowDl()
  vizReset()
}

function playList(list, index) {
  if (!list || !list.length) return
  S.queue = list.slice()
  S.qIndex = index
  setMeta(list[index])
  audio.play().catch(() => {})
}

function playIndex(i) {
  if (!S.queue.length) return
  S.qIndex = (i + S.queue.length) % S.queue.length
  setMeta(S.queue[S.qIndex])
  audio.play().catch(() => {})
}
function next() { if (S.shuffle) playIndex(Math.floor(Math.random() * S.queue.length)); else playIndex(S.qIndex + 1) }
function prev() {
  if (audio.currentTime > 3) { audio.currentTime = 0; return }
  playIndex(S.qIndex - 1)
}

audio.addEventListener('ended', () => {
  if (S.repeat === 'one') { audio.currentTime = 0; audio.play(); return }
  next()
})
audio.addEventListener('error', () => {
  if (S.current && !localFallback) {
    localFallback = true
    const tr = S.current
    audio.src = `moeshak-local://${tr.fileId}?size=${tr.size || 0}&mime=${encodeURIComponent(tr.mimeType || 'audio/mpeg')}`
    api.invoke('file.download', { fileId: tr.fileId }).catch(() => {})
    audio.play().catch(() => {})
  }
})
audio.addEventListener('timeupdate', () => {
  const d = audio.duration || 0
  const p = d ? Math.round(1000 * audio.currentTime / d) : 0
  $('#pbSeek').value = p; $('#npSeek').value = p
  $('#pbCur').textContent = fmt(audio.currentTime); $('#npCur').textContent = fmt(audio.currentTime)
  $('#pbDur').textContent = fmt(d); $('#npDur').textContent = fmt(d)
})
audio.addEventListener('play', () => { S.playing = true; $('#pbPlay').textContent = '⏸'; $('#npPlay').textContent = '⏸'; startViz() })
audio.addEventListener('pause', () => { S.playing = false; $('#pbPlay').textContent = '▶'; $('#npPlay').textContent = '▶' })

function updateNowPlaying() {
  $('#playerBar').classList.remove('hidden')
  $('#pbPlay').textContent = S.playing ? '⏸' : '▶'
  $('#npPlay').textContent = S.playing ? '⏸' : '▶'
  $('#pbShuffle').classList.toggle('active', S.shuffle)
  $('#npShuffle').classList.toggle('active', S.shuffle)
  const rep = { off: '🔁', all: '🔁', one: '🔂' }[S.repeat]
  $('#pbRepeat').textContent = rep
  $('#npRepeat').textContent = rep
  $('#pbRepeat').classList.toggle('active', S.repeat !== 'off')
  $('#npRepeat').classList.toggle('active', S.repeat !== 'off')
  document.querySelectorAll('.track-row').forEach(r => r.classList.remove('playing'))
  if (S.current) {
    document.querySelectorAll('.track-row').forEach(r => {
      // highlight handled on render; simple approach: re-render current tab
    })
  }
}

// wire player controls
$('#pbPlay').onclick = () => { if (audio.paused) audio.play().catch(() => {}); else audio.pause() }
$('#npPlay').onclick = $('#pbPlay').onclick
$('#pbNext').onclick = next; $('#npNext').onclick = next
$('#pbPrev').onclick = prev; $('#npPrev').onclick = prev
$('#pbShuffle').onclick = () => { S.shuffle = !S.shuffle; updateNowPlaying() }
$('#npShuffle').onclick = $('#pbShuffle').onclick
$('#pbRepeat').onclick = () => { S.repeat = S.repeat === 'off' ? 'all' : S.repeat === 'all' ? 'one' : 'off'; updateNowPlaying() }
$('#npRepeat').onclick = $('#pbRepeat').onclick
function seek(v) { if (audio.duration) audio.currentTime = v / 1000 * audio.duration }
$('#pbSeek').oninput = e => seek(e.target.value)
$('#npSeek').oninput = e => seek(e.target.value)
$('#pbExpand').onclick = () => $('#np').classList.remove('hidden')
$('#npClose').onclick = () => $('#np').classList.add('hidden')
$('#npDl').onclick = downloadCurrent
$('#pbDl').onclick = downloadCurrent

// media keys
if ('mediaSession' in navigator) {
  navigator.mediaSession.setActionHandler('play', () => audio.play())
  navigator.mediaSession.setActionHandler('pause', () => audio.pause())
  navigator.mediaSession.setActionHandler('previoustrack', prev)
  navigator.mediaSession.setActionHandler('nexttrack', next)
  navigator.mediaSession.setActionHandler('seekbackward', () => { audio.currentTime = Math.max(0, audio.currentTime - 10) })
  navigator.mediaSession.setActionHandler('seekforward', () => { audio.currentTime = Math.min(audio.duration || 0, audio.currentTime + 10) })
}

// ---------------- visualizer ----------------
// نرم + همیشه‌نمایان: گذار نرم بین فریم‌ها + ریبایند خودکار موقع تعویض ترک.
// AudioContext/MediaElementSource فقط یک‌بار ساخته می‌شوند (قانون Chromium) و
// موقع تعویض ترک فقط بافر نرم‌سازی ریست می‌شود تا میله‌ها از صفر دوباره بالا بیایند.
let audioCtx = null, analyser = null, vizRaf = null
let vizValues = null
const VIZ_BARS = 64
const VIZ_SMOOTH = 0.35

function teardownViz() {
  if (vizRaf) { cancelAnimationFrame(vizRaf); vizRaf = null }
  vizValues = null
}

function vizReset() {
  teardownViz()
  if (S.playing) startViz()
}

function startViz() {
  const cv = $('#viz')
  if (!cv) return
  if (!audioCtx) {
    try {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)()
      analyser = audioCtx.createAnalyser()
      analyser.fftSize = 256
      analyser.smoothingTimeConstant = 0.75
      const src = audioCtx.createMediaElementSource(audio)
      src.connect(analyser); analyser.connect(audioCtx.destination)
    } catch (e) { return }
  }
  if (audioCtx.state === 'suspended') audioCtx.resume().catch(() => {})
  if (vizRaf) return
  const ctx = cv.getContext('2d')
  const draw = () => {
    vizRaf = requestAnimationFrame(draw)
    const w = cv.width = cv.clientWidth
    const h = cv.height = cv.clientHeight
    ctx.clearRect(0, 0, w, h)
    const freq = analyser.frequencyBinCount || 128
    const raw = new Uint8Array(freq)
    analyser.getByteFrequencyData(raw)
    if (!vizValues) {
      vizValues = new Float32Array(VIZ_BARS)
      vizValues.fill(0.02)
    }
    // اگر همهٔ بایت‌ها صفر بود (هنوز دیتایی نرسیده) پالس آرام نمایش بده
    let anySound = false
    for (let k = 0; k < raw.length; k++) { if (raw[k] > 0) { anySound = true; break } }
    const gap = 2
    const bw = (w - gap * (VIZ_BARS - 1)) / VIZ_BARS
    const accent = getComputedStyle(document.body).getPropertyValue('--accent').trim() || '#a855f7'
    const binPer = raw.length / VIZ_BARS
    for (let i = 0; i < VIZ_BARS; i++) {
      let target
      if (anySound) {
        target = raw[Math.floor(i * binPer)] / 255
      } else {
        target = 0.04 + 0.03 * Math.sin(Date.now() / 300 + i * 0.6)
      }
      vizValues[i] += (target - vizValues[i]) * VIZ_SMOOTH
      const v = vizValues[i]
      const bh = Math.max(3, v * h * 0.9)
      ctx.fillStyle = accent
      ctx.globalAlpha = 0.25 + v
      ctx.beginPath()
      const x = i * (bw + gap)
      ctx.roundRect(x, h - bh, bw, bh, 3)
      ctx.fill()
    }
    ctx.globalAlpha = 1
  }
  draw()
}

// ---------------- login wiring ----------------
$('#tabPhone').onclick = () => { $('#tabPhone').classList.add('active'); $('#tabQr').classList.remove('active'); renderLogin({ state: S.auth }) }
$('#tabQr').onclick = async () => {
  $('#tabQr').classList.add('active'); $('#tabPhone').classList.remove('active')
  $('#loginPhone').classList.add('hidden'); $('#loginCode').classList.add('hidden'); $('#loginPassword').classList.add('hidden'); $('#loginRegister').classList.add('hidden')
  $('#loginQr').classList.remove('hidden')
  const r = await api.invoke('auth.qr').catch(e => loginError(e))
  if (r && r.qr) $('#qrImg').src = r.qr
}
$('#qrRefresh').onclick = () => $('#tabQr').onclick()
$('#phoneNext').onclick = async () => {
  try { await api.invoke('auth.setPhone', { phone: $('#phoneInput').value.trim() }); $('#loginError').classList.add('hidden') }
  catch (e) { loginError(e) }
}
$('#codeNext').onclick = async () => {
  try { await api.invoke('auth.setCode', { code: $('#codeInput').value.trim() }); $('#loginError').classList.add('hidden') }
  catch (e) { loginError(e) }
}
$('#passwordNext').onclick = async () => {
  try { await api.invoke('auth.setPassword', { password: $('#passwordInput').value }) ; $('#loginError').classList.add('hidden') }
  catch (e) { loginError(e) }
}
$('#registerNext').onclick = async () => {
  try { await api.invoke('auth.setName', { firstName: $('#firstNameInput').value.trim(), lastName: $('#lastNameInput').value.trim() }); $('#loginError').classList.add('hidden') }
  catch (e) { loginError(e) }
}
$('#logoutBtn').onclick = () => {
  const ov = modal(`<h3>${t('logoutConfirm')}</h3><button id="loYes" class="btn primary">${t('yes')}</button><button id="loNo" class="btn ghost">${t('no')}</button>`)
  ov.querySelector('#loYes').onclick = async () => { await api.invoke('auth.logout'); ov.remove(); location.reload() }
  ov.querySelector('#loNo').onclick = () => ov.remove()
}

function toast(msg) {
  let el = $('#toast')
  if (!el) { el = document.createElement('div'); el.id = 'toast'; el.style.cssText = 'position:fixed;bottom:90px;left:50%;transform:translateX(-50%);background:var(--bg3);color:var(--text);padding:10px 18px;border-radius:12px;z-index:200;box-shadow:0 8px 30px rgba(0,0,0,.4)'; document.body.appendChild(el) }
  el.textContent = msg
  el.style.opacity = '1'
  clearTimeout(el._t)
  el._t = setTimeout(() => { el.style.opacity = '0' }, 2500)
}

// preload channels for scan/channels tabs
async function loadChannels() {
  const chats = await api.invoke('chats.list').catch(() => [])
  S.chats = chats
  S.channels = chats.filter(c => c.kind === 'channel' || c.kind === 'group')
  if (S.tab === 'channels') renderChannels()
  if (S.tab === 'scan') renderScan()
}

boot()
