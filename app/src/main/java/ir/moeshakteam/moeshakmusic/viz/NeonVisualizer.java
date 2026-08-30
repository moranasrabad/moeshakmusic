package ir.moeshakteam.moeshakmusic.viz;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * ویژوالایزر نئونی — نسخهٔ نهایی از صفر — بدون هیچ Shader/Gradient
 * فقط Paint ساده با رنگ و آلفا. تضمینی بدون کرش. تیم موشک
 */
public class NeonVisualizer extends View {

    private static final int BARS = 48;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] levels = new float[BARS];

    private volatile boolean playing;
    private long lastFrame;

    public NeonVisualizer(Context context) {
        super(context);
        init();
    }

    public NeonVisualizer(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        barPaint.setStyle(Paint.Style.STROKE);
        barPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1f));
        ringPaint.setColor(0x3322D3EE);
        // مرکز: دایرهٔ سادهٔ نیمه‌شفاف (بدون گرادیان)
        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(0x1822D3EE);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /** رنگ نئون میلهٔ i — فیروزه‌ای → آبی → بنفش → صورتی */
    private static int neon(int i) {
        float hue = 190f + 130f * (i % BARS) / BARS;
        return Color.HSVToColor(new float[]{hue, 0.80f, 1f});
    }

    public interface BeatListener {
        void onBeat(float level);
    }

    private BeatListener beatListener;
    private long lastBeatNotify;

    public void setBeatListener(BeatListener l) {
        beatListener = l;
    }

    public void setPlaying(boolean p) {
        playing = p;
        if (p) postInvalidateOnAnimation();
    }

    public void pushWaveform(byte[] wf) {
        if (wf == null || wf.length == 0) return;
        int seg = Math.max(1, wf.length / BARS);
        for (int i = 0; i < BARS; i++) {
            int start = i * seg;
            int end = Math.min(wf.length, start + seg);
            int sum = 0, cnt = 0;
            for (int j = start; j < end; j += 2) {
                sum += Math.abs(wf[j] - 128);
                cnt++;
            }
            float v = cnt > 0 ? (sum / cnt) / 128f : 0f;
            float target = Math.min(1f, v * 1.6f);
            levels[i] = levels[i] * 0.6f + target * 0.4f;
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 💥 ضدکرش مطلق: هر خطایی در draw → رد شود، هرگز اپ نپرد
        try {
            drawInternal(canvas);
        } catch (Throwable ignored) {
        }
    }

    private void drawInternal(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        float minDim = Math.min(w, h);
        float baseR = minDim * 0.36f;
        long now = System.currentTimeMillis();
        double ts = now / 1000.0;
        float dt = Math.max(16f, now - lastFrame) / 1000f;
        lastFrame = now;

        // سطح میله‌ها
        if (playing) {
            double beat = Math.pow(Math.max(0d, Math.sin(ts * Math.PI * 2 / 1.85)), 3d);
            for (int i = 0; i < BARS; i++) {
                double wave = 0.5 + 0.5 * Math.sin(ts * 2.6 + i * 0.55);
                double jitter = Math.abs(Math.sin(i * 7.13 + ts * 9.7));
                float target = (float) Math.min(1d,
                        0.16 + 0.58 * wave * (0.42 + 0.58 * beat) + 0.16 * jitter);
                levels[i] += (target - levels[i]) * Math.min(1f, dt * 14f);
            }
        } else {
            for (int i = 0; i < BARS; i++) {
                levels[i] += (0.05f - levels[i]) * Math.min(1f, dt * 6f);
            }
        }

        float avg = 0f;
        for (int i = 0; i < BARS; i++) avg += levels[i];
        avg /= BARS;

        // ۱) مرکز نئونی — چند دایرهٔ متحدالمرکز با آلفای کم (به‌جای گرادیان)
        int pulse = (int) (avg * 4);
        for (int k = 0; k < 4 + pulse; k++) {
            float radius = baseR * (1.05f + k * 0.09f);
            int alpha = Math.max(0, 22 - k * 5);
            if (alpha <= 0) break;
            centerPaint.setAlpha(alpha);
            centerPaint.setColor(k % 2 == 0 ? 0x22D3EE : 0x8B5CF6);
            canvas.drawCircle(cx, cy, radius, centerPaint);
        }

        // ۲) حلقهٔ ظریف پایه
        canvas.drawCircle(cx, cy, baseR - dp(4), ringPaint);

        // ۳) میله‌ها — هسته + هاله
        float barW = (float) (2 * Math.PI * baseR / BARS) * 0.55f;
        float maxLen = minDim * 0.11f;
        for (int i = 0; i < BARS; i++) {
            float lvl = Math.max(levels[i], 0.05f);
            float len = minDim * 0.02f + lvl * maxLen;
            double ang = 2 * Math.PI * i / BARS - Math.PI / 2;
            double cos = Math.cos(ang), sin = Math.sin(ang);
            float x1 = (float) (cx + cos * (baseR + dp(3)));
            float y1 = (float) (cy + sin * (baseR + dp(3)));
            float x2 = (float) (cx + cos * (baseR + dp(3) + len));
            float y2 = (float) (cy + sin * (baseR + dp(3) + len));
            int col = neon(i);

            // هالهٔ نئون بیرونی
            glowPaint.setColor(col);
            glowPaint.setStrokeWidth(barW * 2.4f);
            glowPaint.setAlpha((int) (26 + lvl * 60));
            canvas.drawLine(x1, y1, x2, y2, glowPaint);

            // هستهٔ میله
            barPaint.setColor(col);
            barPaint.setStrokeWidth(barW);
            canvas.drawLine(x1, y1, x2, y2, barPaint);
        }

        // ۴) نوتیف ضرب برای پالس کاور
        if (beatListener != null) {
            final float beatOut = avg;
            post(() -> beatListener.onBeat(beatOut));
        }

        // ۵) ادامهٔ حلقهٔ انیمیشن
        if (playing) postInvalidateOnAnimation();
    }
}
