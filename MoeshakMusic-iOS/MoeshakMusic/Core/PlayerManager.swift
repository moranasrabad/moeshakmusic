import Foundation
import AVFoundation
import MediaPlayer
import UIKit

/// مدیریت پخش — AVPlayer + دانلود TDLib + Now Playing زنده — تیم موشک
@MainActor
final class PlayerManager: ObservableObject {

    static let shared = PlayerManager()

    @Published var queue: [Track] = []
    @Published var index: Int = -1
    @Published var isPlaying = false
    @Published var position: Double = 0
    @Published var duration: Double = 0
    @Published var shuffle = false
    /// 0=خاموش 1=همه 2=یک آهنگ
    @Published var repeatMode = 0
    /// موج ویژوالایزر (0..1) — ۱۲ میله مثل استور
    @Published var visualizer: [Float] = Array(repeating: 0.1, count: 12)
    /// دانلود در حال جریان برای تراک فعلی — درصد
    @Published var downloadPct = -1

    private var player: AVPlayer?
    private var timeObserver: Any?
    private var shuffleOrder: [Int] = []

    private init() {
        setupRemoteCommands()
        setupInterruption()
    }

    var current: Track? {
        index >= 0 && index < queue.count ? queue[index] : nil
    }

    // MARK: - Control

    func play(_ tracks: [Track], at idx: Int) {
        queue = tracks
        load(idx, autoplay: true)
    }

    func load(_ idx: Int, autoplay: Bool) {
        guard idx >= 0, idx < queue.count else { return }
        index = idx
        let t = queue[idx]
        player?.pause()
        player = nil
        position = 0
        duration = Double(t.duration)

        Task.detached { [weak self] in
            guard let self else { return }
            // ۱) فایل دانلودشدهٔ محلی
            let localPath = await MainActor.run { Store.shared.downloads.pathOf(t) }
            if let p = localPath, FileManager.default.fileExists(atPath: p) {
                await self.startPlayback(path: p, autoplay: autoplay)
                return
            }
            // ۲) دانلود کامل با درصد (مثل مسیر مطمئن اندروید)
            await MainActor.run { self.downloadPct = 0 }
            if let p = try? TDLoader.downloadToCache(t, progress: { pct in
                Task { @MainActor in self.downloadPct = pct }
            }) {
                await MainActor.run {
                    Store.shared.downloads.mark(t, path: p)
                    self.downloadPct = -1
                }
                await self.startPlayback(path: p, autoplay: autoplay)
            } else {
                await MainActor.run { self.downloadPct = -1 }
            }
        }
    }

    private func startPlayback(path: String, autoplay: Bool) async {
        let url = URL(fileURLWithPath: path)
        let item = AVPlayerItem(url: url)
        let av = AVPlayer(playerItem: item)
        player = av
        if let t = timeObserver { av.removeTimeObserver(t); timeObserver = nil }
        timeObserver = av.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.4, preferredTimescale: 600),
                                                  queue: .main) { [weak self] time in
            Task { @MainActor [weak self] in
                guard let self else { return }
                self.position = time.seconds
                if let d = self.player?.currentItem?.duration.seconds, d.isFinite, d > 0 {
                    self.duration = d
                }
                self.updateNowPlaying()
                if self.isPlaying { self.tickVisualizer() }
            }
        }
        NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime,
                                               object: item, queue: .main) { [weak self] _ in
            Task { @MainActor [weak self] in self?.next() }
        }
        av.play()
        isPlaying = autoplay
        updateNowPlaying()
    }

    func toggle() {
        if current == nil {
            if !queue.isEmpty { load(0, autoplay: true) }
            return
        }
        if isPlaying { player?.pause() } else { player?.play() }
        isPlaying.toggle()
        updateNowPlaying()
    }

    func next() {
        guard !queue.isEmpty else { return }
        if repeatMode == 2 { seek(to: 0); player?.play(); isPlaying = true; return }
        let ni = nextIndex()
        if ni == -1 { player?.pause(); isPlaying = false; return }
        load(ni, autoplay: true)
    }

    func prev() {
        guard !queue.isEmpty else { return }
        if position > 3 { seek(to: 0); return }
        let pi = prevIndex()
        load(pi == -1 ? 0 : pi, autoplay: true)
    }

    func seek(to sec: Double) {
        player?.seek(to: CMTime(seconds: sec, preferredTimescale: 600))
        position = sec
    }

    func toggleShuffle() {
        shuffle.toggle()
        if shuffle { rebuildShuffle() }
    }

    func cycleRepeat() { repeatMode = (repeatMode + 1) % 3 }

    func removeFromQueue(_ pos: Int) {
        guard pos >= 0, pos < queue.count else { return }
        queue.remove(at: pos)
        if pos < index { index -= 1 }
        else if pos == index && !queue.isEmpty && index >= queue.count { index = queue.count - 1 }
    }

    func addToQueue(_ t: Track) { queue.append(t) }

    func toggleFavorite() {
        guard let t = current else { return }
        Store.shared.favorites.toggle(t)
        LibraryManager.shared.persistFavoriteTracks()
        // دانلود خودکار فیوریت — مثل اندروید
        if Store.shared.favorites.contains(t) && !Store.shared.downloads.isDownloaded(t) {
            downloadInBackground(t)
        }
    }

    private func downloadInBackground(_ t: Track) {
        Task.detached {
            if let p = try? TDLoader.downloadToCache(t, progress: { _ in }) {
                await MainActor.run { Store.shared.downloads.mark(t, path: p) }
            }
        }
    }

    /// توقف کامل — هنگام خاتمهٔ نشست
    func stopAll() {
        player?.pause()
        player = nil
        queue = []
        index = -1
        isPlaying = false
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    // MARK: - Shuffle helpers

    private func rebuildShuffle() {
        shuffleOrder = Array(0..<queue.count).shuffled()
        if let i = shuffleOrder.firstIndex(of: index) {
            shuffleOrder.swapAt(0, i)
        }
    }

    private func nextIndex() -> Int {
        if shuffle {
            if shuffleOrder.isEmpty { rebuildShuffle() }
            if let pos = shuffleOrder.firstIndex(of: index), pos + 1 < shuffleOrder.count {
                return shuffleOrder[pos + 1]
            }
            rebuildShuffle()
            return shuffleOrder.first ?? -1
        }
        return index + 1 < queue.count ? index + 1 : (repeatMode == 1 ? 0 : -1)
    }

    private func prevIndex() -> Int {
        if shuffle {
            if shuffleOrder.isEmpty { rebuildShuffle() }
            if let pos = shuffleOrder.firstIndex(of: index), pos > 0 { return shuffleOrder[pos - 1] }
            return shuffleOrder.last ?? -1
        }
        return index - 1 >= 0 ? index - 1 : (repeatMode == 1 ? queue.count - 1 : -1)
    }

    // MARK: - Visualizer (انیمیشن ریتمیک روی MainActor)

    private func tickVisualizer() {
        let t = Date().timeIntervalSince1970
        let beat = pow(max(0, sin(t * .pi * 2 / 1.85)), 3)
        for i in 0..<visualizer.count {
            let wave = 0.5 + 0.5 * sin(t * 2.7 + Double(i) * 0.53)
            let jitter = abs(sin(Double(i) * 7.13 + t * 9.7))
            visualizer[i] = min(1, Float(0.14 + 0.55 * wave * (0.45 + 0.55 * beat) + 0.18 * jitter))
        }
    }

    // MARK: - Now Playing / Control Center

    private func updateNowPlaying() {
        guard let t = current else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: t.title,
            MPMediaItemPropertyArtist: t.subtitle,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0
        ]
        // کاور — از ArtLoader
        if let img = ArtLoader.cacheForSync(t) {
            let art = MPMediaItemArtwork(boundsSize: img.size) { _ in img }
            info[MPMediaItemPropertyArtwork] = art
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func setupRemoteCommands() {
        let c = MPRemoteCommandCenter.shared()
        c.playCommand.addTarget { _ in
            Task { @MainActor in self.toggle() }
            return .success
        }
        c.pauseCommand.addTarget { _ in
            Task { @MainActor in self.toggle() }
            return .success
        }
        c.nextTrackCommand.addTarget { _ in
            Task { @MainActor in self.next() }
            return .success
        }
        c.previousTrackCommand.addTarget { _ in
            Task { @MainActor in self.prev() }
            return .success
        }
        c.changePlaybackPositionCommand.addTarget { event in
            guard let e = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            Task { @MainActor in self.seek(to: e.positionTime) }
            return .success
        }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        try? AVAudioSession.sharedInstance().setActive(true)
    }

    private func setupInterruption() {
        NotificationCenter.default.addObserver(forName: AVAudioSession.interruptionNotification,
                                               object: nil, queue: .main) { [weak self] note in
            guard let info = note.userInfo,
                  let typeRaw = info[AVAudioSessionInterruptionTypeKey] as? UInt,
                  let type = AVAudioSession.InterruptionType(rawValue: typeRaw) else { return }
            if type == .began {
                Task { @MainActor in
                    self?.isPlaying = false
                    self?.updateNowPlaying()
                }
            }
        }
    }
}
