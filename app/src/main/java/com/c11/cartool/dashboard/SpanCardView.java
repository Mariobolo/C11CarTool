package com.c11.cartool.dashboard;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 卡片基类：统一圆角卡片底、标题行、来源角标与三种状态（普通/激活/无数据）。
 * 子类在 {@link #buildContent()} 中填充内容区。
 */
public abstract class SpanCardView extends FrameLayout {

    protected final Context ctx;
    protected FrameLayout contentContainer;
    private TextView titleView;
    private TextView badgeView;

    private GradientDrawable bg;

    public SpanCardView(Context context, String title, int cornerDp) {
        super(context);
        this.ctx = context;
        setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setPadding(dp(10), dp(8), dp(10), dp(6));

        // 圆角卡片底
        bg = new GradientDrawable();
        bg.setCornerRadius(dp(cornerDp));
        bg.setColor(DashboardTheme.CARD);
        setBackground(bg);

        // 标题行
        if (title != null && !title.isEmpty()) {
            titleView = new TextView(context);
            titleView.setText(title);
            titleView.setTextColor(DashboardTheme.DIM);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            titleView.setGravity(Gravity.CENTER_HORIZONTAL);
            LayoutParams tlp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            tlp.gravity = Gravity.TOP;
            addView(titleView, tlp);
        }

        // 内容区（标题之下）
        contentContainer = new FrameLayout(context);
        LayoutParams clp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        clp.topMargin = titleView == null ? 0 : dp(18);
        addView(contentContainer, clp);

        // 来源角标（右下角小字）
        badgeView = new TextView(context);
        badgeView.setTextColor(DashboardTheme.FAINT);
        badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
        LayoutParams blp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM | Gravity.END;
        addView(badgeView, blp);

        // 注意：buildContent 不能在此构造函数中调用 —— 子类字段初始化器（数组/集合/字符串）
        // 在 super() 返回之后才执行，构造函数里调用会写入 null 数组导致 NPE。
        // 内容构建推迟到视图挂载时（onAttachedToWindow），届时子类字段已全部初始化。
    }

    private boolean contentBuilt = false;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!contentBuilt) {
            contentBuilt = true;
            buildContent();
        }
    }

    /** 子类填充内容区 */
    protected abstract void buildContent();

    /** 内容区（子类往这里加内容） */
    protected FrameLayout content() { return contentContainer; }

    // ── 状态样式 ──

    public void setState(State s) {
        bg.setColor(s == State.ACTIVE ? DashboardTheme.CARD_ACT
                : s == State.ERROR ? DashboardTheme.CARD_ERR
                : DashboardTheme.CARD);
    }

    public void setBadge(String text) {
        badgeView.setText(text == null ? "" : text);
    }

    public void setTitle(String t) {
        if (titleView != null) titleView.setText(t);
    }

    public enum State { NORMAL, ACTIVE, ERROR }

    // ── 工具 ──

    protected int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    protected int sp(float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, v, getResources().getDisplayMetrics()));
    }

    protected TextView text(int sizeSp, int color, int style) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        tv.setTextColor(color);
        if (style > 0) tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }
}
