'use strict'
const os = require('os')
const tdl = require('tdl')
const { getTdjson } = require('prebuilt-tdlib')

tdl.configure({ tdjson: getTdjson(), verbosityLevel: 1 })

const CHUNK = 512 * 1024 // readFilePart limit

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
        application_version: '5.10.9',
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

  // ---- chats ------------------------------------------------------------
  async getChats(limit = 200) {
    const res = await this.invoke({ _: 'getChats', chat_list: { _: 'chatListMain' }, limit })
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

  // ---- scan -------------------------------------------------------------
  async scan(chatId, mode, onProgress) {
    if (this._scanActive) throw new Error('scan_busy')
    this._scanActive = true
    this.scanCancel = false
    const limit = mode === 'all' ? Infinity : parseInt(mode, 10) || 100
    const perPage = 100
    const results = []
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
      while (!this.scanCancel && processed < limit && guard++ < 10000) {
        let hist
        try {
          hist = await this.invoke({
            _: 'getChatHistory', chat_id: chatId, from_message_id: from,
            offset: 0, limit: Math.min(perPage, limit - processed), only_local: false
          })
        } catch (e) { break }
        const msgs = hist.messages || []
        if (total === null) total = hist.total_count || 0
        if (!msgs.length) break
        for (const m of msgs) {
          if (this.scanCancel || processed >= limit) break
          processed++
          const t = extractTrack(m, chatId, chatTitle)
          if (t) { t.chatPhotoFileId = chatPhotoFileId; results.push(t) }
        }
        from = msgs[msgs.length - 1].id
        onProgress({ processed, total, found: results.length })
        if (msgs.length < perPage) break
      }
    } finally {
      this._scanActive = false
    }
    onProgress({ processed, total, found: results.length, done: true, canceled: this.scanCancel })
    return results
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
  return {
    id: c.id,
    title: c.title || '',
    kind,
    photoFileId,
    isChannel: t._ === 'chatTypeSupergroup'
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
  } else if (c._ === 'messageVoiceNote' && c.voice_note) {
    title = 'Voice Note'
    performer = ''
    duration = c.voice_note.duration || 0
    doc = c.voice_note.voice
    mime = (doc && doc.mime_type) || 'audio/ogg'
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

module.exports = { Tg, CHUNK }
