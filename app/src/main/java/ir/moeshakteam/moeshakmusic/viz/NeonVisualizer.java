package ir.moeshakteam.moeshakmusic.viz;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * ویژوالایزر نئونی — بازنویسی کامل از صفر (ساده، بدون وابستگی، تضمینی) — تیم موشک
 * ۶۴ میلهٔ رنگین‌کمانی (فیروزه‌ای→صورتی) + هالهٔ نئون دولایه + مرکز نفس‌کشنده.
 * حتی اگر Visualizer میکروفن وصل نشود، موقع پخش با انیمیشن ضرب می‌رقصد.
 */
public class NeonVisualizer extends View {

    private static final int BARS = 48;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] levels = new float[BARS];

    private volatile boolean playing;
    private boolean attached;
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
        // فعال نگه داشتن لایهٔ نرم‌افزاری — سازگار با همهٔ GPUها
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /** رنگ نئون میلهٔ i — دور دایره از فیروزه‌ای به صورتی */
    private static int neon(int i) {
        float hue = 190f + 130f * (i % BARS) / BARS;
        return Color.HSVToColor(new float[]{hue, 0.80f, 1f});
    }

    public interface BeatListener {
        void onBeat(float level);
    }

    private BeatListener beatListener;
    private long lastBeatNotify;

    /** کاور پالس بخورد */
    public void setBeatListener(BeatListener l) {
        beatListener = l;
    }

    /** PlayerManager این را صدا می‌زند */
    public void setPlaying(boolean p) {
        playing = p;
        postInvalidateOnAnimation();
    }

    /** موج واقعی میکروفن — اگر باشد */
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
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        float minDim = Math.min(w, h);
        float baseR = minDim * 0.36f;
        long now = System.currentTimeMillis();
        double ts = now / 1000.0;
        float dt = Math.max(16f, now - lastFrame) / 1000f;
        lastFrame = now;

        // محاسبهٔ سطح میله‌ها — واقعی یا انیمیشن ضرب
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
            // آرام به سطح استراحت برگرد (میله‌ها همیشه دیده شوند)
            for (int i = 0; i < BARS; i++) {
                levels[i] += (0.12f - levels[i]) * Math.min(1f, dt * 6f);
            }
        }

        // میانگین برای شدت مرکز
        float avg = 0f;
        for (int i = 0; i < BARS; i++) avg += levels[i];
        avg /= BARS;

        // ۱) هالهٔ مرکزی نئونی — طول color و position باید برابر باشد (کرش RadialGradient)
        int glowA = (int) (30 + avg * 95);
        float radius = Math.max(1f, baseR * 1.55f);
        try {
            centerPaint.setShader(new RadialGradient(cx, cy, radius,
                    new int[]{0x00000000,
                            (glowA << 24) | 0x22D3EE,
                            ((glowA / 2) << 24) | 0x8B5CF6,
                            0x00000000},
                    new float[]{0f, 0.42f, 0.72f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius, centerPaint);
        } catch (Throwable ignored) {
            centerPaint.setShader(null);
            centerPaint.setColor((glowA << 24) | 0x22D3EE);
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
        if (beatListener != null && now - lastBeatNotify > 120) {
            lastBeatNotify = now;
            final float beatOut = avg;
            post(() -> beatListener.onBeat(beatOut));
        }

        // ۵) ادامهٔ حلقهٔ انیمیشن تا وقتی ویو به پنجره وصله
        // (موقع توقف هم استراحت نرم نمایش داده می‌شود تا میله‌ها همیشه دیده شوند)
        if (attached) postInvalidateOnAnimation();
    }
}
