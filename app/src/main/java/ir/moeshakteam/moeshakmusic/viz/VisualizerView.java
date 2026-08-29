package ir.moeshakteam.moeshakmusic.viz;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import ir.moeshakteam.moeshakmusic.R;

/**
 * ویژوالایزر دایره‌ای با گرادیان یخی (فیروزه‌ای → بنفش) — الهام از هویت moeshakteam.ir
 * تیم موشک
 */
public class VisualizerView extends View {

    private static final int BARS = 64;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] levels = new float[BARS];
    private final float[] targets = new float[BARS];
    private SweepGradient sweep;
    private int c1, c2;
    private boolean hasData;
    private long lastPush;
    /** در حال پخش؟ — برای انیمیشن ریتمیک وقتی Visualizer واقعی در دسترس نیست */
    private volatile boolean playing;
    private long lastWaveAt;

    public VisualizerView(Context context) {
        super(context);
        init(context);
    }

    public VisualizerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context c) {
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(R.attr.colorPrimary, tv, true);
        c2 = tv.data;
        c.getTheme().resolveAttribute(R.attr.colorSecondary, tv, true);
        c1 = tv.data;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(1.2f));
        ringPaint.setColor(c1);
        ringPaint.setAlpha(70);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        sweep = new SweepGradient(w / 2f, h / 2f, new int[]{c2, c1, c2}, new float[]{0f, 0.5f, 1f});
    }

    /** وضعیت پخش — وقتی پخش است حتی بدون دادهٔ واقعی هم زنده می‌رقصد */
    public void setPlaying(boolean p) {
        playing = p;
        if (p) postInvalidateOnAnimation();
    }

    /** دادهٔ موج صدا (0..255 حول 128) از android.media.Visualizer */
    public void pushWaveform(byte[] wf) {
        if (wf == null || wf.length == 0) return;
        hasData = true;
        lastWaveAt = System.currentTimeMillis();
        int n = BARS;
        int seg = Math.max(1, wf.length / n);
        for (int i = 0; i < n; i++) {
            int start = i * seg;
            int end = Math.min(wf.length, start + seg);
            int sum = 0, cnt = 0;
            for (int j = start; j < end; j += 2) {
                sum += Math.abs(wf[j] - 128);
                cnt++;
            }
            float v = cnt > 0 ? (sum / cnt) / 128f : 0f;
            targets[i] = Math.min(1f, v * 1.5f);
        }
        for (int i = 0; i < n; i++) {
            levels[i] = levels[i] * 0.55f + targets[i] * 0.45f;
        }
        long now = System.currentTimeMillis();
        if (now - lastPush > 30) {
            lastPush = now;
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float cx = w / 2f, cy = h / 2f;
        float minDim = Math.min(w, h);
        float baseR = minDim * 0.315f;
        float maxLen = minDim * 0.135f;

        // حلقهٔ داخلی کم‌رنگ
        canvas.drawCircle(cx, cy, baseR - dp(8), ringPaint);

        boolean live = hasData && (System.currentTimeMillis() - lastWaveAt) < 900;
        if (!live && playing) {
            // انیمیشن ریتمیک جایگزین — وقتی Visualizer واقعی (میکروفن) در دسترس نیست
            long t = System.currentTimeMillis();
            double ts = t / 1000.0;
            double beat = Math.pow(Math.max(0d, Math.sin(ts * Math.PI * 2 / 1.85)), 3d);
            for (int i = 0; i < BARS; i++) {
                double wave = 0.5 + 0.5 * Math.sin(ts * 2.7 + i * 0.53);
                double jitter = Math.abs(Math.sin(i * 7.13 + ts * 9.7));
                float v = (float) Math.min(1d, 0.14 + 0.55 * wave * (0.45 + 0.55 * beat) + 0.18 * jitter);
                targets[i] = v;
                levels[i] = levels[i] * 0.72f + v * 0.28f;
            }
        }

        paint.setShader(sweep);
        int n = BARS;
        float barW = (float) (2 * Math.PI * baseR / n) * 0.55f;
        for (int i = 0; i < n; i++) {
            float lvl = (live || playing) ? levels[i] : 0.06f;
            float len = minDim * 0.012f + lvl * maxLen;
            double ang = 2 * Math.PI * i / n - Math.PI / 2;
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            float x1 = cx + cos * (baseR + dp(6));
            float y1 = cy + sin * (baseR + dp(6));
            float x2 = cx + cos * (baseR + dp(6) + len);
            float y2 = cy + sin * (baseR + dp(6) + len);
            // هالهٔ ملایم
            paint.setStrokeWidth(barW * 2.2f);
            paint.setAlpha(45);
            canvas.drawLine(x1, y1, x2, y2, paint);
            // میلهٔ اصلی
            paint.setStrokeWidth(barW);
            paint.setAlpha(255);
            canvas.drawLine(x1, y1, x2, y2, paint);
        }
        paint.setAlpha(255);
        // ادامهٔ حلقهٔ انیمیشن فقط وقتی پخش است (صرفه‌جویی باتری در توقف)
        if (playing) postInvalidateOnAnimation();
    }
}
