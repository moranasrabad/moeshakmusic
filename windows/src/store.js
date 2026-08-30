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

  library() { return this.read('library.json', []) }
  saveLibrary(tracks) { this.write('library.json', tracks) }

  favorites() { return this.read('favorites.json', []) }
  saveFavorites(tracks) { this.write('favorites.json', tracks) }

  playlists() { return this.read('playlists.json', []) }
  savePlaylists(pls) { this.write('playlists.json', pls) }

  downloads() { return this.read('downloads.json', []) }
  saveDownloads(list) { this.write('downloads.json', list) }
}

module.exports = { Store }
