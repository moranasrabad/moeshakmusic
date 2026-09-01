'use strict'
const os = require('os')
const tdl = require('tdl')
const { getTdjson } = require('prebuilt-tdlib')

tdl.configure({ tdjson: getTdjson(), verbosityLevel: 1 })

const CHUNK = 512 * 1024 // readFilePart limit
const APP_VERSION = '6.0.3'

// Map TDLib authorization_state names to friendly UI keys.
const AUTH_MAP = {
  authorizationStateWaitTdlibParameters: 'waitTdlib',
  authorizationStateWaitPhoneNumber: 'waitPhoneNumber',
  authorizationStateWaitEmailAddress: 'waitEmailAddress',
  authorizationStateWaitEmailCode: 'waitEmailCode',
  authorizationStateWaitCode: 'waitCode',
  authorizationStateWaitPassword: 'waitPassword',
  authorizationStateWaitRegistration: 'waitRegistration',
  authorizationStateWaitOtherDeviceConfirmation: 'waitOtherDevice',
  authorizationStateReady: 'ready',
  authorizationStateLoggingOut: 'loggingOut',
  authorizationStateClosing: 'closing',
  authorizationStateClosed: 'closed'
}

class Tg {
  constructor(opts) {
    this.apiId = opts.apiId
    this.apiHash = opts.apiHash
    this.onEvent = opts.onEvent || (() => {})
    this.scanCancel = false
    this._scanActive = false
    this.lastQrLink = ''
    this._dlRequested = new Set()

    this.client = tdl.createClient({
      apiId: this.apiId,
      apiHash: this.apiHash,
      databaseDirectory: opts.databaseDirectory,
      filesDirectory: opts.filesDirectory,
      tdlibParameters: {
        use_message_database: true,
        use_secret_chats: false,
        system_language_code: 'fa',
        application_version: APP_VERSION,
        device_model: 'Moeshak Music Desktop',
        system_version: os.platform() + ' ' + os.release()
      }
    })

    this.client.on('update', u => this._onUpdate(u))
    this.client.on('error', e => this.onEvent('error', String((e && e.message) || e)))
  }

  invoke(method) {
    return this.client.invoke(method)
  }

  _onUpdate(u) {
    if (!u) return
    if (u._ === 'updateAuthorizationState') {
      const st = u.authorization_state || {}
      const key = AUTH_MAP[st._] || st._
      if (key === 'waitOtherDevice') this.lastQrLink = st.link || ''
      this.onEvent('auth', { state: key, hint: st.password_hint || '', link: st.link || '' })
    } else if (u._ === 'updateConnectionState') {
      this.onEvent('connection', { state: (u.state && u.state._) || '' })
    } else if (u._ === 'updateFile') {
      this.onEvent('file', fileToInfo(u.file))
    }
  }

  // ---- auth -------------------------------------------------------------
  setPhone(phone) {
    return this.invoke({
      _: 'setAuthenticationPhoneNumber',
      phone_number: phone,
      settings: { allow_flash_call: false, is_current_phone_number: true, allow_sms_retriever_api: false }
    })
  }
  setCode(code) { return this.invoke({ _: 'checkAuthenticationCode', code }) }
  setPassword(password) { return this.invoke({ _: 'checkAuthenticationPassword', password }) }
  registerUser(firstName, lastName) {
    return this.invoke({ _: 'registerUser', first_name: firstName, last_name: lastName || '' })
  }
  qrLogin() {
    this.lastQrLink = ''
    return this.invoke({ _: 'requestQrCodeAuthentication', other_user_ids: [] })
  }
  logout() { return this.invoke({ _: 'logOut' }).catch(() => {}) }

  getMe() { return this.invoke({ _: 'getMe' }) }

  // ---- chats & folders (مثل فولدرهای خود تلگرام) ------------------------
  /** لیست چت‌های یک لیست (main / archive) با ترتیب خود تلگرام */
  async getChatsForList(chatList, limit = 1000) {
    // مطمئن شو همهٔ چت‌ها از دیتابیس لود شده‌اند (چند صفحه)
    for (let i = 0; i < 30; i++) {
      try { await this.invoke({ _: 'loadChats', chat_list: chatList, limit: 100 }) } catch (e) { break }
    }
    const res = await this.invoke({ _: 'getChats', chat_list: chatList, limit })
    const ids = (res.chat_ids || []).slice(0, limit)
    const out = []
    for (const id of ids) {
      try {
        const c = await this.invoke({ _: 'getChat', chat_id: id })
        out.push(chatToInfo(c))
      } catch (e) {}
    }
    return out
  }

  async getChats(limit = 1000) {
    return this.getChatsForList({ _: 'chatListMain' }, limit)
  }

  /** همهٔ چت‌ها (اصلی + آرشیو) */
  async getAllChats() {
    const main = await this.getChatsForList({ _: 'chatListMain' })
    const arch = await this.getChatsForList({ _: 'chatListArchive' })
    const seen = new Set()
    const out = []
    for (const c of main.concat(arch)) {
      if (!seen.has(c.id)) { seen.add(c.id); out.push(c) }
    }
    return out
  }

  /**
   * فولدرهای تلگرام — دقیقاً مثل اپ تلگرام:
   * «همه» = چت‌های اصلی، «آرشیو» = آرشیو، بقیه = فولدرهای سفارشی کاربر.
   * برای فولدرهای سفارشی، کل ساختار هر فولدر را با getChatFolder می‌گیریم (شامل
   * included/excluded/pinned) تا دقیقاً همان چت‌های فولدر بیایند.
   */
  async getChatFolders() {
    const folders = [{ id: -1, name: 'all', title: '', chats: [] }, { id: -2, name: 'archive', title: '', chats: [] }]
    let mainChats = [], archChats = []
    try {
      mainChats = await this.getChatsForList({ _: 'chatListMain' })
      folders[0].chats = mainChats
    } catch (e) {}
    try {
      archChats = await this.getChatsForList({ _: 'chatListArchive' })
      folders[1].chats = archChats
    } catch (e) {}

    // فولدرهای سفارشی کاربر — لیست idها را بگیر، سپس ساختار هرکدام را جداگانه
    try {
      const info = await this.invoke({ _: 'getChatFolders' }) // ChatFolderInfo { chat_folder_ids[] }
      const ids = info.chat_folder_ids || info.ids || []
      for (const fid of ids) {
        try {
          const f = await this.invoke({ _: 'getChatFolder', chat_folder_id: fid })
          if (!f) continue
          // چت‌های این فولدر: included + pinned، منهای excluded
          const inIds = new Set([
            ...(f.included_chat_ids || []),
            ...(f.pinned_chat_ids || [])
          ])
          for (const ex of (f.excluded_chat_ids || [])) inIds.delete(ex)
          const chats = []
          for (const id of inIds) {
            try {
              const c = chatToInfo(await this.invoke({ _: 'getChat', chat_id: id }))
              // فولدرهایی که شامل آرشیو هستند: چت‌های آرشیوشده هم شامل شوند
              chats.push(c)
            } catch (e) {}
          }
          // اگر فولدر include_archive دارد، چت‌های آرشیو را هم (که exclude نشده‌اند) اضافه کن
          if (f.include_archived_chats) {
            for (const c of archChats) {
              if (!(f.excluded_chat_ids || []).includes(c.id)) chats.push(c)
            }
          }
          folders.push({
            id: fid,
            name: 'custom',
            title: f.name && f.name.text ? f.name.text : f.title || ('📁 ' + fid),
            icon: (f.icon && f.icon.name) || '',
            chats: dedupeChats(chats)
          })
        } catch (e) {}
      }
    } catch (e) {
      // نسخهٔ قدیمی TDLib یا اکانت محدود — بدون فولدر سفارشی ادامه بده
    }
    return folders
  }

  async searchChats(query, limit = 40) {
    try {
      const res = await this.invoke({ _: 'searchChats', query, limit })
      const out = []
      for (const id of (res.chat_ids || [])) {
        try { out.push(chatToInfo(await this.invoke({ _: 'getChat', chat_id: id }))) } catch (e) {}
      }
      return out
    } catch (e) { return [] }
  }

  async getChatInfo(chatId) {
    const c = await this.invoke({ _: 'getChat', chat_id: chatId })
    return chatToInfo(c)
  }

  /** آیا چت در آرشیو است؟ */
  async isArchived(chatId) {
    try {
      const c = await this.invoke({ _: 'getChat', chat_id: chatId })
      const list = c.chat_list
      return !!(list && list._ === 'chatListArchive')
    } catch (e) { return false }
  }

  // ---- scan -------------------------------------------------------------
  /** آیا اسکنی در جریان است؟ (جلوگیری از اسکن دوتایی هم‌زمان) */
  isScanning() { return !!this._scanActive }

  /** اسکن یک چت — کل تاریخچه (دیپ) یا با سقف پیام */
  async scan(chatId, mode, onProgress, opts) {
    // ✅ v6.0.2: فقط یک اسکنِ کاربر هم‌زمان. اسکن پس‌زمینه (چک دنبال‌شده‌ها) منتظر
    // می‌ماند تا اسکن کاربر تمام شود تا با هم تداخل نکنند.
    while (this._scanActive) {
      if (this.scanCancel && !(opts && opts.background)) break
      await new Promise(r => setTimeout(r, 300))
    }
    this._scanActive = true
    // اگر اسکن قبلی لغو شده بود، پرچم را از نو ریست کن (وگرنه دیپ‌اسکن بعد از لغو خالی می‌شد)
    this.scanCancel = false
    try {
      return await this._scanChat(chatId, mode, onProgress)
    } finally {
      this._scanActive = false
    }
  }

  async _scanChat(chatId, mode, onProgress) {
    const limit = mode === 'all' ? Infinity : parseInt(mode, 10) || 100
    const perPage = 100
    const results = []
    const seen = new Set()
    let from = 0, processed = 0, total = null
    let chatTitle = '', chatPhotoFileId = 0
    try {
      const ch = await this.invoke({ _: 'getChat', chat_id: chatId }).catch(() => null)
      if (ch) {
        chatTitle = ch.title || ''
        if (ch.photo && ch.photo.small) chatPhotoFileId = ch.photo.small.id
      }
    } catch (e) {}

    try {
      let guard = 0
      while (!this.scanCancel && processed < limit && guard++ < 100000) {
        let hist
        try {
          hist = await this.invoke({
            _: 'getChatHistory', chat_id: chatId, from_message_id: from,
            offset: 0, limit: perPage, only_local: false
          })
        } catch (e) { break }
        const msgs = hist.messages || []
        if (total === null) total = hist.total_count || 0
        if (!msgs.length) break
        for (const m of msgs) {
          if (this.scanCancel || processed >= limit) break
          processed++
          const t = extractTrack(m, chatId, chatTitle)
          if (t) {
            const key = t.chatId + ':' + t.messageId
            if (!seen.has(key)) {
              seen.add(key)
              t.chatPhotoFileId = chatPhotoFileId
              results.push(t)
            }
          }
        }
        from = msgs[msgs.length - 1].id
        onProgress && onProgress({ processed, total, found: results.length, chatTitle })
        if (msgs.length < perPage) break
      }

      // ✅ فالبک: اگر تاریخچهٔ مستقیم چیزی نداد (مثلاً چت شخصی که هنوز
      // تاریخچه‌اش از سرور همگام نشده)، با searchChatMessages فیلتر Audio امتحان کن.
      if (results.length === 0 && !this.scanCancel) {
        let fromMsg = 0
        for (let page = 0; page < 200; page++) {
          if (this.scanCancel) break
          let sres
          try {
            sres = await this.invoke({
              _: 'searchChatMessages',
              chat_id: chatId,
              query: '',
              sender_id: null,
              from_message_id: fromMsg,
              offset: 0,
              limit: 100,
              filter: { _: 'searchMessagesFilterAudio' }
            })
          } catch (e) { break }
          const smsgs = (sres && sres.messages) || []
          if (!smsgs.length) break
          for (const m of smsgs) {
            const t = extractTrack(m, chatId, chatTitle)
            if (t) {
              const key = t.chatId + ':' + t.messageId
              if (!seen.has(key)) { seen.add(key); t.chatPhotoFileId = chatPhotoFileId; results.push(t) }
            }
          }
          processed += smsgs.length
          if (total === null) total = (sres.total_count || 0)
          onProgress && onProgress({ processed, total, found: results.length, chatTitle })
          if (smsgs.length < 100) break
          fromMsg = smsgs[smsgs.length - 1].id
        }
      }
    } catch (e) {}
    onProgress && onProgress({ processed, total, found: results.length, chatTitle, done: true, canceled: this.scanCancel })
    return results
  }

  /**
   * ✅ v6.0.0: اسکن همهٔ چت‌ها — مثل اندروید، دیگر لازم نیست یکی‌یکی انتخاب کنی.
   * به‌ترتیب فولدرهای تلگرام؛ در هر چت تا depthPerChat پیام (یا ∞ برای حالت all).
   */
  async scanAll(onProgress, opts) {
    if (this._scanActive) return []
    this._scanActive = true
    this.scanCancel = false
    const depth = (opts && opts.depth) || 300
    const allResults = []
    let processedChats = 0
    let totalChats = 0
    try {
      const all = await this.getAllChats()
      totalChats = all.length
      const seen = new Set()
      for (const c of all) {
        if (this.scanCancel) break
        const tracks = await this._scanChat(c.id, depth, p => {
          onProgress && onProgress({
            processed: p.processed, total: p.total, found: allResults.length + p.found,
            chatTitle: c.title, chatIndex: processedChats, chatCount: totalChats, allChats: true
          })
        })
        for (const t of tracks) {
          const key = t.chatId + ':' + t.messageId
          if (!seen.has(key)) { seen.add(key); allResults.push(t) }
        }
        processedChats++
        onProgress && onProgress({ found: allResults.length, chatTitle: c.title, chatIndex: processedChats, chatCount: totalChats, allChats: true, phase: 'chat-done' })
      }
      onProgress && onProgress({ found: allResults.length, chatIndex: processedChats, chatCount: totalChats, allChats: true, done: true, canceled: this.scanCancel })
    } finally {
      this._scanActive = false
    }
    return allResults
  }

  cancelScan() { this.scanCancel = true }

  // ---- files ------------------------------------------------------------
  getFile(fileId) {
    return this.invoke({ _: 'getFile', file_id: fileId }).then(fileToInfo)
  }
  downloadFile(fileId, priority = 16) {
    return this.invoke({ _: 'downloadFile', file_id: fileId, priority, offset: 0, limit: 0, synchronous: false })
      .then(fileToInfo)
  }
  cancelDownloadFile(fileId) {
    return this.invoke({ _: 'cancelDownloadFile', file_id: fileId, only_if_pending: false }).catch(() => {})
  }
  async readFilePart(fileId, offset, count) {
    const r = await this.invoke({ _: 'readFilePart', file_id: fileId, offset, count: Math.min(count, CHUNK) })
    return r && r.data ? Buffer.from(r.data, 'base64') : Buffer.alloc(0)
  }
  deleteFile(fileId) { return this.invoke({ _: 'deleteFile', file_id: fileId }).catch(() => {}) }

  // ---- proxy ------------------------------------------------------------
  async setProxy(p) {
    if (!p || !p.server || !p.port) {
      return this.invoke({ _: 'setOption', name: 'proxy', value: { _: 'proxyTypeEmpty' } }).catch(() => {})
    }
    const type = p.type === 'http'
      ? { _: 'proxyTypeHttp', username: p.username || '', password: p.password || '', http_only: true }
      : { _: 'proxyTypeSocks5', username: p.username || '', password: p.password || '' }
    try {
      const added = await this.invoke({ _: 'addProxy', server: p.server, port: parseInt(p.port, 10), enable: true, type })
      await this.invoke({ _: 'setOption', name: 'proxy', value: added })
      return true
    } catch (e) {
      throw e
    }
  }

  async close() {
    try { await this.client.close() } catch (e) {}
  }
}

function fileToInfo(f) {
  if (!f) return null
  return {
    id: f.id,
    size: f.size || f.expected_size || 0,
    path: (f.local && f.local.path) || '',
    downloading: !!(f.local && f.local.is_downloading_active),
    completed: !!(f.local && f.local.is_downloading_completed),
    downloaded: (f.local && f.local.downloaded_size) || 0
  }
}

function chatToInfo(c) {
  const t = c.type || {}
  let kind = 'chat'
  if (t._ === 'chatTypeSupergroup') kind = c.is_channel ? 'channel' : 'group'
  else if (t._ === 'chatTypeBasicGroup') kind = 'group'
  else if (t._ === 'chatTypePrivate') kind = 'private'
  let photoFileId = 0
  if (c.photo && c.photo.small) photoFileId = c.photo.small.id
  const list = c.chat_list || {}
  return {
    id: c.id,
    title: c.title || '',
    kind,
    photoFileId,
    isChannel: t._ === 'chatTypeSupergroup' && !!c.is_channel,
    archived: list._ === 'chatListArchive'
  }
}

function extractTrack(m, chatId, chatTitle) {
  const c = m.content || {}
  let title, performer, duration, doc, coverFileId = 0, coverMini = '', mime = 'audio/mpeg'
  if (c._ === 'messageAudio' && c.audio) {
    const a = c.audio
    title = a.title || ''
    performer = a.performer || ''
    duration = a.duration || 0
    doc = a.audio
    mime = (doc && doc.mime_type) || 'audio/mpeg'
    if (a.album_cover_thumbnail && a.album_cover_thumbnail.file) coverFileId = a.album_cover_thumbnail.file.id
    if (a.album_cover_minithumbnail && a.album_cover_minithumbnail.data) {
      coverMini = 'data:image/jpeg;base64,' + a.album_cover_minithumbnail.data
    }
  } else if (c._ === 'messageVoiceNote') {
    // ✅ v6.0.2: ویس‌ها اصلاً جزو «آهنگ‌ها» نیایند
    return null
  } else if (c._ === 'messageDocument' && c.document && /audio/i.test(c.document.mime_type || '')) {
    title = (c.document.file_name || 'Audio').replace(/\.[^.]+$/, '')
    performer = ''
    duration = 0
    doc = c.document.document
    mime = c.document.mime_type || 'audio/mpeg'
    if (c.document.thumbnail && c.document.thumbnail.file) coverFileId = c.document.thumbnail.file.id
  }
  if (!doc || !doc.id) return null
  return {
    id: doc.id,
    fileId: doc.id,
    title: title || 'Unknown',
    performer: performer || '',
    duration: duration || 0,
    mimeType: mime,
    size: doc.size || doc.expected_size || 0,
    chatId, chatTitle: chatTitle || '',
    messageId: m.id, date: m.date || 0,
    albumCoverFileId: coverFileId,
    albumCoverMini: coverMini
  }
}

function dedupeChats(chats) {
  const seen = new Set()
  const out = []
  for (const c of chats) {
    if (!seen.has(c.id)) { seen.add(c.id); out.push(c) }
  }
  return out
}

module.exports = { Tg, CHUNK, APP_VERSION }
