# -*- coding: utf-8 -*-
# پچ v5.9.3 بخش ۲ — سوییچ به NeonVisualizer + UX دنبال‌شده‌ها + حذف VisualizerView قدیمی
import io, re, os

# ═══ ۱) fragment_player.xml — NeonVisualizer ═══
p = 'res/layout/fragment_player.xml'
s = io.open(p, encoding='utf-8').read()
s = s.replace('ir.moeshakteam.moeshakmusic.viz.VisualizerView',
              'ir.moeshakteam.moeshakmusic.viz.NeonVisualizer')
io.open(p, 'w', encoding='utf-8').write(s)
print('1. fragment_player.xml ✓')

# ═══ ۲) PlayerFragment — تایپ‌ها + setPlaying در رویون ═══
p = 'java/ir/moeshakteam/moeshakmusic/ui/PlayerFragment.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('import ir.moeshakteam.moeshakmusic.viz.VisualizerView;',
              'import ir.moeshakteam.moeshakmusic.viz.NeonVisualizer;')
s = s.replace('private VisualizerView viz;', 'private NeonVisualizer viz;')
# پالس کاور — با same listener API
s = s.replace('''        // 💥 کاور با ضرب ویژوالایزر بزرگ/کوچک می‌شود
        viz.setBeatListener(level -> {
            if (art == null || !isAdded()) return;
            float sc = 1f + level * 0.07f;
            art.setScaleX(sc);
            art.setScaleY(sc);
        });''','''        // 💥 کاور با ضرب ویژوالایزر بزرگ/کوچک می‌شود
        viz.setBeatListener(level -> {
            if (art == null || !isAdded()) return;
            float sc = 1f + level * 0.06f;
            art.animate().scaleX(sc).scaleY(sc).setDuration(90).start();
        });''')
io.open(p, 'w', encoding='utf-8').write(s)
print('2. PlayerFragment import/type ✓ (setBeatListener اگر نبود بعداً اضافه)')

# NeonVisualizer باید setBeatListener هم داشته باشد — اضافه کن
p = 'java/ir/moeshakteam/moeshakmusic/viz/NeonVisualizer.java'
s = io.open(p, encoding='utf-8').read()
if 'setBeatListener' not in s:
    s = s.replace('''    /** PlayerManager این را صدا می‌زند */
    public void setPlaying(boolean p) {''','''    public interface BeatListener {
        void onBeat(float level);
    }

    private BeatListener beatListener;
    private long lastBeatNotify;

    /** کاور پالس بخورد */
    public void setBeatListener(BeatListener l) {
        beatListener = l;
    }

    /** PlayerManager این را صدا می‌زند */
    public void setPlaying(boolean p) {''')
    s = s.replace('''        // ۴) ادامهٔ حلقهٔ انیمیشن فقط موقع پخش
        if (playing && attached) postInvalidateOnAnimation();
    }''','''        // ۴) نوتیف ضرب برای پالس کاور
        if (beatListener != null && now - lastBeatNotify > 120) {
            lastBeatNotify = now;
            post(() -> beatListener.onBeat(avg));
        }

        // ۵) ادامهٔ حلقهٔ انیمیشن فقط موقع پخش
        if (playing && attached) postInvalidateOnAnimation();
    }''')
    io.open(p, 'w', encoding='utf-8').write(s)
print('3. NeonVisualizer beatListener ✓')

# ═══ ۳ب) PlayerManager — تایپ VisualizerView → NeonVisualizer ═══
p = 'java/ir/moeshakteam/moeshakmusic/player/PlayerManager.java'
s = io.open(p, encoding='utf-8').read()
s = s.replace('import ir.moeshakteam.moeshakmusic.viz.VisualizerView;',
              'import ir.moeshakteam.moeshakmusic.viz.NeonVisualizer;')
s = s.replace('private VisualizerView vizView;', 'private NeonVisualizer vizView;')
s = s.replace('public void setVisualizerView(VisualizerView v) {',
              'public void setVisualizerView(NeonVisualizer v) {')
io.open(p, 'w', encoding='utf-8').write(s)
print('4. PlayerManager type ✓')

# VisualizerView قدیمی را حذف کن (جلوی تداخل)
old = 'java/ir/moeshakteam/moeshakmusic/viz/VisualizerView.java'
if os.path.exists(old):
    os.remove(old)
    print('5. VisualizerView قدیمی حذف ✓')

# چک: references باقی‌مانده
for f in ['java/ir/moeshakteam/moeshakmusic/ui/PlayerFragment.java',
          'java/ir/moeshakteam/moeshakmusic/player/PlayerManager.java',
          'java/ir/moeshakteam/moeshakmusic/ui/MainActivity.java']:
    s = io.open(f, encoding='utf-8').read()
    if 'VisualizerView' in s:
        print('⚠️ هنوز VisualizerView در', f)
print('چک تمام')
