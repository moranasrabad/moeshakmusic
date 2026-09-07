'use strict'
const fs = require('fs')
const path = require('path')

// Simple JSON persistence in the app's userData folder.
class Store {
  constructor(dir) {
    this.dir = dir
    try { fs.mkdirSync(dir, { recursive: true }) } catch (e) {}
  }

  _file(name) {
    return path.join(this.dir, name)
  }

  read(name, fallback) {
    try {
      const raw = fs.readFileSync(this._file(name), 'utf-8')
      return JSON.parse(raw)
    } catch (e) {
      return fallback
    }
  }

  write(name, value) {
    try {
      const tmp = this._file(name) + '.tmp'
      fs.writeFileSync(tmp, JSON.stringify(value), 'utf-8')
      fs.renameSync(tmp, this._file(name))
    } catch (e) {}
  }

  settings() {
    return Object.assign({
      theme: 'dark',
      accent: 'purple',
      lang: 'fa',
      apiId: '',
      apiHash: '',
      proxy: null
    }, this.read('settings.json', {}))
  }

  saveSettings(patch) {
    const s = this.settings()
    Object.assign(s, patch)
    this.write('settings.json', s)
    return s
  }

  /**
   * ✅ نرمال‌سازی: کتابخانهٔ نسخه‌های قدیمی ممکن است ترک‌هایی داشته باشد که فیلد fileId
   * ندارند (فقط id). بدون fileId نه پخش کار می‌کند نه دانلود. اینجا ترمیم می‌کنیم و
   * نسخهٔ سالم را دوباره روی دیسک می‌نویسیم.
   */
  normTracks(tracks) {
    if (!Array.isArray(tracks)) return []
    const out = tracks.map(t => {
      if (!t || typeof t !== 'object') return t
      let tr = t
      // ✅ مینی‌تامبنیلِ base64 را در کتابخانه ذخیره نکن (۳۳ هزار آهنگ → فایل/IPC چندده‌مگابایتی و فریز)
      if (tr.albumCoverMini) tr = Object.assign({}, tr, { albumCoverMini: '' })
      if ((tr.fileId === undefined || tr.fileId === null || !tr.fileId) && tr.id) {
        tr = Object.assign({}, tr, { fileId: tr.id })
      }
      if ((tr.id === undefined || tr.id === null || !tr.id) && tr.fileId) {
        tr = Object.assign({}, tr, { id: tr.fileId })
      }
      return tr
    })
    return out
  }

  library() { return this.normTracks(this.read('library.json', [])) }
  // ذخیرهٔ ناهم‌زمان و دیباونس‌شده تا نوشتن فایل بزرگ روی main اپ را قفل نکند
  saveLibrary(tracks) {
    const clean = this.normTracks(tracks)
    this._libCache = clean
    clearTimeout(this._libTimer)
    this._libTimer = setTimeout(() => {
      try { this.write('library.json', this._libCache || clean) } catch (e) {}
    }, 400)
  }

  favorites() { return this.read('favorites.json', []) }
  saveFavorites(tracks) { this.write('favorites.json', tracks) }

  playlists() { return this.read('playlists.json', []) }
  savePlaylists(pls) { this.write('playlists.json', pls) }

  downloads() { return this.read('downloads.json', []) }
  saveDownloads(list) { this.write('downloads.json', list) }

  // ✅ v6.0.0: چت‌های دنبال‌شده (مثل نسخهٔ اندروید)
  followed() { return this.read('followed.json', []) }
  saveFollowed(list) { this.write('followed.json', list) }
}

module.exports = { Store }
