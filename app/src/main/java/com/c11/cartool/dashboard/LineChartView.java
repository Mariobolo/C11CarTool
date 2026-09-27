package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 历史折线 / 波形小模块（玻璃底，自绘，零依赖）。
 *
 * <p>环形缓冲最近 {@value #N} 个采样，Canvas 画折线 + 线下渐变填充 + 当前点高亮；
 * 范围可固定（{@link #setRange}）或按数据自动缩放。用于电压 / 电流 / 功率 / 车速历史。
 */
public class LineChartView extends FrameLayout {

    private static final int N = 64;

    private final float[] data = new float[N];
    private int count = 0;
    private int head = 0;

    private final ChartSurface surface;
    private final TextView nowView;

    private int lineColor = 0xFF3B82F6;
    private float fixedMin = Float.NaN;
    private float fixedMax = Float.NaN;
    private String unit = "";

    public LineChartView(Context ctx, String title) {
        super(ctx);
        setBackground(Glass.bg(ctx, 12, Glass.NORMAL));
        setPadding(dp(8), dp(6), dp(8), dp(6));

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        addView(root, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout headRow = new LinearLayout(ctx);
        headRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView titleView = make(GridDimens.SP_LABEL, DashboardTheme.DIM, false);
        titleView.setText(title);
        titleView.setGravity(Gravity.START);
        nowView = make(GridDimens.SP_STEP_VAL, DashboardTheme.TEXT, true);
        nowView.setText("--");
        nowView.setGravity(Gravity.END);
        headRow.addView(titleView, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        headRow.addView(nowView);
        root.addView(headRow);

        surface = new ChartSurface(ctx);
        root.addView(surface, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** 追加一个采样（NaN / 无穷大忽略）。 */
    public void addPoint(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return;
        data[head] = v;
        head = (head + 1) % N;
        if (count < N) count++;
        nowView.setText(fmt(v) + unit);
        surface.invalidate();
    }

    public void setUnit(String u) { this.unit = u == null ? "" : u; }
    public void setColor(int c) { this.lineColor = c; }
    public void setRange(float min, float max) { this.fixedMin = min; this.fixedMax = max; }

    /** 顺序读取第 i 个有效点（0 = 最早）。 */
    private float at(int i) {
        int start = count < N ? 0 : head;
        return data[(start + i) % N];
    }

    private String fmt(float v) {
        if (Math.abs(v) >= 100) return String.valueOf(Math.round(v));
        return String.valueOf(Math.round(v * 10) / 10f);
    }

    // ── 绘图面 ──

    private class ChartSurface extends View {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path linePath = new Path();
        private final Path fillPath = new Path();

        ChartSurface(Context c) {
            super(c);
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(dp(2));
            linePaint.setStrokeCap(Paint.Cap.ROUND);
            linePaint.setStrokeJoin(Paint.Join.ROUND);
            gridPaint.setColor(0x22FFFFFF);
            gridPaint.setStrokeWidth(1);
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;

            // 横向网格基线（3 条）
            for (int i = 1; i <= 3; i++) {
                canvas.drawLine(0, h * i / 4f, w, h * i / 4f, gridPaint);
            }
            if (count < 2) return;

            float lo = fixedMin;
            float hi = fixedMax;
            if (Float.isNaN(lo) || Float.isNaN(hi)) {
                lo = Float.MAX_VALUE;
                hi = -Float.MAX_VALUE;
                for (int i = 0; i < count; i++) {
                    float v = at(i);
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                }
                if (hi - lo < 1e-4) { lo -= 1; hi += 1; }
                float pad = (hi - lo) * 0.12f;
                lo -= pad;
                hi += pad;
            }
            final float min = lo;
            final float max = hi;

            linePath.reset();
            fillPath.reset();
            for (int i = 0; i < count; i++) {
                float px = count == 1 ? 0 : w * i / (float) (count - 1);
                float py = h - (at(i) - min) / (max - min) * h;
                if (i == 0) {
                    linePath.moveTo(px, py);
                    fillPath.moveTo(px, h);
                    fillPath.lineTo(px, py);
                } else {
                    linePath.lineTo(px, py);
                    fillPath.lineTo(px, py);
                }
            }
            int last = count - 1;
            float lastX = w * last / (float) (count - 1);
            fillPath.lineTo(lastX, h);
            fillPath.close();

            fillPaint.setShader(new LinearGradient(0, 0, 0, h,
                    (lineColor & 0x00FFFFFF) | 0x55000000,
                    lineColor & 0x00FFFFFF, Shader.TileMode.CLAMP));
            linePaint.setColor(lineColor);
            canvas.drawPath(fillPath, fillPaint);
            canvas.drawPath(linePath, linePaint);

            // 当前点高亮（色环 + 白心）
            float cy = h - (at(last) - min) / (max - min) * h;
            dotPaint.setColor(lineColor);
            canvas.drawCircle(lastX, cy, dp(5), dotPaint);
            dotPaint.setColor(0xFFFFFFFF);
            canvas.drawCircle(lastX, cy, dp(2.5f), dotPaint);
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
