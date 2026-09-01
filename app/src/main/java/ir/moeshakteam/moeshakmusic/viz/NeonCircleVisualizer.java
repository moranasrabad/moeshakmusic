package ir.moeshakteam.moeshakmusic.viz;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.annotation.Nullable;

import com.chibde.visualizer.CircleBarVisualizerSmooth;

/**
 * حلقهٔ میله‌ای نرم و همیشه‌نمایان — تیم موشک.
 * روی CircleBarVisualizerSmooth (کتابخانهٔ audiovisualizer) سوار است که
 * گذار نرم میله‌ها را انجام می‌دهد؛ این کلاس فقط یک حلقهٔ پایه + پالس آرام
 * در حالت بی‌صدا می‌کشد تا ویجت هیچ‌وقت خالی/نامرئی نماند.
 */
public class NeonCircleVisualizer extends CircleBarVisualizerSmooth {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint idlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int accentColor = 0xFF22D3EE;

    public NeonCircleVisualizer(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        ringPaint.setStyle(Paint.Style.STROKE);
        idlePaint.setStyle(Paint.Style.STROKE);
        idlePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    public void setColor(int color) {
        super.setColor(color);
        this.accentColor = color;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        // شعاع دقیقاً مطابق فرمول کتابخانه: minDim * 0.65 / 2 * 0.6
        float radius = Math.min(w, h) * 0.65f / 2f * 0.6f;
        float cx = w / 2f, cy = h / 2f;

        // حلقهٔ پایهٔ همیشگی — ویجت هیچ‌وقت نامرئی نمی‌شود
        ringPaint.setStrokeWidth(dp(2));
        ringPaint.setColor((accentColor & 0x00FFFFFF) | 0x33000000);
        canvas.drawCircle(cx, cy, radius, ringPaint);

        if (bytes == null || bytes.length == 0) {
            // پالس آرام در حالت بی‌صدا تا همیشه زنده دیده شود
            long now = System.currentTimeMillis();
            double t = now / 1000.0;
            float breathe = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2 / 2.4));
            float len = radius * (0.04f + 0.05f * breathe);
            idlePaint.setStrokeWidth(dp(5));
            idlePaint.setColor((accentColor & 0x00FFFFFF) | 0x66000000);
            idlePaint.setAlpha((int) (120 + 60 * breathe));
            int idleBars = 48;
            for (int i = 0; i < idleBars; i++) {
                double ang = 2 * Math.PI * i / idleBars;
                double cos = Math.cos(ang), sin = Math.sin(ang);
                float x1 = (float) (cx + cos * radius);
                float y1 = (float) (cy + sin * radius);
                float x2 = (float) (cx + cos * (radius + len));
                float y2 = (float) (cy + sin * (radius + len));
                canvas.drawLine(x1, y1, x2, y2, idlePaint);
            }
            postInvalidateOnAnimation();
            return;
        }
        super.onDraw(canvas);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
