package com.maxlab.motioncues;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Колонки точек по краям экрана, смещаются против ускорения машины. */
public class CuesView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private float offX, offY, radiusPx;
    private int fillColor = 0xFF1E1E1E, strokeColor = 0xFFFFFFFF;
    private float intensity = 1f, vis = 1f;

    public CuesView(Context c) {
        super(c);
        density = getResources().getDisplayMetrics().density;
        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(1.5f * density);
        setDotSizeDp(9);
    }

    public void setDotSizeDp(int dp) { radiusPx = dp * density / 2f; invalidate(); }
    public void setColors(int f, int s) { fillColor = f; strokeColor = s; invalidate(); }
    public void setIntensity(float v) { intensity = v; invalidate(); }
    public void setVis(float v) { if (Math.abs(v - vis) > 0.005f) { vis = v; invalidate(); } }
    public void setOffsetPx(float x, float y) { offX = x; offY = y; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        int a = Math.round(255 * intensity * vis);
        if (w == 0 || h == 0 || a < 3) return;
        fill.setColor(fillColor); fill.setAlpha(a);
        stroke.setColor(strokeColor); stroke.setAlpha(a);
        boolean landscape = w > h;
        int rows = landscape ? 4 : 6;
        float[] cols = landscape ? new float[]{0.05f, 0.11f} : new float[]{0.07f, 0.17f};
        float top = h * 0.14f, bottom = h * 0.86f;
        float step = (bottom - top) / (rows - 1);
        for (int side = 0; side < 2; side++) {
            for (int ci = 0; ci < cols.length; ci++) {
                float x = side == 0 ? w * cols[ci] : w * (1f - cols[ci]);
                float stagger = (ci % 2 == 1) ? step / 2f : 0f;
                for (int r = 0; r < rows; r++) {
                    float y = top + r * step + stagger;
                    if (y > bottom + 1) continue;
                    c.drawCircle(x + offX, y + offY, radiusPx, fill);
                    c.drawCircle(x + offX, y + offY, radiusPx, stroke);
                }
            }
        }
    }
}
