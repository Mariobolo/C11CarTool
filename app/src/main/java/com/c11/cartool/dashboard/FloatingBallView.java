package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * [v0.3.19] 悬浮返回球（车机手势层的配套组件）。
 *
 * <p>设计参照跑跑桌面悬浮返回球（容器内点球返回主界面），适配车机规范：
 * <ul>
 *   <li>直径 {@value #SIZE_DP}dp ≥ 76dp 触摸目标（Google 车机指南）；</li>
 *   <li>玻璃拟态圆球（半透明底 + 光晕 + 细描边），与主题一致；</li>
 *   <li>可拖动（拖动后自动吸附右/左边缘，不改变点击回调）；</li>
 *   <li>点击回调由宿主注入 {@link #setOnTap(Runnable)}。</li>
 * </ul>
 *
 * <p>用法（宿主 FrameLayout 容器）：
 * <pre>
 * FloatingBallView ball = new FloatingBallView(ctx);
 * ball.setOnTap(() -> finish());          // 容器页返回主界面
 * host.addView(ball, new FrameLayout.LayoutParams(ballSize, ballSize, Gravity.END | Gravity.CENTER_VERTICAL));
 * ball.setVisible(true);
 * </pre>
 *
 * <p>v0.3.19 交付组件；v0.3.20 分屏/容器页挂载使用。
 */
public class FloatingBallView extends View {

    /** 悬浮球直径（dp），≥ 车机 76dp 触摸目标。 */
    public static final int SIZE_DP = 76;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Runnable onTap = null;
    private boolean visible = true;

    // 拖动状态
    private float lastTouchX, lastTouchY;
    private boolean dragging = false;
    private boolean moved = false;
    private FrameLayout.LayoutParams dragLp = null;

    public FloatingBallView(Context ctx) {
        super(ctx);
        int size = Math.round(SIZE_DP * ctx.getResources().getDisplayMetrics().density);
        bgPaint.setShader(new RadialGradient(size / 2f, size / 2f, size / 2f,
                new int[]{0xE64A5568, 0xCC1A1D27, 0xE6161A22},
                new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP));
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(Math.max(1f, ctx.getResources().getDisplayMetrics().density));
        ringPaint.setColor(0x66FFFFFF);
        textPaint.setColor(0xFFE4E7EB);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(Math.round(30 * ctx.getResources().getDisplayMetrics().density));
        textPaint.setFakeBoldText(true);
        setClickable(true);
    }

    /** 点击回调（返回主界面等）。 */
    public void setOnTap(Runnable r) {
        this.onTap = r;
    }

    /** 显示/隐藏（容器页进入时显示，退出时隐藏）。 */
    public void setVisible(boolean v) {
        this.visible = v;
        setVisibility(v ? VISIBLE : GONE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float r = getWidth() / 2f;
        canvas.drawCircle(r, r, r - 2f, bgPaint);
        canvas.drawCircle(r, r, r - 2f, ringPaint);
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float baseline = r - (fm.ascent + fm.descent) / 2f;
        canvas.drawText("⏎", r, baseline, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = ev.getRawX();
                lastTouchY = ev.getRawY();
                dragging = false;
                moved = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = ev.getRawX() - lastTouchX;
                float dy = ev.getRawY() - lastTouchY;
                if (Math.abs(dx) + Math.abs(dy) > 12f) {
                    moved = true;
                    if (!dragging) {
                        dragging = true;
                        dragLp = (FrameLayout.LayoutParams) getLayoutParams();
                    }
                }
                if (dragging && dragLp != null) {
                    dragLp.leftMargin += (int) dx;
                    dragLp.topMargin += (int) dy;
                    lastTouchX = ev.getRawX();
                    lastTouchY = ev.getRawY();
                    // 拖动中限制在父容器范围内（粗略：左右边缘吸附）
                    requestLayout();
                } else {
                    lastTouchX = ev.getRawX();
                    lastTouchY = ev.getRawY();
                }
                return true;
            case MotionEvent.ACTION_UP: {
                // 拖动结束：吸附到最近侧边（左/右边缘）
                if (dragging && getParent() instanceof FrameLayout && dragLp != null) {
                    FrameLayout host = (FrameLayout) getParent();
                    int half = getWidth() / 2;
                    boolean snapRight = dragLp.leftMargin + half > host.getWidth() / 2;
                    dragLp.leftMargin = snapRight
                            ? host.getWidth() - getWidth() - dp(8)
                            : dp(8);
                    int maxTop = host.getHeight() - getHeight() - dp(8);
                    if (dragLp.topMargin < dp(8)) dragLp.topMargin = dp(8);
                    if (dragLp.topMargin > maxTop) dragLp.topMargin = maxTop;
                    requestLayout();
                }
                if (!moved && onTap != null) onTap.run();
                dragging = false;
                return true;
            }
            default:
                return super.onTouchEvent(ev);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
