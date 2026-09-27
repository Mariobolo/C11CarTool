package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 圆弧仪表盘小模块（玻璃底，自绘，零依赖）：背景弧 + 进度弧 + 中心当前值与单位。
 *
 * <p>默认 270° 弧、开口向下。用于车速 / 电量 / 功率等单值需要突出显示的场景。
 */
public class GaugeView extends FrameLayout {

    private final GaugeSurface surface;

    private float value = Float.NaN;
    private float minV = 0;
    private float maxV = 100;
    private String unit = "";
    private int arcColor = 0xFF3B82F6;

    public GaugeView(Context ctx, String title) {
        super(ctx);
        setBackground(Glass.bg(ctx, 12, Glass.NORMAL));
        setPadding(dp(8), dp(6), dp(8), dp(6));

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        TextView titleView = make(GridDimens.SP_LABEL, DashboardTheme.DIM, false);
        titleView.setText(title);
        titleView.setGravity(Gravity.CENTER);
        root.addView(titleView);

        surface = new GaugeSurface(ctx);
        root.addView(surface, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    public void setValue(float v) {
        this.value = v;
        surface.invalidate();
    }

    public void setRange(float min, float max) { this.minV = min; this.maxV = max; }
    public void setUnit(String u) { this.unit = u == null ? "" : u; }
    public void setColor(int c) { this.arcColor = c; }

    private String fmt(float v) {
        if (Math.abs(v) >= 100) return String.valueOf(Math.round(v));
        return String.valueOf(Math.round(v * 10) / 10f);
    }

    // ── 绘图面 ──

    private class GaugeSurface extends View {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();

        GaugeSurface(Context c) {
            super(c);
            bgPaint.setStyle(Paint.Style.STROKE);
            bgPaint.setStrokeWidth(dp(8));
            bgPaint.setColor(0x26FFFFFF);
            bgPaint.setStrokeCap(Paint.Cap.ROUND);
            arcPaint.setStyle(Paint.Style.STROKE);
            arcPaint.setStrokeWidth(dp(8));
            arcPaint.setStrokeCap(Paint.Cap.ROUND);
            numPaint.setColor(DashboardTheme.TEXT);
            numPaint.setTextAlign(Paint.Align.CENTER);
            numPaint.setTypeface(Typeface.DEFAULT_BOLD);
            unitPaint.setColor(DashboardTheme.DIM);
            unitPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            float stroke = dp(8);
            float size = Math.min(w, h) - stroke;
            float left = (w - size) / 2f;
            float top = (h - size) / 2f;
            oval.set(left, top, left + size, top + size);

            // 270° 弧：自 135°（左下）顺时针扫到 45°（右下），开口向下
            final float start = 135f;
            final float sweep = 270f;
            canvas.drawArc(oval, start, sweep, false, bgPaint);

            if (!Float.isNaN(value)) {
                float span = maxV - minV;
                float frac = span == 0 ? 0 : (value - minV) / span;
                frac = Math.max(0, Math.min(1, frac));
                arcPaint.setColor(arcColor);
                canvas.drawArc(oval, start, sweep * frac, false, arcPaint);
            }

            // 中心数值 + 单位
            float cx = w / 2f;
            float cy = h / 2f;
            numPaint.setTextSize(Math.max(18, size * 0.22f));
            canvas.drawText(Float.isNaN(value) ? "--" : fmt(value), cx, cy + size * 0.06f, numPaint);
            unitPaint.setTextSize(Math.max(11, size * 0.09f));
            canvas.drawText(unit, cx, cy + size * 0.22f, unitPaint);
        }
    }

    private TextView make(int sp, int color, boolean bold) {
        TextView tv = new TextView(getContext());
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
